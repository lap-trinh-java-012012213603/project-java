package com.analoghub.platform.marketplace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateListingRequest(
        @NotBlank(message = "Tiêu đề tin đăng không được để trống")
        @Size(min = 10, max = 200, message = "Tiêu đề phải từ 10 đến 200 ký tự")
        String title,

        @NotBlank(message = "Mô tả không được để trống")
        @Size(min = 30, max = 5000, message = "Mô tả phải từ 30 đến 5000 ký tự")
        String description,

        @NotBlank(message = "Danh mục thiết bị không được để trống")
        String category,

        @NotBlank(message = "Thương hiệu không được để trống")
        @Size(max = 64, message = "Thương hiệu tối đa 64 ký tự")
        String brand,

        @NotBlank(message = "Tên model không được để trống")
        @Size(max = 120, message = "Tên model tối đa 120 ký tự")
        String modelName,

        @NotBlank(message = "Phân hạng tình trạng không được để trống")
        String conditionGrade,

        @NotNull(message = "Thông số kỹ thuật không được để trống")
        @Valid
        TechnicalSpecsDto technicalSpecs,

        @NotNull(message = "Giá bán không được để trống")
        @DecimalMin(value = "10000.00", message = "Giá bán tối thiểu 10.000đ")
        @DecimalMax(value = "500000000.00", message = "Giá bán tối đa 500.000.000đ")
        BigDecimal priceVnd,

        @NotNull(message = "allowPriceOffer không được để trống")
        Boolean allowPriceOffer,

        @NotNull(message = "allowSwap không được để trống")
        Boolean allowSwap,

        @Size(max = 500, message = "preferredSwapItems tối đa 500 ký tự")
        String preferredSwapItems,

        @NotBlank(message = "Mã tỉnh/thành phố không được để trống")
        @Pattern(regexp = "^[0-9]{2}$", message = "Mã tỉnh/thành phố phải gồm 2 chữ số")
        String provinceCode,

        @NotBlank(message = "Mã quận/huyện không được để trống")
        @Pattern(regexp = "^[0-9]{3}$", message = "Mã quận/huyện phải gồm 3 chữ số")
        String districtCode,

        @NotEmpty(message = "Danh sách ảnh thiết bị không được để trống")
        @Size(min = 1, max = 10, message = "Cần tải lên từ 1 đến 10 ảnh thiết bị")
        List<String> imageS3Keys,

        Boolean publishImmediately
) {
}
