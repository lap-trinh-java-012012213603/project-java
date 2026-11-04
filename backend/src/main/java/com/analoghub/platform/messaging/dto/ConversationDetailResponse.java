package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID conversationId,
        boolean createdNew,
        String contextType,
        UUID contextReferenceId,
        Instant lastMessageAt,
        int unreadCount,
        List<ParticipantInfo> participants,
        ChatMessageResponse lastMessage
) {
    public record ParticipantInfo(
            UUID userId,
            String fullName,
            String avatarUrl,
            Instant lastReadAt
    ) {
    }
}
