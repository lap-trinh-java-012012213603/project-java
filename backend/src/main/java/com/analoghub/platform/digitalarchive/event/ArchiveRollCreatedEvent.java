package com.analoghub.platform.digitalarchive.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ArchiveRollCreatedEvent(
        UUID archiveRollId,
        UUID sourceFilmRollId,
        UUID ownerUserId,
        UUID sourceLabId,
        int frameCount,
        List<FrameRef> frames,
        OffsetDateTime createdAt
) {
    public record FrameRef(
            UUID frameId,
            int frameNumber,
            String s3KeyWeb,
            String s3KeyThumb
    ) {}
}