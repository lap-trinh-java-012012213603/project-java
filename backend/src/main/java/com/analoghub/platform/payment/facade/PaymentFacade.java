package com.analoghub.platform.payment.facade;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.payment.dto.AdminLedgerEntryDto;
import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import com.analoghub.platform.payment.dto.EscrowSnapshotDto;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutAdminItemDto;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentFacade {

    UUID hold(EscrowHoldRequest request);

    void release(UUID referenceId, String referenceType);

    void partialRefundBlankRoll(UUID orderId, UUID rollId, UUID customerId, BigDecimal refundScanFeeVnd, String reason);

    void fullRefund(UUID referenceId, String referenceType, String reason);

    void freezeForDispute(UUID referenceId, String referenceType, UUID disputeId);

    void resolveDisputeSplit(UUID referenceId, String referenceType, BigDecimal refundToBuyerVnd, BigDecimal releaseToSellerVnd);

    default Optional<EscrowSnapshotDto> getEscrowSnapshot(UUID referenceId, String referenceType) {
        return Optional.empty();
    }

    default void releaseEscrow(UUID referenceId, String referenceType) {
        release(referenceId, referenceType);
    }

    default void processBlankRollPartialRefund(UUID orderId, UUID rollId, UUID customerId, BigDecimal refundScanFeeVnd, String reason) {
        partialRefundBlankRoll(orderId, rollId, customerId, refundScanFeeVnd, reason);
    }

    default void refundEscrow(UUID referenceId, String referenceType, String reason) {
        fullRefund(referenceId, referenceType, reason);
    }

    default PageResponse<PayoutAdminItemDto> queryPayoutRequests(
            String status,
            UUID requesterUserId,
            int page,
            int size
    ) {
        return PageResponse.from(List.of(), page, size, 0, 0);
    }

    default PayoutSettlementDto completePayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            String bankTransferRef,
            String reviewNote
    ) {
        throw new UnsupportedOperationException("completePayoutRequest not implemented");
    }

    default PayoutSettlementDto rejectPayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            String reviewNote
    ) {
        throw new UnsupportedOperationException("rejectPayoutRequest not implemented");
    }

    default PageResponse<AdminLedgerEntryDto> queryLedgerEntries(
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
        return PageResponse.from(List.of(), page, size, 0, 0);
    }

    default LedgerReconciliationReportDto reconcileLedger() {
        throw new UnsupportedOperationException("reconcileLedger not implemented");
    }
}
