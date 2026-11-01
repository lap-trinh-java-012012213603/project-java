package com.analoghub.platform.community.dto;

import java.util.UUID;

public record ExpertArticleDto(
        UUID id,
        UUID authorExpertId,
        String slug,
        String title,
        String contentMarkdown,
        String category,
        String status,
        boolean approvedForRag
) {
}
