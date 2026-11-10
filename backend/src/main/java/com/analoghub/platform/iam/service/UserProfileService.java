package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.ArchiveStorageException;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageCategory;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.dto.PresignAvatarUploadRequest;
import com.analoghub.platform.iam.dto.PresignedAvatarUploadResponse;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.StylePreferencesDto;
import com.analoghub.platform.iam.dto.UpdateUserProfileRequest;
import com.analoghub.platform.iam.dto.UserProfileDto;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for User Profile &amp; Film Photography Style Preferences (APIs 4.7 - 4.8 of {@code 04-iam.md}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    public static final String USER_PROFILE_CACHE_PREFIX = "iam:user_profile:";
    public static final String AI_RECSYS_LABS_CACHE_PREFIX = "ai:recsys:labs:";
    public static final Duration USER_PROFILE_CACHE_TTL = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final StorageService storageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * API 4.7: Retrieves current user's profile and active roles, reading through Redis cache
     * {@code iam:user_profile:{userId}} (TTL 15 minutes).
     */
    @Transactional(readOnly = true)
    public UserProfileDto getMyProfile() {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        UserProfileDto cached = readCachedProfile(currentUserId);
        if (cached != null) {
            return cached;
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng hoặc tài khoản đã bị vô hiệu hóa."
                ));

        Set<RoleCode> roles = resolveUserRoles(currentUserId);
        UserProfileDto profileDto = toUserProfileDto(user, roles);
        writeCachedProfile(currentUserId, profileDto);
        return profileDto;
    }

    /**
     * Uploads a user avatar image directly to S3/MinIO, updates {@code user.avatarKey} in PostgreSQL,
     * evicts Redis profile caches, and returns the updated {@link UserProfileDto} in 1 single API call.
     */
    @Transactional
    public UserProfileDto uploadMyAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vui lòng chọn file ảnh đại diện hợp lệ."
            );
        }

        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        User user = userRepository.findByIdForUpdate(currentUserId)
                .or(() -> userRepository.findByIdAndDeletedAtIsNull(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng hoặc tài khoản đã bị vô hiệu hóa."
                ));

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw ArchiveStorageException.presignFailed(ex);
        }

        String objectKey = storageService.uploadPublicFile(
                StorageCategory.AVATAR,
                currentUserId,
                file.getOriginalFilename(),
                file.getContentType(),
                fileBytes
        );

        user.setAvatarKey(objectKey);
        user = userRepository.saveAndFlush(user);
        evictUserProfileCache(currentUserId);

        Set<RoleCode> roles = resolveUserRoles(currentUserId);
        return toUserProfileDto(user, roles);
    }

    /**
     * Generates a Presigned PUT URL for uploading a public user avatar image into {@code public/avatars/{userId}/...}.
     */
    public PresignedAvatarUploadResponse presignAvatarUpload(PresignAvatarUploadRequest request) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        StorageService.PresignedUploadResult result = storageService.generatePublicUploadUrl(
                StorageCategory.AVATAR,
                currentUserId,
                request.fileName(),
                request.contentType(),
                request.fileSizeBytes()
        );
        return new PresignedAvatarUploadResponse(
                result.objectKey(),
                result.uploadPutUrl(),
                storageService.resolveReadUrl(result.objectKey()),
                result.expiresInSeconds()
        );
    }

    /**
     * API 4.8: Updates current user's profile ({@code fullName}, {@code phoneNumber}, {@code avatarKey},
     * {@code stylePreferences}) and evicts both {@code iam:user_profile:{userId}} and
     * {@code ai:recsys:labs:{userId}} Redis caches.
     */
    @Transactional
    public UserProfileDto updateMyProfile(UpdateUserProfileRequest request) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        User user = userRepository.findByIdForUpdate(currentUserId)
                .or(() -> userRepository.findByIdAndDeletedAtIsNull(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng hoặc tài khoản đã bị vô hiệu hóa."
                ));

        String normalizedPhone = AuthService.normalizeVnPhone(request.phoneNumber());
        if (normalizedPhone != null && !normalizedPhone.equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(normalizedPhone, currentUserId)) {
                throw new BusinessException(
                        ErrorCode.PHONE_ALREADY_EXISTS,
                        "Số điện thoại '" + normalizedPhone + "' đã được liên kết với một tài khoản khác trên hệ thống."
                );
            }
            user.setPhoneNumber(normalizedPhone);
        }

        user.setFullName(request.fullName().trim());
        if (request.avatarKey() != null && !request.avatarKey().isBlank()) {
            user.setAvatarKey(request.avatarKey().trim());
        }
        if (request.stylePreferences() != null) {
            user.setStylePreferences(request.stylePreferences().toMap());
        }

        user = userRepository.saveAndFlush(user);
        evictUserProfileCache(currentUserId);

        Set<RoleCode> roles = resolveUserRoles(currentUserId);
        return toUserProfileDto(user, roles);
    }

    /**
     * Evicts Redis profile and AI recommendation caches for the specified user.
     */
    public void evictUserProfileCache(UUID userId) {
        if (userId == null || stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(USER_PROFILE_CACHE_PREFIX + userId);
            stringRedisTemplate.delete(AI_RECSYS_LABS_CACHE_PREFIX + userId);
        } catch (Exception ex) {
            log.debug("[UserProfileService] Failed to evict profile cache for userId={}: {}", userId, ex.getMessage());
        }
    }

    private Set<RoleCode> resolveUserRoles(UUID userId) {
        Set<RoleCode> roles = userRoleRepository.findRoleCodesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Set.of(RoleCode.CUSTOMER);
        }
        return new LinkedHashSet<>(roles);
    }

    private UserProfileDto toUserProfileDto(User user, Set<RoleCode> roles) {
        String avatarKey = user.getAvatarKey();
        String avatarUrl = null;
        if (avatarKey != null && !avatarKey.isBlank()) {
            if (storageService != null) {
                try {
                    avatarUrl = storageService.resolveReadUrl(avatarKey);
                } catch (Exception ex) {
                    log.debug("[UserProfileService] Fallback avatarUrl for key={}: {}", avatarKey, ex.getMessage());
                }
            }
            if (avatarUrl == null) {
                avatarUrl = avatarKey;
            }
        }

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .avatarKey(avatarKey)
                .avatarUrl(avatarUrl)
                .accountStatus(user.getAccountStatus())
                .authProvider(user.getAuthProvider())
                .roles(roles)
                .labMemberships(List.of())
                .stylePreferences(StylePreferencesDto.fromMap(user.getStylePreferences()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private UserProfileDto readCachedProfile(UUID userId) {
        if (stringRedisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            var ops = stringRedisTemplate.opsForValue();
            if (ops == null) {
                return null;
            }
            String json = ops.get(USER_PROFILE_CACHE_PREFIX + userId);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, UserProfileDto.class);
        } catch (Exception ex) {
            log.debug("[UserProfileService] Cache miss/error reading profile for userId={}: {}", userId, ex.getMessage());
            return null;
        }
    }

    private void writeCachedProfile(UUID userId, UserProfileDto profileDto) {
        if (stringRedisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            var ops = stringRedisTemplate.opsForValue();
            if (ops == null) {
                return;
            }
            String json = objectMapper.writeValueAsString(profileDto);
            ops.set(USER_PROFILE_CACHE_PREFIX + userId, json, USER_PROFILE_CACHE_TTL);
        } catch (Exception ex) {
            log.debug("[UserProfileService] Failed to cache profile for userId={}: {}", userId, ex.getMessage());
        }
    }
}
