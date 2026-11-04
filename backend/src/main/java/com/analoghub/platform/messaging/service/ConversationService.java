package com.analoghub.platform.messaging.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.messaging.domain.ChatMessageType;
import com.analoghub.platform.messaging.domain.Conversation;
import com.analoghub.platform.messaging.domain.ConversationContextType;
import com.analoghub.platform.messaging.domain.ConversationParticipant;
import com.analoghub.platform.messaging.domain.Message;
import com.analoghub.platform.messaging.dto.ChatMessageResponse;
import com.analoghub.platform.messaging.dto.ConversationDetailResponse;
import com.analoghub.platform.messaging.dto.ConversationInboxResponse;
import com.analoghub.platform.messaging.dto.CursorMessagePageResponse;
import com.analoghub.platform.messaging.dto.MarkConversationReadResponse;
import com.analoghub.platform.messaging.dto.OpenConversationRequest;
import com.analoghub.platform.messaging.event.MessageSentEvent;
import com.analoghub.platform.messaging.repository.ConversationParticipantRepository;
import com.analoghub.platform.messaging.repository.ConversationRepository;
import com.analoghub.platform.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final StompNotificationPublisher stompPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public ConversationDetailResponse openOrGetConversation(UUID currentUserId, OpenConversationRequest request) {
        if (currentUserId.equals(request.recipientUserId())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Không thể tự mở cuộc hội thoại với chính tài khoản của bạn"
            );
        }

        ConversationContextType contextType = parseContextType(request.contextType());

        UserSummaryDto recipientUser = iamFacade.findUserById(request.recipientUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy người dùng nhận tin nhắn"
                ));

        if (recipientUser.accountStatus() == AccountStatus.BANNED) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_LOCKED_OR_DISABLED,
                    "Tài khoản người nhận hiện không khả dụng"
            );
        }

        UUID userA = currentUserId.compareTo(request.recipientUserId()) < 0 ? currentUserId : request.recipientUserId();
        UUID userB = currentUserId.compareTo(request.recipientUserId()) < 0 ? request.recipientUserId() : currentUserId;
        String lockKey = String.format("lock:conv:%s:%s:%s:%s",
                contextType.name(), request.contextReferenceId(), userA, userB);

        try {
            redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
        } catch (Exception ignored) {
        }

        try {
            Instant now = Instant.now();
            Optional<Conversation> existingOpt = conversationRepository.findByContextAndParticipantPair(
                    contextType, request.contextReferenceId(), userA, userB
            );

            boolean createdNew = false;
            Conversation conversation;
            if (existingOpt.isPresent()) {
                conversation = existingOpt.get();
            } else {
                createdNew = true;
                conversation = Conversation.builder()
                        .contextType(contextType)
                        .contextReferenceId(request.contextReferenceId())
                        .lastMessageAt(now)
                        .build();
                conversation = conversationRepository.save(conversation);

                ConversationParticipant senderParticipant = ConversationParticipant.builder()
                        .conversationId(conversation.getId())
                        .userId(currentUserId)
                        .lastReadAt(now)
                        .unreadCount(0)
                        .build();

                ConversationParticipant recipientParticipant = ConversationParticipant.builder()
                        .conversationId(conversation.getId())
                        .userId(request.recipientUserId())
                        .lastReadAt(null)
                        .unreadCount(0)
                        .build();

                participantRepository.saveAll(List.of(senderParticipant, recipientParticipant));
            }

            Message latestMessage;
            if (request.initialMessage() != null && !request.initialMessage().isBlank()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (request.contextTitleSnapshot() != null && !request.contextTitleSnapshot().isBlank()) {
                    metadata.put("contextTitleSnapshot", request.contextTitleSnapshot().trim());
                }

                Message initialMsg = Message.builder()
                        .conversationId(conversation.getId())
                        .senderUserId(currentUserId)
                        .messageType(ChatMessageType.TEXT)
                        .content(request.initialMessage().trim())
                        .metadataPayload(metadata)
                        .build();
                latestMessage = messageRepository.save(initialMsg);

                conversation.setLastMessageAt(now);
                conversationRepository.save(conversation);

                participantRepository.findByConversationIdAndUserId(conversation.getId(), request.recipientUserId())
                        .ifPresent(rp -> {
                            rp.setUnreadCount(rp.getUnreadCount() + 1);
                            participantRepository.save(rp);
                        });

                ChatMessageResponse msgDto = toChatMessageResponse(latestMessage);
                stompPublisher.publishUserMessage(request.recipientUserId(), Map.of(
                        "eventType", "NEW_MESSAGE",
                        "message", msgDto
                ));

                String preview = truncatePreview(latestMessage.getContent());
                eventPublisher.publishEvent(new MessageSentEvent(
                        latestMessage.getId(),
                        conversation.getId(),
                        currentUserId,
                        List.of(request.recipientUserId()),
                        preview
                ));
            } else {
                latestMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
                        .orElse(null);
            }

            List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
            Map<UUID, UserSummaryDto> userMap = iamFacade.findUsersByIds(List.of(currentUserId, request.recipientUserId()));

            int myUnreadCount = participants.stream()
                    .filter(p -> p.getUserId().equals(currentUserId))
                    .mapToInt(ConversationParticipant::getUnreadCount)
                    .findFirst()
                    .orElse(0);

            List<ConversationDetailResponse.ParticipantInfo> participantInfos = participants.stream()
                    .map(p -> {
                        UserSummaryDto summary = userMap.get(p.getUserId());
                        String fullName = summary != null ? summary.fullName() : "Người dùng AnalogHub";
                        String avatarUrl = summary != null ? storageService.resolveReadUrl(summary.avatarKey()) : null;
                        return new ConversationDetailResponse.ParticipantInfo(
                                p.getUserId(),
                                fullName,
                                avatarUrl,
                                p.getLastReadAt()
                        );
                    })
                    .toList();

            return new ConversationDetailResponse(
                    conversation.getId(),
                    createdNew,
                    conversation.getContextType().name(),
                    conversation.getContextReferenceId(),
                    conversation.getLastMessageAt(),
                    myUnreadCount,
                    participantInfos,
                    latestMessage != null ? toChatMessageResponse(latestMessage) : null
            );
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional(readOnly = true)
    public ConversationInboxResponse getMyConversations(
            UUID currentUserId,
            String contextTypeStr,
            boolean unreadOnly,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số phân trang không hợp lệ"
            );
        }

        ConversationContextType contextType = null;
        if (contextTypeStr != null && !contextTypeStr.isBlank()) {
            contextType = parseContextType(contextTypeStr);
        }

        List<ConversationParticipant> myParticipations = participantRepository.findByUserId(currentUserId);
        long totalUnreadConversations = myParticipations.stream()
                .filter(p -> p.getUnreadCount() > 0)
                .count();
        long totalUnreadMessages = myParticipations.stream()
                .mapToLong(ConversationParticipant::getUnreadCount)
                .sum();

        Page<Conversation> convPage = conversationRepository.findUserConversations(
                currentUserId,
                contextType,
                unreadOnly,
                PageRequest.of(page, size)
        );

        List<Conversation> conversations = convPage.getContent();
        if (conversations.isEmpty()) {
            return new ConversationInboxResponse(
                    totalUnreadConversations,
                    totalUnreadMessages,
                    new ConversationInboxResponse.InboxPageData(
                            List.of(),
                            page,
                            size,
                            convPage.getTotalElements(),
                            convPage.getTotalPages(),
                            convPage.hasNext()
                    )
            );
        }

        List<UUID> convIds = conversations.stream().map(Conversation::getId).toList();
        List<ConversationParticipant> allParticipants = participantRepository.findByConversationIdIn(convIds);

        Map<UUID, List<ConversationParticipant>> participantsByConv = allParticipants.stream()
                .collect(Collectors.groupingBy(ConversationParticipant::getConversationId));

        Set<UUID> counterpartyIds = new HashSet<>();
        for (ConversationParticipant p : allParticipants) {
            if (!p.getUserId().equals(currentUserId)) {
                counterpartyIds.add(p.getUserId());
            }
        }

        Map<UUID, UserSummaryDto> userMap = counterpartyIds.isEmpty()
                ? Map.of()
                : iamFacade.findUsersByIds(counterpartyIds);

        List<ConversationInboxResponse.ConversationSummaryItem> items = new ArrayList<>();
        for (Conversation conv : conversations) {
            List<ConversationParticipant> convParticipants = participantsByConv.getOrDefault(conv.getId(), List.of());
            ConversationParticipant myPart = convParticipants.stream()
                    .filter(p -> p.getUserId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);
            ConversationParticipant otherPart = convParticipants.stream()
                    .filter(p -> !p.getUserId().equals(currentUserId))
                    .findFirst()
                    .orElse(null);

            ConversationInboxResponse.CounterpartyInfo counterpartyInfo = null;
            if (otherPart != null) {
                UserSummaryDto otherUser = userMap.get(otherPart.getUserId());
                String fullName = otherUser != null ? otherUser.fullName() : "Người dùng AnalogHub";
                String avatarUrl = otherUser != null ? storageService.resolveReadUrl(otherUser.avatarKey()) : null;
                counterpartyInfo = new ConversationInboxResponse.CounterpartyInfo(
                        otherPart.getUserId(),
                        fullName,
                        avatarUrl
                );
            }

            Message latestMsg = messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conv.getId())
                    .orElse(null);

            items.add(new ConversationInboxResponse.ConversationSummaryItem(
                    conv.getId(),
                    conv.getContextType().name(),
                    conv.getContextReferenceId(),
                    conv.getLastMessageAt(),
                    myPart != null ? myPart.getUnreadCount() : 0,
                    myPart != null ? myPart.getLastReadAt() : null,
                    counterpartyInfo,
                    latestMsg != null ? toChatMessageResponse(latestMsg) : null
            ));
        }

        return new ConversationInboxResponse(
                totalUnreadConversations,
                totalUnreadMessages,
                new ConversationInboxResponse.InboxPageData(
                        items,
                        page,
                        size,
                        convPage.getTotalElements(),
                        convPage.getTotalPages(),
                        convPage.hasNext()
                )
        );
    }

    @Transactional(readOnly = true)
    public CursorMessagePageResponse getConversationMessages(
            UUID currentUserId,
            boolean isAdmin,
            UUID conversationId,
            String beforeCursor,
            int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số limit phải nằm trong khoảng từ 1 đến 100"
            );
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy cuộc hội thoại với định danh " + conversationId
                ));

        if (!isAdmin && !participantRepository.existsByConversationIdAndUserId(conversationId, currentUserId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không phải là thành viên của cuộc hội thoại này"
            );
        }

        List<Message> fetchedMessages;
        PageRequest pageRequest = PageRequest.of(0, limit + 1);

        if (beforeCursor == null || beforeCursor.isBlank()) {
            fetchedMessages = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, pageRequest);
        } else {
            DecodedCursor cursor = decodeCursor(beforeCursor);
            fetchedMessages = messageRepository.findMessagesBeforeCursor(
                    conversationId,
                    cursor.createdAt(),
                    cursor.messageId(),
                    pageRequest
            );
        }

        boolean hasMore = fetchedMessages.size() > limit;
        List<Message> pageItems = hasMore ? fetchedMessages.subList(0, limit) : fetchedMessages;

        String nextBeforeCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            Message lastItem = pageItems.get(pageItems.size() - 1);
            nextBeforeCursor = encodeCursor(lastItem.getCreatedAt(), lastItem.getId());
        }

        List<ChatMessageResponse> responseItems = pageItems.stream()
                .map(this::toChatMessageResponse)
                .toList();

        return new CursorMessagePageResponse(
                conversation.getId(),
                conversation.getContextType().name(),
                conversation.getContextReferenceId(),
                responseItems,
                nextBeforeCursor,
                hasMore
        );
    }

    @Transactional
    public MarkConversationReadResponse markConversationAsRead(UUID currentUserId, UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Không tìm thấy cuộc hội thoại với định danh " + conversationId
            );
        }

        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCESS_DENIED,
                        "Bạn không phải là thành viên của cuộc hội thoại này"
                ));

        Instant now = Instant.now();
        participant.setUnreadCount(0);
        participant.setLastReadAt(now);
        participantRepository.save(participant);

        List<ConversationParticipant> allParticipants = participantRepository.findByConversationId(conversationId);
        for (ConversationParticipant other : allParticipants) {
            if (!other.getUserId().equals(currentUserId)) {
                stompPublisher.publishUserMessage(other.getUserId(), Map.of(
                        "eventType", "READ_RECEIPT",
                        "conversationId", conversationId,
                        "readerUserId", currentUserId,
                        "lastReadAt", now
                ));
            }
        }

        return new MarkConversationReadResponse(conversationId, currentUserId, 0, now);
    }

    public ChatMessageResponse toChatMessageResponse(Message message) {
        Map<String, Object> payload = message.getMetadataPayload() != null
                ? new HashMap<>(message.getMetadataPayload())
                : new HashMap<>();

        if (message.getMessageType() == ChatMessageType.IMAGE && payload.containsKey("attachments")) {
            Object rawAttachments = payload.get("attachments");
            if (rawAttachments instanceof List<?> list) {
                List<Map<String, Object>> signedAttachments = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> mapItem) {
                        Map<String, Object> copy = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : mapItem.entrySet()) {
                            copy.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        Object s3KeyObj = copy.get("s3Key");
                        if (s3KeyObj instanceof String s3Key && !s3Key.isBlank()) {
                            copy.put("presignedUrl", storageService.resolveReadUrl(s3Key));
                        }
                        signedAttachments.add(copy);
                    }
                }
                payload.put("attachments", signedAttachments);
            }
        }

        return new ChatMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderUserId(),
                message.getMessageType().name(),
                message.getContent(),
                payload,
                message.getCreatedAt()
        );
    }

    private ConversationContextType parseContextType(String raw) {
        try {
            return ConversationContextType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "contextType chỉ chấp nhận một trong các giá trị: MARKET_LISTING, MARKET_ORDER, LAB_ORDER"
            );
        }
    }

    private String encodeCursor(Instant createdAt, UUID messageId) {
        String raw = createdAt.toEpochMilli() + "_" + messageId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private DecodedCursor decodeCursor(String cursorStr) {
        try {
            String trimmed = cursorStr.trim();
            try {
                UUID directId = UUID.fromString(trimmed);
                Message refMsg = messageRepository.findById(directId)
                        .orElseThrow(() -> new IllegalArgumentException("Cursor message not found"));
                return new DecodedCursor(refMsg.getCreatedAt(), directId);
            } catch (IllegalArgumentException ignored) {
            }

            String decoded = new String(Base64.getUrlDecoder().decode(trimmed), StandardCharsets.UTF_8);
            if (decoded.contains("_")) {
                String[] parts = decoded.split("_", 2);
                Instant createdAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                UUID messageId = UUID.fromString(parts[1]);
                return new DecodedCursor(createdAt, messageId);
            }

            UUID decodedId = UUID.fromString(decoded);
            Message refMsg = messageRepository.findById(decodedId)
                    .orElseThrow(() -> new IllegalArgumentException("Cursor message not found"));
            return new DecodedCursor(refMsg.getCreatedAt(), decodedId);
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Con trỏ phân trang beforeCursor không hợp lệ"
            );
        }
    }

    private String truncatePreview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 117) + "...";
    }

    private record DecodedCursor(Instant createdAt, UUID messageId) {
    }
}
