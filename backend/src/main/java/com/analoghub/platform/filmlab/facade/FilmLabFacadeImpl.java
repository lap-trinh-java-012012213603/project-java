package com.analoghub.platform.filmlab.facade;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FilmLabFacadeImpl implements FilmLabFacade {

    private static final List<String> DEFAULT_SUPPORTED_PROCESSES = List.of("C41", "BW");
    private static final int DEFAULT_MAX_DAILY_ROLLS = 30;
    private static final int DEFAULT_BASE_TURNAROUND_HOURS = 24;
    private static final int DEFAULT_PENALTY_HOURS_PER_BATCH = 12;

    private final LabCapacityRuleRepository capacityRuleRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final @Nullable LabRepository labRepository;
    private final @Nullable ApplicationEventPublisher eventPublisher;

    public FilmLabFacadeImpl(
            LabCapacityRuleRepository capacityRuleRepository,
            ServicePackageRepository servicePackageRepository
    ) {
        this(capacityRuleRepository, servicePackageRepository, null, null);
    }

    @Autowired
    public FilmLabFacadeImpl(
            LabCapacityRuleRepository capacityRuleRepository,
            ServicePackageRepository servicePackageRepository,
            @Nullable LabRepository labRepository,
            @Nullable ApplicationEventPublisher eventPublisher
    ) {
        this.capacityRuleRepository = capacityRuleRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.labRepository = labRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void reserveCapacity(UUID labId, String processType, int rollCount) {
        LabCapacityRule rule = capacityRuleRepository.findByLabIdAndProcessType(labId, processType)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình công suất cho quy trình: " + processType));

        // Fix: Sử dụng isAcceptingOrders() chuẩn của Lombok sinh ra
        if (!Boolean.TRUE.equals(rule.isAcceptingOrders())) {
            throw new IllegalArgumentException("Lab hiện đang tạm ngưng nhận đơn cho quy trình " + processType);
        }

        rule.setCurrentQueuedRolls(rule.getCurrentQueuedRolls() + rollCount);
        capacityRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public void releaseCapacity(UUID labId, String processType, int rollCount) {
        capacityRuleRepository.findByLabIdAndProcessType(labId, processType).ifPresent(rule -> {
            int updated = Math.max(0, rule.getCurrentQueuedRolls() - rollCount);
            rule.setCurrentQueuedRolls(updated);
            capacityRuleRepository.save(rule);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public int getEstimatedTurnaroundHours(UUID labId, String processType) {
        return capacityRuleRepository.findByLabIdAndProcessType(labId, processType)
                .map(rule -> {
                    int batches = rule.getMaxDailyRolls() > 0 ? (rule.getCurrentQueuedRolls() / rule.getMaxDailyRolls()) : 0;
                    return rule.getBaseTurnaroundHours() + (batches * rule.getPenaltyHoursPerBatch());
                })
                .orElse(24);
    }

    @Override
    @Transactional(readOnly = true)
    public ServicePackageInfo getServicePackageInfo(UUID packageId) {
        return servicePackageRepository.findById(packageId)
                .map(pkg -> new ServicePackageInfo(
                        pkg.getId(), pkg.getLabId(), pkg.getName(), pkg.getFilmFormat(),
                        pkg.getProcessType(), pkg.getScanResolution(), pkg.getUnitPriceVnd()
                ))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói dịch vụ: " + packageId));
    }

    @Override
    @Transactional
    public ProvisionedLabInfo provisionLabFromApprovedApplication(
            UUID ownerUserId,
            Map<String, Object> portfolioPayload
    ) {
        if (ownerUserId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ownerUserId không được để trống");
        }
        LabRepository requiredLabRepository = requireLabRepository();
        Map<String, Object> payload = portfolioPayload != null ? portfolioPayload : Map.of();

        String labName = firstNonBlank(payload, "proposedLabName", "labName", "name");
        if (labName == null) {
            labName = "Analog Film Lab";
        }

        String requestedSlug = firstNonBlank(payload, "proposedSlug", "slug");
        String slug = requestedSlug != null ? normalizeSlug(requestedSlug) : normalizeSlug(labName);
        if (slug.isBlank()) {
            slug = "lab-" + ownerUserId.toString().substring(0, 8);
        }

        Optional<Lab> existingBySlug = requiredLabRepository.findBySlug(slug);
        if (existingBySlug.isPresent() && !ownerUserId.equals(existingBySlug.get().getOwnerUserId())) {
            throw new BusinessException(
                    ErrorCode.LAB_SLUG_ALREADY_EXISTS,
                    "Slug Film Lab đã tồn tại trên hệ thống: " + slug
            );
        }

        String fullAddress = firstNonBlank(payload, "fullAddress", "labAddress", "streetAddress", "address");
        if (fullAddress == null) {
            fullAddress = "Chưa cập nhật địa chỉ";
        }
        String districtCode = firstNonBlank(payload, "districtCode");
        if (districtCode == null) {
            districtCode = "760";
        }
        String provinceCode = firstNonBlank(payload, "provinceCode");
        if (provinceCode == null) {
            provinceCode = "79";
        }
        String bio = firstNonBlank(payload, "bio", "description");
        String phone = firstNonBlank(payload, "phone", "contactPhone", "phoneNumber");
        Double latitude = extractDouble(payload.get("latitude"));
        Double longitude = extractDouble(payload.get("longitude"));
        Map<String, Object> operatingHours = extractOperatingHours(payload.get("operatingHours"));

        Optional<Lab> existingByOwner = requiredLabRepository.findFirstByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        Lab lab;
        if (existingByOwner.isPresent()) {
            lab = existingByOwner.get();
            lab.setName(labName);
            lab.setSlug(slug);
            lab.setFullAddress(fullAddress);
            lab.setDistrictCode(districtCode);
            lab.setProvinceCode(provinceCode);
            if (bio != null) {
                lab.setBio(bio);
            }
            if (phone != null) {
                lab.setPhone(phone);
            }
            if (latitude != null) {
                lab.setLatitude(latitude);
            }
            if (longitude != null) {
                lab.setLongitude(longitude);
            }
            if (!operatingHours.isEmpty()) {
                lab.setOperatingHours(operatingHours);
            }
            lab.setDeletedAt(null);
            lab.setStatus("ACTIVE");
        } else {
            lab = Lab.builder()
                    .ownerUserId(ownerUserId)
                    .slug(slug)
                    .name(labName)
                    .bio(bio)
                    .phone(phone)
                    .fullAddress(fullAddress)
                    .districtCode(districtCode)
                    .provinceCode(provinceCode)
                    .latitude(latitude)
                    .longitude(longitude)
                    .operatingHours(operatingHours)
                    .status("ACTIVE")
                    .avgRating(BigDecimal.ZERO)
                    .reviewCount(0)
                    .build();
        }

        Lab savedLab = requiredLabRepository.save(lab);
        ensureDefaultCapacityRules(savedLab.getId(), extractSupportedProcesses(payload.get("supportedProcesses")));

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new FilmLabCreatedEvent(savedLab.getId(), savedLab.getName()));
        }

        return new ProvisionedLabInfo(
                savedLab.getId(),
                savedLab.getOwnerUserId(),
                savedLab.getSlug(),
                savedLab.getName(),
                savedLab.getStatus()
        );
    }

    @Override
    @Transactional
    public ProvisionedLabInfo approveLabOpening(UUID labId) {
        if (labId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "labId không được để trống");
        }
        LabRepository requiredLabRepository = requireLabRepository();
        Lab lab = requiredLabRepository.findById(labId)
                .orElseThrow(() -> ResourceNotFoundException.lab(labId));

        lab.setStatus("ACTIVE");
        lab.setDeletedAt(null);
        Lab savedLab = requiredLabRepository.save(lab);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new FilmLabCreatedEvent(savedLab.getId(), savedLab.getName()));
        }

        return new ProvisionedLabInfo(
                savedLab.getId(),
                savedLab.getOwnerUserId(),
                savedLab.getSlug(),
                savedLab.getName(),
                savedLab.getStatus()
        );
    }

    private LabRepository requireLabRepository() {
        if (labRepository == null) {
            throw new IllegalStateException("LabRepository is required for lab provisioning operations");
        }
        return labRepository;
    }

    private void ensureDefaultCapacityRules(UUID labId, List<String> processes) {
        for (String processType : processes) {
            if (capacityRuleRepository.findByLabIdAndProcessType(labId, processType).isEmpty()) {
                capacityRuleRepository.save(LabCapacityRule.builder()
                        .labId(labId)
                        .processType(processType)
                        .maxDailyRolls(DEFAULT_MAX_DAILY_ROLLS)
                        .currentQueuedRolls(0)
                        .baseTurnaroundHours(DEFAULT_BASE_TURNAROUND_HOURS)
                        .penaltyHoursPerBatch(DEFAULT_PENALTY_HOURS_PER_BATCH)
                        .isAcceptingOrders(true)
                        .build());
            }
        }
    }

    private List<String> extractSupportedProcesses(Object raw) {
        if (raw instanceof Collection<?> col && !col.isEmpty()) {
            List<String> normalized = col.stream()
                    .filter(item -> item != null && !item.toString().isBlank())
                    .map(item -> item.toString().trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return DEFAULT_SUPPORTED_PROCESSES;
    }

    private String firstNonBlank(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object raw = payload.get(key);
            if (raw != null) {
                String trimmed = raw.toString().trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private Double extractDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> extractOperatingHours(Object raw) {
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null) {
                    copy.put(k.toString(), v);
                }
            });
            return copy;
        }
        return Map.of();
    }

    private String normalizeSlug(String input) {
        String preNormalized = input.replace('đ', 'd').replace('Đ', 'D');
        String ascii = Normalizer.normalize(preNormalized, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.length() > 160 ? slug.substring(0, 160) : slug;
    }
}