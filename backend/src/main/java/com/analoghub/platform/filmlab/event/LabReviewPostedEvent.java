package com.analoghub.platform.filmlab.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class LabReviewPostedEvent {
    private final UUID reviewId;
    private final UUID labId;
    private final UUID orderId;
}