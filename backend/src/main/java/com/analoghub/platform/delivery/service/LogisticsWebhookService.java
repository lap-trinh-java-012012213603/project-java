package com.analoghub.platform.delivery.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import com.analoghub.platform.delivery.domain.DeliveryWebhookLog;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import com.analoghub.platform.delivery.dto.AhamoveWebhookPayload;
import com.analoghub.platform.delivery.dto.LalamoveWebhookPayload;
import com.analoghub.platform.delivery.dto.MockLogisticsWebhookRequest;
import com.analoghub.platform.delivery.dto.MockWebhookSimulatedResponse;
import com.analoghub.platform.delivery.dto.WebhookProcessedResponse;
import com.analoghub.platform.delivery.repository.DeliveryTaskRepository;
import com.analoghub.platform.delivery.repository.DeliveryTrackingLogRepository;
import com.analoghub.platform.delivery.repository.DeliveryWebhookLogRepository;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogisticsWebhookService {

    private static final String DEFAULT_AHA_SECRET = "ahamove-dev-webhook-secret";
    private static final String DEFAULT_LLM_SECRET = "lalamove-dev-webhook-secret";
    private static final String DEFAULT_MOCK_SECRET = "mock-dev-webhook-secret";

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryTrackingLogRepository trackingLogRepository;
    private final DeliveryWebhookLogRepository webhookLogRepository;
    private final HmacSignatureVerifier signatureVerifier;
    private final DeliveryEventPublisher deliveryEventPublisher;
    private final PlatformConfigFacade platformConfigFacade;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookProcessedResponse processAhamoveWebhook(String rawBody, String signatureHeader) {
        String secret = platformConfigFacade.getStringOrDefault("ahamove.webhook_secret", DEFAULT_AHA_SECRET);
        if (!signatureVerifier.verifyAhamoveSignature(secret, rawBody, signatureHeader)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid X-Aha-Signature");
        }

        AhamoveWebhookPayload payload = parseJson(rawBody, AhamoveWebhookPayload.class);
        if (payload == null || payload.id() == null || payload.id().isBlank()
                || payload.status() == null || payload.status().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_LOGISTICS_WEBHOOK, "Missing Ahamove _id or status");
        }

        String subStatus = Optional.ofNullable(payload.subStatus()).orElse("NONE");
        String webhookEventId = "AHAMOVE:" + payload.id() + ":" + payload.status() + ":" + subStatus;

        Optional<DeliveryWebhookLog> existingLog = webhookLogRepository.findByWebhookEventId(webhookEventId);
        if (existingLog.isPresent() && "PROCESSED".equals(existingLog.get().getProcessingStatus())) {
            DeliveryTaskStatus mapped = mapAhamoveStatus(payload.status(), payload.subStatus());
            return new WebhookProcessedResponse(webhookEventId, payload.id(), mapped.name(), true);
        }

        DeliveryWebhookLog webhookLog = existingLog.orElseGet(() -> DeliveryWebhookLog.builder()
                .provider("AHAMOVE")
                .webhookEventId(webhookEventId)
                .rawPayload(parseJsonMap(rawBody))
                .processingStatus("PROCESSING")
                .receivedAt(Instant.now())
                .build());
        webhookLogRepository.save(webhookLog);

        DeliveryTask task = taskRepository.findByExternalTrackingIdAndProviderForUpdate(payload.id(), LogisticsProviderType.AHAMOVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Ahamove tracking ID not found: " + payload.id()));

        DeliveryTaskStatus mappedStatus = mapAhamoveStatus(payload.status(), payload.subStatus());
        task.setStatus(mappedStatus);
        if (payload.podImageUrl() != null && !payload.podImageUrl().isBlank()) {
            task.setProofOfDeliveryS3Key(payload.podImageUrl());
        }
        taskRepository.save(task);

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(mappedStatus)
                .latitude(payload.lat())
                .longitude(payload.lng())
                .note("Ahamove status: " + payload.status())
                .loggedAt(Instant.now())
                .build());

        deliveryEventPublisher.broadcastTaskLocation(task.getId(), mappedStatus, payload.lat(), payload.lng(), null, null);
        deliveryEventPublisher.publishStatusChangedEvent(task, subStatus);

        webhookLog.setProcessingStatus("PROCESSED");
        webhookLogRepository.save(webhookLog);

        return new WebhookProcessedResponse(webhookEventId, payload.id(), mappedStatus.name(), false);
    }

    @Transactional
    public WebhookProcessedResponse processLalamoveWebhook(String rawBody, String authorizationHeader) {
        String secret = platformConfigFacade.getStringOrDefault("lalamove.webhook_secret", DEFAULT_LLM_SECRET);
        if (!signatureVerifier.verifyLalamoveAuthorization(secret, "/api/v1/webhooks/logistics/lalamove", rawBody, authorizationHeader)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid Lalamove HMAC signature");
        }

        LalamoveWebhookPayload payload = parseJson(rawBody, LalamoveWebhookPayload.class);
        if (payload == null || payload.eventId() == null || payload.eventId().isBlank()
                || payload.order() == null || payload.order().orderId() == null || payload.order().orderId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_LOGISTICS_WEBHOOK, "Missing Lalamove eventId or order.orderId");
        }

        String webhookEventId = "LALAMOVE:" + payload.eventId();
        String orderId = payload.order().orderId();
        DeliveryTaskStatus mappedStatus = mapLalamoveStatus(payload.order().status());

        Optional<DeliveryWebhookLog> existingLog = webhookLogRepository.findByWebhookEventId(webhookEventId);
        if (existingLog.isPresent() && "PROCESSED".equals(existingLog.get().getProcessingStatus())) {
            return new WebhookProcessedResponse(webhookEventId, orderId, mappedStatus.name(), true);
        }

        DeliveryWebhookLog webhookLog = existingLog.orElseGet(() -> DeliveryWebhookLog.builder()
                .provider("LALAMOVE")
                .webhookEventId(webhookEventId)
                .rawPayload(parseJsonMap(rawBody))
                .processingStatus("PROCESSING")
                .receivedAt(Instant.now())
                .build());
        webhookLogRepository.save(webhookLog);

        DeliveryTask task = taskRepository.findByExternalTrackingIdAndProviderForUpdate(orderId, LogisticsProviderType.LALAMOVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Lalamove orderId not found: " + orderId));

        task.setStatus(mappedStatus);
        taskRepository.save(task);

        Double lat = payload.driver() != null && payload.driver().location() != null ? payload.driver().location().lat() : null;
        Double lng = payload.driver() != null && payload.driver().location() != null ? payload.driver().location().lng() : null;

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(mappedStatus)
                .latitude(lat)
                .longitude(lng)
                .note("Lalamove event: " + payload.eventType())
                .loggedAt(Instant.now())
                .build());

        deliveryEventPublisher.broadcastTaskLocation(task.getId(), mappedStatus, lat, lng, null, null);
        deliveryEventPublisher.publishStatusChangedEvent(task, payload.order().status());

        webhookLog.setProcessingStatus("PROCESSED");
        webhookLogRepository.save(webhookLog);

        return new WebhookProcessedResponse(webhookEventId, orderId, mappedStatus.name(), false);
    }

    @Transactional
    public MockWebhookSimulatedResponse processMockWebhook(MockLogisticsWebhookRequest request, String mockSecretHeader) {
        String expectedSecret = platformConfigFacade.getStringOrDefault("delivery.mock_webhook_secret", DEFAULT_MOCK_SECRET);
        if (mockSecretHeader == null || !expectedSecret.equals(mockSecretHeader.trim())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid X-Mock-Secret");
        }

        DeliveryTaskStatus targetStatus;
        try {
            targetStatus = DeliveryTaskStatus.valueOf(request.simulatedStatus().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid simulatedStatus: " + request.simulatedStatus());
        }

        String webhookEventId = "MOCK:" + request.eventId();
        Optional<DeliveryWebhookLog> existingLog = webhookLogRepository.findByWebhookEventId(webhookEventId);
        if (existingLog.isPresent() && "PROCESSED".equals(existingLog.get().getProcessingStatus())) {
            return new MockWebhookSimulatedResponse(request.taskId(), targetStatus.name(), targetStatus.name(), null, true);
        }

        DeliveryTask task = taskRepository.findByIdForUpdate(request.taskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Delivery task not found: " + request.taskId()));

        String previousStatus = task.getStatus().name();
        task.setStatus(targetStatus);
        if (request.proofOfDeliveryS3Key() != null && !request.proofOfDeliveryS3Key().isBlank()) {
            task.setProofOfDeliveryS3Key(request.proofOfDeliveryS3Key());
        } else if (targetStatus == DeliveryTaskStatus.DELIVERED || targetStatus == DeliveryTaskStatus.PICKED_UP) {
            task.setProofOfDeliveryS3Key("public/demo/mock_pod.jpg");
        }
        taskRepository.save(task);

        trackingLogRepository.save(DeliveryTrackingLog.builder()
                .deliveryTaskId(task.getId())
                .status(targetStatus)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .note(request.note() != null ? request.note() : "[MOCK 3PL] " + targetStatus)
                .loggedAt(Instant.now())
                .build());

        DeliveryWebhookLog webhookLog = existingLog.orElseGet(() -> DeliveryWebhookLog.builder()
                .provider("MOCK")
                .webhookEventId(webhookEventId)
                .rawPayload(Map.of(
                        "eventId", request.eventId(),
                        "taskId", request.taskId().toString(),
                        "simulatedStatus", targetStatus.name()
                ))
                .processingStatus("PROCESSED")
                .receivedAt(Instant.now())
                .build());
        webhookLog.setProcessingStatus("PROCESSED");
        webhookLogRepository.save(webhookLog);

        deliveryEventPublisher.broadcastTaskLocation(task.getId(), targetStatus, request.latitude(), request.longitude(), null, null);
        String publishedEvent = deliveryEventPublisher.publishStatusChangedEvent(task, request.note());

        return new MockWebhookSimulatedResponse(task.getId(), previousStatus, targetStatus.name(), publishedEvent, false);
    }

    private DeliveryTaskStatus mapAhamoveStatus(String status, String subStatus) {
        if ("FAILED".equalsIgnoreCase(subStatus) || "RETURNED".equalsIgnoreCase(subStatus)) {
            return DeliveryTaskStatus.DELIVERY_FAILED;
        }
        return switch (status.toUpperCase()) {
            case "ASSIGNING" -> DeliveryTaskStatus.ASSIGNING_DRIVER;
            case "ACCEPTED" -> DeliveryTaskStatus.DRIVER_ASSIGNED;
            case "IN PROCESS" -> DeliveryTaskStatus.PICKED_UP;
            case "COMPLETED" -> DeliveryTaskStatus.DELIVERED;
            case "CANCELLED" -> DeliveryTaskStatus.DELIVERY_FAILED;
            default -> DeliveryTaskStatus.IN_TRANSIT;
        };
    }

    private DeliveryTaskStatus mapLalamoveStatus(String status) {
        if (status == null) {
            return DeliveryTaskStatus.IN_TRANSIT;
        }
        return switch (status.toUpperCase()) {
            case "ASSIGNING_DRIVER" -> DeliveryTaskStatus.ASSIGNING_DRIVER;
            case "ON_GOING" -> DeliveryTaskStatus.DRIVER_ASSIGNED;
            case "PICKED_UP" -> DeliveryTaskStatus.PICKED_UP;
            case "COMPLETED" -> DeliveryTaskStatus.DELIVERED;
            case "CANCELED", "REJECTED", "EXPIRED" -> DeliveryTaskStatus.DELIVERY_FAILED;
            default -> DeliveryTaskStatus.IN_TRANSIT;
        };
    }

    private <T> T parseJson(String rawBody, Class<T> clazz) {
        try {
            return objectMapper.readValue(rawBody, clazz);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_LOGISTICS_WEBHOOK, "Malformed webhook JSON payload", ex);
        }
    }

    private Map<String, Object> parseJsonMap(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", rawBody);
        }
    }
}
