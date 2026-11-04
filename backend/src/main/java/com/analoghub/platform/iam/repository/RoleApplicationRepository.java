package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.RoleApplication;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleApplicationRepository extends JpaRepository<RoleApplication, UUID> {

    List<RoleApplication> findByStatusOrderByCreatedAtDesc(RoleApplicationStatus status);

    List<RoleApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<RoleApplication> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, RoleApplicationStatus status);

    boolean existsByUserIdAndRequestedRoleAndStatus(UUID userId, RoleCode requestedRole, RoleApplicationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ra FROM RoleApplication ra WHERE ra.id = :id")
    Optional<RoleApplication> findByIdForUpdate(@Param("id") UUID id);
}
