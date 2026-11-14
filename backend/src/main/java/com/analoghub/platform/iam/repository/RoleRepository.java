package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.Role;
import com.analoghub.platform.iam.dto.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);
}
