package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record AcceptAnswerResponse(
        UUID discussionId,
        UUID acceptedAnswerCommentId,
        UUID answerAuthorUserId,
        Instant updatedAt
) {
}
