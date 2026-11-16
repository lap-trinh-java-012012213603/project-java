package com.analoghub.platform.aiassistant.dto;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record TriggerScanDiagnosticRequest(
        Boolean runVisionLlmStage2,
        ModelProvider preferredProvider,
        Boolean forceReanalyze,

        @Size(max = 500, message = "Ghi chú triệu chứng lỗi không vượt quá 500 ký tự")
        String userSymptomNote
) {
}
