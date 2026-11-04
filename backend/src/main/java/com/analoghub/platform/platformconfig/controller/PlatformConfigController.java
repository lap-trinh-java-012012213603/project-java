package com.analoghub.platform.platformconfig.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.platformconfig.dto.FeeConfigDto;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryDto;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import com.analoghub.platform.platformconfig.service.FeeConfigurationService;
import com.analoghub.platform.platformconfig.service.ServiceCategoryService;
import com.analoghub.platform.platformconfig.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for querying standardized Film Lab service categories,
 * platform commission fee rules, and public-safe system settings.
 */
@RestController
@RequestMapping("/api/v1/platform-configs")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final SystemConfigurationService systemConfigurationService;
    private final FeeConfigurationService feeConfigurationService;
    private final ServiceCategoryService serviceCategoryService;

    @GetMapping("/service-categories")
    @RateLimit(keyPrefix = "cfg_service_categories", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ApiResponse<List<ServiceCategoryDto>> getServiceCategories(
            @RequestParam(required = false) ServiceCategoryGroup categoryGroup,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly
    ) {
        List<ServiceCategoryDto> all = serviceCategoryService.getAllServiceCategories();
        List<ServiceCategoryDto> filtered = all.stream()
                .filter(dto -> categoryGroup == null || dto.categoryGroup() == categoryGroup)
                .filter(dto -> !activeOnly || dto.isActive())
                .toList();
        return ApiResponse.success("Lấy danh sách danh mục dịch vụ Film Lab thành công.", filtered);
    }

    @GetMapping("/fee-rules")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "cfg_fee_rules", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ApiResponse<List<FeeConfigDto>> getFeeRules(
            @RequestParam(required = false) FeeScope feeScope
    ) {
        List<FeeConfigDto> rules = (feeScope != null)
                ? List.of(feeConfigurationService.getFeeConfig(feeScope))
                : feeConfigurationService.getAllFeeConfigs();
        return ApiResponse.success("Lấy biểu phí hoa hồng nền tảng thành công.", rules);
    }

    @GetMapping("/public-settings")
    @RateLimit(keyPrefix = "cfg_public_settings", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ApiResponse<Map<String, Object>> getPublicSettings() {
        Map<String, Object> settings = systemConfigurationService.getPublicSettings();
        return ApiResponse.success("Lấy thông số cấu hình hệ thống thành công.", settings);
    }
}
