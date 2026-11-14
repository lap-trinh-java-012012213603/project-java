package com.analoghub.platform.admin.repository;

import com.analoghub.platform.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTargetEntityAndTargetIdOrderByCreatedAtDesc(String targetEntity, UUID targetId);
}
