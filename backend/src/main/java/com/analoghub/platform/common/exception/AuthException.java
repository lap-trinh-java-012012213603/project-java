package com.analoghub.platform.common.exception;

public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public static AuthException unauthorized() {
        return new AuthException(ErrorCode.UNAUTHORIZED);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(ErrorCode.INVALID_CREDENTIALS);
    }

    public static AuthException accessTokenExpired() {
        return new AuthException(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    public static AuthException refreshTokenExpired() {
        return new AuthException(ErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    public static AuthException oauth2Failed(String provider, String reason) {
        return new AuthException(
                ErrorCode.OAUTH2_AUTHENTICATION_FAILED,
                String.format("Đăng nhập qua %s thất bại: %s", provider, reason)
        );
    }

    public static AuthException accountLocked() {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED_OR_DISABLED);
    }

    public static AuthException accessDenied() {
        return new AuthException(ErrorCode.ACCESS_DENIED);
    }
}