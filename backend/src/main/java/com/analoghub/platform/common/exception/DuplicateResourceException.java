package com.analoghub.platform.common.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DuplicateResourceException(ErrorCode errorCode, String value) {
        super(errorCode, String.format("%s: [%s]", errorCode.getMessage(), value));
    }

    public static DuplicateResourceException email(String email) {
        return new DuplicateResourceException(ErrorCode.EMAIL_ALREADY_EXISTS, email);
    }

    public static DuplicateResourceException phone(String phone) {
        return new DuplicateResourceException(ErrorCode.PHONE_ALREADY_EXISTS, phone);
    }

    public static DuplicateResourceException labSlug(String slug) {
        return new DuplicateResourceException(ErrorCode.LAB_SLUG_ALREADY_EXISTS, slug);
    }

    public static DuplicateResourceException rollCode(String rollCode) {
        return new DuplicateResourceException(ErrorCode.ROLL_CODE_ALREADY_EXISTS, rollCode);
    }
}