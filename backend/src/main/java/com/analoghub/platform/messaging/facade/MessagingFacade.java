package com.analoghub.platform.messaging.facade;

import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import java.util.List;
import java.util.UUID;

public interface MessagingFacade {

    UUID getOrCreateConversation(String contextType, UUID contextReferenceId, List<UUID> participantUserIds);

    UUID sendOfferCardMessage(OfferCardMessageRequest request);

    UUID sendSystemNotice(UUID conversationId, String content);

    boolean isParticipant(UUID conversationId, UUID userId);
}
