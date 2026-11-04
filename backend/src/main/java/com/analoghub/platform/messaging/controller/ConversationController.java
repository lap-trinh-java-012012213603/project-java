package com.analoghub.platform.messaging.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.messaging.dto.ConversationDetailResponse;
import com.analoghub.platform.messaging.dto.ConversationInboxResponse;
import com.analoghub.platform.messaging.dto.CursorMessagePageResponse;
import com.analoghub.platform.messaging.dto.MarkConversationReadResponse;
import com.analoghub.platform.messaging.dto.OpenConversationRequest;
import com.analoghub.platform.messaging.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Idempotent(required = false)
    @RateLimit(keyPrefix = "msg:conv:open", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> openOrGetConversation(
            Authentication authentication,
            @Valid @RequestBody OpenConversationRequest request
    ) {
        UUID currentUserId = extractUserId(authentication);
        ConversationDetailResponse response = conversationService.openOrGetConversation(currentUserId, request);
        HttpStatus status = response.createdNew() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = response.createdNew()
                ? "Khởi tạo cuộc hội thoại thành công"
                : "Lấy thông tin cuộc hội thoại thành công";
        return ResponseEntity.status(status).body(ApiResponse.success(message, response));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "msg:conv:list", capacity = 120, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ConversationInboxResponse>> getMyConversations(
            Authentication authentication,
            @RequestParam(required = false) String contextType,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = extractUserId(authentication);
        ConversationInboxResponse response = conversationService.getMyConversations(
                currentUserId, contextType, unreadOnly, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "msg:history", capacity = 180, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<CursorMessagePageResponse>> getConversationMessages(
            Authentication authentication,
            @PathVariable("id") UUID conversationId,
            @RequestParam(required = false) String beforeCursor,
            @RequestParam(defaultValue = "30") int limit
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        CursorMessagePageResponse response = conversationService.getConversationMessages(
                currentUserId, isAdmin, conversationId, beforeCursor, limit
        );
        return ResponseEntity.ok(ApiResponse.success("OK", response));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "msg:read", capacity = 120, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<MarkConversationReadResponse>> markConversationAsRead(
            Authentication authentication,
            @PathVariable("id") UUID conversationId
    ) {
        UUID currentUserId = extractUserId(authentication);
        MarkConversationReadResponse response = conversationService.markConversationAsRead(currentUserId, conversationId);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu đọc toàn bộ tin nhắn trong cuộc hội thoại", response));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Định danh người dùng không hợp lệ");
        }
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }
}
