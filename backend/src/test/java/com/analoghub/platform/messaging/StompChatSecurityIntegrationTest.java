package com.analoghub.platform.messaging;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.messaging.controller.ChatStompController;
import com.analoghub.platform.messaging.domain.Conversation;
import com.analoghub.platform.messaging.domain.ConversationContextType;
import com.analoghub.platform.messaging.domain.ConversationParticipant;
import com.analoghub.platform.messaging.domain.Message;
import com.analoghub.platform.messaging.dto.SendChatMessagePayload;
import com.analoghub.platform.messaging.dto.StompErrorPayload;
import com.analoghub.platform.messaging.repository.ConversationParticipantRepository;
import com.analoghub.platform.messaging.repository.ConversationRepository;
import com.analoghub.platform.messaging.repository.MessageRepository;
import com.analoghub.platform.messaging.service.ChatMessageService;
import com.analoghub.platform.messaging.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StompChatSecurityIntegrationTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationParticipantRepository participantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private StorageService storageService;

    @Mock
    private StompNotificationPublisher stompPublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Map<UUID, Conversation> conversationStore = new ConcurrentHashMap<>();
    private final List<ConversationParticipant> participantStore = new ArrayList<>();
    private final List<Message> messageStore = new ArrayList<>();
    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    private ConversationService conversationService;
    private ChatMessageService chatMessageService;
    private ChatStompController chatStompController;

    private UUID buyerUserId;
    private UUID sellerUserId;
    private UUID intruderUserId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversationStore.clear();
        participantStore.clear();
        messageStore.clear();
        redisStore.clear();

        buyerUserId = UUID.randomUUID();
        sellerUserId = UUID.randomUUID();
        intruderUserId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        Conversation conv = Conversation.builder()
                .contextType(ConversationContextType.MARKET_LISTING)
                .contextReferenceId(UUID.randomUUID())
                .lastMessageAt(Instant.now())
                .build();
        conv.setId(conversationId);
        conversationStore.put(conversationId, conv);

        participantStore.add(ConversationParticipant.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .userId(buyerUserId)
                .unreadCount(0)
                .build());
        participantStore.add(ConversationParticipant.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .userId(sellerUserId)
                .unreadCount(0)
                .build());

        lenient().when(conversationRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(conversationStore.get(inv.getArgument(0))));
        lenient().when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(inv -> {
                    Conversation c = inv.getArgument(0);
                    conversationStore.put(c.getId(), c);
                    return c;
                });

        lenient().when(participantRepository.findByConversationId(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID cId = inv.getArgument(0);
                    return participantStore.stream().filter(p -> p.getConversationId().equals(cId)).toList();
                });
        lenient().when(participantRepository.existsByConversationIdAndUserId(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID cId = inv.getArgument(0);
                    UUID uId = inv.getArgument(1);
                    return participantStore.stream()
                            .anyMatch(p -> p.getConversationId().equals(cId) && p.getUserId().equals(uId));
                });
        lenient().when(participantRepository.save(any(ConversationParticipant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(messageRepository.save(any(Message.class)))
                .thenAnswer(inv -> {
                    Message m = inv.getArgument(0);
                    if (m.getId() == null) {
                        m.setId(UUID.randomUUID());
                    }
                    if (m.getCreatedAt() == null) {
                        m.setCreatedAt(Instant.now());
                    }
                    messageStore.add(m);
                    return m;
                });

        lenient().when(iamFacade.findUserById(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    return Optional.of(new UserSummaryDto(
                            id, "user@analoghub.vn", "0909000111", "Analog Member",
                            null, AccountStatus.ACTIVE, AuthProvider.LOCAL,
                            Set.of(RoleCode.CUSTOMER), Map.of(), Instant.now()
                    ));
                });

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenReturn(1L);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    String val = inv.getArgument(1);
                    return redisStore.putIfAbsent(key, val) == null;
                });

        conversationService = new ConversationService(
                conversationRepository,
                participantRepository,
                messageRepository,
                iamFacade,
                storageService,
                stompPublisher,
                eventPublisher,
                redisTemplate
        );
        chatMessageService = new ChatMessageService(
                conversationRepository,
                participantRepository,
                messageRepository,
                conversationService,
                iamFacade,
                storageService,
                stompPublisher,
                messagingTemplate,
                eventPublisher,
                redisTemplate
        );
        chatStompController = new ChatStompController(chatMessageService);
    }

    @Test
    @DisplayName("Outsider should be blocked from reading conversation messages via REST and sending via STOMP")
    void shouldBlockNonParticipantFromReadingAndSendingStompMessages() {
        assertThatThrownBy(() -> conversationService.getConversationMessages(intruderUserId, false, conversationId, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        UUID clientMsgId = UUID.randomUUID();
        SendChatMessagePayload intruderPayload = new SendChatMessagePayload(
                clientMsgId,
                conversationId,
                "TEXT",
                "Tôi là người ngoài cố tình gửi tin vào phòng chat riêng",
                null
        );

        chatStompController.sendMessage(intruderPayload, () -> intruderUserId.toString());

        assertThat(messageStore).isEmpty();
        ArgumentCaptor<StompErrorPayload> errorCaptor = ArgumentCaptor.forClass(StompErrorPayload.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(intruderUserId.toString()),
                eq("/queue/errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue().errorCode()).isEqualTo("CONVERSATION_ACCESS_DENIED");
        assertThat(errorCaptor.getValue().clientMessageId()).isEqualTo(clientMsgId);
    }

    @Test
    @DisplayName("Participant should be blocked from spoofing OFFER_CARD or SYSTEM_NOTICE via STOMP")
    void shouldRejectClientSpoofedOfferCardAndSystemNotice() {
        UUID clientMsgId = UUID.randomUUID();
        SendChatMessagePayload spoofedOfferCard = new SendChatMessagePayload(
                clientMsgId,
                conversationId,
                "OFFER_CARD",
                "Fake accepted offer card",
                null
        );

        chatStompController.sendMessage(spoofedOfferCard, () -> buyerUserId.toString());

        assertThat(messageStore).isEmpty();
        ArgumentCaptor<StompErrorPayload> errorCaptor = ArgumentCaptor.forClass(StompErrorPayload.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(buyerUserId.toString()),
                eq("/queue/errors"),
                errorCaptor.capture()
        );
        assertThat(errorCaptor.getValue().errorCode()).isEqualTo("FORBIDDEN_MESSAGE_TYPE");
    }

    @Test
    @DisplayName("Valid STOMP message should broadcast to participants and deduplicate retries by clientMessageId")
    void shouldBroadcastValidMessageAndDeduplicateSameClientMessageId() {
        UUID clientMsgId = UUID.randomUUID();
        SendChatMessagePayload validPayload = new SendChatMessagePayload(
                clientMsgId,
                conversationId,
                "TEXT",
                "Chào bác Quân, máy Nikon FM2n còn không ạ?",
                null
        );

        chatStompController.sendMessage(validPayload, () -> buyerUserId.toString());
        chatStompController.sendMessage(validPayload, () -> buyerUserId.toString());

        assertThat(messageStore).hasSize(1);
        assertThat(messageStore.get(0).getContent()).isEqualTo("Chào bác Quân, máy Nikon FM2n còn không ạ?");
        verify(stompPublisher, times(1)).publishUserMessage(eq(buyerUserId), any());
        verify(stompPublisher, times(1)).publishUserMessage(eq(sellerUserId), any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/errors"), any());
    }
}
