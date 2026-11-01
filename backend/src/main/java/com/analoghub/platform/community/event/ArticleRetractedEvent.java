package com.analoghub.platform.community.event;

import java.time.Instant;
import java.util.UUID;

public record ArticleRetractedEvent(
        UUID eventId,
        UUID articleId,
        String slug,
        Instant occurredAt
) {
    public ArticleRetractedEvent(UUID articleId, String slug) {
        this(UUID.randomUUID(), articleId, slug, Instant.now());
    }
}
