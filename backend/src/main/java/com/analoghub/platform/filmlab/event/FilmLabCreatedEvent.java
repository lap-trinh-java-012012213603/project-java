package com.analogohub.platform.filmlab.event;

import java.util.UUID;

public record FilmLabCreatedEvent(
        UUID labId,
        String labName
) {}