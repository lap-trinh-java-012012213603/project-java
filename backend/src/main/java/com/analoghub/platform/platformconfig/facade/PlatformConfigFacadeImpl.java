package com.analoghub.platform.platformconfig.facade;

import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeConfigDto;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryDto;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import com.analoghub.platform.platformconfig.dto.SystemConfigDto;
import com.analoghub.platform.platformconfig.dto.UpdateFeeConfigCommand;
import com.analoghub.platform.platformconfig.dto.UpdateSystemConfigCommand;
import com.analoghub.platform.platformconfig.service.FeeConfigurationService;
import com.analoghub.platform.platformconfig.service.ServiceCategoryService;
import com.analoghub.platform.platformconfig.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link PlatformConfigFacade} backed by PostgreSQL and Redis cache.
 */
@Service
@RequiredArgsConstructor
public class PlatformConfigFacadeImpl implements PlatformConfigFacade {

    private final SystemConfigurationService systemConfigurationService;
    private final FeeConfigurationService feeConfigurationService;
    private final ServiceCategoryService serviceCategoryService;

    // =========================================================================
    // 1. System Configurations (cfg_system_configurations)
    // =========================================================================

    @Override
    public Optional<String> getString(String configKey) {
        return systemConfigurationService.getString(configKey);
    }

    @Override
    public String getStringOrDefault(String configKey, String defaultValue) {
        return systemConfigurationService.getStringOrDefault(configKey, defaultValue);
    }

    @Override
    public int getIntOrDefault(String configKey, int defaultValue) {
        return systemConfigurationService.getIntOrDefault(configKey, defaultValue);
    }

    @Override
    public long getLongOrDefault(String configKey, long defaultValue) {
        return systemConfigurationService.getLongOrDefault(configKey, defaultValue);
    }

    @Override
    public BigDecimal getDecimalOrDefault(String configKey, BigDecimal defaultValue) {
        return systemConfigurationService.getDecimalOrDefault(configKey, defaultValue);
    }

    @Override
    public boolean getBooleanOrDefault(String configKey, boolean defaultValue) {
        return systemConfigurationService.getBooleanOrDefault(configKey, defaultValue);
    }

    @Override
    public List<SystemConfigDto> getAllSystemConfigs() {
        return systemConfigurationService.getAllSystemConfigs();
    }

    @Override
    public SystemConfigDto upsertSystemConfig(UpdateSystemConfigCommand command, UUID changedBy) {
        return systemConfigurationService.upsertSystemConfig(command, changedBy);
    }

    // =========================================================================
    // 2. Fee Configurations & Commission Calculation (cfg_fee_configurations)
    // =========================================================================

    @Override
    public FeeConfigDto getFeeConfig(FeeScope feeScope) {
        return feeConfigurationService.getFeeConfig(feeScope);
    }

    @Override
    public List<FeeConfigDto> getAllFeeConfigs() {
        return feeConfigurationService.getAllFeeConfigs();
    }

    @Override
    public BigDecimal calculateCommissionFee(FeeScope feeScope, BigDecimal grossAmountVnd) {
        return feeConfigurationService.calculateCommissionFee(feeScope, grossAmountVnd);
    }

    @Override
    public FeeCalculationResult calculateFeeBreakdown(FeeScope feeScope, BigDecimal grossAmountVnd) {
        return feeConfigurationService.calculateFeeBreakdown(feeScope, grossAmountVnd);
    }

    @Override
    public FeeConfigDto updateFeeConfig(UpdateFeeConfigCommand command, UUID changedBy) {
        return feeConfigurationService.updateFeeConfig(command, changedBy);
    }

    // =========================================================================
    // 3. Service Categories (cfg_service_categories)
    // =========================================================================

    @Override
    public List<ServiceCategoryDto> getActiveServiceCategories() {
        return serviceCategoryService.getActiveServiceCategories();
    }

    @Override
    public List<ServiceCategoryDto> getServiceCategoriesByGroup(ServiceCategoryGroup categoryGroup) {
        return serviceCategoryService.getServiceCategoriesByGroup(categoryGroup);
    }

    @Override
    public Optional<ServiceCategoryDto> getServiceCategoryByCode(String code) {
        return serviceCategoryService.getServiceCategoryByCode(code);
    }

    @Override
    public boolean isValidActiveServiceCategory(String code) {
        return serviceCategoryService.isValidActiveServiceCategory(code);
    }
}
