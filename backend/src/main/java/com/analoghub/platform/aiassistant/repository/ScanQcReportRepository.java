package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.ScanQcReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScanQcReportRepository extends JpaRepository<ScanQcReport, UUID> {

    List<ScanQcReport> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
