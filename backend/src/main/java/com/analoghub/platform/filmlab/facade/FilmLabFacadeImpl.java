package com.analoghub.platform.filmlab.facade;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.domain.LabMember;
import com.analoghub.platform.filmlab.domain.LabReview;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabMemberRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.LabReviewRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FilmLabFacadeImpl implements FilmLabFacade {

    private static final List<String> DEFAULT_SUPPORTED_PROCESSES = List.of("C41", "BW");
    private static final Set<String> VALID_PROCESS_TYPES = Set.of("C41", "ECN2", "BW", "E6");
    private static final int DEFAULT_MAX_DAILY_ROLLS = 30;
    private static final int DEFAULT_BASE_TURNAROUND_HOURS = 24;
    private static final int DEFAULT_PENALTY_HOURS_PER_BATCH = 12;

    private final LabCapacityRuleRepository capacityRuleRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final @Nullable LabRepository labRepository;
    private final @Nullable LabMemberRepository labMemberRepository;
    private final @Nullable ApplicationEventPublisher eventPublisher;
    private final @Nullable LabReviewRepository labReviewRepository;

    public FilmLabFacadeImpl(
            LabCapacityRuleRepository capacityRuleRepository,
            ServicePackageRepository servicePackageRepository
    ) {
        this(capacityRuleRepository, servicePackageRepository, null, null, null, null);
    }

    public FilmLabFacadeImpl(
            LabCapacityRuleRepository capacityRuleRepository,
            ServicePackageRepository servicePackageRepository,
            @Nullable LabRepository labRepository,
            @Nullable LabMemberRepository labMemberRepository,
            @Nullable ApplicationEventPublisher eventPublisher
    ) {
        this(capacityRuleRepository, servicePackageRepository, labRepository, labMemberRepository, eventPublisher, null);
    }

    @Autowired
    public FilmLabFacadeImpl(
            LabCapacityRuleRepository capacityRuleRepository,
            ServicePackageRepository servicePackageRepository,
            @Nullable LabRepository labRepository,
            @Nullable LabMemberRepository labMemberRepository,
            @Nullable ApplicationEventPublisher eventPublisher,
            @Nullable LabReviewRepository labReviewRepository
    ) {
        this.capacityRuleRepository = capacityRuleRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.labRepository = labRepository;
        this.labMemberRepository = labMemberRepository;
        this.eventPublisher = eventPublisher;
        this.labReviewRepository = labReviewRepository;
    }

    @Override
    @Transactional
    public void reserveCapacity(UUID labId, String processType, int rollCount) {
        LabCapacityRule rule = capacityRuleRepository.findByLabIdAndProcessType(labId, processType)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình công suất cho quy trình: " + processType));

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
                .orElse(DEFAULT_BASE_TURNAROUND_HOURS);
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
        if (existingBySlug.isPresent()
                && !existingBySlug.get().isDeleted()
                && !ownerUserId.equals(existingBySlug.get().getOwnerUserId())) {
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
        ensureOwnerLabMember(savedLab.getId(), ownerUserId);
        ensureDefaultCapacityRules(savedLab.getId(), extractSupportedProcesses(payload.get("supportedProcesses")));

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new FilmLabCreatedEvent(savedLab.getId(), savedLab.getName()));
        }

        return toProvisionedLabInfo(savedLab);
    }

    @Override
    @Transactional
    public ProvisionedLabInfo approveLabOpening(UUID labId) {
        if (labId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "labId không được để trống");
        }
        LabRepository requiredLabRepository = requireLabRepository();
        Lab lab = requiredLabRepository.findById(labId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.lab(labId));

        if ("ACTIVE".equalsIgnoreCase(lab.getStatus())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Hồ sơ Film Lab này đã ở trạng thái ACTIVE."
            );
        }

        lab.setStatus("ACTIVE");
        lab.setDeletedAt(null);
        Lab savedLab = requiredLabRepository.save(lab);

        ensureOwnerLabMember(savedLab.getId(), savedLab.getOwnerUserId());
        ensureDefaultCapacityRules(savedLab.getId(), DEFAULT_SUPPORTED_PROCESSES);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new FilmLabCreatedEvent(savedLab.getId(), savedLab.getName()));
        }

        return toProvisionedLabInfo(savedLab);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvisionedLabInfo> getLabsByStatus(String status) {
        String effectiveStatus = (status == null || status.isBlank())
                ? "PENDING_APPROVAL"
                : status.trim().toUpperCase(Locale.ROOT);
        return requireLabRepository()
                .findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(effectiveStatus)
                .stream()
                .map(FilmLabFacadeImpl::toProvisionedLabInfo)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProvisionedLabInfo> findLabById(UUID labId) {
        if (labId == null || labRepository == null) {
            return Optional.empty();
        }
        return labRepository.findById(labId)
                .filter(lab -> !lab.isDeleted())
                .map(FilmLabFacadeImpl::toProvisionedLabInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LabReviewModerationInfo> findReviewById(UUID reviewId) {
        if (reviewId == null || labReviewRepository == null) {
            return Optional.empty();
        }
        return labReviewRepository.findById(reviewId)
                .map(FilmLabFacadeImpl::toReviewModerationInfo);
    }

    @Override
    @Transactional
    public Optional<LabReviewModerationInfo> hideReviewByAdmin(UUID reviewId) {
        if (reviewId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reviewId không được để trống");
        }
        if (labReviewRepository == null) {
            throw new IllegalStateException("LabReviewRepository is required for review moderation operations");
        }
        LabReview review = labReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Đánh giá Film Lab", reviewId));

        review.setModerationStatus("HIDDEN");
        LabReview savedReview = labReviewRepository.save(review);

        if (labRepository != null) {
            labRepository.findById(savedReview.getLabId()).ifPresent(lab -> {
                List<LabReview> publishedReviews = labReviewRepository.findByLabIdAndModerationStatus(
                        savedReview.getLabId(),
                        "PUBLISHED"
                );
                int reviewCount = publishedReviews.size();
                BigDecimal avgRating = reviewCount == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(publishedReviews.stream()
                                .mapToInt(LabReview::getRating)
                                .average()
                                .orElse(0.0))
                                .setScale(2, RoundingMode.HALF_UP);
                lab.setReviewCount(reviewCount);
                lab.setAvgRating(avgRating);
                labRepository.save(lab);
            });
        }

        return Optional.of(toReviewModerationInfo(savedReview));
    }

    private static LabReviewModerationInfo toReviewModerationInfo(LabReview review) {
        return new LabReviewModerationInfo(
                review.getId(),
                review.getLabId(),
                review.getCustomerId(),
                review.getRating(),
                review.getComment(),
                review.getModerationStatus()
        );
    }

    private LabRepository requireLabRepository() {
        if (labRepository == null) {
            throw new IllegalStateException("LabRepository is required for lab provisioning operations");
        }
        return labRepository;
    }

    private void ensureOwnerLabMember(UUID labId, UUID ownerUserId) {
        if (labMemberRepository == null || labId == null || ownerUserId == null) {
            return;
        }
        LabMember member = labMemberRepository.findByLabIdAndUserId(labId, ownerUserId)
                .orElseGet(() -> LabMember.builder()
                        .labId(labId)
                        .userId(ownerUserId)
                        .memberRole("OWNER")
                        .isActive(true)
                        .build());
        member.setMemberRole("OWNER");
        member.setActive(true);
        labMemberRepository.save(member);
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
                    .filter(Objects::nonNull)
                    .map(item -> normalizeProcessType(item.toString()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return DEFAULT_SUPPORTED_PROCESSES;
    }

    private static String normalizeProcessType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        String mapped = switch (cleaned) {
            case "C-41", "C41" -> "C41";
            case "ECN-2", "ECN2" -> "ECN2";
            case "B&W", "BLACK_AND_WHITE", "B_W", "BW" -> "BW";
            case "E-6", "E6" -> "E6";
            default -> cleaned;
        };
        return VALID_PROCESS_TYPES.contains(mapped) ? mapped : null;
    }

    private static ProvisionedLabInfo toProvisionedLabInfo(Lab lab) {
        return new ProvisionedLabInfo(
                lab.getId(),
                lab.getOwnerUserId(),
                lab.getSlug(),
                lab.getName(),
                lab.getStatus()
        );
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
