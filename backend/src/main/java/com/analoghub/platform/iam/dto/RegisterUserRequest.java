package com.analoghub.platform.iam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
        String email,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
                regexp = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$",
                message = "Số điện thoại di động Việt Nam không hợp lệ"
        )
        String phoneNumber,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#-])[A-Za-z\\d@$!%*?&_#-]{8,72}$",
                message = "Mật khẩu phải bao gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt"
        )
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 120, message = "Họ và tên phải từ 2 đến 120 ký tự")
        String fullName,

        @Valid
        StylePreferencesDto stylePreferences
) {
    public RegisterUserRequest {
        if (email != null) {
            email = email.trim();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
        }
        if (fullName != null) {
            fullName = fullName.trim();
        }
    }
}
