package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record DiscussionPostDetailResponse(
        UUID id,
        String postType,
        String title,
        String bodyMarkdown,
        ExpertArticleDetailResponse.AuthorSummary author,
        AttachedFrameSummary attachedFrame,
        UUID acceptedAnswerCommentId,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean bookmarkedByMe,
        String moderationStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public record AttachedFrameSummary(
            UUID frameId
    ) {
    }
}
