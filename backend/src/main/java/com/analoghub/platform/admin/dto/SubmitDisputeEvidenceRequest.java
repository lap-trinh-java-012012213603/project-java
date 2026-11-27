package com.analoghub.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitDisputeEvidenceRequest(
        @NotBlank(message = "Đường dẫn S3 key bằng chứng (mediaS3Key) không được để trống")
        @Size(max = 300, message = "mediaS3Key tối đa 300 ký tự")
        @Pattern(
                regexp = "^(private|protected)/disputes/.*",
                message = "mediaS3Key phải bắt đầu bằng 'private/disputes/' hoặc 'protected/disputes/'"
        )
        String mediaS3Key,

        @NotBlank(message = "Mô tả bằng chứng (description) không được để trống")
        @Size(min = 10, max = 1500, message = "Mô tả bằng chứng phải từ 10 đến 1500 ký tự")
        String description
) {
}
