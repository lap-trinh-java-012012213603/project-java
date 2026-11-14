package com.analoghub.platform.platformconfig.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.platformconfig.domain.FeeConfiguration;
import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeConfigDto;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.dto.UpdateFeeConfigCommand;
import com.analoghub.platform.platformconfig.event.SystemConfigChangedEvent;
import com.analoghub.platform.platformconfig.repository.FeeConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages platform commission fee rules ({@code cfg_fee_configurations}) and commission calculations
 * with Redis caching under {@code cfg:fee:{feeScope}} and {@code cfg:fee:all}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeConfigurationService {

    public static final String CACHE_KEY_PREFIX = "cfg:fee:";
    public static final String CACHE_KEY_ALL = "cfg:fee:all";
    public static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final BigDecimal MAX_COMMISSION_RATE = new BigDecimal("0.5000");

    private final FeeConfigurationRepository feeConfigurationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public FeeConfigDto getFeeConfig(FeeScope feeScope) {
        if (feeScope == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "feeScope must not be null");
        }

        String cacheKey = buildCacheKey(feeScope);
        Optional<FeeConfigDto> cached = readCachedFeeConfig(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        FeeConfiguration entity = feeConfigurationRepository.findByFeeScope(feeScope)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Fee configuration not found for scope: " + feeScope
                ));

        FeeConfigDto dto = toDto(entity);
        writeCache(cacheKey, dto, CACHE_TTL);
        return dto;
    }

    @Transactional(readOnly = true)
    public FeeConfigDto getActiveFeeConfig(FeeScope feeScope) {
        FeeConfigDto config = getFeeConfig(feeScope);
        if (!config.isActive()) {
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Fee configuration is inactive for scope: " + feeScope
            );
        }
        return config;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<FeeConfigDto> getAllFeeConfigs() {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops != null) {
                Object cached = ops.get(CACHE_KEY_ALL);
                if (cached instanceof List<?> list) {
                    return (List<FeeConfigDto>) list;
                }
            }
        } catch (Exception ex) {
            log.warn("[FeeConfig] Redis cache read failed for key={}: {}", CACHE_KEY_ALL, ex.getMessage());
        }

        List<FeeConfigDto> configs = feeConfigurationRepository.findAllByOrderByFeeScopeAsc().stream()
                .map(this::toDto)
                .toList();

        writeCache(CACHE_KEY_ALL, configs, CACHE_TTL);
        return configs;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCommissionFee(FeeScope feeScope, BigDecimal grossAmountVnd) {
        return calculateFeeBreakdown(feeScope, grossAmountVnd).commissionFeeVnd();
    }

    /**
     * Calculates platform commission fee and net settlement payout according to the active fee rule:
     * <pre>
     * rawFee = round_half_up(grossAmountVnd * commissionRate, 0) + fixedFeeVnd
     * boundedFee = max(minFeeVnd, rawFee)
     * finalFee = (maxFeeVnd != null) ? min(maxFeeVnd, boundedFee) : boundedFee
     * commissionFeeVnd = min(grossAmountVnd, finalFee)
     * netPayoutAmountVnd = grossAmountVnd - commissionFeeVnd
     * </pre>
     */
    @Transactional(readOnly = true)
    public FeeCalculationResult calculateFeeBreakdown(FeeScope feeScope, BigDecimal grossAmountVnd) {
        if (feeScope == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "feeScope must not be null");
        }
        if (grossAmountVnd == null || grossAmountVnd.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "grossAmountVnd must be non-negative");
        }

        FeeConfigDto rule = getActiveFeeConfig(feeScope);
        BigDecimal rate = rule.commissionRate() != null ? rule.commissionRate() : BigDecimal.ZERO;
        BigDecimal fixedFee = rule.fixedFeeVnd() != null ? rule.fixedFeeVnd() : BigDecimal.ZERO;
        BigDecimal minFee = rule.minFeeVnd() != null ? rule.minFeeVnd() : BigDecimal.ZERO;
        BigDecimal maxFee = rule.maxFeeVnd();

        if (grossAmountVnd.compareTo(BigDecimal.ZERO) == 0) {
            return new FeeCalculationResult(
                    feeScope,
                    BigDecimal.ZERO,
                    rate,
                    fixedFee,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        BigDecimal percentageFee = grossAmountVnd.multiply(rate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal rawFee = percentageFee.add(fixedFee).setScale(0, RoundingMode.HALF_UP);
        BigDecimal boundedFee = rawFee.max(minFee.setScale(0, RoundingMode.HALF_UP));

        BigDecimal finalFee = (maxFee != null)
                ? boundedFee.min(maxFee.setScale(0, RoundingMode.HALF_UP))
                : boundedFee;

        BigDecimal grossRounded = grossAmountVnd.setScale(0, RoundingMode.HALF_UP);
        BigDecimal commissionFeeVnd = finalFee.min(grossRounded);
        BigDecimal netPayoutAmountVnd = grossAmountVnd.subtract(commissionFeeVnd);

        return new FeeCalculationResult(
                feeScope,
                grossAmountVnd,
                rate,
                fixedFee,
                commissionFeeVnd,
                netPayoutAmountVnd
        );
    }

    @Transactional
    public FeeConfigDto updateFeeConfig(UpdateFeeConfigCommand command, UUID changedBy) {
        validateUpdateCommand(command);

        FeeConfiguration entity = feeConfigurationRepository.findByFeeScope(command.feeScope())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Fee configuration not found for scope: " + command.feeScope()
                ));

        String oldValue = formatSnapshot(entity);
        Instant now = Instant.now();

        entity.setCommissionRate(command.commissionRate());
        entity.setFixedFeeVnd(command.fixedFeeVnd() != null ? command.fixedFeeVnd() : BigDecimal.ZERO);
        entity.setMinFeeVnd(command.minFeeVnd() != null ? command.minFeeVnd() : BigDecimal.ZERO);
        entity.setMaxFeeVnd(command.maxFeeVnd());
        if (command.isActive() != null) {
            entity.setActive(command.isActive());
        }
        entity.setUpdatedBy(changedBy);
        entity.setUpdatedAt(now);

        FeeConfiguration saved = feeConfigurationRepository.save(entity);
        String newValue = formatSnapshot(saved);
        String cacheKey = buildCacheKey(saved.getFeeScope());

        eventPublisher.publishEvent(new SystemConfigChangedEvent(
                cacheKey,
                oldValue,
                newValue,
                changedBy
        ));

        evictCache(cacheKey, CACHE_KEY_ALL);
        log.info("[FeeConfig] Updated scope={} old={} new={} by={}",
                saved.getFeeScope(), oldValue, newValue, changedBy);

        return toDto(saved);
    }

    public static String buildCacheKey(FeeScope feeScope) {
        return CACHE_KEY_PREFIX + feeScope.name();
    }

    private void validateUpdateCommand(UpdateFeeConfigCommand command) {
        if (command == null || command.feeScope() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "feeScope must not be null");
        }
        if (command.commissionRate() == null
                || command.commissionRate().compareTo(BigDecimal.ZERO) < 0
                || command.commissionRate().compareTo(MAX_COMMISSION_RATE) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "commissionRate must be between 0.0000 and 0.5000 (max 50%)"
            );
        }
        BigDecimal fixedFee = command.fixedFeeVnd() != null ? command.fixedFeeVnd() : BigDecimal.ZERO;
        if (fixedFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fixedFeeVnd must be >= 0");
        }
        BigDecimal minFee = command.minFeeVnd() != null ? command.minFeeVnd() : BigDecimal.ZERO;
        if (minFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "minFeeVnd must be >= 0");
        }
        if (command.maxFeeVnd() != null && command.maxFeeVnd().compareTo(minFee) < 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "maxFeeVnd must be greater than or equal to minFeeVnd"
            );
        }
    }

    private Optional<FeeConfigDto> readCachedFeeConfig(String cacheKey) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops == null) {
                return Optional.empty();
            }
            Object cached = ops.get(cacheKey);
            if (cached instanceof FeeConfigDto dto) {
                return Optional.of(dto);
            }
        } catch (Exception ex) {
            log.warn("[FeeConfig] Redis cache read failed for key={}: {}", cacheKey, ex.getMessage());
        }
        return Optional.empty();
    }

    private void writeCache(String cacheKey, Object value, Duration ttl) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops != null && value != null) {
                ops.set(cacheKey, value, ttl);
            }
        } catch (Exception ex) {
            log.warn("[FeeConfig] Redis cache write failed for key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private void evictCache(String... keys) {
        try {
            for (String key : keys) {
                if (key != null) {
                    redisTemplate.delete(key);
                }
            }
        } catch (Exception ex) {
            log.warn("[FeeConfig] Redis cache eviction failed: {}", ex.getMessage());
        }
    }

    private String formatSnapshot(FeeConfiguration entity) {
        return String.format(
                "{\"feeScope\":\"%s\",\"commissionRate\":%s,\"fixedFeeVnd\":%s,\"minFeeVnd\":%s,\"maxFeeVnd\":%s,\"isActive\":%s}",
                entity.getFeeScope(),
                entity.getCommissionRate(),
                entity.getFixedFeeVnd(),
                entity.getMinFeeVnd(),
                entity.getMaxFeeVnd(),
                entity.isActive()
        );
    }

    private FeeConfigDto toDto(FeeConfiguration entity) {
        return new FeeConfigDto(
                entity.getId(),
                entity.getFeeScope(),
                entity.getCommissionRate(),
                entity.getFixedFeeVnd(),
                entity.getMinFeeVnd(),
                entity.getMaxFeeVnd(),
                entity.isActive(),
                entity.getUpdatedAt()
        );
    }
}
