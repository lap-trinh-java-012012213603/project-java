package com.analoghub.platform.delivery;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.delivery.controller.LogisticsWebhookController;
import com.analoghub.platform.delivery.domain.DeliveryDirection;
import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import com.analoghub.platform.delivery.domain.DeliveryWebhookLog;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import com.analoghub.platform.delivery.dto.WebhookProcessedResponse;
import com.analoghub.platform.delivery.event.FilmBagPickedUpEvent;
import com.analoghub.platform.delivery.event.MarketParcelDeliveredEvent;
import com.analoghub.platform.delivery.repository.DeliveryTaskRepository;
import com.analoghub.platform.delivery.repository.DeliveryTrackingLogRepository;
import com.analoghub.platform.delivery.repository.DeliveryWebhookLogRepository;
import com.analoghub.platform.delivery.service.DeliveryEventPublisher;
import com.analoghub.platform.delivery.service.HmacSignatureVerifier;
import com.analoghub.platform.delivery.service.LogisticsWebhookService;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogisticsWebhookIntegrationTest {

    private static final String AHAMOVE_SECRET = "aha-test-hmac-secret-2026";
    private static final String LALAMOVE_SECRET = "llm-test-hmac-secret-2026";

    @Mock
    private DeliveryTaskRepository taskRepository;

    @Mock
    private DeliveryTrackingLogRepository trackingLogRepository;

    @Mock
    private DeliveryWebhookLogRepository webhookLogRepository;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private StompNotificationPublisher stompPublisher;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, DeliveryTask> taskByExternalId = new ConcurrentHashMap<>();
    private final Map<String, DeliveryWebhookLog> webhookLogByEventId = new ConcurrentHashMap<>();
    private final List<DeliveryTrackingLog> trackingLogs = new ArrayList<>();

    private HmacSignatureVerifier signatureVerifier;
    private DeliveryEventPublisher deliveryEventPublisher;
    private LogisticsWebhookService webhookService;
    private LogisticsWebhookController webhookController;

    private UUID labOrderId;
    private UUID marketOrderId;
    private UUID customerId;
    private UUID labId;
    private UUID sellerId;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        taskByExternalId.clear();
        webhookLogByEventId.clear();
        trackingLogs.clear();

        labOrderId = UUID.randomUUID();
        marketOrderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        labId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        buyerId = UUID.randomUUID();

        DeliveryTask ahamoveTask = DeliveryTask.builder()
                .referenceType("LAB_ORDER")
                .referenceId(labOrderId)
                .direction(DeliveryDirection.PICKUP_TO_LAB)
                .provider(LogisticsProviderType.AHAMOVE)
                .externalTrackingId("AHA-SGN-1001")
                .pickupContactAddress(Map.of("userId", customerId.toString()))
                .dropoffContactAddress(Map.of("labId", labId.toString()))
                .shippingFeeVnd(new BigDecimal("35000.00"))
                .status(DeliveryTaskStatus.DRIVER_ASSIGNED)
                .build();
        ahamoveTask.setId(UUID.randomUUID());
        taskByExternalId.put("AHA-SGN-1001", ahamoveTask);

        DeliveryTask lalamoveTask = DeliveryTask.builder()
                .referenceType("MARKETPLACE_ORDER")
                .referenceId(marketOrderId)
                .direction(DeliveryDirection.C2C_SHIPPING)
                .provider(LogisticsProviderType.LALAMOVE)
                .externalTrackingId("LLM-SGN-2002")
                .pickupContactAddress(Map.of("userId", sellerId.toString()))
                .dropoffContactAddress(Map.of("userId", buyerId.toString()))
                .shippingFeeVnd(new BigDecimal("45000.00"))
                .status(DeliveryTaskStatus.PICKED_UP)
                .build();
        lalamoveTask.setId(UUID.randomUUID());
        taskByExternalId.put("LLM-SGN-2002", lalamoveTask);

        lenient().when(platformConfigFacade.getStringOrDefault(eq("ahamove.webhook_secret"), anyString()))
                .thenReturn(AHAMOVE_SECRET);
        lenient().when(platformConfigFacade.getStringOrDefault(eq("lalamove.webhook_secret"), anyString()))
                .thenReturn(LALAMOVE_SECRET);

        lenient().when(taskRepository.findByExternalTrackingIdAndProviderForUpdate(anyString(), any(LogisticsProviderType.class)))
                .thenAnswer(inv -> Optional.ofNullable(taskByExternalId.get(inv.getArgument(0))));
        lenient().when(taskRepository.save(any(DeliveryTask.class)))
                .thenAnswer(inv -> {
                    DeliveryTask t = inv.getArgument(0);
                    taskByExternalId.put(t.getExternalTrackingId(), t);
                    return t;
                });

        lenient().when(webhookLogRepository.findByWebhookEventId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(webhookLogByEventId.get(inv.getArgument(0))));
        lenient().when(webhookLogRepository.save(any(DeliveryWebhookLog.class)))
                .thenAnswer(inv -> {
                    DeliveryWebhookLog log = inv.getArgument(0);
                    webhookLogByEventId.put(log.getWebhookEventId(), log);
                    return log;
                });

        lenient().when(trackingLogRepository.save(any(DeliveryTrackingLog.class)))
                .thenAnswer(inv -> {
                    DeliveryTrackingLog log = inv.getArgument(0);
                    trackingLogs.add(log);
                    return log;
                });

        signatureVerifier = new HmacSignatureVerifier();
        deliveryEventPublisher = new DeliveryEventPublisher(eventPublisher, stompPublisher);
        webhookService = new LogisticsWebhookService(
                taskRepository,
                trackingLogRepository,
                webhookLogRepository,
                signatureVerifier,
                deliveryEventPublisher,
                platformConfigFacade,
                new ObjectMapper()
        );
        webhookController = new LogisticsWebhookController(webhookService);
    }

    @Test
    @DisplayName("Ahamove webhook should verify HMAC-SHA256, publish FilmBagPickedUpEvent and deduplicate retries")
    void shouldVerifyAhamoveHmacAndDeduplicateWebhookRetries() {
        String rawBody = """
                {
                  "_id": "AHA-SGN-1001",
                  "status": "IN PROCESS",
                  "sub_status": "PICKED_UP",
                  "lat": 10.7769,
                  "lng": 106.7009
                }
                """;
        String validSignature = signatureVerifier.hmacSha256Hex(AHAMOVE_SECRET, rawBody);

        WebhookProcessedResponse firstCall = webhookController.handleAhamove(rawBody, validSignature)
                .getBody().getData();
        assertThat(firstCall.duplicateIgnored()).isFalse();
        assertThat(firstCall.mappedInternalStatus()).isEqualTo("PICKED_UP");
        assertThat(taskByExternalId.get("AHA-SGN-1001").getStatus()).isEqualTo(DeliveryTaskStatus.PICKED_UP);

        WebhookProcessedResponse secondCall = webhookController.handleAhamove(rawBody, validSignature)
                .getBody().getData();
        assertThat(secondCall.duplicateIgnored()).isTrue();

        ArgumentCaptor<FilmBagPickedUpEvent> eventCaptor = ArgumentCaptor.forClass(FilmBagPickedUpEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(labOrderId);
        assertThat(eventCaptor.getValue().customerId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("Lalamove v3 webhook should verify timestamped HMAC header and publish MarketParcelDeliveredEvent")
    void shouldVerifyLalamoveHmacAndPublishMarketParcelDeliveredEvent() {
        String rawBody = """
                {
                  "eventId": "EVT-LLM-998877",
                  "eventType": "ORDER_STATUS_CHANGED",
                  "order": {
                    "orderId": "LLM-SGN-2002",
                    "status": "COMPLETED"
                  },
                  "driver": {
                    "name": "Nguyen Van Tai",
                    "phone": "0901234567",
                    "location": {
                      "lat": 10.7740,
                      "lng": 106.7030
                    }
                  }
                }
                """;
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String signatureInput = timestamp + "\r\nPOST\r\n/api/v1/webhooks/logistics/lalamove\r\n\r\n" + rawBody;
        String hmacHex = signatureVerifier.hmacSha256Hex(LALAMOVE_SECRET, signatureInput);
        String authHeader = "hmac pk_test_analoghub:" + timestamp + ":" + hmacHex;

        WebhookProcessedResponse response = webhookController.handleLalamove(rawBody, authHeader)
                .getBody().getData();
        assertThat(response.duplicateIgnored()).isFalse();
        assertThat(response.mappedInternalStatus()).isEqualTo("DELIVERED");
        assertThat(taskByExternalId.get("LLM-SGN-2002").getStatus()).isEqualTo(DeliveryTaskStatus.DELIVERED);

        ArgumentCaptor<MarketParcelDeliveredEvent> eventCaptor = ArgumentCaptor.forClass(MarketParcelDeliveredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().marketOrderId()).isEqualTo(marketOrderId);
        assertThat(eventCaptor.getValue().buyerId()).isEqualTo(buyerId);
        assertThat(eventCaptor.getValue().sellerId()).isEqualTo(sellerId);
    }

    @Test
    @DisplayName("Forged HMAC signature on Ahamove or Lalamove webhook should be rejected with 401 UNAUTHORIZED")
    void shouldRejectForgedHmacSignatures() {
        String rawBody = "{\"_id\":\"AHA-SGN-1001\",\"status\":\"COMPLETED\"}";
        assertThatThrownBy(() -> webhookController.handleAhamove(rawBody, "deadbeef001122334455"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        assertThatThrownBy(() -> webhookController.handleLalamove(rawBody, "hmac pk:1700000000000:forgedsig"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
