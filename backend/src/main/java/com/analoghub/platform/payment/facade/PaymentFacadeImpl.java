package com.analoghub.platform.payment.facade;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.payment.dto.AdminLedgerEntryDto;
import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import com.analoghub.platform.payment.dto.EscrowSnapshotDto;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutAdminItemDto;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;
import com.analoghub.platform.payment.service.EscrowHoldService;
import com.analoghub.platform.payment.service.WalletAndPayoutService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Concrete implementation of {@link PaymentFacade} delegating Escrow operations to {@link EscrowHoldService}
 * and Admin payout/ledger reconciliation operations to {@link WalletAndPayoutService}.
 */
@Service
public class PaymentFacadeImpl implements PaymentFacade {

    private final EscrowHoldService escrowHoldService;
    private final @Nullable WalletAndPayoutService walletAndPayoutService;

    public PaymentFacadeImpl(EscrowHoldService escrowHoldService) {
        this(escrowHoldService, null);
    }

    @Autowired
    public PaymentFacadeImpl(
            EscrowHoldService escrowHoldService,
            @Nullable WalletAndPayoutService walletAndPayoutService
    ) {
        this.escrowHoldService = escrowHoldService;
        this.walletAndPayoutService = walletAndPayoutService;
    }

    @Override
    @Transactional
    public UUID hold(EscrowHoldRequest request) {
        return escrowHoldService.hold(request);
    }

    @Override
    @Transactional
    public void release(UUID referenceId, String referenceType) {
        escrowHoldService.release(referenceId, referenceType);
    }

    @Override
    @Transactional
    public void partialRefundBlankRoll(
            UUID orderId,
            UUID rollId,
            UUID customerId,
            BigDecimal refundScanFeeVnd,
            String reason
    ) {
        escrowHoldService.partialRefundBlankRoll(orderId, rollId, customerId, refundScanFeeVnd, reason);
    }

    @Override
    @Transactional
    public void fullRefund(UUID referenceId, String referenceType, String reason) {
        escrowHoldService.fullRefund(referenceId, referenceType, reason);
    }

    @Override
    @Transactional
    public void freezeForDispute(UUID referenceId, String referenceType, UUID disputeId) {
        escrowHoldService.freezeForDispute(referenceId, referenceType, disputeId);
    }

    @Override
    @Transactional
    public void resolveDisputeSplit(
            UUID referenceId,
            String referenceType,
            BigDecimal refundToBuyerVnd,
            BigDecimal releaseToSellerVnd
    ) {
        escrowHoldService.resolveDisputeSplit(referenceId, referenceType, refundToBuyerVnd, releaseToSellerVnd);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EscrowSnapshotDto> getEscrowSnapshot(UUID referenceId, String referenceType) {
        return escrowHoldService.getEscrowSnapshot(referenceId, referenceType);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutAdminItemDto> queryPayoutRequests(
            String status,
            UUID requesterUserId,
            int page,
            int size
    ) {
        return requireWalletAndPayoutService().queryPayoutRequests(status, requesterUserId, page, size);
    }

    @Override
    @Transactional
    public PayoutSettlementDto completePayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            String bankTransferRef,
            String reviewNote
    ) {
        return requireWalletAndPayoutService().completePayoutRequest(payoutId, adminUserId, bankTransferRef, reviewNote);
    }

    @Override
    @Transactional
    public PayoutSettlementDto rejectPayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            String reviewNote
    ) {
        return requireWalletAndPayoutService().rejectPayoutRequest(payoutId, adminUserId, reviewNote);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminLedgerEntryDto> queryLedgerEntries(
            UUID walletId,
            UUID ownerUserId,
            UUID journalGroupId,
            String entryDirection,
            String entryType,
            Instant fromTime,
            Instant toTime,
            int page,
            int size
    ) {
        return requireWalletAndPayoutService().queryLedgerEntries(
                walletId,
                ownerUserId,
                journalGroupId,
                entryDirection,
                entryType,
                fromTime,
                toTime,
                page,
                size
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerReconciliationReportDto reconcileLedger() {
        return requireWalletAndPayoutService().reconcileLedger();
    }

    private WalletAndPayoutService requireWalletAndPayoutService() {
        if (walletAndPayoutService == null) {
            throw new IllegalStateException("WalletAndPayoutService is required for payout and ledger operations");
        }
        return walletAndPayoutService;
    }
}
