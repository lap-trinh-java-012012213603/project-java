package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record ExpertArticleDetailResponse(
        UUID id,
        String slug,
        String title,
        String contentMarkdown,
        String category,
        String coverImageUrl,
        String status,
        boolean approvedForRag,
        AuthorSummary authorExpert,
        ArticleStats stats,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public record AuthorSummary(
            UUID userId,
            String fullName,
            String avatarUrl
    ) {
    }

    public record ArticleStats(
            long likeCount,
            long bookmarkCount,
            long commentCount,
            boolean likedByMe,
            boolean bookmarkedByMe
    ) {
    }
}
