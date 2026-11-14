package com.analoghub.platform.platformconfig;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.platformconfig.controller.PlatformConfigController;
import com.analoghub.platform.platformconfig.domain.FeeConfiguration;
import com.analoghub.platform.platformconfig.domain.ServiceCategory;
import com.analoghub.platform.platformconfig.domain.SystemConfiguration;
import com.analoghub.platform.platformconfig.dto.ConfigValueType;
import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeConfigDto;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryDto;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import com.analoghub.platform.platformconfig.dto.SystemConfigDto;
import com.analoghub.platform.platformconfig.dto.UpdateFeeConfigCommand;
import com.analoghub.platform.platformconfig.dto.UpdateSystemConfigCommand;
import com.analoghub.platform.platformconfig.event.SystemConfigChangedEvent;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacadeImpl;
import com.analoghub.platform.platformconfig.repository.FeeConfigurationRepository;
import com.analoghub.platform.platformconfig.repository.ServiceCategoryRepository;
import com.analoghub.platform.platformconfig.repository.SystemConfigurationRepository;
import com.analoghub.platform.platformconfig.service.FeeConfigurationService;
import com.analoghub.platform.platformconfig.service.ServiceCategoryService;
import com.analoghub.platform.platformconfig.service.SystemConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformConfigModuleTest {

    @Mock
    private SystemConfigurationRepository systemConfigurationRepository;

    @Mock
    private FeeConfigurationRepository feeConfigurationRepository;

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SystemConfigurationService systemConfigurationService;
    private FeeConfigurationService feeConfigurationService;
    private ServiceCategoryService serviceCategoryService;
    private PlatformConfigFacadeImpl platformConfigFacade;
    private PlatformConfigController platformConfigController;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        systemConfigurationService = new SystemConfigurationService(
                systemConfigurationRepository,
                redisTemplate,
                eventPublisher
        );
        feeConfigurationService = new FeeConfigurationService(
                feeConfigurationRepository,
                redisTemplate,
                eventPublisher
        );
        serviceCategoryService = new ServiceCategoryService(
                serviceCategoryRepository,
                redisTemplate
        );
        platformConfigFacade = new PlatformConfigFacadeImpl(
                systemConfigurationService,
                feeConfigurationService,
                serviceCategoryService
        );
        platformConfigController = new PlatformConfigController(
                systemConfigurationService,
                feeConfigurationService,
                serviceCategoryService
        );
    }

    @Test
    @DisplayName("calculateCommissionFee should compute percentage, min/max bounds, fixed fee and cache on Redis cfg:fee:*")
    void shouldCalculateCommissionFeeAcrossMinMaxBoundsAndCacheInRedis() {
        UUID ruleId = UUID.randomUUID();
        FeeConfiguration labFeeEntity = FeeConfiguration.builder()
                .feeScope(FeeScope.LAB_ORDER)
                .commissionRate(new BigDecimal("0.0800"))
                .fixedFeeVnd(BigDecimal.ZERO)
                .minFeeVnd(new BigDecimal("5000"))
                .maxFeeVnd(new BigDecimal("150000"))
                .isActive(true)
                .build();
        labFeeEntity.setId(ruleId);
        labFeeEntity.setUpdatedAt(Instant.now());

        when(valueOperations.get("cfg:fee:LAB_ORDER")).thenReturn(null);
        when(feeConfigurationRepository.findByFeeScope(FeeScope.LAB_ORDER)).thenReturn(Optional.of(labFeeEntity));

        // 1. Normal range: 200,000 * 8% = 16,000 VND
        FeeCalculationResult normalResult = platformConfigFacade.calculateFeeBreakdown(
                FeeScope.LAB_ORDER,
                new BigDecimal("200000")
        );
        assertThat(normalResult.commissionFeeVnd()).isEqualByComparingTo("16000");
        assertThat(normalResult.netPayoutAmountVnd()).isEqualByComparingTo("184000");
        verify(valueOperations).set(
                eq("cfg:fee:LAB_ORDER"),
                any(FeeConfigDto.class),
                eq(Duration.ofHours(1))
        );

        // Simulate Redis cache hit for subsequent calls
        FeeConfigDto cachedDto = new FeeConfigDto(
                ruleId,
                FeeScope.LAB_ORDER,
                new BigDecimal("0.0800"),
                BigDecimal.ZERO,
                new BigDecimal("5000"),
                new BigDecimal("150000"),
                true,
                labFeeEntity.getUpdatedAt()
        );
        when(valueOperations.get("cfg:fee:LAB_ORDER")).thenReturn(cachedDto);

        // 2. Below minFeeVnd: 50,000 * 8% = 4,000 -> bounded to min 5,000 VND
        BigDecimal minBoundedFee = platformConfigFacade.calculateCommissionFee(
                FeeScope.LAB_ORDER,
                new BigDecimal("50000")
        );
        assertThat(minBoundedFee).isEqualByComparingTo("5000");

        // 3. Above maxFeeVnd: 3,000,000 * 8% = 240,000 -> capped at max 150,000 VND
        FeeCalculationResult maxBoundedResult = platformConfigFacade.calculateFeeBreakdown(
                FeeScope.LAB_ORDER,
                new BigDecimal("3000000")
        );
        assertThat(maxBoundedResult.commissionFeeVnd()).isEqualByComparingTo("150000");
        assertThat(maxBoundedResult.netPayoutAmountVnd()).isEqualByComparingTo("2850000");

        // 4. Gross smaller than minFeeVnd: 3,000 -> capped at gross 3,000 VND (net 0)
        FeeCalculationResult smallGrossResult = platformConfigFacade.calculateFeeBreakdown(
                FeeScope.LAB_ORDER,
                new BigDecimal("3000")
        );
        assertThat(smallGrossResult.commissionFeeVnd()).isEqualByComparingTo("3000");
        assertThat(smallGrossResult.netPayoutAmountVnd()).isEqualByComparingTo("0");

        // 5. Zero gross -> 0 fee, 0 net
        FeeCalculationResult zeroResult = platformConfigFacade.calculateFeeBreakdown(
                FeeScope.LAB_ORDER,
                BigDecimal.ZERO
        );
        assertThat(zeroResult.commissionFeeVnd()).isEqualByComparingTo("0");
        assertThat(zeroResult.netPayoutAmountVnd()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("calculateCommissionFee should include fixedFeeVnd for MARKETPLACE_SALE")
    void shouldIncludeFixedFeeInCommissionCalculation() {
        FeeConfigDto marketSaleRule = new FeeConfigDto(
                UUID.randomUUID(),
                FeeScope.MARKETPLACE_SALE,
                new BigDecimal("0.0500"),
                new BigDecimal("10000"),
                new BigDecimal("20000"),
                new BigDecimal("500000"),
                true,
                Instant.now()
        );
        when(valueOperations.get("cfg:fee:MARKETPLACE_SALE")).thenReturn(marketSaleRule);

        // 1,000,000 * 5% + 10,000 = 60,000 VND
        FeeCalculationResult breakdown = platformConfigFacade.calculateFeeBreakdown(
                FeeScope.MARKETPLACE_SALE,
                new BigDecimal("1000000")
        );
        assertThat(breakdown.commissionFeeVnd()).isEqualByComparingTo("60000");
        assertThat(breakdown.netPayoutAmountVnd()).isEqualByComparingTo("940000");
        verify(feeConfigurationRepository, never()).findByFeeScope(any());
    }

    @Test
    @DisplayName("updateFeeConfig should persist changes, publish SystemConfigChangedEvent and evict Redis cfg:fee:* cache")
    void shouldPublishEventAndEvictRedisCacheWhenFeeConfigUpdated() {
        UUID adminId = UUID.randomUUID();
        FeeConfiguration existing = FeeConfiguration.builder()
                .feeScope(FeeScope.LAB_ORDER)
                .commissionRate(new BigDecimal("0.1000"))
                .fixedFeeVnd(BigDecimal.ZERO)
                .minFeeVnd(new BigDecimal("5000"))
                .maxFeeVnd(new BigDecimal("200000"))
                .isActive(true)
                .build();
        existing.setId(UUID.randomUUID());

        when(feeConfigurationRepository.findByFeeScope(FeeScope.LAB_ORDER)).thenReturn(Optional.of(existing));
        when(feeConfigurationRepository.save(any(FeeConfiguration.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateFeeConfigCommand command = new UpdateFeeConfigCommand(
                FeeScope.LAB_ORDER,
                new BigDecimal("0.0800"),
                BigDecimal.ZERO,
                new BigDecimal("5000"),
                new BigDecimal("150000"),
                true
        );

        FeeConfigDto updated = platformConfigFacade.updateFeeConfig(command, adminId);

        assertThat(updated.commissionRate()).isEqualByComparingTo("0.0800");
        assertThat(updated.maxFeeVnd()).isEqualByComparingTo("150000");

        ArgumentCaptor<SystemConfigChangedEvent> eventCaptor = ArgumentCaptor.forClass(SystemConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SystemConfigChangedEvent event = eventCaptor.getValue();
        assertThat(event.configKey()).contains("LAB_ORDER");
        assertThat(event.oldValue()).contains("0.1000");
        assertThat(event.newValue()).contains("0.0800");
        assertThat(event.changedBy()).isEqualTo(adminId);

        verify(redisTemplate).delete("cfg:fee:LAB_ORDER");
        verify(redisTemplate).delete("cfg:fee:all");
    }

    @Test
    @DisplayName("updateFeeConfig should reject invalid commissionRate > 50% or maxFeeVnd < minFeeVnd")
    void shouldRejectInvalidFeeConfigurationUpdate() {
        UpdateFeeConfigCommand invalidRate = new UpdateFeeConfigCommand(
                FeeScope.LAB_ORDER,
                new BigDecimal("0.6000"),
                BigDecimal.ZERO,
                new BigDecimal("5000"),
                new BigDecimal("150000"),
                true
        );

        assertThatThrownBy(() -> platformConfigFacade.updateFeeConfig(invalidRate, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        UpdateFeeConfigCommand invalidMinMax = new UpdateFeeConfigCommand(
                FeeScope.LAB_ORDER,
                new BigDecimal("0.0800"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                new BigDecimal("10000"),
                true
        );

        assertThatThrownBy(() -> platformConfigFacade.updateFeeConfig(invalidMinMax, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("SystemConfiguration getters and upsertSystemConfig should cache in Redis cfg:sys:*, validate types, publish event and evict cache")
    void shouldReadAndUpsertSystemConfigurationWithRedisCacheAndEvent() {
        UUID adminId = UUID.randomUUID();
        String key = "AI_RAG_SIMILARITY_THRESHOLD";

        when(valueOperations.get("cfg:sys:" + key)).thenReturn("0.65");
        BigDecimal threshold = platformConfigFacade.getDecimalOrDefault(key, new BigDecimal("0.50"));
        assertThat(threshold).isEqualByComparingTo("0.65");
        verify(systemConfigurationRepository, never()).findById(key);

        // Upsert system configuration
        SystemConfiguration existing = SystemConfiguration.builder()
                .configKey(key)
                .configValue("0.65")
                .valueType(ConfigValueType.DECIMAL)
                .description("RAG threshold")
                .build();
        when(systemConfigurationRepository.findById(key)).thenReturn(Optional.of(existing));
        when(systemConfigurationRepository.save(any(SystemConfiguration.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSystemConfigCommand command = new UpdateSystemConfigCommand(
                key,
                "0.68",
                "DECIMAL",
                "Updated RAG threshold"
        );
        SystemConfigDto updated = platformConfigFacade.upsertSystemConfig(command, adminId);

        assertThat(updated.configKey()).isEqualTo(key);
        assertThat(updated.configValue()).isEqualTo("0.68");
        assertThat(updated.valueType()).isEqualTo("DECIMAL");

        ArgumentCaptor<SystemConfigChangedEvent> eventCaptor = ArgumentCaptor.forClass(SystemConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        SystemConfigChangedEvent event = eventCaptor.getValue();
        assertThat(event.configKey()).isEqualTo(key);
        assertThat(event.oldValue()).isEqualTo("0.65");
        assertThat(event.newValue()).isEqualTo("0.68");
        assertThat(event.changedBy()).isEqualTo(adminId);

        verify(redisTemplate).delete("cfg:sys:" + key);
        verify(redisTemplate).delete("cfg:sys:public_map");
    }

    @Test
    @DisplayName("upsertSystemConfig should reject mismatched configValue for declared valueType")
    void shouldRejectMismatchedSystemConfigValueType() {
        UpdateSystemConfigCommand invalidDecimal = new UpdateSystemConfigCommand(
                "AI_RAG_SIMILARITY_THRESHOLD",
                "not-a-decimal",
                "DECIMAL",
                "Invalid decimal"
        );

        assertThatThrownBy(() -> platformConfigFacade.upsertSystemConfig(invalidDecimal, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("ServiceCategory queries should filter active categories by group and code with Redis cfg:cat:all cache")
    void shouldQueryServiceCategoriesWithRedisCaching() {
        ServiceCategory c41 = ServiceCategory.builder()
                .code("DEV_SCAN_COMBO_C41")
                .name("Combo Tráng & Scan C-41")
                .categoryGroup(ServiceCategoryGroup.DEVELOPING)
                .displayOrder(1)
                .isActive(true)
                .build();
        c41.setId(UUID.randomUUID());

        ServiceCategory scanOnly = ServiceCategory.builder()
                .code("SCAN_ONLY_HIGH_RES")
                .name("Chỉ Scan High-Res")
                .categoryGroup(ServiceCategoryGroup.SCANNING)
                .displayOrder(2)
                .isActive(true)
                .build();
        scanOnly.setId(UUID.randomUUID());

        when(valueOperations.get("cfg:cat:all")).thenReturn(null);
        when(serviceCategoryRepository.findAllByOrderByCategoryGroupAscDisplayOrderAscCodeAsc())
                .thenReturn(List.of(c41, scanOnly));

        List<ServiceCategoryDto> developing = platformConfigFacade.getServiceCategoriesByGroup(ServiceCategoryGroup.DEVELOPING);
        assertThat(developing).hasSize(1);
        assertThat(developing.getFirst().code()).isEqualTo("DEV_SCAN_COMBO_C41");
        assertThat(platformConfigFacade.isValidActiveServiceCategory("DEV_SCAN_COMBO_C41")).isTrue();
        assertThat(platformConfigFacade.isValidActiveServiceCategory("UNKNOWN_CAT")).isFalse();
    }

    @Test
    @DisplayName("PlatformConfigController should return service categories, fee rules and public settings")
    void shouldServePlatformConfigRestEndpoints() {
        ServiceCategoryDto c41Dto = new ServiceCategoryDto(
                UUID.randomUUID(),
                "DEV_SCAN_COMBO_C41",
                "Combo Tráng & Scan C-41",
                ServiceCategoryGroup.DEVELOPING,
                "C-41 standard",
                1,
                true
        );
        when(valueOperations.get("cfg:cat:all")).thenReturn(List.of(c41Dto));

        ApiResponse<List<ServiceCategoryDto>> catResp = platformConfigController.getServiceCategories(
                ServiceCategoryGroup.DEVELOPING,
                true
        );
        assertThat(catResp.isSuccess()).isTrue();
        assertThat(catResp.getData()).hasSize(1);

        FeeConfigDto labFeeDto = new FeeConfigDto(
                UUID.randomUUID(),
                FeeScope.LAB_ORDER,
                new BigDecimal("0.0800"),
                BigDecimal.ZERO,
                new BigDecimal("5000"),
                new BigDecimal("150000"),
                true,
                Instant.now()
        );
        when(valueOperations.get("cfg:fee:LAB_ORDER")).thenReturn(labFeeDto);

        ApiResponse<List<FeeConfigDto>> feeResp = platformConfigController.getFeeRules(FeeScope.LAB_ORDER);
        assertThat(feeResp.isSuccess()).isTrue();
        assertThat(feeResp.getData()).hasSize(1);
        assertThat(feeResp.getData().getFirst().feeScope()).isEqualTo(FeeScope.LAB_ORDER);
    }
}
