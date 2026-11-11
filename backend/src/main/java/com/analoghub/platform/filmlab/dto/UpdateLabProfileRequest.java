package com.analoghub.platform.filmlab.dto;

import lombok.Data;

@Data
public class UpdateLabProfileRequest {
    private String name;
    private String bio;
    private String phone;
    private String fullAddress;
    private String districtCode;
    private String provinceCode;
    // Bổ sung thêm các trường cập nhật khác nếu cần
}