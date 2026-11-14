package com.analoghub.platform.marketplace.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.marketplace.dto.CreateOfferRequest;
import com.analoghub.platform.marketplace.dto.OfferCreatedResponse;
import com.analoghub.platform.marketplace.dto.OfferSummaryResponse;
import com.analoghub.platform.marketplace.dto.OfferUpdatedResponse;
import com.analoghub.platform.marketplace.dto.RespondOfferRequest;
import com.analoghub.platform.marketplace.service.ListingOfferService;
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
@RequestMapping("/api/v1/marketplace")
@RequiredArgsConstructor
public class ListingOfferController {

    private final ListingOfferService offerService;

    @PostMapping("/listings/{id}/offers")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'EXPERT', 'LAB_OWNER')")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:submit-offer", capacity = 15, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<OfferCreatedResponse>> submitOffer(
            Authentication authentication,
            @PathVariable("id") UUID listingId,
            @Valid @RequestBody CreateOfferRequest request
    ) {
        UUID buyerUserId = extractUserId(authentication);
        OfferCreatedResponse response = offerService.submitOffer(buyerUserId, listingId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Đã gửi đề nghị thương lượng và chuyển vào cuộc hội thoại với người bán.",
                        response
                ));
    }

    @GetMapping("/listings/{id}/offers")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "mkt:list-offers", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PageResponse<OfferSummaryResponse>>> getListingOffers(
            Authentication authentication,
            @PathVariable("id") UUID listingId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdmin = hasAdminAuthority(authentication);
        PageResponse<OfferSummaryResponse> response = offerService.getListingOffers(
                currentUserId, isAdmin, listingId, status, page, size
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đề nghị thương lượng thành công.", response));
    }

    @PatchMapping("/offers/{offerId}")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(expirationHours = 24, required = true)
    @RateLimit(keyPrefix = "mkt:respond-offer", capacity = 30, windowSeconds = 3600, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<OfferUpdatedResponse>> respondToOffer(
            Authentication authentication,
            @PathVariable("offerId") UUID offerId,
            @Valid @RequestBody RespondOfferRequest request
    ) {
        UUID actorUserId = extractUserId(authentication);
        OfferUpdatedResponse response = offerService.respondToOffer(actorUserId, offerId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật phản hồi cho đề nghị thương lượng.", response));
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
