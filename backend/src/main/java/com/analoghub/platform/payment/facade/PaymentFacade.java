package com.analoghub.platform.payment.facade;

import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentFacade {

    UUID hold(EscrowHoldRequest request);

    void release(UUID referenceId, String referenceType);

    void partialRefundBlankRoll(UUID orderId, UUID rollId, UUID customerId, BigDecimal refundScanFeeVnd, String reason);

    void fullRefund(UUID referenceId, String referenceType, String reason);

    void freezeForDispute(UUID referenceId, String referenceType, UUID disputeId);

    void resolveDisputeSplit(UUID referenceId, String referenceType, BigDecimal refundToBuyerVnd, BigDecimal releaseToSellerVnd);

    default void releaseEscrow(UUID referenceId, String referenceType) {
        release(referenceId, referenceType);
    }

    default void processBlankRollPartialRefund(UUID orderId, UUID rollId, UUID customerId, BigDecimal refundScanFeeVnd, String reason) {
        partialRefundBlankRoll(orderId, rollId, customerId, refundScanFeeVnd, reason);
    }

    default void refundEscrow(UUID referenceId, String referenceType, String reason) {
        fullRefund(referenceId, referenceType, reason);
    }
}
