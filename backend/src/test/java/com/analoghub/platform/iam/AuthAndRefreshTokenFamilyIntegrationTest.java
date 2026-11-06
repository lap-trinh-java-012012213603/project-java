package com.analoghub.platform.iam;

import com.analoghub.platform.common.exception.AuthException;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.iam.controller.AuthController;
import com.analoghub.platform.iam.domain.RefreshToken;
import com.analoghub.platform.iam.domain.Role;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.domain.UserRole;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.AuthTokenResponse;
import com.analoghub.platform.iam.dto.ChangePasswordRequest;
import com.analoghub.platform.iam.dto.GoogleOAuth2LoginRequest;
import com.analoghub.platform.iam.dto.LoginRequest;
import com.analoghub.platform.iam.dto.LogoutRequest;
import com.analoghub.platform.iam.dto.RefreshTokenRequest;
import com.analoghub.platform.iam.dto.RegisterUserRequest;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.StylePreferencesDto;
import com.analoghub.platform.iam.event.UserRegisteredEvent;
import com.analoghub.platform.iam.repository.RefreshTokenRepository;
import com.analoghub.platform.iam.repository.RoleRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.iam.service.AuthService;
import com.analoghub.platform.iam.service.GoogleIdTokenVerifier;
import com.analoghub.platform.iam.service.RefreshTokenFamilyService;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.JwtProperties;
import com.analoghub.platform.security.JwtTokenProvider;
import com.analoghub.platform.security.TokenBlacklistRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthAndRefreshTokenFamilyIntegrationTest {

    private static final String TEST_JWT_SECRET = "AnalogHubSuperSecretKeyForJwtSigning2026CapstoneProject999!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private final Map<UUID, User> userStore = new ConcurrentHashMap<>();
    private final Map<RoleCode, Role> roleStore = new ConcurrentHashMap<>();
    private final List<UserRole> userRoleStore = new ArrayList<>();
    private final Map<UUID, RefreshToken> refreshTokenStore = new ConcurrentHashMap<>();
    private final Map<String, String> redisKV = new ConcurrentHashMap<>();

    private PasswordEncoder passwordEncoder;
    private JwtProperties jwtProperties;
    private JwtTokenProvider jwtTokenProvider;
    private TokenBlacklistRedis tokenBlacklistRedis;
    private RefreshTokenFamilyService refreshTokenFamilyService;
    private AuthService authService;
    private AuthController authController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userStore.clear();
        roleStore.clear();
        userRoleStore.clear();
        refreshTokenStore.clear();
        redisKV.clear();

        objectMapper = new ObjectMapper();
        passwordEncoder = new BCryptPasswordEncoder(4); // fast cost for tests

        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_JWT_SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(2_592_000_000L);

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();

        wireStatefulRedis();
        wireStatefulRepositories();
        seedDefaultRoles();

        tokenBlacklistRedis = new TokenBlacklistRedis(stringRedisTemplate);
        refreshTokenFamilyService = new RefreshTokenFamilyService(
                refreshTokenRepository,
                jwtProperties,
                stringRedisTemplate
        );
        authService = new AuthService(
                userRepository,
                roleRepository,
                userRoleRepository,
                refreshTokenFamilyService,
                jwtTokenProvider,
                tokenBlacklistRedis,
                passwordEncoder,
                stringRedisTemplate,
                googleIdTokenVerifier,
                eventPublisher
        );
        authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void seedDefaultRoles() {
        for (RoleCode code : RoleCode.values()) {
            Role role = Role.builder()
                    .code(code)
                    .name(code.name())
                    .description("Role " + code.name())
                    .build();
            role.setId(UUID.randomUUID());
            roleStore.put(code, role);
        }
    }

    private void wireStatefulRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(inv -> redisKV.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            redisKV.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(valueOperations.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = Long.parseLong(redisKV.getOrDefault(key, "0")) + 1L;
            redisKV.put(key, String.valueOf(next));
            return next;
        });
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenAnswer(inv -> redisKV.containsKey(inv.getArgument(0)));
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(inv -> redisKV.remove(inv.getArgument(0)) != null);
        lenient().when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
    }

    private void wireStatefulRepositories() {
        // RoleRepository
        lenient().when(roleRepository.findByCode(any(RoleCode.class)))
                .thenAnswer(inv -> Optional.ofNullable(roleStore.get(inv.getArgument(0))));
        lenient().when(roleRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<Role> matched = new ArrayList<>();
            for (UUID id : ids) {
                roleStore.values().stream().filter(r -> r.getId().equals(id)).findFirst().ifPresent(matched::add);
            }
            return matched;
        });

        // UserRepository
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            if (u.getCreatedAt() == null) {
                u.setCreatedAt(Instant.now());
            }
            u.setUpdatedAt(Instant.now());
            userStore.put(u.getId(), u);
            return u;
        });
        lenient().when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> userRepository.save(inv.getArgument(0)));
        lenient().when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            return userStore.values().stream()
                    .anyMatch(u -> u.getDeletedAt() == null && email.equalsIgnoreCase(u.getEmail()));
        });
        lenient().when(userRepository.existsByPhoneNumberAndDeletedAtIsNull(anyString())).thenAnswer(inv -> {
            String phone = inv.getArgument(0);
            return userStore.values().stream()
                    .anyMatch(u -> u.getDeletedAt() == null && phone.equals(u.getPhoneNumber()));
        });
        lenient().when(userRepository.findByEmailAndDeletedAtIsNull(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            return userStore.values().stream()
                    .filter(u -> u.getDeletedAt() == null && email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();
        });
        lenient().when(userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(any(AuthProvider.class), anyString()))
                .thenAnswer(inv -> {
                    AuthProvider provider = inv.getArgument(0);
                    String providerId = inv.getArgument(1);
                    return userStore.values().stream()
                            .filter(u -> u.getDeletedAt() == null
                                    && u.getAuthProvider() == provider
                                    && providerId.equals(u.getProviderId()))
                            .findFirst();
                });
        lenient().when(userRepository.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(userStore.get(id)).filter(u -> u.getDeletedAt() == null);
        });
        lenient().when(userRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(userStore.get(id)).filter(u -> u.getDeletedAt() == null);
        });

        // UserRoleRepository
        lenient().when(userRoleRepository.save(any(UserRole.class))).thenAnswer(inv -> {
            UserRole ur = inv.getArgument(0);
            if (ur.getId() == null) {
                ur.setId(UUID.randomUUID());
            }
            if (ur.getGrantedAt() == null) {
                ur.setGrantedAt(Instant.now());
            }
            userRoleStore.add(ur);
            return ur;
        });
        lenient().when(userRoleRepository.findRoleCodesByUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            Set<RoleCode> codes = new java.util.LinkedHashSet<>();
            for (UserRole ur : userRoleStore) {
                if (userId.equals(ur.getUserId())) {
                    roleStore.values().stream()
                            .filter(r -> r.getId().equals(ur.getRoleId()))
                            .map(Role::getCode)
                            .forEach(codes::add);
                }
            }
            return codes;
        });

        // RefreshTokenRepository
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            if (rt.getId() == null) {
                rt.setId(UUID.randomUUID());
            }
            if (rt.getCreatedAt() == null) {
                rt.setCreatedAt(Instant.now());
            }
            rt.setUpdatedAt(Instant.now());
            refreshTokenStore.put(rt.getId(), rt);
            return rt;
        });
        lenient().when(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .thenAnswer(inv -> refreshTokenRepository.save(inv.getArgument(0)));
        lenient().when(refreshTokenRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<RefreshToken> list = inv.getArgument(0);
            List<RefreshToken> saved = new ArrayList<>();
            for (RefreshToken rt : list) {
                saved.add(refreshTokenRepository.save(rt));
            }
            return saved;
        });
        lenient().when(refreshTokenRepository.saveAllAndFlush(any()))
                .thenAnswer(inv -> refreshTokenRepository.saveAll(inv.getArgument(0)));
        lenient().when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            return refreshTokenStore.values().stream()
                    .filter(rt -> hash.equals(rt.getTokenHash()))
                    .findFirst();
        });
        lenient().when(refreshTokenRepository.findByTokenHash(anyString())).thenAnswer(inv -> {
            String hash = inv.getArgument(0);
            return refreshTokenStore.values().stream()
                    .filter(rt -> hash.equals(rt.getTokenHash()))
                    .findFirst();
        });
        lenient().when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID familyId = inv.getArgument(0);
            return refreshTokenStore.values().stream()
                    .filter(rt -> familyId.equals(rt.getTokenFamilyId()) && rt.getRevokedAt() == null)
                    .toList();
        });
        lenient().when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            return refreshTokenStore.values().stream()
                    .filter(rt -> userId.equals(rt.getUserId()) && rt.getRevokedAt() == null)
                    .toList();
        });
        lenient().when(refreshTokenRepository.revokeAllByTokenFamilyId(any(UUID.class), any(Instant.class))).thenAnswer(inv -> {
            UUID familyId = inv.getArgument(0);
            Instant now = inv.getArgument(1);
            int count = 0;
            for (RefreshToken rt : refreshTokenStore.values()) {
                if (familyId.equals(rt.getTokenFamilyId()) && rt.getRevokedAt() == null) {
                    rt.setRevokedAt(now);
                    count++;
                }
            }
            return count;
        });
        lenient().when(refreshTokenRepository.revokeAllByUserId(any(UUID.class), any(Instant.class))).thenAnswer(inv -> {
            UUID userId = inv.getArgument(0);
            Instant now = inv.getArgument(1);
            int count = 0;
            for (RefreshToken rt : refreshTokenStore.values()) {
                if (userId.equals(rt.getUserId()) && rt.getRevokedAt() == null) {
                    rt.setRevokedAt(now);
                    count++;
                }
            }
            return count;
        });
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should normalize email/phone, hash password, assign CUSTOMER role, issue tokens and publish UserRegisteredEvent")
    void register_shouldCreateUserAssignCustomerRoleIssueTokensAndPublishEvent() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest(
                "  Minh.Nguyen@AnalogHub.vn ",
                "+84908123456",
                "Analog@2026!Secure",
                "Nguyễn Quang Minh",
                new StylePreferencesDto(
                        List.of("FMT_135", "FMT_120"),
                        List.of("C41", "BW"),
                        List.of("WARM_PASTEL"),
                        null,
                        "INTERMEDIATE"
                )
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Windows 11 Chrome 130")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.data.user.email").value("minh.nguyen@analoghub.vn"))
                .andExpect(jsonPath("$.data.user.phoneNumber").value("0908123456"))
                .andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.stylePreferences.shootingLevel").value("INTERMEDIATE"));

        // Verify password was BCrypt hashed and raw refresh token is NOT stored in DB
        assertThat(userStore).hasSize(1);
        User savedUser = userStore.values().iterator().next();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Analog@2026!Secure");
        assertThat(passwordEncoder.matches("Analog@2026!Secure", savedUser.getPasswordHash())).isTrue();

        assertThat(refreshTokenStore).hasSize(1);
        RefreshToken savedToken = refreshTokenStore.values().iterator().next();
        assertThat(savedToken.getTokenHash()).hasSize(64); // SHA-256 hex
        assertThat(savedToken.getDeviceInfo()).isEqualTo("Windows 11 Chrome 130");

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo("minh.nguyen@analoghub.vn");
        assertThat(eventCaptor.getValue().userId()).isEqualTo(savedUser.getId());

        // Duplicate email -> 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("RefreshTokenFamily rotation and Reuse Detection: replaying a rotated RefreshToken must immediately revoke the entire token_family_id")
    void refreshTokenRotation_andReuseDetection_shouldRevokeEntireTokenFamilyOnReplay() throws Exception {
        // 1. Register user -> creates Device A session (token_family_id = F_A) with RT_1
        AuthTokenResponse initialAuth = authService.register(
                new RegisterUserRequest(
                        "photographer@analoghub.vn",
                        "0912345678",
                        "Analog@2026!Secure",
                        "Trần Nhiếp Ảnh",
                        null
                ),
                "MacOS Safari 18"
        );
        UUID userId = initialAuth.user().id();
        String rt1 = initialAuth.refreshToken();

        // Also login on Device B -> creates separate token_family_id = F_B with RT_B1
        AuthTokenResponse deviceBAuth = authService.login(
                new LoginRequest("photographer@analoghub.vn", "Analog@2026!Secure", "iPhone 16 iOS 18"),
                "iPhone 16 iOS 18"
        );
        String rtDeviceB = deviceBAuth.refreshToken();

        RefreshToken rt1RecordBefore = refreshTokenRepository.findByTokenHash(RefreshTokenFamilyService.hashToken(rt1)).orElseThrow();
        UUID familyA = rt1RecordBefore.getTokenFamilyId();
        RefreshToken rtDeviceBRecord = refreshTokenRepository.findByTokenHash(RefreshTokenFamilyService.hashToken(rtDeviceB)).orElseThrow();
        UUID familyB = rtDeviceBRecord.getTokenFamilyId();
        assertThat(familyA).isNotEqualTo(familyB);

        // 2. Legitimate Rotation #1 on Device A: RT_1 -> RT_2 (same familyA)
        AuthTokenResponse rotated1 = authService.refreshToken(new RefreshTokenRequest(rt1), "MacOS Safari 18");
        String rt2 = rotated1.refreshToken();
        assertThat(rt2).isNotEqualTo(rt1);

        RefreshToken rt1RecordAfter = refreshTokenRepository.findByTokenHash(RefreshTokenFamilyService.hashToken(rt1)).orElseThrow();
        RefreshToken rt2Record = refreshTokenRepository.findByTokenHash(RefreshTokenFamilyService.hashToken(rt2)).orElseThrow();
        assertThat(rt1RecordAfter.isRevoked()).isTrue();
        assertThat(rt2Record.isRevoked()).isFalse();
        assertThat(rt2Record.getTokenFamilyId()).isEqualTo(familyA);

        // 3. Legitimate Rotation #2 on Device A: RT_2 -> RT_3 (same familyA)
        AuthTokenResponse rotated2 = authService.refreshToken(new RefreshTokenRequest(rt2), "MacOS Safari 18");
        String rt3 = rotated2.refreshToken();
        RefreshToken rt3Record = refreshTokenRepository.findByTokenHash(RefreshTokenFamilyService.hashToken(rt3)).orElseThrow();
        assertThat(rt2Record.isRevoked()).isTrue();
        assertThat(rt3Record.isRevoked()).isFalse();
        assertThat(rt3Record.getTokenFamilyId()).isEqualTo(familyA);

        // 4. REPLAY ATTACK! Attacker replays already-revoked RT_1 via POST /api/v1/auth/refresh
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_TOKEN_EXPIRED.getCode()));

        // 5. Verify ENTIRE familyA (including active RT_3!) was immediately revoked
        assertThat(rt3Record.isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNull(familyA)).isEmpty();

        // Verify Redis user-level token revocation timestamp was set
        assertThat(redisKV).containsKey(RefreshTokenFamilyService.USER_REVOKED_AFTER_KEY_PREFIX + userId);

        // 6. Verify RT_3 can no longer be used to refresh
        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(rt3), "MacOS Safari 18"))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        // 7. Verify Device B's independent familyB was NOT revoked by familyA's compromise
        assertThat(rtDeviceBRecord.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should enforce Redis brute-force lock after 5 failed password attempts")
    void login_shouldLockAccountTemporarilyAfterFiveFailedAttempts() throws Exception {
        authService.register(
                new RegisterUserRequest(
                        "locktest@analoghub.vn",
                        "0987654321",
                        "Analog@2026!Secure",
                        "Lê Bảo Mật",
                        null
                ),
                "Chrome"
        );

        LoginRequest wrongPasswordReq = new LoginRequest("locktest@analoghub.vn", "WrongPass@123!", "Chrome");

        // 5 failed attempts -> 401 INVALID_CREDENTIALS
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPasswordReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));
        }

        // 6th attempt (even with correct password!) -> blocked because account is temp-locked in Redis
        LoginRequest correctPasswordReq = new LoginRequest("locktest@analoghub.vn", "Analog@2026!Secure", "Chrome");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPasswordReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LOCKED_OR_DISABLED.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/oauth2/google should register new Google user or link existing LOCAL user")
    void loginWithGoogle_shouldCreateNewUserAndLinkExistingUser() throws Exception {
        when(googleIdTokenVerifier.verify("google-id-token-new")).thenReturn(
                new GoogleIdTokenVerifier.GoogleUserPayload(
                        "google-sub-12345",
                        "google.user@analoghub.vn",
                        true,
                        "Google Analog User",
                        "https://lh3.googleusercontent.com/a/avatar"
                )
        );

        // 1. First login -> creates new user (isNewUser = true)
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleOAuth2LoginRequest("google-id-token-new", "Safari"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.email").value("google.user@analoghub.vn"))
                .andExpect(jsonPath("$.data.user.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("CUSTOMER"));

        // 2. Second login with same Google account -> existing user (isNewUser = false)
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleOAuth2LoginRequest("google-id-token-new", "Safari"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout and /change-password should blacklist JTI and revoke refresh tokens")
    void logoutAndChangePassword_shouldRevokeTokensAndUpdatePassword() throws Exception {
        AuthTokenResponse registered = authService.register(
                new RegisterUserRequest(
                        "changepwd@analoghub.vn",
                        "0933445566",
                        "Analog@2026!Secure",
                        "Phạm Đổi Mật Khẩu",
                        null
                ),
                "Firefox"
        );

        AuthenticatedUser principal = jwtTokenProvider.parseAuthenticatedUser(registered.accessToken());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, registered.accessToken(), principal.getAuthorities())
        );

        // 1. Change password -> revokes refresh tokens and updates password hash
        ChangePasswordRequest changeReq = new ChangePasswordRequest(
                "Analog@2026!Secure",
                "NewAnalog#2027$Ultra",
                "NewAnalog#2027$Ultra"
        );
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passwordChangedAt").isNotEmpty());

        // Old refresh token should now be revoked
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(
                RefreshTokenFamilyService.hashToken(registered.refreshToken())
        ).orElseThrow();
        assertThat(oldToken.isRevoked()).isTrue();

        // Login with new password succeeds
        AuthTokenResponse relogin = authService.login(
                new LoginRequest("changepwd@analoghub.vn", "NewAnalog#2027$Ultra", "Firefox"),
                "Firefox"
        );
        AuthenticatedUser newPrincipal = jwtTokenProvider.parseAuthenticatedUser(relogin.accessToken());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(newPrincipal, relogin.accessToken(), newPrincipal.getAuthorities())
        );

        // 2. Logout current device -> blacklists JTI in Redis and revokes refresh token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(relogin.refreshToken(), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokedAccessTokenJti").value(newPrincipal.getJti()))
                .andExpect(jsonPath("$.data.logoutAllDevices").value(false));

        assertThat(tokenBlacklistRedis.isBlacklisted(newPrincipal.getJti())).isTrue();
        RefreshToken reloginRt = refreshTokenRepository.findByTokenHash(
                RefreshTokenFamilyService.hashToken(relogin.refreshToken())
        ).orElseThrow();
        assertThat(reloginRt.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("GoogleIdTokenVerifier should validate OIDC claims (iss, exp, email_verified, aud) and reject invalid tokens")
    void googleIdTokenVerifier_shouldValidateClaimsAndRejectInvalidOrExpiredTokens() throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(objectMapper, "analoghub-client-id.apps.googleusercontent.com");

        String headerJson = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";
        long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
        String validPayloadJson = String.format(
                "{\"iss\":\"https://accounts.google.com\",\"sub\":\"110169484474\",\"aud\":\"analoghub-client-id.apps.googleusercontent.com\",\"email\":\"Minh.Google@AnalogHub.vn\",\"email_verified\":true,\"name\":\"Minh Google\",\"picture\":\"https://lh3.googleusercontent.com/pic\",\"exp\":%d}",
                futureExp
        );
        String validJwt = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes())
                + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(validPayloadJson.getBytes())
                + ".signatureBytes";

        GoogleIdTokenVerifier.GoogleUserPayload parsed = verifier.verify(validJwt);
        assertThat(parsed.sub()).isEqualTo("110169484474");
        assertThat(parsed.email()).isEqualTo("minh.google@analoghub.vn");
        assertThat(parsed.emailVerified()).isTrue();
        assertThat(parsed.fullName()).isEqualTo("Minh Google");

        // Expired token -> 401 OAUTH2_AUTHENTICATION_FAILED
        long pastExp = Instant.now().minusSeconds(60).getEpochSecond();
        String expiredPayloadJson = String.format(
                "{\"iss\":\"https://accounts.google.com\",\"sub\":\"110169484474\",\"aud\":\"analoghub-client-id.apps.googleusercontent.com\",\"email\":\"minh@analoghub.vn\",\"email_verified\":true,\"exp\":%d}",
                pastExp
        );
        String expiredJwt = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes())
                + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(expiredPayloadJson.getBytes())
                + ".signatureBytes";

        assertThatThrownBy(() -> verifier.verify(expiredJwt))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH2_AUTHENTICATION_FAILED);
    }
}
