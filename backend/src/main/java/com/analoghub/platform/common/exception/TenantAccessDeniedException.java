package com.analoghub.platform.common.exception;

import java.util.UUID;

public class TenantAccessDeniedException extends BusinessException {

    public TenantAccessDeniedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TenantAccessDeniedException(UUID targetLabId) {
        super(
                ErrorCode.TENANT_ACCESS_DENIED,
                String.format("Vi phạm cô lập dữ liệu: Bạn không thuộc quyền quản lý của Film Lab [%s]", targetLabId)
        );
    }

    public static TenantAccessDeniedException missingContext() {
        return new TenantAccessDeniedException(ErrorCode.TENANT_CONTEXT_MISSING);
    }
}