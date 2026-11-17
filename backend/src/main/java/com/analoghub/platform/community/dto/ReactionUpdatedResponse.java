package com.analoghub.platform.community.dto;

import java.util.UUID;

public record ReactionUpdatedResponse(
        String targetType,
        UUID targetId,
        String reactionType,
        boolean active,
        long currentCount
) {
}
