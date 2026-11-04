package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {

    List<AiUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AiUsageLog> findByActionContainingIgnoreCaseOrderByCreatedAtDesc(String action);
}

