package com.analoghub.platform.search.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingSearchCriteria {

    @Size(max = 150, message = "Từ khóa tìm kiếm tối đa 150 ký tự")
    private String q;

    private String category;

    @Size(max = 80, message = "Tên thương hiệu (brand) tối đa 80 ký tự")
    private String brand;

    private List<String> conditionGrades;

    @DecimalMin(value = "0.00", message = "Giá tối thiểu (minPriceVnd) không được âm")
    private BigDecimal minPriceVnd;

    @DecimalMin(value = "10000.00", message = "Giá tối đa (maxPriceVnd) phải từ 10,000 VND")
    private BigDecimal maxPriceVnd;

    private Boolean allowSwap;

    private Boolean allowPriceOffer;

    @Pattern(regexp = "^[0-9]{2}$", message = "Mã tỉnh/thành phố (provinceCode) phải gồm 2 chữ số")
    private String provinceCode;

    private ListingSortBy sortBy;

    @Builder.Default
    @Min(value = 0, message = "Chỉ số trang (page) không được âm")
    private Integer page = 0;

    @Builder.Default
    @Min(value = 1, message = "Kích thước trang (size) tối thiểu là 1")
    @Max(value = 50, message = "Kích thước trang (size) tối đa là 50")
    private Integer size = 20;
}

