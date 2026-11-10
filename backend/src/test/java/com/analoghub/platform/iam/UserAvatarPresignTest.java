package com.analoghub.platform.iam;

import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.common.storage.StorageCategory;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.controller.UserProfileController;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.iam.service.UserProfileService;
import com.analoghub.platform.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserAvatarPresignTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        UserProfileService userProfileService = new UserProfileService(
                userRepository,
                userRoleRepository,
                storageService,
                stringRedisTemplate,
                objectMapper
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserProfileController(userProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUserId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                testUserId,
                "jti-avatar-test",
                List.of(RoleCode.CUSTOMER.name()),
                List.of(),
                Instant.now(),
                Instant.now().plusSeconds(900)
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/users/me/avatar should upload image to S3, save avatarKey in DB, evict cache and return updated UserProfileDto in 1 API")
    void uploadMyAvatar_shouldUploadAndSaveAvatarInOneCall() throws Exception {
        User existingUser = User.builder()
                .email("minh.nguyen@analoghub.vn")
                .phoneNumber("0908123456")
                .fullName("Nguyễn Quang Minh")
                .accountStatus(AccountStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .stylePreferences(new LinkedHashMap<>())
                .build();
        existingUser.setId(testUserId);

        String uploadedKey = "public/avatars/" + testUserId + "/avatar_camera.webp";
        String resolvedUrl = "http://localhost:9000/analoghub-media/" + uploadedKey;

        when(userRepository.findByIdForUpdate(testUserId)).thenReturn(Optional.of(existingUser));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRoleRepository.findRoleCodesByUserId(testUserId)).thenReturn(Set.of(RoleCode.CUSTOMER));
        when(storageService.uploadPublicFile(
                eq(StorageCategory.AVATAR),
                eq(testUserId),
                eq("avatar_camera.webp"),
                eq("image/webp"),
                any(byte[].class)
        )).thenReturn(uploadedKey);
        when(storageService.resolveReadUrl(uploadedKey)).thenReturn(resolvedUrl);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar_camera.webp",
                "image/webp",
                "fake-webp-bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/users/me/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.data.avatarKey").value(uploadedKey))
                .andExpect(jsonPath("$.data.avatarUrl").value(resolvedUrl));

        assertThat(existingUser.getAvatarKey()).isEqualTo(uploadedKey);
        verify(stringRedisTemplate).delete(UserProfileService.USER_PROFILE_CACHE_PREFIX + testUserId);
    }

    @Test
    @DisplayName("POST /api/v1/users/me/avatar/presign should return objectKey, uploadPutUrl and publicPreviewUrl for AVATAR category")
    void presignAvatarUpload_shouldReturnPresignedPutUrlAndObjectKey() throws Exception {
        String expectedKey = "public/avatars/" + testUserId + "/avatar_new.webp";
        String expectedPutUrl = "https://s3.analoghub.vn/analoghub-media-bucket/" + expectedKey + "?X-Amz-Algorithm=AWS4-HMAC-SHA256";

        when(storageService.generatePublicUploadUrl(
                StorageCategory.AVATAR,
                testUserId,
                "avatar_new.webp",
                "image/webp",
                245760L
        )).thenReturn(new StorageService.PresignedUploadResult(expectedKey, expectedPutUrl, 900L));

        when(storageService.resolveReadUrl(expectedKey))
                .thenReturn("https://cdn.analoghub.vn/" + expectedKey);

        Map<String, Object> presignReq = Map.of(
                "fileName", "avatar_new.webp",
                "contentType", "image/webp",
                "fileSizeBytes", 245760L
        );

        mockMvc.perform(post("/api/v1/users/me/avatar/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(presignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectKey").value(expectedKey))
                .andExpect(jsonPath("$.data.uploadPutUrl").value(expectedPutUrl))
                .andExpect(jsonPath("$.data.publicPreviewUrl").value("https://cdn.analoghub.vn/" + expectedKey))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900));
    }
}
