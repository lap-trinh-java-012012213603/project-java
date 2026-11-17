package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.ContentReport;
import com.analoghub.platform.community.domain.ReportStatus;
import com.analoghub.platform.community.domain.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    boolean existsByTargetTypeAndTargetIdAndReporterUserIdAndStatus(
            ReportTargetType targetType,
            UUID targetId,
            UUID reporterUserId,
            ReportStatus status
    );
}
