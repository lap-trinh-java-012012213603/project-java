package com.analoghub.platform.admin.repository;

import com.analoghub.platform.admin.domain.ModerationTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModerationTicketRepository extends JpaRepository<ModerationTicket, UUID> {

    Optional<ModerationTicket> findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);

    List<ModerationTicket> findByEntityTypeAndEntityIdIn(String entityType, Collection<UUID> entityIds);

    List<ModerationTicket> findByStatusOrderByCreatedAtDesc(String status);
}
