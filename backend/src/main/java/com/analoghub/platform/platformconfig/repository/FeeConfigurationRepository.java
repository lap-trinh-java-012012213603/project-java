package com.analoghub.platform.platformconfig.repository;

import com.analoghub.platform.platformconfig.domain.FeeConfiguration;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeConfigurationRepository extends JpaRepository<FeeConfiguration, UUID> {

    Optional<FeeConfiguration> findByFeeScope(FeeScope feeScope);

    Optional<FeeConfiguration> findByFeeScopeAndIsActiveTrue(FeeScope feeScope);

    List<FeeConfiguration> findAllByOrderByFeeScopeAsc();
}
