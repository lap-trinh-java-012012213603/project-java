package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record DiscussionPostSummaryResponse(
        UUID id,
        String postType,
        String title,
        String excerpt,
        ExpertArticleDetailResponse.AuthorSummary author,
        UUID attachedArchiveFrameId,
        boolean hasAcceptedAnswer,
        long likeCount,
        long commentCount,
        Instant createdAt
) {
}
