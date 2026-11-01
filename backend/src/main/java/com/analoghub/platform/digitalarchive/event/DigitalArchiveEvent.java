package com.analoghub.platform.digitalarchive.event;

import java.util.UUID;
import org.jmolecules.event.annotation.DomainEvent;
import java.time.Instant;

public record DigitalArchiveEvent(
        UUID archiveId,
        String fileUri,
        Instant occurredAt
) {
        public DigitalArchiveEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}