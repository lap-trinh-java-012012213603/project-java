package com.analoghub.platform.iam;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.controller.RoleApplicationController;
import com.analoghub.platform.iam.controller.UserAddressController;
import com.analoghub.platform.iam.controller.UserProfileController;
import com.analoghub.platform.iam.domain.Role;
import com.analoghub.platform.iam.domain.RoleApplication;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.domain.UserAddress;
import com.analoghub.platform.iam.domain.UserRole;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.StylePreferencesDto;
import com.analoghub.platform.iam.dto.SubmitRoleApplicationRequest;
import com.analoghub.platform.iam.dto.UpdateUserProfileRequest;
import com.analoghub.platform.iam.dto.UpsertUserAddressRequest;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.event.RoleApplicationApprovedEvent;
import com.analoghub.platform.iam.event.RoleApplicationSubmittedEvent;
import com.analoghub.platform.iam.facade.IamFacadeImpl;
import com.analoghub.platform.iam.repository.RoleApplicationRepository;
import com.analoghub.platform.iam.repository.RoleRepository;
import com.analoghub.platform.iam.repository.UserAddressRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.iam.service.AddressCoordinateResolver;
import com.analoghub.platform.iam.service.RefreshTokenFamilyService;
import com.analoghub.platform.iam.service.RoleApplicationPayloadValidator;
import com.analoghub.platform.iam.service.RoleApplicationService;
import com.analoghub.platform.iam.service.UserAddressService;
import com.analoghub.platform.iam.service.UserProfileService;
import com.analoghub.platform.security.AuthenticatedUser;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileAddressRoleApplicationIntegrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private RoleApplicationRepository roleApplicationRepository;

    @Mock
    private RefreshTokenFamilyService refreshTokenFamilyService;

    @Mock
    private StorageService storageService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Map<UUID, User> userStore = new ConcurrentHashMap<>();
    private final Map<RoleCode, Role> roleStore = new ConcurrentHashMap<>();
    private final List<UserRole> userRoleStore = new ArrayList<>();
    private final Map<UUID, UserAddress> addressStore = new ConcurrentHashMap<>();
    private final Map<UUID, RoleApplication> roleAppStore = new ConcurrentHashMap<>();
    private final Map<String, String> redisKV = new ConcurrentHashMap<>();

    private ObjectMapper objectMapper;
    private AddressCoordinateResolver coordinateResolver;
    private UserProfileService userProfileService;
    private UserAddressService userAddressService;
    private RoleApplicationPayloadValidator payloadValidator;
    private RoleApplicationService roleApplicationService;
    private IamFacadeImpl iamFacade;

    private MockMvc mockMvc;
    private UUID testUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userStore.clear();
        roleStore.clear();
        userRoleStore.clear();
        addressStore.clear();
        roleAppStore.clear();
        redisKV.clear();

        objectMapper = new ObjectMapper();
        coordinateResolver = new AddressCoordinateResolver();

        wireStatefulRedis();
        wireStatefulStorage();
        wireStatefulRepositories();
        seedRolesAndUsers();

        userProfileService = new UserProfileService(
                userRepository,
                userRoleRepository,
                storageService,
                stringRedisTemplate,
                objectMapper
        );
        userAddressService = new UserAddressService(
                userAddressRepository,
                userRepository,
                coordinateResolver
        );
        payloadValidator = new RoleApplicationPayloadValidator(coordinateResolver);
        roleApplicationService = new RoleApplicationService(
                roleApplicationRepository,
                userRepository,
                userRoleRepository,
                payloadValidator,
                storageService,
                eventPublisher
        );
        iamFacade = new IamFacadeImpl(
                userRepository,
                roleRepository,
                userRoleRepository,
                userAddressRepository,
                roleApplicationRepository,
                refreshTokenFamilyService,
                userProfileService,
                eventPublisher
        );

        UserProfileController profileController = new UserProfileController(userProfileService);
        UserAddressController addressController = new UserAddressController(userAddressService);
        RoleApplicationController roleAppController = new RoleApplicationController(roleApplicationService);

        mockMvc = MockMvcBuilders.standaloneSetup(profileController, addressController, roleAppController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        authenticateAs(testUserId, Set.of("ROLE_CUSTOMER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Set<String> roles) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .roles(roles)
                .labIds(Set.of())
                .jti(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities())
        );
    }

    private void seedRolesAndUsers() {
        for (RoleCode code : RoleCode.values()) {
            Role role = Role.builder()
                    .code(code)
                    .name(code.name())
                    .description("Role " + code.name())
                    .build();
            role.setId(UUID.randomUUID());
            roleStore.put(code, role);
        }

        testUserId = UUID.randomUUID();
        User mainUser = User.builder()
                .email("minh.nguyen@analoghub.vn")
                .phoneNumber("0908123456")
                .fullName("Nguyễn Quang Minh")
                .avatarKey("public/avatars/2026/11/avatar_minh.webp")
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .stylePreferences(new LinkedHashMap<>(Map.of(
                        "favoriteFormats", List.of("FMT_135", "FMT_120"),
                        "favoriteProcesses", List.of("C41", "BW"),
                        "shootingLevel", "INTERMEDIATE"
                )))
                .build();
        mainUser.setId(testUserId);
        mainUser.setCreatedAt(Instant.now().minusSeconds(3600));
        mainUser.setUpdatedAt(Instant.now().minusSeconds(1800));
        userStore.put(testUserId, mainUser);

        userRoleRepository.save(UserRole.builder()
                .userId(testUserId)
                .roleId(roleStore.get(RoleCode.CUSTOMER).getId())
                .grantedAt(Instant.now())
                .build());

        otherUserId = UUID.randomUUID();
        User secondUser = User.builder()
                .email("other.user@analoghub.vn")
                .phoneNumber("0911222333")
                .fullName("Trần Người Khác")
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .stylePreferences(new LinkedHashMap<>())
                .build();
        secondUser.setId(otherUserId);
        secondUser.setCreatedAt(Instant.now().minusSeconds(3600));
        secondUser.setUpdatedAt(Instant.now().minusSeconds(1800));
        userStore.put(otherUserId, secondUser);
    }

    private void wireStatefulRedis() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(inv -> redisKV.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            redisKV.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(inv -> redisKV.remove(inv.getArgument(0)) != null);
    }

    private void wireStatefulStorage() {
        lenient().when(storageService.resolveReadUrl(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key == null || key.isBlank()) {
                return null;
            }
            if (key.startsWith("public/")) {
                return "https://cdn.analoghub.vn/" + key;
            }
            return "https://s3.analoghub.vn/analoghub-media-bucket/" + key + "?X-Amz-Expires=900";
        });
    }

    private void wireStatefulRepositories() {
        // RoleRepository
        lenient().when(roleRepository.findByCode(any(RoleCode.class)))
                .thenAnswer(inv -> Optional.ofNullable(roleStore.get(inv.getArgument(0))));

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
        lenient().when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> userRepository.save(inv.getArgument(0)));
        lenient().when(userRepository.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(userStore.get(id)).filter(u -> u.getDeletedAt() == null);
        });
        lenient().when(userRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(userStore.get(id)).filter(u -> u.getDeletedAt() == null);
        });
        lenient().when(userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(anyString(), any(UUID.class)))
                .thenAnswer(inv -> {
                    String phone = inv.getArgument(0);
                    UUID excludeId = inv.getArgument(1);
                    return userStore.values().stream()
                            .anyMatch(u -> u.getDeletedAt() == null
                                    && !u.getId().equals(excludeId)
                                    && phone.equals(u.getPhoneNumber()));
                });
        lenient().when(userRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<User> list = new ArrayList<>();
            for (UUID id : ids) {
                User u = userStore.get(id);
                if (u != null) {
                    list.add(u);
                }
            }
            return list;
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
        lenient().when(userRoleRepository.existsByUserIdAndRoleId(any(UUID.class), any(UUID.class))).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            UUID rId = inv.getArgument(1);
            return userRoleStore.stream().anyMatch(ur -> uId.equals(ur.getUserId()) && rId.equals(ur.getRoleId()));
        });
        lenient().doAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            UUID rId = inv.getArgument(1);
            userRoleStore.removeIf(ur -> uId.equals(ur.getUserId()) && rId.equals(ur.getRoleId()));
            return null;
        }).when(userRoleRepository).deleteByUserIdAndRoleId(any(UUID.class), any(UUID.class));
        lenient().when(userRoleRepository.findRoleCodesByUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            Set<RoleCode> codes = new LinkedHashSet<>();
            for (UserRole ur : userRoleStore) {
                if (uId.equals(ur.getUserId())) {
                    roleStore.values().stream()
                            .filter(r -> r.getId().equals(ur.getRoleId()))
                            .map(Role::getCode)
                            .forEach(codes::add);
                }
            }
            return codes;
        });

        // UserAddressRepository
        lenient().when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress addr = inv.getArgument(0);
            if (addr.getId() == null) {
                addr.setId(UUID.randomUUID());
            }
            if (addr.getCreatedAt() == null) {
                addr.setCreatedAt(Instant.now());
            }
            addr.setUpdatedAt(Instant.now());
            addressStore.put(addr.getId(), addr);
            return addr;
        });
        lenient().when(userAddressRepository.saveAndFlush(any(UserAddress.class)))
                .thenAnswer(inv -> userAddressRepository.save(inv.getArgument(0)));
        lenient().when(userAddressRepository.findByIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(addressStore.get(id)).filter(a -> a.getDeletedAt() == null);
        });
        lenient().when(userAddressRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(addressStore.get(id)).filter(a -> a.getDeletedAt() == null);
        });
        lenient().when(userAddressRepository.findByIdAndUserIdAndDeletedAtIsNull(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    UUID uId = inv.getArgument(1);
                    return Optional.ofNullable(addressStore.get(id))
                            .filter(a -> a.getDeletedAt() == null && uId.equals(a.getUserId()));
                });
        lenient().when(userAddressRepository.countByUserIdAndDeletedAtIsNull(any(UUID.class))).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            return addressStore.values().stream()
                    .filter(a -> a.getDeletedAt() == null && uId.equals(a.getUserId()))
                    .count();
        });
        lenient().when(userAddressRepository.clearDefaultForUser(any(UUID.class))).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            int count = 0;
            for (UserAddress a : addressStore.values()) {
                if (a.getDeletedAt() == null && uId.equals(a.getUserId()) && a.isDefault()) {
                    a.setDefault(false);
                    count++;
                }
            }
            return count;
        });
        lenient().when(userAddressRepository.findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID uId = inv.getArgument(0);
                    return addressStore.values().stream()
                            .filter(a -> a.getDeletedAt() == null && uId.equals(a.getUserId()))
                            .sorted(Comparator.comparing(UserAddress::isDefault).reversed()
                                    .thenComparing(UserAddress::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                });
        lenient().when(userAddressRepository.findFirstByUserIdAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID uId = inv.getArgument(0);
                    return addressStore.values().stream()
                            .filter(a -> a.getDeletedAt() == null && uId.equals(a.getUserId()) && a.isDefault())
                            .max(Comparator.comparing(UserAddress::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
                });
        lenient().when(userAddressRepository.findFirstByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID uId = inv.getArgument(0);
                    return addressStore.values().stream()
                            .filter(a -> a.getDeletedAt() == null && uId.equals(a.getUserId()))
                            .max(Comparator.comparing(UserAddress::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
                });

        // RoleApplicationRepository
        lenient().when(roleApplicationRepository.save(any(RoleApplication.class))).thenAnswer(inv -> {
            RoleApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(UUID.randomUUID());
            }
            if (app.getCreatedAt() == null) {
                app.setCreatedAt(Instant.now());
            }
            app.setUpdatedAt(Instant.now());
            roleAppStore.put(app.getId(), app);
            return app;
        });
        lenient().when(roleApplicationRepository.saveAndFlush(any(RoleApplication.class)))
                .thenAnswer(inv -> roleApplicationRepository.save(inv.getArgument(0)));
        lenient().when(roleApplicationRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(roleAppStore.get(inv.getArgument(0))));
        lenient().when(roleApplicationRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(roleAppStore.get(inv.getArgument(0))));
        lenient().when(roleApplicationRepository.existsByUserIdAndRequestedRoleAndStatus(
                any(UUID.class), any(RoleCode.class), any(RoleApplicationStatus.class)
        )).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            RoleCode role = inv.getArgument(1);
            RoleApplicationStatus status = inv.getArgument(2);
            return roleAppStore.values().stream()
                    .anyMatch(a -> uId.equals(a.getUserId()) && role == a.getRequestedRole() && status == a.getStatus());
        });
        lenient().when(roleApplicationRepository.findByUserIdOrderByCreatedAtDesc(any(UUID.class))).thenAnswer(inv -> {
            UUID uId = inv.getArgument(0);
            return roleAppStore.values().stream()
                    .filter(a -> uId.equals(a.getUserId()))
                    .sorted(Comparator.comparing(RoleApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        });
        lenient().when(roleApplicationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(any(UUID.class), any(RoleApplicationStatus.class)))
                .thenAnswer(inv -> {
                    UUID uId = inv.getArgument(0);
                    RoleApplicationStatus st = inv.getArgument(1);
                    return roleAppStore.values().stream()
                            .filter(a -> uId.equals(a.getUserId()) && st == a.getStatus())
                            .sorted(Comparator.comparing(RoleApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                });
        lenient().when(roleApplicationRepository.findByStatusOrderByCreatedAtDesc(any(RoleApplicationStatus.class)))
                .thenAnswer(inv -> {
                    RoleApplicationStatus st = inv.getArgument(0);
                    return roleAppStore.values().stream()
                            .filter(a -> st == a.getStatus())
                            .sorted(Comparator.comparing(RoleApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                });
    }

    @Test
    @DisplayName("GET & PUT /api/v1/users/me should cache profile in Redis, update stylePreferences, evict caches and block duplicate phone")
    void userProfile_getAndUpdate_shouldManageStylePreferencesAndCacheAndCheckDuplicatePhone() throws Exception {
        // 1. GET /api/v1/users/me -> populates Redis cache iam:user_profile:{userId}
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.data.email").value("minh.nguyen@analoghub.vn"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.analoghub.vn/public/avatars/2026/11/avatar_minh.webp"))
                .andExpect(jsonPath("$.data.stylePreferences.shootingLevel").value("INTERMEDIATE"));

        String profileCacheKey = UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId;
        assertThat(redisKV).containsKey(profileCacheKey);

        // Seed AI recsys cache key to verify eviction on profile update
        String aiRecsysKey = UserProfileService.AI_RECSYS_LABS_CACHE_PREFIX + testUserId;
        redisKV.put(aiRecsysKey, "cached_labs");

        // 2. PUT /api/v1/users/me -> updates fullName, phoneNumber, avatarKey, stylePreferences & evicts caches
        UpdateUserProfileRequest updateReq = new UpdateUserProfileRequest(
                "Nguyễn Quang Minh (Analog Enthusiast)",
                "+84908999888",
                "public/avatars/2026/11/new_avatar.webp",
                new StylePreferencesDto(
                        List.of("FMT_135", "FMT_120"),
                        List.of("C41", "ECN2", "BW"),
                        List.of("CINEMATIC_TEAL_ORANGE", "WARM_GOLDEN"),
                        List.of("Leica M6", "Hasselblad 500C/M"),
                        "ADVANCED"
                )
        );

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Quang Minh (Analog Enthusiast)"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0908999888"))
                .andExpect(jsonPath("$.data.avatarKey").value("public/avatars/2026/11/new_avatar.webp"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.analoghub.vn/public/avatars/2026/11/new_avatar.webp"))
                .andExpect(jsonPath("$.data.stylePreferences.shootingLevel").value("ADVANCED"))
                .andExpect(jsonPath("$.data.stylePreferences.favoriteCameras[0]").value("Leica M6"));

        assertThat(redisKV).doesNotContainKey(profileCacheKey);
        assertThat(redisKV).doesNotContainKey(aiRecsysKey);

        // 3. PUT /api/v1/users/me with phone number already used by otherUserId -> 409 Conflict
        UpdateUserProfileRequest duplicatePhoneReq = new UpdateUserProfileRequest(
                "Nguyễn Quang Minh",
                "0911222333",
                null,
                null
        );
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePhoneReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.PHONE_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("Address CRUD (/api/v1/users/me/addresses) should resolve coordinates, manage default address, enforce ownership and 10-address limit")
    void userAddressCrud_shouldResolveCoordinatesManageDefaultAndEnforceOwnership() throws Exception {
        // 1. Create first address WITHOUT explicit coordinates (latitude=null, longitude=null) and isDefault=false
        // -> First address must automatically be forced to isDefault=true and coordinates resolved from province/district!
        UpsertUserAddressRequest createFirstReq = new UpsertUserAddressRequest(
                "Nguyễn Quang Minh",
                "+84908123456",
                "Số 42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                "26734",
                "760",
                "79",
                null,
                null,
                false
        );

        String resp1 = mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(createFirstReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientPhone").value("0908123456"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.latitude").isNumber())
                .andExpect(jsonPath("$.data.longitude").isNumber())
                .andReturn().getResponse().getContentAsString();

        UUID addr1Id = UUID.fromString(objectMapper.readTree(resp1).path("data").path("id").asText());

        // 2. Create second address WITH explicit coordinates and isDefault=true -> demotes addr1
        UpsertUserAddressRequest createSecondReq = new UpsertUserAddressRequest(
                "Minh Nguyễn (Studio Quận 3)",
                "0908123456",
                "Số 18B Phạm Ngọc Thạch, Phường Võ Thị Sáu, Quận 3, TP. Hồ Chí Minh",
                "27127",
                "770",
                "79",
                10.781234,
                106.695123,
                true
        );

        String resp2 = mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(createSecondReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.latitude").value(10.781234))
                .andExpect(jsonPath("$.data.longitude").value(106.695123))
                .andReturn().getResponse().getContentAsString();

        UUID addr2Id = UUID.fromString(objectMapper.readTree(resp2).path("data").path("id").asText());
        assertThat(addressStore.get(addr1Id).isDefault()).isFalse();
        assertThat(addressStore.get(addr2Id).isDefault()).isTrue();

        // 3. GET /api/v1/users/me/addresses -> returns default address first
        mockMvc.perform(get("/api/v1/users/me/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].id").value(addr1Id.toString()))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));

        // 4. PUT /api/v1/users/me/addresses/{addr1Id} -> promote addr1 back to default
        UpsertUserAddressRequest updateFirstReq = new UpsertUserAddressRequest(
                "Nguyễn Quang Minh (Updated)",
                "0908123456",
                "Số 42 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                "26734",
                "760",
                "79",
                10.774562,
                106.703418,
                true
        );

        mockMvc.perform(put("/api/v1/users/me/addresses/" + addr1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateFirstReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("Nguyễn Quang Minh (Updated)"))
                .andExpect(jsonPath("$.data.isDefault").value(true));

        assertThat(addressStore.get(addr1Id).isDefault()).isTrue();
        assertThat(addressStore.get(addr2Id).isDefault()).isFalse();

        // 5. IDOR test: otherUserId tries to update or delete addr1Id -> 403 Forbidden
        authenticateAs(otherUserId, Set.of("ROLE_CUSTOMER"));
        mockMvc.perform(put("/api/v1/users/me/addresses/" + addr1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateFirstReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        mockMvc.perform(delete("/api/v1/users/me/addresses/" + addr1Id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        // Switch back to testUserId
        authenticateAs(testUserId, Set.of("ROLE_CUSTOMER"));

        // 6. DELETE /api/v1/users/me/addresses/{addr1Id} (current default) -> soft-deletes addr1 & auto-promotes addr2 to default!
        mockMvc.perform(delete("/api/v1/users/me/addresses/" + addr1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAddressId").value(addr1Id.toString()))
                .andExpect(jsonPath("$.data.newDefaultAddressId").value(addr2Id.toString()))
                .andExpect(jsonPath("$.data.deletedAt").isNotEmpty());

        assertThat(addressStore.get(addr1Id).isDeleted()).isTrue();
        assertThat(addressStore.get(addr1Id).isDefault()).isFalse();
        assertThat(addressStore.get(addr2Id).isDefault()).isTrue();

        // 7. Enforce maximum 10 active addresses per user
        for (int i = 2; i <= 10; i++) {
            userAddressService.createAddress(new UpsertUserAddressRequest(
                    "Người Nhận " + i,
                    "0908123456",
                    "Số " + (10 + i) + " Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                    "26734",
                    "760",
                    "79",
                    null,
                    null,
                    false
            ));
        }
        assertThat(userAddressRepository.countByUserIdAndDeletedAtIsNull(testUserId)).isEqualTo(10);

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(createSecondReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("POST & GET /api/v1/role-applications and IamFacadeImpl approval should publish events and grant role")
    void roleApplication_submitAndApproveViaFacade_shouldPublishEventsAndGrantRole() throws Exception {
        Map<String, Object> labPayload = new LinkedHashMap<>();
        labPayload.put("proposedLabName", "LLab Sài Gòn - Darkroom & Film Scanning");
        labPayload.put("proposedSlug", "llab-sai-gon-darkroom");
        labPayload.put("contactPhone", "0908123456");
        labPayload.put("labAddress", "386/12 Lê Văn Sỹ, Phường 14, Quận 3, TP. Hồ Chí Minh");
        labPayload.put("provinceCode", "79");
        labPayload.put("districtCode", "770");
        labPayload.put("latitude", 10.788542);
        labPayload.put("longitude", 106.678912);
        labPayload.put("equipmentList", List.of("Noritsu HS-1800", "Fujifilm Frontier SP-3000"));
        labPayload.put("supportedProcesses", List.of("C41", "ECN2", "BW", "E6"));
        labPayload.put("evidenceDocumentKeys", List.of(
                "protected/role-applications/" + testUserId + "/0192e8f1_noritsu_hs1800.webp",
                "protected/role-applications/" + testUserId + "/0192e8f2_darkroom_setup.webp"
        ));

        SubmitRoleApplicationRequest submitReq = new SubmitRoleApplicationRequest(RoleCode.LAB_OWNER, labPayload);

        // 1. Submit LAB_OWNER role application -> 201 Created + publishes RoleApplicationSubmittedEvent
        String submitResp = mockMvc.perform(post("/api/v1/role-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.data.requestedRole").value("LAB_OWNER"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        UUID appId = UUID.fromString(objectMapper.readTree(submitResp).path("data").path("id").asText());

        ArgumentCaptor<RoleApplicationSubmittedEvent> submittedCaptor =
                ArgumentCaptor.forClass(RoleApplicationSubmittedEvent.class);
        verify(eventPublisher).publishEvent(submittedCaptor.capture());
        RoleApplicationSubmittedEvent submittedEvent = submittedCaptor.getValue();
        assertThat(submittedEvent.applicationId()).isEqualTo(appId);
        assertThat(submittedEvent.userId()).isEqualTo(testUserId);
        assertThat(submittedEvent.requestedRole()).isEqualTo("LAB_OWNER");

        // 2. Submitting another PENDING application for the same role -> 409 Conflict
        mockMvc.perform(post("/api/v1/role-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));

        // 3. GET /api/v1/role-applications/me?status=PENDING -> returns the submitted application with presigned evidenceDocumentUrls
        mockMvc.perform(get("/api/v1/role-applications/me").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(appId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].portfolioPayload.evidenceDocumentUrls[0]").isNotEmpty());

        // 4. Admin approves via IamFacade.approveRoleApplication -> grants LAB_OWNER role, evicts cache, publishes RoleApplicationApprovedEvent
        UUID adminUserId = UUID.randomUUID();
        redisKV.put(UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId, "stale_profile");

        RoleApplicationDto approvedDto = iamFacade.approveRoleApplication(
                appId,
                adminUserId,
                "Đã xác minh giấy phép kinh doanh và hệ thống máy Noritsu HS-1800."
        );

        assertThat(approvedDto.status()).isEqualTo(RoleApplicationStatus.APPROVED);
        assertThat(approvedDto.reviewedByAdminId()).isEqualTo(adminUserId);
        assertThat(iamFacade.hasRole(testUserId, RoleCode.LAB_OWNER)).isTrue();
        assertThat(iamFacade.getUserRoles(testUserId)).contains(RoleCode.CUSTOMER, RoleCode.LAB_OWNER);
        assertThat(redisKV).doesNotContainKey(UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId);

        ArgumentCaptor<Object> allEventsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(2)).publishEvent(allEventsCaptor.capture());
        RoleApplicationApprovedEvent approvedEvent = allEventsCaptor.getAllValues().stream()
                .filter(RoleApplicationApprovedEvent.class::isInstance)
                .map(RoleApplicationApprovedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(approvedEvent.applicationId()).isEqualTo(appId);
        assertThat(approvedEvent.userId()).isEqualTo(testUserId);
        assertThat(approvedEvent.approvedRole()).isEqualTo("LAB_OWNER");
        assertThat(approvedEvent.reviewedByAdminId()).isEqualTo(adminUserId);

        // 5. Re-approving an already approved application throws 409 Conflict (DUPLICATE_RESOURCE)
        assertThatThrownBy(() -> iamFacade.approveRoleApplication(appId, adminUserId, "Duplicate approve"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);

        // 6. Submitting role application when user already holds the role -> 409 Conflict
        mockMvc.perform(post("/api/v1/role-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("RoleApplication validation, rejection and IamFacade account status moderation should work accurately")
    void roleApplicationValidation_rejectionAndAccountModeration_shouldEnforceRules() throws Exception {
        // 1. Reject requesting ADMIN role -> 400 Bad Request
        SubmitRoleApplicationRequest invalidRoleReq = new SubmitRoleApplicationRequest(
                RoleCode.ADMIN,
                Map.of("reason", "Want admin")
        );
        mockMvc.perform(post("/api/v1/role-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(invalidRoleReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        // 2. Reject foreign evidenceDocumentKeys belonging to otherUserId -> 400 Bad Request
        SubmitRoleApplicationRequest foreignEvidenceReq = new SubmitRoleApplicationRequest(
                RoleCode.EXPERT,
                Map.of(
                        "specialtyAreas", List.of("DARKROOM_PRINTING", "LARGE_FORMAT"),
                        "yearsOfExperience", 8,
                        "evidenceDocumentKeys", List.of("protected/role-applications/" + otherUserId + "/stolen_doc.pdf")
                )
        );
        mockMvc.perform(post("/api/v1/role-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(foreignEvidenceReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        // 3. Valid EXPERT application -> Admin rejects via IamFacade.rejectRoleApplication
        RoleApplicationDto expertApp = roleApplicationService.submitRoleApplication(
                new SubmitRoleApplicationRequest(
                        RoleCode.EXPERT,
                        Map.of(
                                "specialtyAreas", List.of("DARKROOM_PRINTING", "ECN2_CHEMISTRY"),
                                "yearsOfExperience", 6,
                                "evidenceDocumentKeys", List.of("protected/role-applications/" + testUserId + "/portfolio.pdf")
                        )
                )
        );

        UUID adminId = UUID.randomUUID();
        RoleApplicationDto rejectedDto = iamFacade.rejectRoleApplication(
                expertApp.id(),
                adminId,
                "Cần bổ sung thêm bài viết mẫu chuyên sâu về quy trình tráng."
        );
        assertThat(rejectedDto.status()).isEqualTo(RoleApplicationStatus.REJECTED);
        assertThat(iamFacade.hasRole(testUserId, RoleCode.EXPERT)).isFalse();

        // 4. IamFacade.updateAccountStatus(SUSPENDED) revokes all refresh tokens and evicts cache
        redisKV.put(UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId, "cached");
        iamFacade.updateAccountStatus(testUserId, AccountStatus.SUSPENDED, adminId, "Vi phạm quy định sàn");
        assertThat(iamFacade.isActiveUser(testUserId)).isFalse();
        verify(refreshTokenFamilyService).revokeAllUserTokens(testUserId);
        assertThat(redisKV).doesNotContainKey(UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId);

        // 5. Verify Haversine distance calculation between District 1 and District 3 HCMC (~1.9 km)
        double distanceKm = AddressCoordinateResolver.calculateDistanceKm(10.775658, 106.700424, 10.784370, 106.684409);
        assertThat(distanceKm).isBetween(1.5, 2.5);
    }
}
