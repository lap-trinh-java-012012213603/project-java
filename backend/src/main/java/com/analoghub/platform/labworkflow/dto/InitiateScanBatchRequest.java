package com.analoghub.platform.labworkflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InitiateScanBatchRequest(
        @NotEmpty(message = "Danh sách frame không được để trống")
        @Size(max = 45, message = "Mỗi cuộn film chỉ hỗ trợ tối đa 45 frames")
        List<@Valid FrameFileSpec> frames
) {
    public record FrameFileSpec(
            @Min(1) @Max(45) int frameNumber,
            @NotBlank String fileName,
            @NotBlank String contentType,
            @Min(1) long fileSizeBytes
    ) {}
}