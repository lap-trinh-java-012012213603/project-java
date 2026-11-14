package com.analoghub.platform.platformconfig.service;

import com.analoghub.platform.platformconfig.domain.ServiceCategory;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryDto;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import com.analoghub.platform.platformconfig.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Manages standardized Film Lab service categories ({@code cfg_service_categories})
 * with Redis caching under {@code cfg:cat:all}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCategoryService {

    public static final String CACHE_KEY_ALL = "cfg:cat:all";
    public static final Duration CACHE_TTL = Duration.ofHours(6);

    private final ServiceCategoryRepository serviceCategoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ServiceCategoryDto> getAllServiceCategories() {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops != null) {
                Object cached = ops.get(CACHE_KEY_ALL);
                if (cached instanceof List<?> list) {
                    return (List<ServiceCategoryDto>) list;
                }
            }
        } catch (Exception ex) {
            log.warn("[ServiceCategory] Redis cache read failed for key={}: {}", CACHE_KEY_ALL, ex.getMessage());
        }

        List<ServiceCategoryDto> all = serviceCategoryRepository
                .findAllByOrderByCategoryGroupAscDisplayOrderAscCodeAsc()
                .stream()
                .map(this::toDto)
                .toList();

        writeCache(CACHE_KEY_ALL, all, CACHE_TTL);
        return all;
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryDto> getActiveServiceCategories() {
        return getAllServiceCategories().stream()
                .filter(ServiceCategoryDto::isActive)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceCategoryDto> getServiceCategoriesByGroup(ServiceCategoryGroup categoryGroup) {
        if (categoryGroup == null) {
            return getActiveServiceCategories();
        }
        return getAllServiceCategories().stream()
                .filter(dto -> dto.isActive() && dto.categoryGroup() == categoryGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ServiceCategoryDto> getServiceCategoryByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalizedCode = code.trim();
        return getAllServiceCategories().stream()
                .filter(dto -> dto.code().equalsIgnoreCase(normalizedCode))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public boolean isValidActiveServiceCategory(String code) {
        return getServiceCategoryByCode(code)
                .map(ServiceCategoryDto::isActive)
                .orElse(false);
    }

    private void writeCache(String cacheKey, Object value, Duration ttl) {
        try {
            ValueOperations<String, Object> ops = redisTemplate.opsForValue();
            if (ops != null && value != null) {
                ops.set(cacheKey, value, ttl);
            }
        } catch (Exception ex) {
            log.warn("[ServiceCategory] Redis cache write failed for key={}: {}", cacheKey, ex.getMessage());
        }
    }

    private ServiceCategoryDto toDto(ServiceCategory entity) {
        return new ServiceCategoryDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCategoryGroup(),
                entity.getDescription(),
                entity.getDisplayOrder(),
                entity.isActive()
        );
    }
}
