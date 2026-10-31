package com.analoghub.platform.common.exception;

public class AiServiceException extends BusinessException {

    public AiServiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AiServiceException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public AiServiceException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }

    public static AiServiceException promptTooLong(int maxChars) {
        return new AiServiceException(
                ErrorCode.AI_PROMPT_TOO_LONG,
                String.format("Câu hỏi vượt quá giới hạn %d ký tự cho phép", maxChars)
        );
    }

    // Đã đổi từ openAiProviderError -> geminiProviderError
    public static AiServiceException geminiProviderError(Throwable cause) {
        return new AiServiceException(
                ErrorCode.AI_PROVIDER_ERROR,
                ErrorCode.AI_PROVIDER_ERROR.getMessage(),
                cause
        );
    }

    public static AiServiceException imageQcFailed(String photoId, Throwable cause) {
        return new AiServiceException(
                ErrorCode.IMAGE_QC_ANALYSIS_FAILED,
                String.format("Lỗi phân tích chất lượng điểm ảnh cho photoId [%s]", photoId),
                cause
        );
    }
}