package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateExpertArticleRequest(
        @NotBlank @Size(min = 15, max = 200) String title,
        @NotBlank @Size(min = 300, max = 50000) String contentMarkdown,
        @NotBlank String category,
        @Size(max = 255) String coverImageKey,
        Boolean publishNow
) {
}
