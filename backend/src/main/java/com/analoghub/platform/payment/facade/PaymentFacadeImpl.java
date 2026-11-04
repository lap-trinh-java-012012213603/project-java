package com.analoghub.platform.payment.facade;

import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import com.analoghub.platform.payment.service.EscrowHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Concrete implementation of {@link PaymentFacade} delegating Escrow hold, blank-roll partial refund,
 * release with platform commission deduction, full refund, and dispute settlement to {@link EscrowHoldService}.
 */
@Service
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {

    private final EscrowHoldService escrowHoldService;

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
}
