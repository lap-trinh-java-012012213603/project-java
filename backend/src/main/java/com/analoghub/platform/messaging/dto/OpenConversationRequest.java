package com.analoghub.platform.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OpenConversationRequest(
        @NotBlank(message = "contextType không được để trống")
        String contextType,

        @NotNull(message = "contextReferenceId không được để trống")
        UUID contextReferenceId,

        @NotNull(message = "recipientUserId không được để trống")
        UUID recipientUserId,

        @Size(max = 200, message = "contextTitleSnapshot không được vượt quá 200 ký tự")
        String contextTitleSnapshot,

        @Size(min = 1, max = 2000, message = "initialMessage phải có độ dài từ 1 đến 2000 ký tự")
        String initialMessage
) {
}
