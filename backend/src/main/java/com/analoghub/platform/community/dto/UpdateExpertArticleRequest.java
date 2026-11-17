package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateExpertArticleRequest(
        @NotBlank @Size(min = 15, max = 200) String title,
        @NotBlank @Size(min = 300, max = 50000) String contentMarkdown,
        @NotBlank String category,
        @Size(max = 255) String coverImageKey,
        @NotBlank String status,
        @NotNull @Min(0) Long version
) {
}
