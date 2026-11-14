package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.iam.domain.Role;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.domain.UserRole;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.AuthTokenResponse;
import com.analoghub.platform.iam.dto.AuthUserDto;
import com.analoghub.platform.iam.dto.ChangePasswordRequest;
import com.analoghub.platform.iam.dto.ChangePasswordResponse;
import com.analoghub.platform.iam.dto.GoogleOAuth2LoginRequest;
import com.analoghub.platform.iam.dto.LoginRequest;
import com.analoghub.platform.iam.dto.LogoutRequest;
import com.analoghub.platform.iam.dto.LogoutResponse;
import com.analoghub.platform.iam.dto.RefreshTokenRequest;
import com.analoghub.platform.iam.dto.RegisterUserRequest;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.StylePreferencesDto;
import com.analoghub.platform.iam.event.UserRegisteredEvent;
import com.analoghub.platform.iam.repository.RoleRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.JwtTokenProvider;
import com.analoghub.platform.security.SecurityContextUtils;
import com.analoghub.platform.security.TokenBlacklistRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Core authentication service for {@code iam} implementing APIs 4.1 through 4.6 of {@code 04-iam.md}:
 * <ul>
 *     <li>{@code POST /api/v1/auth/register}</li>
 *     <li>{@code POST /api/v1/auth/login}</li>
 *     <li>{@code POST /api/v1/auth/oauth2/google}</li>
 *     <li>{@code POST /api/v1/auth/refresh}</li>
 *     <li>{@code POST /api/v1/auth/logout}</li>
 *     <li>{@code POST /api/v1/auth/change-password}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String LOGIN_ATTEMPTS_KEY_PREFIX = "auth:login_attempts:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT_TTL = Duration.ofMinutes(15);
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M1ws4cH2c.8eK2p8e8R5v2t9w5yW";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenFamilyService refreshTokenFamilyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRedis tokenBlacklistRedis;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * API 4.1: Registers a new local user account, assigns default {@code CUSTOMER} role,
     * initializes a new refresh token family, and publishes {@link UserRegisteredEvent}.
     */
    @Transactional
    public AuthTokenResponse register(RegisterUserRequest request, String userAgent) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedPhone = normalizeVnPhone(request.phoneNumber());

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Địa chỉ email '" + normalizedEmail + "' đã được sử dụng bởi một tài khoản khác."
            );
        }
        if (userRepository.existsByPhoneNumberAndDeletedAtIsNull(normalizedPhone)) {
            throw new BusinessException(
                    ErrorCode.PHONE_ALREADY_EXISTS,
                    "Số điện thoại '" + normalizedPhone + "' đã được đăng ký bởi một tài khoản khác."
            );
        }

        Map<String, Object> stylePrefsMap = request.stylePreferences() != null
                ? request.stylePreferences().toMap()
                : new LinkedHashMap<>();

        User user = User.builder()
                .email(normalizedEmail)
                .phoneNumber(normalizedPhone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .stylePreferences(stylePrefsMap)
                .build();
        user = userRepository.save(user);

        assignDefaultCustomerRole(user.getId());
        Set<RoleCode> roles = Set.of(RoleCode.CUSTOMER);

        RefreshTokenFamilyService.IssuedRefreshToken issuedRefresh =
                refreshTokenFamilyService.createNewTokenFamily(user.getId(), userAgent);
        String accessToken = issueJwtAccessToken(user.getId(), roles);

        eventPublisher.publishEvent(new UserRegisteredEvent(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                Instant.now()
        ));

        return buildAuthTokenResponse(user, roles, accessToken, issuedRefresh, null, null);
    }

    /**
     * API 4.2: Authenticates user with email &amp; password, enforcing Redis brute-force protection
     * ({@code auth:login_attempts:{email}}, max 5 failed attempts in 15 minutes).
     */
    @Transactional(noRollbackFor = {AuthException.class, BusinessException.class})
    public AuthTokenResponse login(LoginRequest request, String userAgent) {
        String normalizedEmail = normalizeEmail(request.email());
        ensureNotBruteForceLocked(normalizedEmail);

        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);
        if (userOpt.isEmpty() || userOpt.get().getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            int remaining = recordFailedLoginAttempt(normalizedEmail);
            throw buildInvalidCredentialsException(remaining);
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int remaining = recordFailedLoginAttempt(normalizedEmail);
            throw buildInvalidCredentialsException(remaining);
        }

        clearFailedLoginAttempts(normalizedEmail);
        requireActiveAccount(user);

        Set<RoleCode> roles = resolveUserRoles(user.getId());
        String deviceInfo = (request.deviceInfo() != null && !request.deviceInfo().isBlank())
                ? request.deviceInfo().trim()
                : userAgent;

        RefreshTokenFamilyService.IssuedRefreshToken issuedRefresh =
                refreshTokenFamilyService.createNewTokenFamily(user.getId(), deviceInfo);
        String accessToken = issueJwtAccessToken(user.getId(), roles);

        return buildAuthTokenResponse(user, roles, accessToken, issuedRefresh, null, null);
    }

    /**
     * API 4.3: Verifies a Google OAuth2/OIDC ID Token and either provisions a new {@code GOOGLE}
     * user account or links/authenticates an existing account.
     */
    @Transactional
    public AuthTokenResponse loginWithGoogle(GoogleOAuth2LoginRequest request, String userAgent) {
        GoogleIdTokenVerifier.GoogleUserPayload googleUser = googleIdTokenVerifier.verify(request.idToken());
        String normalizedEmail = normalizeEmail(googleUser.email());

        Optional<User> existingOpt = userRepository
                .findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, googleUser.sub())
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail));

        boolean isNewUser;
        User user;
        Set<RoleCode> roles;

        if (existingOpt.isEmpty()) {
            isNewUser = true;
            user = User.builder()
                    .email(normalizedEmail)
                    .fullName(googleUser.fullName())
                    .accountStatus(AccountStatus.ACTIVE)
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId(googleUser.sub())
                    .stylePreferences(new LinkedHashMap<>())
                    .build();
            user = userRepository.save(user);
            assignDefaultCustomerRole(user.getId());
            roles = Set.of(RoleCode.CUSTOMER);

            eventPublisher.publishEvent(new UserRegisteredEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    Instant.now()
            ));
        } else {
            isNewUser = false;
            user = existingOpt.get();
            requireActiveAccount(user);

            if (user.getProviderId() == null || user.getProviderId().isBlank()) {
                user.setProviderId(googleUser.sub());
                user = userRepository.save(user);
            }
            roles = resolveUserRoles(user.getId());
        }

        String deviceInfo = (request.deviceInfo() != null && !request.deviceInfo().isBlank())
                ? request.deviceInfo().trim()
                : userAgent;

        RefreshTokenFamilyService.IssuedRefreshToken issuedRefresh =
                refreshTokenFamilyService.createNewTokenFamily(user.getId(), deviceInfo);
        String accessToken = issueJwtAccessToken(user.getId(), roles);

        return buildAuthTokenResponse(user, roles, accessToken, issuedRefresh, isNewUser, googleUser.pictureUrl());
    }

    /**
     * API 4.4: Rotates a Refresh Token within its {@code token_family_id} and issues a fresh
     * Access Token + Refresh Token pair. Triggers Reuse Detection if the token was already revoked.
     */
    @Transactional(noRollbackFor = {AuthException.class, BusinessException.class})
    public AuthTokenResponse refreshToken(RefreshTokenRequest request, String userAgent) {
        RefreshTokenFamilyService.IssuedRefreshToken rotated =
                refreshTokenFamilyService.validateAndRotate(request.refreshToken(), userAgent);

        UUID userId = rotated.tokenEntity().getUserId();
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.REFRESH_TOKEN_EXPIRED,
                        "Tài khoản người dùng không tồn tại hoặc đã bị vô hiệu hóa."
                ));

        if (!user.isActive()) {
            refreshTokenFamilyService.revokeAllUserTokens(userId);
            throw new AuthException(
                    ErrorCode.ACCOUNT_LOCKED_OR_DISABLED,
                    "Tài khoản của bạn đã bị tạm ngưng hoặc khóa vĩnh viễn."
            );
        }

        Set<RoleCode> roles = resolveUserRoles(userId);
        String accessToken = issueJwtAccessToken(userId, roles);

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.rawRefreshToken())
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresInSeconds(resolveAccessExpiresInSeconds())
                .refreshExpiresInSeconds(rotated.expiresInSeconds())
                .build();
    }

    /**
     * API 4.5: Logs out the current session (blacklisting the Access Token {@code jti} in Redis
     * and revoking either the current device's Refresh Token or all user sessions).
     */
    @Transactional
    public LogoutResponse logout(LogoutRequest request) {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        UUID userId = currentUser.getUserId();
        String jti = currentUser.getJti();

        if (jti != null && !jti.isBlank()) {
            Instant expiresAt = currentUser.getExpiresAt() != null
                    ? currentUser.getExpiresAt()
                    : Instant.now().plusMillis(jwtTokenProvider.getAccessExpirationMs());
            Duration remainingTtl = Duration.between(Instant.now(), expiresAt);
            tokenBlacklistRedis.blacklistToken(jti, remainingTtl, "USER_LOGOUT");
        }

        boolean logoutAllDevices = request != null && Boolean.TRUE.equals(request.logoutAllDevices());
        if (logoutAllDevices) {
            refreshTokenFamilyService.revokeAllUserTokens(userId);
        } else if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenFamilyService.revokeSingleToken(userId, request.refreshToken());
        }

        return new LogoutResponse(jti, logoutAllDevices, Instant.now());
    }

    /**
     * API 4.6: Changes the current user's password and immediately revokes all active
     * Refresh Tokens and Access Tokens across all devices.
     */
    @Transactional
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        UUID userId = currentUser.getUserId();

        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu mới và xác nhận mật khẩu không khớp nhau."
            );
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mật khẩu mới không được trùng với mật khẩu hiện tại."
            );
        }

        User user = userRepository.findByIdForUpdate(userId)
                .or(() -> userRepository.findByIdAndDeletedAtIsNull(userId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng."
                ));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Mật khẩu hiện tại không chính xác."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);

        if (currentUser.getJti() != null && !currentUser.getJti().isBlank()) {
            Instant expiresAt = currentUser.getExpiresAt() != null
                    ? currentUser.getExpiresAt()
                    : Instant.now().plusMillis(jwtTokenProvider.getAccessExpirationMs());
            tokenBlacklistRedis.blacklistToken(
                    currentUser.getJti(),
                    Duration.between(Instant.now(), expiresAt),
                    "PASSWORD_CHANGED"
            );
        }
        refreshTokenFamilyService.revokeAllUserTokens(userId);

        return new ChangePasswordResponse(Instant.now());
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private void assignDefaultCustomerRole(UUID userId) {
        Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "Vai trò mặc định CUSTOMER chưa được khởi tạo trong hệ thống."
                ));
        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(customerRole.getId())
                .grantedAt(Instant.now())
                .build();
        userRoleRepository.save(userRole);
    }

    private Set<RoleCode> resolveUserRoles(UUID userId) {
        Set<RoleCode> roles = userRoleRepository.findRoleCodesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Set.of(RoleCode.CUSTOMER);
        }
        return new LinkedHashSet<>(roles);
    }

    private String issueJwtAccessToken(UUID userId, Set<RoleCode> roles) {
        List<String> roleNames = roles.stream().map(RoleCode::name).toList();
        return jwtTokenProvider.generateAccessToken(userId, roleNames, List.of());
    }

    private long resolveAccessExpiresInSeconds() {
        long ms = jwtTokenProvider.getAccessExpirationMs();
        return ms > 0 ? ms / 1000L : 900L;
    }

    private AuthTokenResponse buildAuthTokenResponse(
            User user,
            Set<RoleCode> roles,
            String accessToken,
            RefreshTokenFamilyService.IssuedRefreshToken issuedRefresh,
            Boolean isNewUser,
            String fallbackAvatarUrl
    ) {
        AuthUserDto userDto = AuthUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarKey() != null ? user.getAvatarKey() : fallbackAvatarUrl)
                .accountStatus(user.getAccountStatus())
                .authProvider(user.getAuthProvider())
                .roles(roles)
                .labMemberships(List.of())
                .stylePreferences(StylePreferencesDto.fromMap(user.getStylePreferences()))
                .createdAt(user.getCreatedAt())
                .build();

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(issuedRefresh.rawRefreshToken())
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresInSeconds(resolveAccessExpiresInSeconds())
                .refreshExpiresInSeconds(issuedRefresh.expiresInSeconds())
                .isNewUser(isNewUser)
                .user(userDto)
                .build();
    }

    private void requireActiveAccount(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(
                    ErrorCode.ACCOUNT_LOCKED_OR_DISABLED,
                    "Tài khoản của bạn đang bị tạm khóa hoặc đình chỉ hoạt động."
            );
        }
    }

    private void ensureNotBruteForceLocked(String normalizedEmail) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            var ops = stringRedisTemplate.opsForValue();
            if (ops == null) {
                return;
            }
            String rawAttempts = ops.get(LOGIN_ATTEMPTS_KEY_PREFIX + normalizedEmail);
            if (rawAttempts != null) {
                long attempts = Long.parseLong(rawAttempts.trim());
                if (attempts >= MAX_LOGIN_ATTEMPTS) {
                    throw new AuthException(
                            ErrorCode.ACCOUNT_LOCKED_OR_DISABLED,
                            "Tài khoản tạm thời bị khóa 15 phút do nhập sai mật khẩu quá 5 lần liên tiếp."
                    );
                }
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[AuthService] Failed to read login_attempts from Redis for email={}: {}",
                    normalizedEmail, ex.getMessage());
        }
    }

    private int recordFailedLoginAttempt(String normalizedEmail) {
        if (stringRedisTemplate == null) {
            return MAX_LOGIN_ATTEMPTS - 1;
        }
        try {
            var ops = stringRedisTemplate.opsForValue();
            if (ops == null) {
                return MAX_LOGIN_ATTEMPTS - 1;
            }
            String key = LOGIN_ATTEMPTS_KEY_PREFIX + normalizedEmail;
            Long current = ops.increment(key);
            long count = current != null ? current : 1L;
            if (count == 1L) {
                stringRedisTemplate.expire(key, LOGIN_LOCKOUT_TTL);
            }
            return (int) Math.max(0L, MAX_LOGIN_ATTEMPTS - count);
        } catch (Exception ex) {
            log.warn("[AuthService] Failed to increment login_attempts in Redis for email={}: {}",
                    normalizedEmail, ex.getMessage());
            return MAX_LOGIN_ATTEMPTS - 1;
        }
    }

    private void clearFailedLoginAttempts(String normalizedEmail) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(LOGIN_ATTEMPTS_KEY_PREFIX + normalizedEmail);
        } catch (Exception ex) {
            log.debug("[AuthService] Failed to clear login_attempts in Redis for email={}: {}",
                    normalizedEmail, ex.getMessage());
        }
    }

    private static AuthException buildInvalidCredentialsException(int remainingAttempts) {
        if (remainingAttempts > 0) {
            return new AuthException(
                    ErrorCode.INVALID_CREDENTIALS,
                    String.format("Email hoặc mật khẩu không chính xác. Bạn còn %d lần thử trước khi tài khoản bị tạm khóa 15 phút.", remainingAttempts)
            );
        }
        return new AuthException(
                ErrorCode.INVALID_CREDENTIALS,
                "Email hoặc mật khẩu không chính xác. Tài khoản đã bị tạm khóa 15 phút."
        );
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeVnPhone(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String trimmed = phoneNumber.trim();
        if (trimmed.startsWith("+84")) {
            return "0" + trimmed.substring(3);
        }
        return trimmed;
    }
}
