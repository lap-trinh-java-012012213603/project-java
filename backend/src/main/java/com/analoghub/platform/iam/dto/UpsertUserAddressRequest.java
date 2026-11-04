package com.analoghub.platform.iam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertUserAddressRequest(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(min = 2, max = 120, message = "Tên người nhận phải từ 2 đến 120 ký tự")
        String recipientName,

        @NotBlank(message = "Số điện thoại người nhận không được để trống")
        @Pattern(
                regexp = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$",
                message = "Số điện thoại người nhận không hợp lệ"
        )
        String recipientPhone,

        @NotBlank(message = "Địa chỉ chi tiết không được để trống")
        @Size(min = 10, max = 300, message = "Địa chỉ chi tiết phải từ 10 đến 300 ký tự")
        String fullAddress,

        @NotBlank(message = "Mã phường/xã không được để trống")
        @Size(max = 20, message = "Mã phường/xã tối đa 20 ký tự")
        String wardCode,

        @NotBlank(message = "Mã quận/huyện không được để trống")
        @Size(max = 20, message = "Mã quận/huyện tối đa 20 ký tự")
        String districtCode,

        @NotBlank(message = "Mã tỉnh/thành phố không được để trống")
        @Size(max = 20, message = "Mã tỉnh/thành phố tối đa 20 ký tự")
        String provinceCode,

        @DecimalMin(value = "-90.0", message = "Vĩ độ (latitude) phải từ -90.0 đến 90.0")
        @DecimalMax(value = "90.0", message = "Vĩ độ (latitude) phải từ -90.0 đến 90.0")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Kinh độ (longitude) phải từ -180.0 đến 180.0")
        @DecimalMax(value = "180.0", message = "Kinh độ (longitude) phải từ -180.0 đến 180.0")
        Double longitude,

        @NotNull(message = "Trường isDefault không được để trống")
        Boolean isDefault
) {
}
