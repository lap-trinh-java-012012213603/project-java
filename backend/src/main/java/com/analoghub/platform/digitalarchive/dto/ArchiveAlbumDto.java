package com.analoghub.platform.digitalarchive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ArchiveAlbumDto(
        UUID id,
        UUID ownerUserId,
        String title,
        String description,
        String visibility,
        OffsetDateTime createdAt
) {
    public record SaveAlbumRequest(
            @NotBlank @Size(max = 150) String title,
            String description,
            String visibility
    ) {}
}