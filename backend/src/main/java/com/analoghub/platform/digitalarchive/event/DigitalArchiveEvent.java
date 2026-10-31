package com.analogohub.platform.digitalarchive.event;

import java.util.UUID;

public record DigitalArchiveEvent(
        UUID archiveId,
        String fileUri
) {}