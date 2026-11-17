package com.analoghub.platform.community.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.community.dto.CommentResponse;
import com.analoghub.platform.community.dto.ContentReportResponse;
import com.analoghub.platform.community.dto.CreateCommentRequest;
import com.analoghub.platform.community.dto.CreateContentReportRequest;
import com.analoghub.platform.community.dto.ReactionUpdatedResponse;
import com.analoghub.platform.community.dto.ToggleReactionRequest;
import com.analoghub.platform.community.service.CommunityInteractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityInteractionController {

    private final CommunityInteractionService interactionService;

    @PostMapping("/comments")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = false)
    @RateLimit(keyPrefix = "com:create-comment", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            Authentication authentication,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        CommentResponse response = interactionService.createComment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã đăng bình luận thành công.", response));
    }

    @GetMapping("/comments")
    @RateLimit(keyPrefix = "com:list-comments", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> listComments(
            @RequestParam String targetType,
            @RequestParam UUID targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<CommentResponse> response = interactionService.listComments(targetType, targetId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bình luận thành công.", response));
    }

    @PostMapping("/reactions")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "com:toggle-reaction", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ReactionUpdatedResponse>> toggleReaction(
            Authentication authentication,
            @Valid @RequestBody ToggleReactionRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        ReactionUpdatedResponse response = interactionService.toggleReaction(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật tương tác thành công.", response));
    }

    @PostMapping("/reports")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "com:submit-report", capacity = 10, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ContentReportResponse>> submitReport(
            Authentication authentication,
            @Valid @RequestBody CreateContentReportRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        ContentReportResponse response = interactionService.submitReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Đã ghi nhận báo cáo vi phạm và chuyển tới bộ phận kiểm duyệt.",
                        response
                ));
    }

    private UUID extractRequiredUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Định danh người dùng không hợp lệ");
        }
    }
}
