package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.RefundRequest;
import com.analoghub.platform.payment.domain.RefundRequestStatus;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import com.analoghub.platform.payment.event.BlankRollRefundProcessedEvent;
import com.analoghub.platform.payment.event.EscrowReleasedEvent;
import com.analoghub.platform.payment.event.RefundProcessedEvent;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.RefundRequestRepository;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Core domain service managing Escrow Holds ({@code pay_escrow_holds}), Partial Blank Roll Refunds,
 * Escrow Releases with Platform Commission deduction, Full Refunds, and Dispute Split settlements.
 */
@Service
@RequiredArgsConstructor
public class EscrowHoldService {

    public static final String REF_TYPE_LAB_ORDER = "LAB_ORDER";
    public static final String REF_TYPE_MARKETPLACE_ORDER = "MARKETPLACE_ORDER";

    private final EscrowHoldRepository escrowHoldRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final WalletLedgerService walletLedgerService;
    private final PlatformConfigFacade platformConfigFacade;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UUID hold(EscrowHoldRequest request) {
        Objects.requireNonNull(request, "EscrowHoldRequest must not be null");
        Objects.requireNonNull(request.paymentTxnId(), "paymentTxnId must not be null");
        Objects.requireNonNull(request.referenceId(), "referenceId must not be null");
        Objects.requireNonNull(request.payerUserId(), "payerUserId must not be null");
        Objects.requireNonNull(request.payeeUserId(), "payeeUserId must not be null");
        String normalizedRefType = normalizeReferenceType(request.referenceType());
        BigDecimal grossAmount = normalizePositiveAmount(request.grossAmountVnd(), "grossAmountVnd");

        Optional<EscrowHold> existing = escrowHoldRepository.findByReferenceId(request.referenceId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        EscrowHold escrowHold = EscrowHold.builder()
                .paymentTxnId(request.paymentTxnId())
                .referenceType(normalizedRefType)
                .referenceId(request.referenceId())
                .payerUserId(request.payerUserId())
                .payeeUserId(request.payeeUserId())
                .grossAmountVnd(grossAmount)
                .refundedAmountVnd(new BigDecimal("0.00"))
                .commissionFeeVnd(new BigDecimal("0.00"))
                .netPayoutAmountVnd(new BigDecimal("0.00"))
                .status(EscrowStatus.HELD)
                .build();

        EscrowHold saved = escrowHoldRepository.save(escrowHold);
        return saved.getId();
    }

    /**
     * Immediately refunds {@code refundScanFeeVnd} (`scanFeeSnapshotVnd`) from {@code SYSTEM_ESCROW}
     * back to the customer's wallet while keeping the development fee held in Escrow for the Lab.
     */
    @Transactional
    public void partialRefundBlankRoll(
            UUID orderId,
            UUID rollId,
            UUID customerId,
            BigDecimal refundScanFeeVnd,
            String reason
    ) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(rollId, "rollId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        BigDecimal refundAmount = normalizePositiveAmount(refundScanFeeVnd, "refundScanFeeVnd");

        EscrowHold escrow = findEscrowForUpdate(REF_TYPE_LAB_ORDER, orderId);
        if (!escrow.getStatus().isReleasable()) {
            throw new IllegalStateException(
                    "Cannot process blank roll partial refund when Escrow status is " + escrow.getStatus()
            );
        }

        BigDecimal remainingBefore = escrow.getRemainingAmountVnd();
        if (refundAmount.compareTo(remainingBefore) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Blank roll scan fee refund (%s VND) exceeds remaining escrow balance (%s VND)",
                    refundAmount.toPlainString(),
                    remainingBefore.toPlainString()
            ));
        }

        walletLedgerService.recordBlankRollRefund(
                customerId,
                refundAmount,
                escrow.getPaymentTxnId(),
                rollId
        );

        BigDecimal updatedRefunded = escrow.getRefundedAmountVnd()
                .add(refundAmount)
                .setScale(2, RoundingMode.HALF_UP);
        escrow.setRefundedAmountVnd(updatedRefunded);

        Instant now = Instant.now();
        if (escrow.getRemainingAmountVnd().compareTo(BigDecimal.ZERO) == 0) {
            escrow.setStatus(EscrowStatus.REFUNDED_TO_PAYER);
            escrow.setSettledAt(now);
        } else {
            escrow.setStatus(EscrowStatus.PARTIALLY_REFUNDED);
        }
        escrowHoldRepository.save(escrow);

