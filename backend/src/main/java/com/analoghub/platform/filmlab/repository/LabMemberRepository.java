package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.LabMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabMemberRepository extends JpaRepository<LabMember, UUID> {

    Optional<LabMember> findByLabIdAndUserId(UUID labId, UUID userId);

    List<LabMember> findByUserIdAndIsActiveTrue(UUID userId);

    List<LabMember> findByLabIdAndIsActiveTrue(UUID labId);
}
