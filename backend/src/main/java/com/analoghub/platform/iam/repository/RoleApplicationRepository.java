package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.RoleApplication;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleApplicationRepository extends JpaRepository<RoleApplication, UUID> {

    List<RoleApplication> findByStatusOrderByCreatedAtDesc(RoleApplicationStatus status);

    List<RoleApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndRequestedRoleAndStatus(UUID userId, RoleCode requestedRole, RoleApplicationStatus status);
}
