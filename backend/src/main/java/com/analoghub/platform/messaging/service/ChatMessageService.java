package com.analoghub.platform.messaging.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.messaging.domain.ChatMessageType;
import com.analoghub.platform.messaging.domain.Conversation;
import com.analoghub.platform.messaging.domain.ConversationContextType;
import com.analoghub.platform.messaging.domain.ConversationParticipant;
import com.analoghub.platform.messaging.domain.Message;
import com.analoghub.platform.messaging.dto.ChatMessageResponse;
import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import com.analoghub.platform.messaging.dto.SendChatMessagePayload;
import com.analoghub.platform.messaging.dto.StompChatEventPayload;
import com.analoghub.platform.messaging.dto.StompErrorPayload;
import com.analoghub.platform.messaging.event.MessageSentEvent;
import com.analoghub.platform.messaging.repository.ConversationParticipantRepository;
import com.analoghub.platform.messaging.repository.ConversationRepository;
import com.analoghub.platform.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final UUID SYSTEM_SENDER_ID = new UUID(0L, 0L);
    private static final String QUEUE_ERRORS = "/queue/errors";

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final StompNotificationPublisher stompPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void handleStompSendMessage(UUID senderUserId, SendChatMessagePayload payload) {
        UUID clientMsgId = payload != null ? payload.clientMessageId() : null;

        if (payload == null || payload.conversationId() == null || clientMsgId == null || payload.messageType() == null) {
            sendStompError(senderUserId, "STOMP_VALIDATION_ERROR", clientMsgId,
                    "Thiếu thông tin bắt buộc (conversationId, clientMessageId hoặc messageType)");
            return;
        }

        if (!checkWsRateLimit(senderUserId)) {
            sendStompError(senderUserId, "STOMP_RATE_LIMIT_EXCEEDED", clientMsgId,
                    "Vượt quá giới hạn 30 tin nhắn trong 10 giây");
            return;
        }

        String rawType = payload.messageType().trim().toUpperCase();
        if ("OFFER_CARD".equals(rawType) || "SYSTEM_NOTICE".equals(rawType)) {
            sendStompError(senderUserId, "FORBIDDEN_MESSAGE_TYPE", clientMsgId,
                    "Không được phép gửi trực tiếp thẻ OFFER_CARD hoặc SYSTEM_NOTICE qua kênh chat");
            return;
        }

        ChatMessageType messageType;
        try {
            messageType = ChatMessageType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            sendStompError(senderUserId, "STOMP_VALIDATION_ERROR", clientMsgId, "Loại tin nhắn không hợp lệ");
            return;
        }

        if (messageType == ChatMessageType.TEXT) {
            if (payload.content() == null || payload.content().isBlank() || payload.content().length() > 2000) {
                sendStompError(senderUserId, "STOMP_VALIDATION_ERROR", clientMsgId,
                        "Nội dung tin nhắn văn bản không được để trống và tối đa 2000 ký tự");
                return;
            }
        } else if (messageType == ChatMessageType.IMAGE) {
            List<String> keys = payload.attachmentS3Keys();
            if (keys == null || keys.isEmpty() || keys.size() > 5) {
                sendStompError(senderUserId, "STOMP_VALIDATION_ERROR", clientMsgId,
                        "Tin nhắn ảnh bắt buộc đính kèm từ 1 đến 5 file ảnh");
                return;
            }
            if (payload.content() != null && payload.content().length() > 2000) {
                sendStompError(senderUserId, "STOMP_VALIDATION_ERROR", clientMsgId,
                        "Chú thích ảnh không được vượt quá 2000 ký tự");
                return;
            }
        }

        if (!acquireClientMessageDedup(senderUserId, clientMsgId)) {
            return;
        }

        Optional<Conversation> convOpt = conversationRepository.findById(payload.conversationId());
        if (convOpt.isEmpty()) {
            sendStompError(senderUserId, "CONVERSATION_NOT_FOUND", clientMsgId,
                    "Không tìm thấy cuộc hội thoại " + payload.conversationId());
            return;
        }
        Conversation conversation = convOpt.get();

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        boolean isMember = participants.stream().anyMatch(p -> p.getUserId().equals(senderUserId));
        if (!isMember) {
            sendStompError(senderUserId, "CONVERSATION_ACCESS_DENIED", clientMsgId,
                    "Bạn không phải là thành viên của cuộc hội thoại này");
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (messageType == ChatMessageType.IMAGE) {
            String expectedPrefix = "private/chat/" + conversation.getId() + "/";
            List<Map<String, Object>> attachments = new ArrayList<>();
            for (String s3Key : payload.attachmentS3Keys()) {
                if (s3Key == null || !s3Key.startsWith(expectedPrefix) || !storageService.exists(s3Key)) {
                    sendStompError(senderUserId, "ATTACHMENT_NOT_FOUND", clientMsgId,
                            "File ảnh đính kèm chưa được tải lên hoặc sai thư mục hội thoại: " + s3Key);
                    return;
                }
                Map<String, Object> att = new LinkedHashMap<>();
                att.put("s3Key", s3Key);
                attachments.add(att);
            }
            metadata.put("attachments", attachments);
        }

        String normalizedContent = payload.content() != null ? payload.content().trim() : "";
        Instant now = Instant.now();

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .senderUserId(senderUserId)
                .messageType(messageType)
                .content(normalizedContent)
                .metadataPayload(metadata)
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        List<UUID> recipientIds = new ArrayList<>();
        for (ConversationParticipant p : participants) {
            if (!p.getUserId().equals(senderUserId)) {
                p.setUnreadCount(p.getUnreadCount() + 1);
                participantRepository.save(p);
                recipientIds.add(p.getUserId());
            }
        }

        String preview = messageType == ChatMessageType.IMAGE && normalizedContent.isEmpty()
                ? "[Hình ảnh]"
                : truncatePreview(normalizedContent);

        if (!recipientIds.isEmpty()) {
            eventPublisher.publishEvent(new MessageSentEvent(
                    message.getId(),
                    conversation.getId(),
                    senderUserId,
                    recipientIds,
                    preview
            ));
        }

        StompChatEventPayload outboundPayload = buildStompEventPayload(conversation, message, senderUserId, clientMsgId);
        List<UUID> targetUserIds = participants.stream().map(ConversationParticipant::getUserId).toList();
        runAfterCommit(() -> {
            for (UUID targetUserId : targetUserIds) {
                stompPublisher.publishUserMessage(targetUserId, outboundPayload);
            }
        });
    }

    @Transactional
    public UUID getOrCreateConversation(String contextType, UUID contextReferenceId, List<UUID> participantUserIds) {
        if (participantUserIds == null || participantUserIds.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cuộc hội thoại cần tối thiểu 2 thành viên");
        }

        ConversationContextType parsedContext;
        try {
            parsedContext = ConversationContextType.valueOf(contextType.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "contextType không hợp lệ");
        }

        UUID user1 = participantUserIds.get(0);
        UUID user2 = participantUserIds.get(1);
        UUID userA = user1.compareTo(user2) < 0 ? user1 : user2;
        UUID userB = user1.compareTo(user2) < 0 ? user2 : user1;

        Optional<Conversation> existing = conversationRepository.findByContextAndParticipantPair(
                parsedContext, contextReferenceId, userA, userB
        );
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        Instant now = Instant.now();
        Conversation conversation = Conversation.builder()
                .contextType(parsedContext)
                .contextReferenceId(contextReferenceId)
                .lastMessageAt(now)
                .build();
        conversation = conversationRepository.save(conversation);

        for (UUID userId : participantUserIds.stream().distinct().toList()) {
            ConversationParticipant participant = ConversationParticipant.builder()
                    .conversationId(conversation.getId())
                    .userId(userId)
                    .lastReadAt(null)
                    .unreadCount(0)
                    .build();
            participantRepository.save(participant);
        }

        return conversation.getId();
    }

    @Transactional
    public UUID sendOfferCardMessage(OfferCardMessageRequest request) {
        UUID conversationId = getOrCreateConversation(
                ConversationContextType.MARKET_LISTING.name(),
                request.listingId(),
                List.of(request.senderUserId(), request.recipientUserId())
        );

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("offerId", request.offerId());
        metadata.put("listingId", request.listingId());
        metadata.put("offerType", request.offerType());
        metadata.put("offeredPriceVnd", request.offeredPriceVnd());
        metadata.put("swapItemDescription", request.swapDescription());
        metadata.put("offerStatus", request.status());

        String content = buildOfferSummaryText(request);
        Instant now = Instant.now();

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderUserId(request.senderUserId())
                .messageType(ChatMessageType.OFFER_CARD)
                .content(content)
                .metadataPayload(metadata)
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        participantRepository.findByConversationIdAndUserId(conversationId, request.recipientUserId())
                .ifPresent(rp -> {
                    rp.setUnreadCount(rp.getUnreadCount() + 1);
                    participantRepository.save(rp);
                });

        eventPublisher.publishEvent(new MessageSentEvent(
                message.getId(),
                conversationId,
                request.senderUserId(),
                List.of(request.recipientUserId()),
                content
        ));

        StompChatEventPayload outboundPayload = buildStompEventPayload(conversation, message, request.senderUserId(), null);
        runAfterCommit(() -> {
            stompPublisher.publishUserMessage(request.recipientUserId(), outboundPayload);
            stompPublisher.publishUserMessage(request.senderUserId(), outboundPayload);
        });

        return message.getId();
    }

    @Transactional
    public UUID sendSystemNotice(UUID conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy cuộc hội thoại " + conversationId
                ));

        Instant now = Instant.now();
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderUserId(SYSTEM_SENDER_ID)
                .messageType(ChatMessageType.SYSTEM_NOTICE)
                .content(content)
                .metadataPayload(Map.of())
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        for (ConversationParticipant p : participants) {
            p.setUnreadCount(p.getUnreadCount() + 1);
            participantRepository.save(p);
        }

        StompChatEventPayload outboundPayload = buildStompEventPayload(conversation, message, SYSTEM_SENDER_ID, null);
        List<UUID> targetUserIds = participants.stream().map(ConversationParticipant::getUserId).toList();
        runAfterCommit(() -> {
            for (UUID userId : targetUserIds) {
                stompPublisher.publishUserMessage(userId, outboundPayload);
            }
        });

        return message.getId();
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    private StompChatEventPayload buildStompEventPayload(
            Conversation conversation,
            Message message,
            UUID senderUserId,
            UUID clientMessageId
    ) {
        ChatMessageResponse signedMsg = conversationService.toChatMessageResponse(message);
        StompChatEventPayload.SenderSummary senderSummary;

        if (SYSTEM_SENDER_ID.equals(senderUserId)) {
            senderSummary = new StompChatEventPayload.SenderSummary(SYSTEM_SENDER_ID, "Hệ thống AnalogHub", null);
        } else {
            UserSummaryDto user = iamFacade.findUserById(senderUserId).orElse(null);
            String fullName = user != null ? user.fullName() : "Người dùng AnalogHub";
            String avatarUrl = user != null ? storageService.resolveReadUrl(user.avatarKey()) : null;
            senderSummary = new StompChatEventPayload.SenderSummary(senderUserId, fullName, avatarUrl);
        }

        return new StompChatEventPayload(
                "NEW_MESSAGE",
                clientMessageId,
                new StompChatEventPayload.MessageDetail(
                        signedMsg.messageId(),
                        signedMsg.conversationId(),
                        conversation.getContextType().name(),
                        conversation.getContextReferenceId(),
                        senderSummary,
                        signedMsg.messageType(),
                        signedMsg.content(),
                        signedMsg.metadataPayload(),
                        signedMsg.createdAt()
                )
        );
    }

    private void sendStompError(UUID userId, String errorCode, UUID clientMessageId, String errorMessage) {
        if (userId == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                QUEUE_ERRORS,
                StompErrorPayload.of(errorCode, clientMessageId, errorMessage)
        );
    }

    private boolean checkWsRateLimit(UUID userId) {
        String key = "rate:ws:chat:" + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(10));
            }
            return count == null || count <= 30L;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean acquireClientMessageDedup(UUID userId, UUID clientMessageId) {
        String key = "msg:dedup:" + userId + ":" + clientMessageId;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(300));
            return !Boolean.FALSE.equals(acquired);
        } catch (Exception e) {
            return true;
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String buildOfferSummaryText(OfferCardMessageRequest request) {
        if ("SWAP".equalsIgnoreCase(request.offerType()) || "SWAP_WITH_CASH".equalsIgnoreCase(request.offerType())) {
            String desc = request.swapDescription() != null ? request.swapDescription() : "Thiết bị trao đổi";
            if (request.offeredPriceVnd() != null && request.offeredPriceVnd().signum() > 0) {
                return String.format("[Thẻ thương lượng] Đề nghị đổi %s bù thêm %sđ (%s)",
                        desc, request.offeredPriceVnd().toPlainString(), request.status());
            }
            return String.format("[Thẻ thương lượng] Đề nghị trao đổi: %s (%s)", desc, request.status());
        }
        String priceStr = request.offeredPriceVnd() != null ? request.offeredPriceVnd().toPlainString() : "0";
        return String.format("[Thẻ thương lượng] Đề nghị mức giá %sđ (%s)", priceStr, request.status());
    }

    private String truncatePreview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 117) + "...";
    }
}