        RefundRequest refundRequest = RefundRequest.builder()
                .escrowId(escrow.getId())
                .refundReason(reason != null && !reason.isBlank() ? reason.trim() : "BLANK_ROLL_SCAN_FEE")
                .refundAmountVnd(refundAmount)
                .status(RefundRequestStatus.PROCESSED)
                .processedAt(now)
                .build();
        refundRequestRepository.save(refundRequest);

        eventPublisher.publishEvent(new BlankRollRefundProcessedEvent(
                escrow.getId(),
                orderId,
                rollId,
                customerId,
                refundAmount
        ));
    }

    /**
     * Releases the remaining Escrow balance (`gross_amount_vnd - refunded_amount_vnd`),
     * deducts platform commission via {@link PlatformConfigFacade}, credits net payout to Lab/Seller wallet,
     * credits commission fee to {@code SYSTEM_REVENUE}, and publishes {@link EscrowReleasedEvent}.
     */
    @Transactional
    public void release(UUID referenceId, String referenceType) {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        EscrowHold escrow = findEscrowForUpdate(referenceType, referenceId);

        if (escrow.getStatus() == EscrowStatus.RELEASED_TO_PAYEE
                || escrow.getStatus() == EscrowStatus.SPLIT_SETTLED) {
            return;
        }

        if (!escrow.getStatus().isReleasable()) {
            throw new IllegalStateException(
                    "Escrow cannot be released from status: " + escrow.getStatus()
            );
        }

        BigDecimal releasableGross = escrow.getRemainingAmountVnd();
        Instant now = Instant.now();
        if (releasableGross.compareTo(BigDecimal.ZERO) <= 0) {
            escrow.setStatus(EscrowStatus.REFUNDED_TO_PAYER);
            escrow.setSettledAt(now);
            escrowHoldRepository.save(escrow);
            return;
        }

        FeeScope feeScope = resolveFeeScope(escrow.getReferenceType());
        WalletType payeeWalletType = resolvePayeeWalletType(escrow.getReferenceType());

        BigDecimal rawCommission = platformConfigFacade.calculateCommissionFee(feeScope, releasableGross);
        BigDecimal commissionFee = normalizeCommissionFee(rawCommission, releasableGross);
        BigDecimal netPayout = releasableGross.subtract(commissionFee).setScale(2, RoundingMode.HALF_UP);

        walletLedgerService.recordEscrowRelease(
                escrow.getPayeeUserId(),
                payeeWalletType,
                releasableGross,
                commissionFee,
                escrow.getPaymentTxnId(),
                escrow.getReferenceType(),
                escrow.getReferenceId()
        );

        escrow.setCommissionFeeVnd(commissionFee);
        escrow.setNetPayoutAmountVnd(netPayout);
        escrow.setStatus(EscrowStatus.RELEASED_TO_PAYEE);
        escrow.setSettledAt(now);
        escrowHoldRepository.save(escrow);

        eventPublisher.publishEvent(new EscrowReleasedEvent(
                escrow.getId(),
                escrow.getReferenceType(),
                escrow.getReferenceId(),
                escrow.getPayeeUserId(),
                netPayout
        ));
    }

    /**
     * Refunds 100% of the remaining Escrow balance back to the payer's wallet
     * and publishes {@link RefundProcessedEvent}.
     */
    @Transactional
    public void fullRefund(UUID referenceId, String referenceType, String reason) {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        EscrowHold escrow = findEscrowForUpdate(referenceType, referenceId);

        if (escrow.getStatus() == EscrowStatus.REFUNDED_TO_PAYER) {
            return;
        }

        if (escrow.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot refund Escrow that is already settled in status: " + escrow.getStatus()
            );
        }

        BigDecimal remainingToRefund = escrow.getRemainingAmountVnd();
        if (remainingToRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("No remaining Escrow balance to refund");
        }

        walletLedgerService.recordFullRefund(
                escrow.getPayerUserId(),
                remainingToRefund,
                escrow.getPaymentTxnId(),
                escrow.getReferenceType(),
                escrow.getReferenceId()
        );

        Instant now = Instant.now();
        RefundRequest refundRequest = RefundRequest.builder()
                .escrowId(escrow.getId())
                .refundReason(reason != null && !reason.isBlank() ? reason.trim() : "FULL_REFUND")
                .refundAmountVnd(remainingToRefund)
                .status(RefundRequestStatus.PROCESSED)
                .processedAt(now)
                .build();
        RefundRequest savedRefund = refundRequestRepository.save(refundRequest);

        escrow.setRefundedAmountVnd(escrow.getGrossAmountVnd());
        escrow.setCommissionFeeVnd(new BigDecimal("0.00"));
        escrow.setNetPayoutAmountVnd(new BigDecimal("0.00"));
        escrow.setStatus(EscrowStatus.REFUNDED_TO_PAYER);
        escrow.setSettledAt(now);
        escrowHoldRepository.save(escrow);

        eventPublisher.publishEvent(new RefundProcessedEvent(
                savedRefund.getId(),
                escrow.getId(),
                escrow.getReferenceType(),
                escrow.getReferenceId(),
                remainingToRefund
        ));
    }

    /**
     * Freezes an active Escrow hold when a dispute is opened, preventing auto-release or standard release.
     */
    @Transactional
    public void freezeForDispute(UUID referenceId, String referenceType, UUID disputeId) {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        Objects.requireNonNull(disputeId, "disputeId must not be null");

        EscrowHold escrow = findEscrowForUpdate(referenceType, referenceId);
        if (escrow.getStatus() == EscrowStatus.FROZEN_BY_DISPUTE) {
            return;
        }
        if (escrow.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot freeze Escrow for dispute when already in terminal status: " + escrow.getStatus()
            );
        }

        escrow.setStatus(EscrowStatus.FROZEN_BY_DISPUTE);
        escrow.setAutoReleaseAt(null);
        escrowHoldRepository.save(escrow);
    }

    /**
     * Settles a disputed Escrow hold by splitting the remaining balance between buyer refund
     * and seller release (minus platform commission fee).
     */
    @Transactional
    public void resolveDisputeSplit(
            UUID referenceId,
            String referenceType,
            BigDecimal refundToBuyerVnd,
            BigDecimal releaseToSellerVnd
    ) {
        Objects.requireNonNull(referenceId, "referenceId must not be null");
        EscrowHold escrow = findEscrowForUpdate(referenceType, referenceId);

        if (escrow.getStatus() == EscrowStatus.SPLIT_SETTLED) {
            return;
        }
        if (escrow.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot resolve dispute split when Escrow is already in terminal status: " + escrow.getStatus()
            );
        }

        BigDecimal buyerRefund = normalizeNonNegativeAmount(refundToBuyerVnd, "refundToBuyerVnd");
        BigDecimal sellerGross = normalizeNonNegativeAmount(releaseToSellerVnd, "releaseToSellerVnd");
        BigDecimal totalSplit = buyerRefund.add(sellerGross).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = escrow.getRemainingAmountVnd();

        if (totalSplit.compareTo(remaining) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Dispute split sum (%s VND) must equal remaining Escrow balance (%s VND)",
                    totalSplit.toPlainString(),
                    remaining.toPlainString()
            ));
        }

        BigDecimal commissionFee = new BigDecimal("0.00");
        BigDecimal sellerNet = new BigDecimal("0.00");
        if (sellerGross.compareTo(BigDecimal.ZERO) > 0) {
            FeeScope feeScope = resolveFeeScope(escrow.getReferenceType());
            BigDecimal rawFee = platformConfigFacade.calculateCommissionFee(feeScope, sellerGross);
            commissionFee = normalizeCommissionFee(rawFee, sellerGross);
            sellerNet = sellerGross.subtract(commissionFee).setScale(2, RoundingMode.HALF_UP);
        }

        WalletType sellerWalletType = resolvePayeeWalletType(escrow.getReferenceType());
        walletLedgerService.recordDisputeSplit(
                escrow.getPayerUserId(),
                escrow.getPayeeUserId(),
                sellerWalletType,
                buyerRefund,
                sellerNet,
                commissionFee,
                escrow.getPaymentTxnId(),
                escrow.getReferenceType(),
                escrow.getReferenceId()
        );

        Instant now = Instant.now();
        if (buyerRefund.compareTo(BigDecimal.ZERO) > 0) {
            RefundRequest refundRequest = RefundRequest.builder()
                    .escrowId(escrow.getId())
                    .refundReason("DISPUTE_SPLIT_REFUND")
                    .refundAmountVnd(buyerRefund)
                    .status(RefundRequestStatus.PROCESSED)
                    .processedAt(now)
                    .build();
            RefundRequest savedRefund = refundRequestRepository.save(refundRequest);

            eventPublisher.publishEvent(new RefundProcessedEvent(
                    savedRefund.getId(),
                    escrow.getId(),
                    escrow.getReferenceType(),
                    escrow.getReferenceId(),
                    buyerRefund
            ));
        }

        if (sellerNet.compareTo(BigDecimal.ZERO) > 0) {
            eventPublisher.publishEvent(new EscrowReleasedEvent(
                    escrow.getId(),
                    escrow.getReferenceType(),
                    escrow.getReferenceId(),
                    escrow.getPayeeUserId(),
                    sellerNet
            ));
        }

        escrow.setRefundedAmountVnd(
                escrow.getRefundedAmountVnd().add(buyerRefund).setScale(2, RoundingMode.HALF_UP)
        );
        escrow.setCommissionFeeVnd(commissionFee);
        escrow.setNetPayoutAmountVnd(sellerNet);
        if (buyerRefund.compareTo(BigDecimal.ZERO) > 0 && sellerGross.compareTo(BigDecimal.ZERO) > 0) {
            escrow.setStatus(EscrowStatus.SPLIT_SETTLED);
        } else if (buyerRefund.compareTo(BigDecimal.ZERO) > 0) {
            escrow.setStatus(EscrowStatus.REFUNDED_TO_PAYER);
        } else {
            escrow.setStatus(EscrowStatus.RELEASED_TO_PAYEE);
        }
        escrow.setSettledAt(now);
        escrowHoldRepository.save(escrow);
    }

    private EscrowHold findEscrowForUpdate(String referenceType, UUID referenceId) {
        if (referenceType != null && !referenceType.isBlank()) {
            String normalizedType = normalizeReferenceType(referenceType);
            Optional<EscrowHold> byTypeAndId = escrowHoldRepository
                    .findByReferenceTypeAndReferenceIdForUpdate(normalizedType, referenceId);
            if (byTypeAndId.isPresent()) {
                return byTypeAndId.get();
            }
        }
        return escrowHoldRepository.findByReferenceIdForUpdate(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Escrow hold not found for referenceId: " + referenceId
                ));
    }

    private static String normalizeReferenceType(String referenceType) {
        if (referenceType == null || referenceType.isBlank()) {
            throw new IllegalArgumentException("referenceType must not be blank");
        }
        return referenceType.trim().toUpperCase(Locale.ROOT);
    }

    private static FeeScope resolveFeeScope(String referenceType) {
        String normalized = normalizeReferenceType(referenceType);
        return switch (normalized) {
            case REF_TYPE_LAB_ORDER -> FeeScope.LAB_ORDER;
            case REF_TYPE_MARKETPLACE_ORDER, "MARKETPLACE_SALE" -> FeeScope.MARKETPLACE_SALE;
            case "MARKETPLACE_SWAP" -> FeeScope.MARKETPLACE_SWAP;
            case "WORKSHOP_TICKET" -> FeeScope.WORKSHOP_TICKET;
            default -> FeeScope.LAB_ORDER;
        };
    }

    private static WalletType resolvePayeeWalletType(String referenceType) {
        String normalized = normalizeReferenceType(referenceType);
        return REF_TYPE_LAB_ORDER.equals(normalized)
                ? WalletType.LAB_WALLET
                : WalletType.USER_WALLET;
    }

    private static BigDecimal normalizeCommissionFee(BigDecimal rawFee, BigDecimal grossAmount) {
        if (rawFee == null || rawFee.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.00");
        }
        BigDecimal scaled = rawFee.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(grossAmount) > 0) {
            return grossAmount;
        }
        return scaled;
    }

    private static BigDecimal normalizePositiveAmount(BigDecimal amount, String fieldName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be strictly positive (> 0)");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeNonNegativeAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            return new BigDecimal("0.00");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
