package com.analoghub.platform.messaging.controller;

import com.analoghub.platform.common.websocket.WebSocketTopicConstants;
import com.analoghub.platform.messaging.dto.SendChatMessagePayload;
import com.analoghub.platform.messaging.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;

    @MessageMapping(WebSocketTopicConstants.APP_CHAT_SEND)
    public void sendMessage(@Payload SendChatMessagePayload payload, Principal principal) {
        if (principal == null || principal.getName() == null) {
            log.warn("Rejected unauthenticated STOMP message to {}", WebSocketTopicConstants.APP_CHAT_SEND);
            return;
        }

        UUID senderUserId;
        try {
            senderUserId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user principal in STOMP session: {}", principal.getName());
            return;
        }

        chatMessageService.handleStompSendMessage(senderUserId, payload);
    }
}
