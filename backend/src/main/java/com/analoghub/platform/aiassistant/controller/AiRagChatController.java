package com.analoghub.platform.aiassistant.controller;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.dto.AiChatHistoryResponse;
import com.analoghub.platform.aiassistant.dto.AiChatSessionListResponse;
import com.analoghub.platform.aiassistant.dto.AiChatSessionResponse;
import com.analoghub.platform.aiassistant.dto.CreateAiChatSessionRequest;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageRequest;
import com.analoghub.platform.aiassistant.dto.SendAiChatMessageResponse;
import com.analoghub.platform.aiassistant.service.rag.RagChatService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/chat/sessions")
@RequiredArgsConstructor
public class AiRagChatController {

    private final RagChatService ragChatService;

    /**
     * API 1: POST /api/v1/ai/chat/sessions
     * Tạo Phiên Trò chuyện Mới với Trợ lý Nhiếp ảnh RAG.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ai:chat:create-session", capacity = 15, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<AiChatSessionResponse>> createSession(
            @Valid @RequestBody(required = false) CreateAiChatSessionRequest request
    ) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        AiChatSessionResponse response = ragChatService.createSession(
                currentUserId,
                request != null ? request : CreateAiChatSessionRequest.builder().build()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Khởi tạo phiên trò chuyện với Trợ lý Nhiếp ảnh AI thành công.",
                        response
                ));
    }

    /**
     * API 2: GET /api/v1/ai/chat/sessions
     * Lấy Danh sách Phiên Trò chuyện của Tôi.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ai:chat:list-sessions", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<AiChatSessionListResponse>> listMySessions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) ModelProvider provider,
            @RequestParam(required = false) @Size(max = 100) String keyword
    ) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        AiChatSessionListResponse response = ragChatService.listUserSessions(
                currentUserId,
                page,
                size,
                provider,
                keyword
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách phiên trò chuyện AI thành công.",
                response
        ));
    }

    /**
     * API 3: GET /api/v1/ai/chat/sessions/{sessionId}/messages
     * Xem Lịch sử Tin nhắn & Trích dẫn của Phiên.
     */
    @GetMapping("/{sessionId}/messages")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ai:chat:get-messages", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<AiChatHistoryResponse>> getSessionMessages(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit
    ) {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        AiChatHistoryResponse response = ragChatService.getSessionMessages(
                currentUser.getUserId(),
                currentUser.isAdmin(),
                sessionId,
                cursor,
                limit
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử tin nhắn phiên trò chuyện thành công.",
                response
        ));
    }

    /**
     * API 4: POST /api/v1/ai/chat/sessions/{sessionId}/messages
     * Gửi Câu hỏi & Nhận Phản hồi RAG Có Trích dẫn.
     */
    @PostMapping("/{sessionId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(
            keyPrefix = "ai:chat:send-msg",
            capacity = 10,
            windowSeconds = 60,
            scope = RateLimitScope.USER,
            enforceDailyQuota = true
    )
    public ResponseEntity<ApiResponse<SendAiChatMessageResponse>> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendAiChatMessageRequest request
    ) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        SendAiChatMessageResponse response = ragChatService.sendMessage(
                currentUserId,
                sessionId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Trợ lý Nhiếp ảnh RAG đã phản hồi câu hỏi.",
                response
        ));
    }
}
