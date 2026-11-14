package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.UserRole;
import com.analoghub.platform.iam.dto.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findAllByUserId(UUID userId);

    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("""
            SELECT r.code
            FROM UserRole ur
            JOIN Role r ON r.id = ur.roleId
            WHERE ur.userId = :userId
            """)
    Set<RoleCode> findRoleCodesByUserId(@Param("userId") UUID userId);
}
