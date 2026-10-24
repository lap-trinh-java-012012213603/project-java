package com.analoghub.platform.common.exception;

public class LabException extends BusinessException {

    public LabException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LabException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static LabException temporarilyClosed(String labName) {
        return new LabException(
                ErrorCode.LAB_TEMPORARILY_CLOSED,
                String.format("Film Lab [%s] hiện đang tạm ngưng nhận đơn mới", labName)
        );
    }

    public static LabException processNotSupported(String processOrFormat) {
        return new LabException(
                ErrorCode.LAB_PROCESS_NOT_SUPPORTED,
                String.format("Film Lab không hỗ trợ hệ xử lý hoặc khổ phim: %s", processOrFormat)
        );
    }

    public static LabException alreadyReviewed() {
        return new LabException(ErrorCode.ALREADY_REVIEWED_LAB);
    }
}