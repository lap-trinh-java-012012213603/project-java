package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        String targetType,
        UUID targetId,
        UUID parentCommentId,
        ExpertArticleDetailResponse.AuthorSummary author,
        String content,
        long helpfulCount,
        Instant createdAt,
        List<CommentResponse> replies
) {
}
