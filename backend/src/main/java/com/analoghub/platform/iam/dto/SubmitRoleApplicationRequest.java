package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitRoleApplicationRequest(
        @NotNull(message = "Vai trò xin nâng cấp không được để trống")
        RoleCode requestedRole,

        @NotNull(message = "Hồ sơ năng lực (portfolioPayload) không được để trống")
        @NotEmpty(message = "Hồ sơ năng lực (portfolioPayload) không được để rỗng")
        Map<String, Object> portfolioPayload
) {
}
