package com.analoghub.platform.common.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                ErrorCode.AI_RATE_LIMIT_EXCEEDED,
                String.format("Bạn đã vượt quá giới hạn sử dụng AI. Vui lòng thử lại sau %d giây.", retryAfterSeconds)
        );
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(ErrorCode errorCode, long retryAfterSeconds, String message) {
        super(errorCode, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
