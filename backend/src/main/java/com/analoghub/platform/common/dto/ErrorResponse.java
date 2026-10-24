package com.analoghub.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors,
        Instant timestamp
) {
    public static ErrorResponse of(boolean success, String code, String message, String path, List<FieldError> fieldErrors, Instant timestamp) {
        return new ErrorResponse(success, code, message, path, fieldErrors, timestamp);
    }
}