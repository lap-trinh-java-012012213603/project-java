package com.analoghub.platform.platformconfig.facade;

import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeConfigDto;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryDto;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import com.analoghub.platform.platformconfig.dto.SystemConfigDto;
import com.analoghub.platform.platformconfig.dto.UpdateFeeConfigCommand;
import com.analoghub.platform.platformconfig.dto.UpdateSystemConfigCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public cross-module contract for {@code platformconfig} (Tier 1).
 * Provides cached access to system parameters, fee/commission calculation rules,
 * and standardized Film Lab service categories without upward dependencies on {@code admin}.
 */
public interface PlatformConfigFacade {

    // =========================================================================
    // 1. System Configurations (cfg_system_configurations)
    // =========================================================================

    Optional<String> getString(String configKey);

    String getStringOrDefault(String configKey, String defaultValue);

    int getIntOrDefault(String configKey, int defaultValue);

    long getLongOrDefault(String configKey, long defaultValue);

    BigDecimal getDecimalOrDefault(String configKey, BigDecimal defaultValue);

    boolean getBooleanOrDefault(String configKey, boolean defaultValue);

    List<SystemConfigDto> getAllSystemConfigs();

    SystemConfigDto upsertSystemConfig(UpdateSystemConfigCommand command, UUID changedBy);

    // =========================================================================
    // 2. Fee Configurations & Commission Calculation (cfg_fee_configurations)
    // =========================================================================

    FeeConfigDto getFeeConfig(FeeScope feeScope);

    List<FeeConfigDto> getAllFeeConfigs();

    BigDecimal calculateCommissionFee(FeeScope feeScope, BigDecimal grossAmountVnd);

    FeeCalculationResult calculateFeeBreakdown(FeeScope feeScope, BigDecimal grossAmountVnd);

    FeeConfigDto updateFeeConfig(UpdateFeeConfigCommand command, UUID changedBy);

    // =========================================================================
    // 3. Service Categories (cfg_service_categories)
    // =========================================================================

    List<ServiceCategoryDto> getActiveServiceCategories();

    List<ServiceCategoryDto> getServiceCategoriesByGroup(ServiceCategoryGroup categoryGroup);

    Optional<ServiceCategoryDto> getServiceCategoryByCode(String code);

    boolean isValidActiveServiceCategory(String code);
}
