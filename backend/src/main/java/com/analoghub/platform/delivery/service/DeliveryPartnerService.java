package com.analoghub.platform.delivery.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import com.analoghub.platform.delivery.dto.AcceptDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.AvailableDeliveryTaskResponse;
import com.analoghub.platform.delivery.dto.DeliveryTaskAcceptedResponse;
import com.analoghub.platform.delivery.dto.DeliveryTaskDetailResponse;
import com.analoghub.platform.delivery.dto.DeliveryTaskStatusUpdatedResponse;
import com.analoghub.platform.delivery.dto.DriverLocationUpdateRequest;
import com.analoghub.platform.delivery.dto.DriverLocationUpdatedResponse;
import com.analoghub.platform.delivery.dto.UpdateDeliveryTaskStatusRequest;
import com.analoghub.platform.delivery.provider.LogisticsProviderRegistry;
import com.analoghub.platform.delivery.repository.DeliveryTaskRepository;
import com.analoghub.platform.delivery.repository.DeliveryTrackingLogRepository;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryPartnerService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String LIVE_LOC_KEY_PREFIX = "dlv:live-loc:";
    private static final String DB_LOG_THROTTLE_PREFIX = "dlv:db-log-throttle:";
    private static final Set<DeliveryTaskStatus> ACTIVE_DRIVER_STATUSES = EnumSet.of(
            DeliveryTaskStatus.DRIVER_ASSIGNED,
            DeliveryTaskStatus.PICKED_UP,
            DeliveryTaskStatus.IN_TRANSIT
    );

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryTrackingLogRepository trackingLogRepository;
    private final DeliveryStateMachineValidator stateMachineValidator;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final StorageService storageService;
    private final IamFacade iamFacade;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public PageResponse<AvailableDeliveryTaskResponse> getAvailableTasks(
            Double lat,
            Double lon,
            double radiusKm,
            String direction,
            String provinceCode,
            int page,
            int size
    ) {
        if ((lat != null && lon == null) || (lat == null && lon != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Both lat and lon must be provided together");
        }

        List<DeliveryTask> candidates = taskRepository
                .findByProviderAndStatusAndAssignedDriverUserIdIsNullOrderByPickupDeadlineAtAsc(
                        LogisticsProviderType.INTERNAL_DRIVER,
                        DeliveryTaskStatus.ASSIGNING_DRIVER
                );

        List<AvailableDeliveryTaskResponse> filtered = new ArrayList<>();
        for (DeliveryTask task : candidates) {
            if (direction != null && !direction.isBlank() && !task.getDirection().name().equalsIgnoreCase(direction.trim())) {
                continue;
            }
            if (provinceCode != null && !provinceCode.isBlank()) {
                Object taskProvince = task.getPickupContactAddress() != null ? task.getPickupContactAddress().get("provinceCode") : null;
                if (taskProvince == null || !provinceCode.trim().equalsIgnoreCase(taskProvince.toString())) {
                    continue;
                }
            }

            Double pickupLat = extractDouble(task.getPickupContactAddress(), "latitude");
            Double pickupLon = extractDouble(task.getPickupContactAddress(), "longitude");
            Double dropoffLat = extractDouble(task.getDropoffContactAddress(), "latitude");
            Double dropoffLon = extractDouble(task.getDropoffContactAddress(), "longitude");

            Double distanceToPickup = null;
            if (lat != null && lon != null && pickupLat != null && pickupLon != null) {
                distanceToPickup = LogisticsProviderRegistry.haversineKm(lat, lon, pickupLat, pickupLon);
                if (distanceToPickup > radiusKm) {
                    continue;
                }
            }

            Double routeDistance = LogisticsProviderRegistry.estimateRoadDistanceKm(pickupLat, pickupLon, dropoffLat, dropoffLon);
            filtered.add(new AvailableDeliveryTaskResponse(
                    task.getId(),
                    task.getReferenceType(),
                    task.getReferenceId(),
                    task.getDirection().name(),
                    task.getProvider().name(),
                    task.getStatus().name(),
                    task.getShippingFeeVnd(),
                    distanceToPickup,
                    routeDistance,
                    task.getPickupContactAddress(),
                    task.getDropoffContactAddress(),
                    task.getPickupDeadlineAt(),
                    task.getCreatedAt()
            ));
        }

        if (lat != null && lon != null) {
            filtered.sort(Comparator.comparing(
                    AvailableDeliveryTaskResponse::distanceToPickupKm,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ));
        }

        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<AvailableDeliveryTaskResponse> pageItems = filtered.subList(fromIndex, toIndex);

        return PageResponse.from(pageItems, page, size, totalElements, totalPages);
    }

    @Transactional
    public DeliveryTaskAcceptedResponse acceptTask(UUID taskId, UUID driverUserId, AcceptDeliveryTaskRequest request) {
        long activeCount = taskRepository.countByAssignedDriverUserIdAndStatusIn(driverUserId, ACTIVE_DRIVER_STATUSES);
        if (activeCount >= 3) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Driver has reached the maximum of 3 active delivery tasks");
        }

        DeliveryTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found"));

        if (task.getStatus() == DeliveryTaskStatus.DRIVER_ASSIGNED && driverUserId.equals(task.getAssignedDriverUserId())) {
            return toAcceptedResponse(task);
        }

        if (task.getStatus() != DeliveryTaskStatus.ASSIGNING_DRIVER
                || task.getAssignedDriverUserId() != null
                || task.getProvider() != LogisticsProviderType.INTERNAL_DRIVER) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Delivery task is already assigned or unavailable");
        }

        task.setAssignedDriverUserId(driverUserId);
        task.setStatus(DeliveryTaskStatus.DRIVER_ASSIGNED);
        if (task.getExternalTrackingId() == null || task.getExternalTrackingId().isBlank()) {
            String datePart = LocalDate.now(ZoneOffset.UTC).format(DATE_FMT);
            String shortId = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            task.setExternalTrackingId("INT-" + datePart + "-" + shortId);
        }
        taskRepository.save(task);

        String note = request.estimatedArrivalMinutes() != null
                ? "Driver assigned (ETA " + request.estimatedArrivalMinutes() + " minutes)"
                : "Driver assigned";

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(DeliveryTaskStatus.DRIVER_ASSIGNED)
                .latitude(request.currentLatitude())
                .longitude(request.currentLongitude())
                .note(note)
                .loggedAt(Instant.now())
                .build());

        deliveryEventPublisher.broadcastTaskLocation(
                task.getId(),
                DeliveryTaskStatus.DRIVER_ASSIGNED,
                request.currentLatitude(),
                request.currentLongitude(),
                null,
                null
        );

        return toAcceptedResponse(task);
    }

    @Transactional
    public DeliveryTaskStatusUpdatedResponse updateTaskStatus(UUID taskId, UUID driverUserId, UpdateDeliveryTaskStatusRequest request) {
        DeliveryTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found"));

        if (!driverUserId.equals(task.getAssignedDriverUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You are not the assigned driver for this delivery task");
        }

        DeliveryTaskStatus targetStatus;
        try {
            targetStatus = DeliveryTaskStatus.valueOf(request.targetStatus().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid targetStatus: " + request.targetStatus());
        }

        DeliveryTaskStatus previousStatus = task.getStatus();
        stateMachineValidator.validateTransition(previousStatus, targetStatus);

        if (targetStatus == DeliveryTaskStatus.PICKED_UP || targetStatus == DeliveryTaskStatus.DELIVERED) {
            if (request.proofOfDeliveryS3Key() == null || request.proofOfDeliveryS3Key().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "proofOfDeliveryS3Key is required for " + targetStatus);
            }
            if (!storageService.exists(request.proofOfDeliveryS3Key())) {
                throw new BusinessException(ErrorCode.STORAGE_OBJECT_NOT_FOUND, "Proof of delivery file not found on storage");
            }
            task.setProofOfDeliveryS3Key(request.proofOfDeliveryS3Key());
        }

        if (targetStatus == DeliveryTaskStatus.DELIVERY_FAILED
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Failure reason note is required when status is DELIVERY_FAILED");
        }

        task.setStatus(targetStatus);
        taskRepository.save(task);

        Instant now = Instant.now();
        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(targetStatus)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .note(request.note())
                .loggedAt(now)
                .build());

        deliveryEventPublisher.publishStatusChangedEvent(task, request.note());
        deliveryEventPublisher.broadcastTaskLocation(
                task.getId(),
                targetStatus,
                request.latitude(),
                request.longitude(),
                null,
                null
        );

        String podUrl = storageService.resolveReadUrl(task.getProofOfDeliveryS3Key());
        return new DeliveryTaskStatusUpdatedResponse(
                task.getId(),
                task.getReferenceType(),
                task.getReferenceId(),
                task.getDirection().name(),
                previousStatus.name(),
                targetStatus.name(),
                task.getProofOfDeliveryS3Key(),
                podUrl,
                now
        );
    }

    @Transactional
    public DriverLocationUpdatedResponse reportLocation(UUID taskId, UUID driverUserId, DriverLocationUpdateRequest request) {
        DeliveryTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found"));

        if (!driverUserId.equals(task.getAssignedDriverUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You are not the assigned driver for this delivery task");
        }

        if (!ACTIVE_DRIVER_STATUSES.contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot report location for non-active delivery task");
        }

        Instant now = Instant.now();
        saveLiveLocationToRedis(taskId, request.latitude(), request.longitude(), now);

        deliveryEventPublisher.broadcastTaskLocation(
                taskId,
                task.getStatus(),
                request.latitude(),
                request.longitude(),
                request.headingDegrees(),
                request.speedKmh()
        );

        boolean persistedToDb = shouldPersistLocationLog(taskId);
        if (persistedToDb) {
            trackingLogRepository.save(DeliveryTrackingLog.builder()
                    .deliveryTaskId(taskId)
                    .status(task.getStatus())
                    .latitude(request.latitude())
                    .longitude(request.longitude())
                    .note("GPS_PING")
                    .loggedAt(now)
                    .build());
        }

        return new DriverLocationUpdatedResponse(
                taskId,
                task.getStatus().name(),
                request.latitude(),
                request.longitude(),
                request.headingDegrees(),
                request.speedKmh(),
                persistedToDb,
                now
        );
    }

    @Transactional(readOnly = true)
    public DeliveryTaskDetailResponse getTaskDetail(UUID taskId, UUID currentUserId, boolean isAdmin) {
        DeliveryTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found"));

        if (!isAdmin && !hasTaskReadAccess(task, currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You do not have permission to view this delivery task");
        }

        DeliveryTaskDetailResponse.DriverSummary driverSummary = null;
        if (task.getAssignedDriverUserId() != null) {
            Optional<UserSummaryDto> driverOpt = iamFacade.findUserById(task.getAssignedDriverUserId());
            if (driverOpt.isPresent()) {
                UserSummaryDto driver = driverOpt.get();
                driverSummary = new DeliveryTaskDetailResponse.DriverSummary(
                        driver.id(),
                        driver.fullName(),
                        driver.phoneNumber(),
                        storageService.resolveReadUrl(driver.avatarKey())
                );
            }
        }

        List<DeliveryTrackingLog> logs = trackingLogRepository.findTop100ByDeliveryTaskIdOrderByLoggedAtDesc(taskId);
        List<DeliveryTaskDetailResponse.TrackingLogItem> logItems = logs.stream()
                .map(log -> new DeliveryTaskDetailResponse.TrackingLogItem(
                        log.getId(),
                        log.getStatus().name(),
                        log.getLatitude(),
                        log.getLongitude(),
                        log.getNote(),
                        log.getLoggedAt()
                ))
                .toList();

        DeliveryTaskDetailResponse.LiveLocation liveLocation = readLiveLocationFromRedis(taskId, logs);
        String podUrl = storageService.resolveReadUrl(task.getProofOfDeliveryS3Key());

        return new DeliveryTaskDetailResponse(
                task.getId(),
                task.getReferenceType(),
                task.getReferenceId(),
                task.getDirection().name(),
                task.getProvider().name(),
                task.getExternalTrackingId(),
                driverSummary,
                task.getPickupContactAddress(),
                task.getDropoffContactAddress(),
                task.getShippingFeeVnd(),
                task.getStatus().name(),
                podUrl,
                task.getPickupDeadlineAt(),
                liveLocation,
                logItems
        );
    }

    private boolean hasTaskReadAccess(DeliveryTask task, UUID currentUserId) {
        if (currentUserId == null) {
            return false;
        }
        if (currentUserId.equals(task.getAssignedDriverUserId()) || currentUserId.equals(task.getCreatedBy())) {
            return true;
        }
        UUID pickupUserId = extractUuid(task.getPickupContactAddress(), "userId");
        UUID dropoffUserId = extractUuid(task.getDropoffContactAddress(), "userId");
        return currentUserId.equals(pickupUserId) || currentUserId.equals(dropoffUserId);
    }

    private void saveLiveLocationToRedis(UUID taskId, Double lat, Double lon, Instant now) {
        try {
            String key = LIVE_LOC_KEY_PREFIX + taskId;
            redisTemplate.opsForHash().put(key, "latitude", String.valueOf(lat));
            redisTemplate.opsForHash().put(key, "longitude", String.valueOf(lon));
            redisTemplate.opsForHash().put(key, "updatedAt", now.toString());
            redisTemplate.expire(key, Duration.ofHours(2));
        } catch (Exception ignored) {
        }
    }

    private DeliveryTaskDetailResponse.LiveLocation readLiveLocationFromRedis(UUID taskId, List<DeliveryTrackingLog> logs) {
        try {
            String key = LIVE_LOC_KEY_PREFIX + taskId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries != null && entries.containsKey("latitude") && entries.containsKey("longitude")) {
                Double lat = Double.valueOf(entries.get("latitude").toString());
                Double lon = Double.valueOf(entries.get("longitude").toString());
                Instant updatedAt = entries.containsKey("updatedAt")
                        ? Instant.parse(entries.get("updatedAt").toString())
                        : Instant.now();
                return new DeliveryTaskDetailResponse.LiveLocation(lat, lon, updatedAt);
            }
        } catch (Exception ignored) {
        }
        for (DeliveryTrackingLog log : logs) {
            if (log.getLatitude() != null && log.getLongitude() != null) {
                return new DeliveryTaskDetailResponse.LiveLocation(log.getLatitude(), log.getLongitude(), log.getLoggedAt());
            }
        }
        return null;
    }

    private boolean shouldPersistLocationLog(UUID taskId) {
        try {
            String key = DB_LOG_THROTTLE_PREFIX + taskId;
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30));
            return Boolean.TRUE.equals(acquired);
        } catch (Exception ex) {
            return true;
        }
    }

    private DeliveryTaskAcceptedResponse toAcceptedResponse(DeliveryTask task) {
        return new DeliveryTaskAcceptedResponse(
                task.getId(),
                task.getExternalTrackingId(),
                task.getReferenceType(),
                task.getReferenceId(),
                task.getDirection().name(),
                task.getProvider().name(),
                task.getAssignedDriverUserId(),
                task.getStatus().name(),
                task.getShippingFeeVnd(),
                task.getPickupContactAddress(),
                task.getDropoffContactAddress(),
                task.getPickupDeadlineAt(),
                task.getUpdatedAt()
        );
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        Object val = map.get(key);
        if (val instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private UUID extractUuid(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        try {
            return UUID.fromString(map.get(key).toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
