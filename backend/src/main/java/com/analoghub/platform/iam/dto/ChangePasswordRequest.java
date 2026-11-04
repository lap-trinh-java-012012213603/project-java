package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        @Size(max = 72, message = "Mật khẩu hiện tại không hợp lệ")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#-])[A-Za-z\\d@$!%*?&_#-]{8,72}$",
                message = "Mật khẩu mới phải bao gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt"
        )
        String newPassword,

        @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
        @Size(max = 72, message = "Xác nhận mật khẩu mới không hợp lệ")
        String confirmNewPassword
) {
}
