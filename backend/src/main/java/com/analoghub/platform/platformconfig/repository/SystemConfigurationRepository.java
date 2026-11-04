package com.analoghub.platform.platformconfig.repository;

import com.analoghub.platform.platformconfig.domain.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, String> {

    List<SystemConfiguration> findAllByOrderByConfigKeyAsc();

    List<SystemConfiguration> findByConfigKeyIn(Collection<String> configKeys);
}
