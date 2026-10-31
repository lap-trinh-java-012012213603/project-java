package com.analoghub.platform.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Object identifier) {
        super(errorCode, String.format("%s (ID/Mã: %s)", errorCode.getMessage(), identifier));
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("Không tìm thấy %s với định danh: %s", resourceName, identifier));
    }

    // Static Factory cho từng thực thể trong hệ thống
    public static ResourceNotFoundException user(Object id) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, id);
    }

    public static ResourceNotFoundException lab(Object idOrSlug) {
        return new ResourceNotFoundException(ErrorCode.LAB_NOT_FOUND, idOrSlug);
    }

    public static ResourceNotFoundException labService(Object id) {
        return new ResourceNotFoundException(ErrorCode.LAB_SERVICE_NOT_FOUND, id);
    }

    public static ResourceNotFoundException filmStock(Object id) {
        return new ResourceNotFoundException(ErrorCode.FILM_STOCK_NOT_FOUND, id);
    }

    public static ResourceNotFoundException order(Object idOrCode) {
        return new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, idOrCode);
    }

    public static ResourceNotFoundException roll(Object idOrCode) {
        return new ResourceNotFoundException(ErrorCode.ROLL_NOT_FOUND, idOrCode);
    }

    public static ResourceNotFoundException scanPhoto(Object id) {
        return new ResourceNotFoundException(ErrorCode.SCAN_PHOTO_NOT_FOUND, id);
    }

    public static ResourceNotFoundException listing(Object id) {
        return new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND, id);
    }

    public static ResourceNotFoundException offer(Object id) {
        return new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, id);
    }

    public static ResourceNotFoundException post(Object id) {
        return new ResourceNotFoundException(ErrorCode.POST_NOT_FOUND, id);
    }

    public static ResourceNotFoundException comment(Object id) {
        return new ResourceNotFoundException(ErrorCode.COMMENT_NOT_FOUND, id);
    }
}