package com.analoghub.platform.iam.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.iam.dto.DeleteUserAddressResponse;
import com.analoghub.platform.iam.dto.UpsertUserAddressRequest;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.service.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for User Address Book CRUD (APIs 4.9 - 4.12 of {@code 04-iam.md}).
 */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_address_list", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<List<UserAddressDto>>> getMyAddresses() {
        List<UserAddressDto> addresses = userAddressService.getMyAddresses();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sổ địa chỉ thành công.", addresses));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 24)
    @RateLimit(keyPrefix = "user_address_create", capacity = 15, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UserAddressDto>> createAddress(
            @Valid @RequestBody UpsertUserAddressRequest request
    ) {
        UserAddressDto created = userAddressService.createAddress(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm địa chỉ mới thành công.", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_address_update", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UserAddressDto>> updateAddress(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpsertUserAddressRequest request
    ) {
        UserAddressDto updated = userAddressService.updateAddress(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật địa chỉ thành công.", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_address_delete", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<DeleteUserAddressResponse>> deleteAddress(
            @PathVariable("id") UUID id
    ) {
        DeleteUserAddressResponse response = userAddressService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa địa chỉ khỏi sổ địa chỉ.", response));
    }
}
