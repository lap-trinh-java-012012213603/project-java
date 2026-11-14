package com.analoghub.platform.messaging.facade;

import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import com.analoghub.platform.messaging.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessagingFacadeImpl implements MessagingFacade {

    private final ChatMessageService chatMessageService;

    @Override
    public UUID getOrCreateConversation(String contextType, UUID contextReferenceId, List<UUID> participantUserIds) {
        return chatMessageService.getOrCreateConversation(contextType, contextReferenceId, participantUserIds);
    }

    @Override
    public UUID sendOfferCardMessage(OfferCardMessageRequest request) {
        return chatMessageService.sendOfferCardMessage(request);
    }

    @Override
    public UUID sendSystemNotice(UUID conversationId, String content) {
        return chatMessageService.sendSystemNotice(conversationId, content);
    }

    @Override
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return chatMessageService.isParticipant(conversationId, userId);
    }
}
