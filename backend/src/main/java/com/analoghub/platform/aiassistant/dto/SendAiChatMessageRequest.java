package com.analoghub.platform.aiassistant.dto;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SendAiChatMessageRequest(
        @NotBlank(message = "Câu hỏi kỹ thuật nhiếp ảnh không được để trống")
        @Size(min = 3, max = 2000, message = "Câu hỏi phải có độ dài từ 3 đến 2000 ký tự")
        String question,

        ModelProvider overrideProvider,

        String filterCategory,

        @Min(value = 3, message = "Số lượng đoạn tri thức topK tối thiểu là 3")
        @Max(value = 8, message = "Số lượng đoạn tri thức topK tối đa là 8")
        Integer topK
) {
}
