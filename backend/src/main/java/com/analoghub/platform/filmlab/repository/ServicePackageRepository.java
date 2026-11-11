package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

    // Lấy danh sách các gói dịch vụ active của một Lab cụ thể
    List<ServicePackage> findByLabIdAndIsActiveTrue(UUID labId);
}
