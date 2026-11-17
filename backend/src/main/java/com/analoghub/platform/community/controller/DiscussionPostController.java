package com.analoghub.platform.community.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.community.dto.AcceptAnswerRequest;
import com.analoghub.platform.community.dto.AcceptAnswerResponse;
import com.analoghub.platform.community.dto.CreateDiscussionPostRequest;
import com.analoghub.platform.community.dto.DiscussionPostDetailResponse;
import com.analoghub.platform.community.dto.DiscussionPostSummaryResponse;
import com.analoghub.platform.community.service.DiscussionPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/community/discussions")
@RequiredArgsConstructor
public class DiscussionPostController {

    private final DiscussionPostService discussionPostService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "com:create-discussion", capacity = 15, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DiscussionPostDetailResponse>> createDiscussionPost(
            Authentication authentication,
            @Valid @RequestBody CreateDiscussionPostRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        DiscussionPostDetailResponse response = discussionPostService.createDiscussionPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng bài viết cộng đồng thành công.", response));
    }

    @GetMapping
    @RateLimit(keyPrefix = "com:list-discussions", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<PageResponse<DiscussionPostSummaryResponse>>> listDiscussionPosts(
            @RequestParam(required = false) String postType,
            @RequestParam(defaultValue = "false") boolean unansweredOnly,
            @RequestParam(defaultValue = "NEWEST") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<DiscussionPostSummaryResponse> response = discussionPostService.listDiscussionPosts(
                postType, unansweredOnly, sortBy, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bài viết thảo luận cộng đồng thành công.", response));
    }

    @GetMapping("/{id}")
    @RateLimit(keyPrefix = "com:discussion-detail", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<DiscussionPostDetailResponse>> getDiscussionDetail(
            Authentication authentication,
            @PathVariable("id") UUID postId
    ) {
        UUID viewerId = extractOptionalUserId(authentication);
        DiscussionPostDetailResponse response = discussionPostService.getDiscussionDetail(viewerId, postId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bài thảo luận thành công.", response));
    }

    @PatchMapping("/{id}/accept-answer")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "com:accept-answer", capacity = 20, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<AcceptAnswerResponse>> acceptAnswer(
            Authentication authentication,
            @PathVariable("id") UUID postId,
            @Valid @RequestBody AcceptAnswerRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        AcceptAnswerResponse response = discussionPostService.acceptAnswer(userId, postId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu câu trả lời được chấp nhận cho câu hỏi Q&A.", response));
    }

    private UUID extractRequiredUserId(Authentication authentication) {
        UUID userId = extractOptionalUserId(authentication);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        return userId;
    }

    private UUID extractOptionalUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
