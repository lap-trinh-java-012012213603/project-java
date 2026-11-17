package com.analoghub.platform.community.dto;

import java.time.Instant;
import java.util.UUID;

public record DeleteExpertArticleResponse(
        UUID id,
        String status,
        boolean approvedForRag,
        Instant deletedAt
) {
}
