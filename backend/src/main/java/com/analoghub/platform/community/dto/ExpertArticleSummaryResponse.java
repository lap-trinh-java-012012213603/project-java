package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record ExpertArticleSummaryResponse(
        UUID id,
        String slug,
        String title,
        String excerpt,
        String category,
        String coverImageUrl,
        ExpertArticleDetailResponse.AuthorSummary authorExpert,
        long likeCount,
        long bookmarkCount,
        long commentCount,
        boolean approvedForRag,
        Instant createdAt
) {
}
