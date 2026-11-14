package com.analoghub.platform.platformconfig.repository;

import com.analoghub.platform.platformconfig.domain.ServiceCategory;
import com.analoghub.platform.platformconfig.dto.ServiceCategoryGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {

    Optional<ServiceCategory> findByCode(String code);

    Optional<ServiceCategory> findByCodeAndIsActiveTrue(String code);

    boolean existsByCodeAndIsActiveTrue(String code);

    List<ServiceCategory> findByIsActiveTrueOrderByDisplayOrderAscCodeAsc();

    List<ServiceCategory> findByCategoryGroupAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(ServiceCategoryGroup categoryGroup);

    List<ServiceCategory> findAllByOrderByCategoryGroupAscDisplayOrderAscCodeAsc();
}
