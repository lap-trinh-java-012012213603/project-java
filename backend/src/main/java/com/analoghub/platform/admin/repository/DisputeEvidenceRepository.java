package com.analoghub.platform.admin.repository;

import com.analoghub.platform.admin.domain.DisputeEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);

    List<DisputeEvidence> findByDisputeIdInOrderByCreatedAtAsc(Collection<UUID> disputeIds);

    long countByDisputeId(UUID disputeId);
}
