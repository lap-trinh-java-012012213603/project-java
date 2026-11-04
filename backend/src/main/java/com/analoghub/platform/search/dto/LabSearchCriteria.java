package com.analoghub.platform.search.dto;

import jakarta.validation.constraints.DecimalMax;
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
public class LabSearchCriteria {

    @Size(max = 120, message = "Từ khóa tìm kiếm tối đa 120 ký tự")
    private String q;

    @DecimalMin(value = "-90.0", message = "Vĩ độ (lat) phải nằm trong khoảng [-90.0, 90.0]")
    @DecimalMax(value = "90.0", message = "Vĩ độ (lat) phải nằm trong khoảng [-90.0, 90.0]")
    private Double lat;

    @DecimalMin(value = "-180.0", message = "Kinh độ (lon) phải nằm trong khoảng [-180.0, 180.0]")
    @DecimalMax(value = "180.0", message = "Kinh độ (lon) phải nằm trong khoảng [-180.0, 180.0]")
    private Double lon;

    @DecimalMin(value = "0.5", message = "Bán kính tìm kiếm (radiusKm) tối thiểu là 0.5 km")
    @DecimalMax(value = "200.0", message = "Bán kính tìm kiếm (radiusKm) tối đa là 200.0 km")
    private Double radiusKm;

    @Pattern(regexp = "^[0-9]{2}$", message = "Mã tỉnh/thành phố (provinceCode) phải gồm 2 chữ số")
    private String provinceCode;

    @Pattern(regexp = "^[0-9]{3}$", message = "Mã quận/huyện (districtCode) phải gồm 3 chữ số")
    private String districtCode;

    private List<String> formats;

    private List<String> processes;

    private List<String> scannerModels;

    @DecimalMin(value = "10000.00", message = "Giá tối đa (maxPriceVnd) phải từ 10,000 VND")
    @DecimalMax(value = "5000000.00", message = "Giá tối đa (maxPriceVnd) không vượt quá 5,000,000 VND")
    private BigDecimal maxPriceVnd;

    @Min(value = 1, message = "Thời gian trả ảnh tối đa (maxTurnaroundHours) tối thiểu là 1 giờ")
    @Max(value = 720, message = "Thời gian trả ảnh tối đa (maxTurnaroundHours) tối đa là 720 giờ")
    private Integer maxTurnaroundHours;

    @Builder.Default
    private Boolean onlyAccepting = Boolean.TRUE;

    private LabSortBy sortBy;

    @Builder.Default
    @Min(value = 0, message = "Chỉ số trang (page) không được âm")
    private Integer page = 0;

    @Builder.Default
    @Min(value = 1, message = "Kích thước trang (size) tối thiểu là 1")
    @Max(value = 50, message = "Kích thước trang (size) tối đa là 50")
    private Integer size = 20;
}

