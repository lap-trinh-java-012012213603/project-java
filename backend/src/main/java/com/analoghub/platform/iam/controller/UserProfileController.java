package com.analoghub.platform.iam.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.iam.dto.PresignAvatarUploadRequest;
import com.analoghub.platform.iam.dto.PresignedAvatarUploadResponse;
import com.analoghub.platform.iam.dto.UpdateUserProfileRequest;
import com.analoghub.platform.iam.dto.UserProfileDto;
import com.analoghub.platform.iam.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for User Profile &amp; Style Preferences (APIs 4.7 - 4.8 of {@code 04-iam.md}).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_profile_get", capacity = 60, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile() {
        UserProfileDto profile = userProfileService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hồ sơ cá nhân thành công.", profile));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_profile_upload_avatar", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UserProfileDto>> uploadMyAvatar(
            @RequestParam("file") MultipartFile file
    ) {
        UserProfileDto updated = userProfileService.uploadMyAvatar(file);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh đại diện thành công.", updated));
    }

    @PostMapping("/avatar/presign")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_profile_presign_avatar", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<PresignedAvatarUploadResponse>> presignAvatarUpload(
            @Valid @RequestBody PresignAvatarUploadRequest request
    ) {
        PresignedAvatarUploadResponse response = userProfileService.presignAvatarUpload(request);
        return ResponseEntity.ok(ApiResponse.success("Tạo đường dẫn tải ảnh đại diện thành công.", response));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "user_profile_update", capacity = 20, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<UserProfileDto>> updateMyProfile(
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        UserProfileDto updated = userProfileService.updateMyProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ cá nhân thành công.", updated));
    }
}
