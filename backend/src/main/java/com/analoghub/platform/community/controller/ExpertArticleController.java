package com.analoghub.platform.community.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.community.dto.CreateExpertArticleRequest;
import com.analoghub.platform.community.dto.DeleteExpertArticleResponse;
import com.analoghub.platform.community.dto.ExpertArticleDetailResponse;
import com.analoghub.platform.community.dto.ExpertArticleSummaryResponse;
import com.analoghub.platform.community.dto.UpdateExpertArticleRequest;
import com.analoghub.platform.community.service.ExpertArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/articles")
@RequiredArgsConstructor
public class ExpertArticleController {

    private final ExpertArticleService articleService;

    @PostMapping
    @PreAuthorize("hasRole('EXPERT')")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "com:create-article", capacity = 10, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ExpertArticleDetailResponse>> createArticle(
            Authentication authentication,
            @Valid @RequestBody CreateExpertArticleRequest request
    ) {
        UUID expertUserId = extractRequiredUserId(authentication);
        ExpertArticleDetailResponse response = articleService.createArticle(expertUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Xuất bản bài viết chuyên môn thành công và đã đưa vào hàng đợi đồng bộ kho tri thức AI RAG.",
                        response
                ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EXPERT')")
    @RateLimit(keyPrefix = "com:update-article", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ExpertArticleDetailResponse>> updateArticle(
            Authentication authentication,
            @PathVariable("id") UUID articleId,
            @Valid @RequestBody UpdateExpertArticleRequest request
    ) {
        UUID expertUserId = extractRequiredUserId(authentication);
        ExpertArticleDetailResponse response = articleService.updateArticle(expertUserId, articleId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bài viết chuyên môn thành công.", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EXPERT', 'ADMIN')")
    @RateLimit(keyPrefix = "com:delete-article", capacity = 20, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeleteExpertArticleResponse>> retractOrDeleteArticle(
            Authentication authentication,
            @PathVariable("id") UUID articleId
    ) {
        UUID actorUserId = extractRequiredUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        DeleteExpertArticleResponse response = articleService.retractOrDeleteArticle(actorUserId, isAdmin, articleId);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gỡ bài viết chuyên môn và thu hồi dữ liệu khỏi kho tri thức AI RAG.",
                response
        ));
    }

    @GetMapping
    @RateLimit(keyPrefix = "com:list-articles", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<PageResponse<ExpertArticleSummaryResponse>>> listArticles(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID authorExpertId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PageResponse<ExpertArticleSummaryResponse> response = articleService.listArticles(
                category, authorExpertId, keyword, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bài viết chuyên môn thành công.", response));
    }

    @GetMapping("/{slugOrId}")
    @RateLimit(keyPrefix = "com:article-detail", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<ExpertArticleDetailResponse>> getArticleDetail(
            Authentication authentication,
            @PathVariable("slugOrId") String slugOrId
    ) {
        UUID viewerId = extractOptionalUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        ExpertArticleDetailResponse response = articleService.getArticleDetail(viewerId, isAdmin, slugOrId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bài viết chuyên môn thành công.", response));
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
