package com.analoghub.platform.marketplace.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.marketplace.dto.CreateListingRequest;
import com.analoghub.platform.marketplace.dto.DeleteListingResponse;
import com.analoghub.platform.marketplace.dto.ListingDetailResponse;
import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.dto.PresignListingImageRequest;
import com.analoghub.platform.marketplace.dto.PresignedImageUploadResponse;
import com.analoghub.platform.marketplace.dto.UpdateListingRequest;
import com.analoghub.platform.marketplace.dto.UpdateListingResponse;
import com.analoghub.platform.marketplace.service.MarketplaceListingService;
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
@RequestMapping("/api/v1/marketplace/listings")
@RequiredArgsConstructor
public class MarketplaceListingController {

    private final MarketplaceListingService listingService;

    @PostMapping("/images/presign")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'EXPERT', 'LAB_OWNER')")
    @RateLimit(keyPrefix = "mkt:presign-image", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PresignedImageUploadResponse>> presignListingImage(
            Authentication authentication,
            @Valid @RequestBody PresignListingImageRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        PresignedImageUploadResponse response = listingService.presignListingImage(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo đường dẫn tải ảnh thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'EXPERT', 'LAB_OWNER')")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:create-listing", capacity = 10, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ListingDetailResponse>> createListing(
            Authentication authentication,
            @Valid @RequestBody CreateListingRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        ListingDetailResponse response = listingService.createListing(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng tin thiết bị thành công.", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:update-listing", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UpdateListingResponse>> updateListing(
            Authentication authentication,
            @PathVariable("id") UUID listingId,
            @Valid @RequestBody UpdateListingRequest request
    ) {
        UUID userId = extractRequiredUserId(authentication);
        UpdateListingResponse response = listingService.updateListing(userId, listingId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tin đăng thành công.", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:delete-listing", capacity = 20, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeleteListingResponse>> deleteListing(
            Authentication authentication,
            @PathVariable("id") UUID listingId
    ) {
        UUID userId = extractRequiredUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        DeleteListingResponse response = listingService.deleteListing(userId, isAdmin, listingId);
        return ResponseEntity.ok(ApiResponse.success("Đã gỡ và xóa mềm tin đăng thành công.", response));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:my-listings", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<MarketplaceListingDto>>> getMyListings(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = extractRequiredUserId(authentication);
        PageResponse<MarketplaceListingDto> response = listingService.getMyListings(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tin đăng cá nhân thành công.", response));
    }

    @GetMapping("/{slugOrId}")
    @RateLimit(keyPrefix = "mkt:get-listing", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<ListingDetailResponse>> getListingDetail(
            Authentication authentication,
            @PathVariable("slugOrId") String slugOrId
    ) {
        UUID viewerId = extractOptionalUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        ListingDetailResponse response = listingService.getListingDetail(viewerId, isAdmin, slugOrId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết tin đăng thành công.", response));
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
