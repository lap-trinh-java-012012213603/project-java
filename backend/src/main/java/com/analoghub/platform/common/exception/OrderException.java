package com.analoghub.platform.common.exception;

public class OrderException extends BusinessException {

    public OrderException(ErrorCode errorCode) {
        super(errorCode);
    }

    public OrderException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static OrderException emptyRolls() {
        return new OrderException(ErrorCode.EMPTY_ORDER_ROLLS);
    }

    public static OrderException cannotCancel(String currentStatus) {
        return new OrderException(
                ErrorCode.ORDER_CANNOT_BE_CANCELLED,
                String.format("Đơn hàng đang ở trạng thái [%s], không thể hủy", currentStatus)
        );
    }

    public static OrderException alreadyPaid(String orderCode) {
        return new OrderException(
                ErrorCode.ORDER_ALREADY_PAID,
                String.format("Đơn hàng [%s] đã được thanh toán trước đó", orderCode)
        );
    }

    public static OrderException invalidRollTransition(Object fromStatus, Object toStatus) {
        return new OrderException(
                ErrorCode.INVALID_ROLL_STATE_TRANSITION,
                String.format("Không thể chuyển trạng thái cuộn phim từ [%s] sang [%s]", fromStatus, toStatus)
        );
    }

    public static OrderException invalidPaymentSignature() {
        return new OrderException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
    }

    public static OrderException paymentFailed(String reason) {
        return new OrderException(
                ErrorCode.PAYMENT_FAILED,
                String.format("Giao dịch thanh toán thất bại: %s", reason)
        );
    }

    public static OrderException deliveryAddressRequired() {
        return new OrderException(ErrorCode.DELIVERY_ADDRESS_REQUIRED);
    }

    public static OrderException invalidLogisticsWebhook(String detail) {
        return new OrderException(
                ErrorCode.INVALID_LOGISTICS_WEBHOOK,
                String.format("Dữ liệu webhook vận chuyển không hợp lệ: %s", detail)
        );
    }
}