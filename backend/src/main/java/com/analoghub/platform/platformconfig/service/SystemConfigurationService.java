package com.analoghub.platform.platformconfig.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.platformconfig.domain.SystemConfiguration;
import com.analoghub.platform.platformconfig.dto.ConfigValueType;
import com.analoghub.platform.platformconfig.dto.SystemConfigDto;
import com.analoghub.platform.platformconfig.dto.UpdateSystemConfigCommand;
import com.analoghub.platform.platformconfig.event.SystemConfigChangedEvent;
import com.analoghub.platform.platformconfig.repository.SystemConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages dynamic system configurations ({@code cfg_system_configurations}) with Redis caching
 * under {@code cfg:sys:{configKey}} and {@code cfg:sys:public_map}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigurationService {

    public static final String CACHE_KEY_PREFIX = "cfg:sys:";
    public static final String CACHE_KEY_PUBLIC_MAP = "cfg:sys:public_map";
    public static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public static final Set<String> PUBLIC_SAFE_CONFIG_KEYS = Set.of(
            "archive.raw_storage_retention_days",
            "archive.max_frames_per_roll",
            "booking.unpaid_order_timeout_minutes",
            "booking.negative_hold_default_days",
            "marketplace.offer_expiration_hours",
            "marketplace.buyer_inspection_window_hours",
            "marketplace.listing_expiration_days",
            "storage.max_avatar_size_bytes",
            "storage.max_general_upload_size_bytes",
            "ai.default_chat_provider",
            "support.hotline_phone",
            "support.dispute_sla_hours"
    );

    private final SystemConfigurationRepository systemConfigurationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Optional<String> getString(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return Optional.empty();
        }
        String normalizedKey = configKey.trim();
        String cacheKey = buildCacheKey(normalizedKey);

        Optional<String> cachedValue = readCachedString(cacheKey);
        if (cachedValue.isPresent()) {
            return cachedValue;
        }

        return systemConfigurationRepository.findById(normalizedKey)
                .map(entity -> {
                    writeCache(cacheKey, entity.getConfigValue(), CACHE_TTL);
                    return entity.getConfigValue();
                });
    }

    @Transactional(readOnly = true)
    public String getStringOrDefault(String configKey, String defaultValue) {
        return getString(configKey).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getIntOrDefault(String configKey, int defaultValue) {
        Optional<String> raw = getString(configKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.get().trim());
        } catch (NumberFormatException ex) {
            log.warn("[SystemConfig] Invalid integer value for key={}: {}", configKey, raw.get());
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public long getLongOrDefault(String configKey, long defaultValue) {
        Optional<String> raw = getString(configKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.get().trim());
        } catch (NumberFormatException ex) {
            log.warn("[SystemConfig] Invalid long value for key={}: {}", configKey, raw.get());
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimalOrDefault(String configKey, BigDecimal defaultValue) {
        Optional<String> raw = getString(configKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(raw.get().trim());
        } catch (NumberFormatException ex) {
            log.warn("[SystemConfig] Invalid decimal value for key={}: {}", configKey, raw.get());
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBooleanOrDefault(String configKey, boolean defaultValue) {
        Optional<String> raw = getString(configKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        String value = raw.get().trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        log.warn("[SystemConfig] Invalid boolean value for key={}: {}", configKey, value);
        return defaultValue;
    }

    @Transactional(readOnly = true)
    public List<SystemConfigDto> getAllSystemConfigs() {
        return systemConfigurationRepository.findAllByOrderByConfigKeyAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPublicSettings() {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops != null) {
                Object cached = ops.get(CACHE_KEY_PUBLIC_MAP);
                if (cached instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            }
        } catch (Exception ex) {
            log.warn("[SystemConfig] Redis cache read failed for key={}: {}", CACHE_KEY_PUBLIC_MAP, ex.getMessage());
        }

        List<SystemConfiguration> publicConfigs = systemConfigurationRepository.findByConfigKeyIn(PUBLIC_SAFE_CONFIG_KEYS);
        Map<String, Object> result = new LinkedHashMap<>();
        for (SystemConfiguration cfg : publicConfigs) {
            result.put(cfg.getConfigKey(), convertToTypedObject(cfg.getConfigValue(), cfg.getValueType()));
        }

        writeCache(CACHE_KEY_PUBLIC_MAP, result, CACHE_TTL);
        return result;
    }

    @Transactional
    public SystemConfigDto upsertSystemConfig(UpdateSystemConfigCommand command, UUID changedBy) {
        if (command == null || command.configKey() == null || command.configKey().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "System configuration key must not be blank");
        }
        if (command.configValue() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "System configuration value must not be null");
        }

        String normalizedKey = command.configKey().trim();
        Optional<SystemConfiguration> existingOpt = systemConfigurationRepository.findById(normalizedKey);

        ConfigValueType resolvedType = resolveValueType(command.valueType(), existingOpt);
        validateValueForType(normalizedKey, command.configValue(), resolvedType);

        String oldValue = existingOpt.map(SystemConfiguration::getConfigValue).orElse(null);
        Instant now = Instant.now();

        SystemConfiguration entity = existingOpt.orElseGet(() -> SystemConfiguration.builder()
                .configKey(normalizedKey)
                .createdAt(now)
                .createdBy(changedBy)
                .build());

        entity.setConfigValue(command.configValue());
        entity.setValueType(resolvedType);
        if (command.description() != null) {
            entity.setDescription(command.description());
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(changedBy);

        SystemConfiguration saved = systemConfigurationRepository.save(entity);

        eventPublisher.publishEvent(new SystemConfigChangedEvent(
                normalizedKey,
                oldValue,
                saved.getConfigValue(),
                changedBy
        ));

        evictCache(buildCacheKey(normalizedKey), CACHE_KEY_PUBLIC_MAP);
        log.info("[SystemConfig] Updated key={} old={} new={} by={}",
                normalizedKey, oldValue, saved.getConfigValue(), changedBy);

        return toDto(saved);
    }

    public static String buildCacheKey(String configKey) {
        return CACHE_KEY_PREFIX + configKey.trim();
    }

    private ConfigValueType resolveValueType(String rawValueType, Optional<SystemConfiguration> existingOpt) {
        if (rawValueType != null && !rawValueType.isBlank()) {
            String normalized = rawValueType.trim().toUpperCase(Locale.ROOT);
            if ("INT".equals(normalized)) {
                return ConfigValueType.INTEGER;
            }
            try {
                return ConfigValueType.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported config valueType: " + rawValueType);
            }
        }
        return existingOpt.map(SystemConfiguration::getValueType)
                .orElse(ConfigValueType.STRING);
    }

    private void validateValueForType(String configKey, String rawValue, ConfigValueType valueType) {
        String trimmed = rawValue.trim();
        try {
            switch (valueType) {
                case INTEGER -> Integer.parseInt(trimmed);
                case LONG -> Long.parseLong(trimmed);
                case DECIMAL -> new BigDecimal(trimmed);
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                        throw new IllegalArgumentException("Expected boolean 'true' or 'false'");
                    }
                }
                case JSON -> {
                    if (trimmed.isEmpty() || !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                        throw new IllegalArgumentException("Expected valid JSON object or array");
                    }
                }
                case STRING -> {
                    // Any non-null string is valid
                }
            }
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    String.format("Configuration value '%s' does not match required type '%s' for key '%s'",
                            rawValue, valueType, configKey),
                    ex
            );
        }
    }

    private Object convertToTypedObject(String rawValue, ConfigValueType valueType) {
        if (rawValue == null || valueType == null) {
            return rawValue;
        }
        String trimmed = rawValue.trim();
        try {
            return switch (valueType) {
                case INTEGER -> Integer.parseInt(trimmed);
                case LONG -> Long.parseLong(trimmed);
                case DECIMAL -> new BigDecimal(trimmed);
                case BOOLEAN -> Boolean.parseBoolean(trimmed);
                case STRING, JSON -> rawValue;
            };
        } catch (Exception ex) {
            return rawValue;
        }
    }

    private Optional<String> readCachedString(String cacheKey) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops == null) {
                return Optional.empty();
            }
            Object cached = ops.get(cacheKey);
            if (cached instanceof String str) {
                return Optional.of(str);
            }
            if (cached instanceof SystemConfigDto dto) {
                return Optional.ofNullable(dto.configValue());
            }
        } catch (Exception ex) {
            log.warn("[SystemConfig] Redis cache read failed for key={}: {}", cacheKey, ex.getMessage());
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
            log.warn("[SystemConfig] Redis cache write failed for key={}: {}", cacheKey, ex.getMessage());
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
            log.warn("[SystemConfig] Redis cache eviction failed: {}", ex.getMessage());
        }
    }

    private SystemConfigDto toDto(SystemConfiguration entity) {
        return new SystemConfigDto(
                entity.getConfigKey(),
                entity.getConfigValue(),
                entity.getValueType() != null ? entity.getValueType().name() : ConfigValueType.STRING.name(),
                entity.getDescription(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }
}
