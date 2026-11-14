package com.analoghub.platform.filmlab.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class PostLabReviewRequest {
    private UUID orderId;
    private short rating;
    private String comment;
}