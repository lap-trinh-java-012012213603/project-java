package com.analoghub.platform.aiassistant.dto;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateAiChatSessionRequest(
        @Size(max = 150, message = "Tiêu đề phiên không vượt quá 150 ký tự")
        String title,

        ModelProvider preferredProvider,

        @Size(max = 64, message = "Mã cuộn film không vượt quá 64 ký tự")
        @Pattern(regexp = "^[A-Z0-9_\\-]+$", message = "Mã cuộn film chỉ bao gồm chữ hoa, chữ số, dấu gạch dưới hoặc gạch ngang")
        String contextFilmStockCode
) {
}
