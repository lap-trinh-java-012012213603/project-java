package com.analoghub.platform.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDiscussionPostRequest(
        @NotBlank String postType,
        @NotBlank @Size(min = 10, max = 200) String title,
        @NotBlank @Size(min = 20, max = 15000) String bodyMarkdown,
        UUID attachedArchiveFrameId
) {
}
