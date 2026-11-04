package com.analoghub.platform.messaging.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationInboxResponse(
        long totalUnreadConversations,
        long totalUnreadMessages,
        InboxPageData pageData
) {
    public record InboxPageData(
            List<ConversationSummaryItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record ConversationSummaryItem(
            UUID conversationId,
            String contextType,
            UUID contextReferenceId,
            Instant lastMessageAt,
            int myUnreadCount,
            Instant myLastReadAt,
            CounterpartyInfo counterparty,
            ChatMessageResponse lastMessagePreview
    ) {
    }

    public record CounterpartyInfo(
            UUID userId,
            String fullName,
            String avatarUrl
    ) {
    }
}
