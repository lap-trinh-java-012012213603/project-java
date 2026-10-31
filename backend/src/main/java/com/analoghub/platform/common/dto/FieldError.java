package com.analoghub.platform.common.dto;

public record FieldError(
        String field,
        Object rejectedValue,
        String message
) {}