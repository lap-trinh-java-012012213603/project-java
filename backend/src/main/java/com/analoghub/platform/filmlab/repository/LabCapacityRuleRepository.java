package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabCapacityRuleRepository extends JpaRepository<LabCapacityRule, UUID> {

    // Tìm quy tắc công suất theo ID của Lab
    Optional<LabCapacityRule> findByLabIdAndProcessType(UUID labId, String processType);
    List<LabCapacityRule> findByLabId(UUID labId);
}