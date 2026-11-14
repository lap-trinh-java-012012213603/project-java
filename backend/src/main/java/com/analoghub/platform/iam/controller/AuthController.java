package com.analoghub.platform.iam.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.idempotency.Idempotent;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.iam.dto.AuthTokenResponse;
import com.analoghub.platform.iam.dto.ChangePasswordRequest;
import com.analoghub.platform.iam.dto.ChangePasswordResponse;
import com.analoghub.platform.iam.dto.GoogleOAuth2LoginRequest;
import com.analoghub.platform.iam.dto.LoginRequest;
import com.analoghub.platform.iam.dto.LogoutRequest;
import com.analoghub.platform.iam.dto.LogoutResponse;
import com.analoghub.platform.iam.dto.RefreshTokenRequest;
import com.analoghub.platform.iam.dto.RegisterUserRequest;
import com.analoghub.platform.iam.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Authentication &amp; Session Management (APIs 4.1 - 4.6 of {@code 04-iam.md}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Idempotent(required = false, expirationHours = 24)
    @RateLimit(keyPrefix = "auth_register", capacity = 5, windowSeconds = 300, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> register(
            @Valid @RequestBody RegisterUserRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthTokenResponse response = authService.register(request, userAgent);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công.", response));
    }

    @PostMapping("/login")
    @RateLimit(keyPrefix = "auth_login", capacity = 10, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthTokenResponse response = authService.login(request, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công.", response));
    }

    @PostMapping("/oauth2/google")
    @RateLimit(keyPrefix = "auth_google", capacity = 10, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleOAuth2LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthTokenResponse response = authService.loginWithGoogle(request, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập qua Google thành công.", response));
    }

    @PostMapping("/refresh")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 1)
    @RateLimit(keyPrefix = "auth_refresh", capacity = 20, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthTokenResponse response = authService.refreshToken(request, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Làm mới phiên đăng nhập thành công.", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "auth_logout", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @Valid @RequestBody(required = false) LogoutRequest request
    ) {
        LogoutResponse response = authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công. Phiên làm việc đã được thu hồi.", response));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Idempotent(required = true, lockTtlSeconds = 60, expirationHours = 1)
    @RateLimit(keyPrefix = "auth_change_pwd", capacity = 5, windowSeconds = 300, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<ChangePasswordResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        ChangePasswordResponse response = authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đổi mật khẩu thành công. Toàn bộ các phiên đăng nhập cũ đã được thu hồi.",
                response
        ));
    }
}
