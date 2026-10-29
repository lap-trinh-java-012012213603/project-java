package com.analoghub.platform.iam.facade;

import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.dto.UserSummaryDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public cross-module contract for {@code iam} (Tier 1).
 * Exposes user identity lookups, address snapshots, role verification, and synchronous
 * role-application/account-moderation operations invoked downward by {@code admin} (Tier 5).
 */
public interface IamFacade {

    // =========================================================================
    // 1. User Profile, Identity & Style Preferences Lookup
    // =========================================================================

    Optional<UserSummaryDto> findUserById(UUID userId);

    UserSummaryDto getRequiredUserById(UUID userId);

    Map<UUID, UserSummaryDto> findUsersByIds(Collection<UUID> userIds);

    boolean isActiveUser(UUID userId);

    Set<RoleCode> getUserRoles(UUID userId);

    boolean hasRole(UUID userId, RoleCode roleCode);

    Map<String, Object> getUserStylePreferences(UUID userId);

    // =========================================================================
    // 2. User Address Lookup (for Booking / Delivery / Marketplace)
    // =========================================================================

    Optional<UserAddressDto> findAddressById(UUID userId, UUID addressId);

    UserAddressDto getRequiredAddress(UUID userId, UUID addressId);

    Optional<UserAddressDto> findDefaultAddress(UUID userId);

    // =========================================================================
    // 3. Role Assignment & Role Application Review (called by Admin - Tier 5)
    // =========================================================================

    void grantRole(UUID userId, RoleCode roleCode);

    void revokeRole(UUID userId, RoleCode roleCode);

    RoleApplicationDto getRoleApplicationById(UUID applicationId);

    List<RoleApplicationDto> getRoleApplicationsByStatus(RoleApplicationStatus status);

    RoleApplicationDto approveRoleApplication(UUID applicationId, UUID adminUserId, String reviewNote);

    RoleApplicationDto rejectRoleApplication(UUID applicationId, UUID adminUserId, String reviewNote);

    // =========================================================================
    // 4. User Account Moderation (called by Admin - Tier 5)
    // =========================================================================

    void updateAccountStatus(UUID userId, AccountStatus newStatus, UUID adminUserId, String reason);
}
