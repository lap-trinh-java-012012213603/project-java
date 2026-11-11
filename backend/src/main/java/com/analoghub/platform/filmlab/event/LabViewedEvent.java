package com.analoghub.platform.filmlab.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class LabViewedEvent {
    private final UUID labId;
    private final String viewerIpOrId;
}