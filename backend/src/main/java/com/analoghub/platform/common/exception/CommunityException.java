package com.analoghub.platform.common.exception;

public class CommunityException extends BusinessException {

    public CommunityException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static CommunityException eventCapacityFull() {
        return new CommunityException(ErrorCode.EVENT_CAPACITY_FULL);
    }

    public static CommunityException alreadyJoinedEvent() {
        return new CommunityException(ErrorCode.ALREADY_JOINED_EVENT);
    }
}