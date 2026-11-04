package com.analoghub.platform.iam.facade;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.iam.domain.Role;
import com.analoghub.platform.iam.domain.RoleApplication;
import com.analoghub.platform.iam.domain.User;
import com.analoghub.platform.iam.domain.UserAddress;
import com.analoghub.platform.iam.domain.UserRole;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.event.RoleApplicationApprovedEvent;
import com.analoghub.platform.iam.repository.RoleApplicationRepository;
import com.analoghub.platform.iam.repository.RoleRepository;
import com.analoghub.platform.iam.repository.UserAddressRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.iam.service.RefreshTokenFamilyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAddressRepository userAddressRepository;
    private final RoleApplicationRepository roleApplicationRepository;
    private final RefreshTokenFamilyService refreshTokenFamilyService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummaryDto> findUserById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(this::toUserSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getRequiredUserById(UUID userId) {
        return findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummaryDto> findUsersByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UserSummaryDto> result = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(distinctIds)) {
            if (!user.isDeleted()) {
                result.put(user.getId(), toUserSummaryDto(user));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveUser(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(User::isActive)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<RoleCode> getUserRoles(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<RoleCode> roles = userRoleRepository.findRoleCodesByUserId(userId);
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, RoleCode roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        return getUserRoles(userId).contains(roleCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserStylePreferences(UUID userId) {
        if (userId == null) {
            return Map.of();
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(User::getStylePreferences)
                .filter(Objects::nonNull)
                .map(Map::copyOf)
                .orElse(Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAddressDto> findAddressById(UUID userId, UUID addressId) {
        if (userId == null || addressId == null) {
            return Optional.empty();
        }
        return userAddressRepository.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)
                .map(this::toUserAddressDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressDto getRequiredAddress(UUID userId, UUID addressId) {
        return findAddressById(userId, addressId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAddress", addressId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAddressDto> findDefaultAddress(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userAddressRepository.findFirstByUserIdAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .map(this::toUserAddressDto);
    }

    @Override
    @Transactional
    public void grantRole(UUID userId, RoleCode roleCode) {
        if (userId == null || roleCode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "userId and roleCode are required");
        }
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code(roleCode)
                        .description("System role: " + roleCode.name())
                        .build()));

        if (!userRoleRepository.existsByUserIdAndRoleId(userId, role.getId())) {
            userRoleRepository.save(UserRole.builder()
                    .userId(userId)
                    .roleId(role.getId())
                    .grantedAt(Instant.now())
                    .build());
            log.info("Granted role {} to userId={}", roleCode, userId);
        }
    }

    @Override
    @Transactional
    public void revokeRole(UUID userId, RoleCode roleCode) {
        if (userId == null || roleCode == null) {
            return;
        }
        roleRepository.findByCode(roleCode)
                .ifPresent(role -> {
                    userRoleRepository.deleteByUserIdAndRoleId(userId, role.getId());
                    log.info("Revoked role {} from userId={}", roleCode, userId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public RoleApplicationDto getRoleApplicationById(UUID applicationId) {
        if (applicationId == null) {
            throw new ResourceNotFoundException("RoleApplication", "null");
        }
        return roleApplicationRepository.findById(applicationId)
                .map(this::toRoleApplicationDto)
                .orElseThrow(() -> new ResourceNotFoundException("RoleApplication", applicationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleApplicationDto> getRoleApplicationsByStatus(RoleApplicationStatus status) {
        List<RoleApplication> applications = status == null
                ? roleApplicationRepository.findAll()
                : roleApplicationRepository.findByStatusOrderByCreatedAtDesc(status);
        return applications.stream()
                .map(this::toRoleApplicationDto)
                .toList();
    }

    @Override
    @Transactional
    public RoleApplicationDto approveRoleApplication(UUID applicationId, UUID adminUserId, String reviewNote) {
        RoleApplication application = roleApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoleApplication", applicationId));

        if (application.getStatus() != RoleApplicationStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đơn đăng ký nâng cấp vai trò đã được xử lý trước đó."
            );
        }

        Instant reviewedAt = Instant.now();
        application.setStatus(RoleApplicationStatus.APPROVED);
        application.setReviewedByAdminId(adminUserId);
        application.setReviewNote(reviewNote);
        application.setReviewedAt(reviewedAt);
        application = roleApplicationRepository.save(application);

        grantRole(application.getUserId(), application.getRequestedRole());

        eventPublisher.publishEvent(new RoleApplicationApprovedEvent(
                UUID.randomUUID(),
                application.getId(),
                application.getUserId(),
                application.getRequestedRole().name(),
                adminUserId,
                reviewedAt
        ));

        log.info("Approved role application: id={}, userId={}, role={}, adminId={}",
                application.getId(), application.getUserId(), application.getRequestedRole(), adminUserId);

        return toRoleApplicationDto(application);
    }

    @Override
    @Transactional
    public RoleApplicationDto rejectRoleApplication(UUID applicationId, UUID adminUserId, String reviewNote) {
        RoleApplication application = roleApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoleApplication", applicationId));

        if (application.getStatus() != RoleApplicationStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đơn đăng ký nâng cấp vai trò đã được xử lý trước đó."
            );
        }

        application.setStatus(RoleApplicationStatus.REJECTED);
        application.setReviewedByAdminId(adminUserId);
        application.setReviewNote(reviewNote);
        application.setReviewedAt(Instant.now());
        application = roleApplicationRepository.save(application);

        log.info("Rejected role application: id={}, userId={}, role={}, adminId={}",
                application.getId(), application.getUserId(), application.getRequestedRole(), adminUserId);

        return toRoleApplicationDto(application);
    }

    @Override
    @Transactional
    public void updateAccountStatus(UUID userId, AccountStatus newStatus, UUID adminUserId, String reason) {
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "newStatus is required");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setAccountStatus(newStatus);
        userRepository.save(user);

        if (newStatus == AccountStatus.SUSPENDED || newStatus == AccountStatus.BANNED) {
            refreshTokenFamilyService.revokeAllUserTokens(userId);
        }

        log.info("Updated account status: userId={}, newStatus={}, adminId={}, reason={}",
                userId, newStatus, adminUserId, reason);
    }

    private UserSummaryDto toUserSummaryDto(User user) {
        Set<RoleCode> roles = getUserRoles(user.getId());
        Map<String, Object> stylePrefs = user.getStylePreferences() != null
                ? Map.copyOf(user.getStylePreferences())
                : Map.of();
        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getAvatarKey(),
                user.getAccountStatus(),
                user.getAuthProvider(),
                roles,
                stylePrefs,
                user.getCreatedAt()
        );
    }

    private UserAddressDto toUserAddressDto(UserAddress address) {
        return new UserAddressDto(
                address.getId(),
                address.getUserId(),
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getFullAddress(),
                address.getWardCode(),
                address.getDistrictCode(),
                address.getProvinceCode(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefault()
        );
    }

    private RoleApplicationDto toRoleApplicationDto(RoleApplication app) {
        Map<String, Object> payload = app.getPortfolioPayload() != null
                ? Map.copyOf(app.getPortfolioPayload())
                : Map.of();
        return new RoleApplicationDto(
                app.getId(),
                app.getUserId(),
                app.getRequestedRole(),
                payload,
                app.getStatus(),
                app.getReviewedByAdminId(),
                app.getReviewNote(),
                app.getReviewedAt(),
                app.getCreatedAt()
        );
    }
}
