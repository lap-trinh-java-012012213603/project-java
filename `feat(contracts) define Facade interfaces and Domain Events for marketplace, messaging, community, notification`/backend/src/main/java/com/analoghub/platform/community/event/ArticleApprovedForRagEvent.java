package com.analoghub.platform.community.event;

import java.time.Instant;
import java.util.UUID;

public record ArticleApprovedForRagEvent(
        UUID eventId,
        UUID articleId,
        String slug,
        String title,
        String category,
        String markdownContent,
        Instant occurredAt
) {
    public ArticleApprovedForRagEvent(UUID articleId, String slug, String title, String category, String markdownContent) {
        this(UUID.randomUUID(), articleId, slug, title, category, markdownContent, Instant.now());
    }
}
