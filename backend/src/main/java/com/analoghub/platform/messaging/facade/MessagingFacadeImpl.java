package com.analoghub.platform.messaging.facade;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.messaging.domain.ChatMessageType;
import com.analoghub.platform.messaging.domain.Conversation;
import com.analoghub.platform.messaging.domain.ConversationContextType;
import com.analoghub.platform.messaging.domain.ConversationParticipant;
import com.analoghub.platform.messaging.domain.Message;
import com.analoghub.platform.messaging.dto.ChatMessageResponse;
import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import com.analoghub.platform.messaging.event.MessageSentEvent;
import com.analoghub.platform.messaging.repository.ConversationParticipantRepository;
import com.analoghub.platform.messaging.repository.ConversationRepository;
import com.analoghub.platform.messaging.repository.MessageRepository;
import com.analoghub.platform.messaging.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessagingFacadeImpl implements MessagingFacade {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final StompNotificationPublisher stompPublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UUID getOrCreateConversation(String contextType, UUID contextReferenceId, List<UUID> participantUserIds) {
        if (participantUserIds == null || participantUserIds.size() < 2) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Cuộc hội thoại cần tối thiểu 2 thành viên"
            );
        }

        ConversationContextType parsedContext = ConversationContextType.valueOf(contextType.trim().toUpperCase());
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

    @Override
    @Transactional
    public UUID sendOfferCardMessage(OfferCardMessageRequest request) {
        UUID conversationId = getOrCreateConversation(
                ConversationContextType.MARKET_LISTING.name(),
                request.listingId(),
                List.of(request.senderUserId(), request.recipientUserId())
        );

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy cuộc hội thoại"
                ));

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

        ChatMessageResponse msgDto = conversationService.toChatMessageResponse(message);
        Map<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("eventType", "NEW_MESSAGE");
        wsPayload.put("clientMessageId", null);
        wsPayload.put("message", msgDto);

        stompPublisher.publishUserMessage(request.recipientUserId(), wsPayload);
        stompPublisher.publishUserMessage(request.senderUserId(), wsPayload);

        eventPublisher.publishEvent(new MessageSentEvent(
                message.getId(),
                conversationId,
                request.senderUserId(),
                List.of(request.recipientUserId()),
                content
        ));

        return message.getId();
    }

    @Override
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
                .senderUserId(null)
                .messageType(ChatMessageType.SYSTEM_NOTICE)
                .content(content)
                .metadataPayload(Map.of())
                .build();
        message = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        ChatMessageResponse msgDto = conversationService.toChatMessageResponse(message);

        for (ConversationParticipant p : participants) {
            p.setUnreadCount(p.getUnreadCount() + 1);
            participantRepository.save(p);

            stompPublisher.publishUserMessage(p.getUserId(), Map.of(
                    "eventType", "NEW_MESSAGE",
                    "message", msgDto
            ));
        }

        return message.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    private String buildOfferSummaryText(OfferCardMessageRequest request) {
        if ("SWAP".equalsIgnoreCase(request.offerType()) || "SWAP_WITH_CASH".equalsIgnoreCase(request.offerType())) {
            String desc = request.swapDescription() != null ? request.swapDescription() : "Thiết bị trao đổi";
            if (request.offeredPriceVnd() != null && request.offeredPriceVnd().signum() > 0) {
                return String.format("[Thẻ thương lượng] Đề nghị đổi %s bù thêm %sđ", desc, request.offeredPriceVnd().toPlainString());
            }
            return String.format("[Thẻ thương lượng] Đề nghị trao đổi: %s", desc);
        }
        String priceStr = request.offeredPriceVnd() != null ? request.offeredPriceVnd().toPlainString() : "0";
        return String.format("[Thẻ thương lượng] Đề nghị mức giá %sđ (%s)", priceStr, request.status());
    }
}
