package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.DuplicateResourceException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.PayoutRequest;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.CreatePayoutRequest;
import com.analoghub.platform.payment.dto.PayoutResponse;
import com.analoghub.platform.payment.dto.PayoutSummaryDto;
import com.analoghub.platform.payment.dto.WalletLedgerEntryDto;
import com.analoghub.platform.payment.dto.WalletSummaryResponse;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PayoutRequestRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletAndPayoutService {

    private static final int MAX_PENDING_PAYOUTS = 3;
    private static final BigDecimal MIN_PAYOUT_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal MAX_PAYOUT_AMOUNT = new BigDecimal("200000000.00");
    private static final BigDecimal PAYOUT_STEP_VND = new BigDecimal("1000");

    private static final Set<EscrowStatus> PENDING_RECEIVABLE_ESCROW_STATUSES = EnumSet.of(
            EscrowStatus.HELD,
            EscrowStatus.PARTIALLY_REFUNDED
    );

    private static final Set<PayoutRequestStatus> ACTIVE_PAYOUT_STATUSES = EnumSet.of(
            PayoutRequestStatus.PENDING_APPROVAL,
            PayoutRequestStatus.PROCESSING
    );

    private final WalletRepository walletRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final WalletLedgerService walletLedgerService;

    @Transactional
    public WalletSummaryResponse getMyWallet() {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        WalletType defaultType = currentUser.hasRole("LAB_OWNER")
                ? WalletType.LAB_WALLET
                : WalletType.USER_WALLET;

        Wallet wallet = walletLedgerService.getOrCreateUserWallet(currentUser.getUserId(), defaultType);
        BigDecimal pendingReceivable = escrowHoldRepository.sumPendingReceivableByPayeeUserId(
                currentUser.getUserId(),
                PENDING_RECEIVABLE_ESCROW_STATUSES
        );
        BigDecimal normalizedPending = pendingReceivable != null
                ? pendingReceivable.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("0.00");

        return new WalletSummaryResponse(
                wallet.getId(),
                wallet.getOwnerUserId(),
                wallet.getWalletType(),
                wallet.getAvailableBalanceVnd(),
                wallet.getLockedBalanceVnd(),
                wallet.getTotalBalanceVnd(),
                normalizedPending,
                wallet.getCurrency(),
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt() : wallet.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletLedgerEntryDto> getMyWalletLedger(
            LedgerEntryDirection entryDirection,
            LedgerEntryType entryType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "fromDate must be before or equal to toDate"
            );
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid pagination parameters: page must be >= 0 and size between 1 and 100"
            );
        }

        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        Optional<Wallet> walletOpt = walletRepository.findByOwnerUserId(currentUserId);
        if (walletOpt.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        Instant fromTime = fromDate != null
                ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;
        Instant toTime = toDate != null
                ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<WalletLedgerEntry> ledgerPage = walletLedgerService.searchWalletLedger(
                walletOpt.get().getId(),
                entryDirection,
                entryType,
                fromTime,
                toTime,
                pageable
        );

        List<WalletLedgerEntryDto> items = ledgerPage.getContent().stream()
                .map(this::toLedgerEntryDto)
                .toList();

        return PageResponse.from(
                items,
                ledgerPage.getNumber(),
                ledgerPage.getSize(),
                (int) ledgerPage.getTotalElements(),
                ledgerPage.getTotalPages()
        );
    }

    @Transactional
    public PayoutResponse requestPayout(CreatePayoutRequest request) {
        Objects.requireNonNull(request, "CreatePayoutRequest must not be null");
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        BigDecimal amountVnd = validateAndNormalizePayoutAmount(request.amountVnd());

        Wallet wallet = walletRepository.findByOwnerUserIdForUpdate(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Wallet not found for current user"
                ));

        long pendingPayoutCount = payoutRequestRepository.countByRequesterUserIdAndStatusIn(
                currentUserId,
                ACTIVE_PAYOUT_STATUSES
        );
        if (pendingPayoutCount >= MAX_PENDING_PAYOUTS) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Maximum number of pending payout requests (3) reached"
            );
        }

        if (wallet.getAvailableBalanceVnd().compareTo(amountVnd) < 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "Insufficient available balance in wallet for payout"
            );
        }

        PayoutRequest payoutRequest = PayoutRequest.builder()
                .walletId(wallet.getId())
                .requesterUserId(currentUserId)
                .amountVnd(amountVnd)
                .bankCode(request.bankCode().trim())
                .bankAccountNo(request.bankAccountNo().trim())
                .bankAccountName(request.bankAccountName().trim())
                .status(PayoutRequestStatus.PENDING_APPROVAL)
                .build();
        PayoutRequest savedPayout = payoutRequestRepository.save(payoutRequest);

        walletLedgerService.recordPayoutLock(wallet.getId(), amountVnd, savedPayout.getId());

        return new PayoutResponse(
                savedPayout.getId(),
                wallet.getId(),
                currentUserId,
                amountVnd,
                savedPayout.getBankCode(),
                savedPayout.getBankAccountNo(),
                savedPayout.getBankAccountName(),
                savedPayout.getStatus(),
                wallet.getAvailableBalanceVnd(),
                wallet.getLockedBalanceVnd(),
                savedPayout.getCreatedAt() != null ? savedPayout.getCreatedAt() : Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutSummaryDto> getMyPayouts(PayoutRequestStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid pagination parameters: page must be >= 0 and size between 1 and 50"
            );
        }

        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        Page<PayoutRequest> payoutPage = (status != null)
                ? payoutRequestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(currentUserId, status, pageable)
                : payoutRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(currentUserId, pageable);

        List<PayoutSummaryDto> items = payoutPage.getContent().stream()
                .map(this::toPayoutSummaryDto)
                .toList();

        return PageResponse.from(
                items,
                payoutPage.getNumber(),
                payoutPage.getSize(),
                (int) payoutPage.getTotalElements(),
                payoutPage.getTotalPages()
        );
    }

    private BigDecimal validateAndNormalizePayoutAmount(BigDecimal rawAmount) {
        if (rawAmount == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amountVnd is required");
        }
        BigDecimal scaled = rawAmount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(MIN_PAYOUT_AMOUNT) < 0 || scaled.compareTo(MAX_PAYOUT_AMOUNT) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Payout amount must be between 100,000.00 VND and 200,000,000.00 VND"
            );
        }
        if (scaled.remainder(PAYOUT_STEP_VND).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Payout amount must be a multiple of 1,000 VND"
            );
        }
        return scaled;
    }

    private WalletLedgerEntryDto toLedgerEntryDto(WalletLedgerEntry entry) {
        return new WalletLedgerEntryDto(
                entry.getId(),
                entry.getJournalGroupId(),
                entry.getWalletId(),
                entry.getPaymentTxnId(),
                entry.getEntryDirection(),
                entry.getEntryType(),
                entry.getAmountVnd(),
                entry.getBalanceAfterVnd(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getCreatedAt()
        );
    }

    private PayoutSummaryDto toPayoutSummaryDto(PayoutRequest payout) {
        return new PayoutSummaryDto(
                payout.getId(),
                payout.getAmountVnd(),
                payout.getBankCode(),
                payout.getBankAccountNo(),
                payout.getBankAccountName(),
                payout.getStatus(),
                payout.getBankTransferRef(),
                payout.getCreatedAt(),
                payout.getCompletedAt()
        );
    }
}
