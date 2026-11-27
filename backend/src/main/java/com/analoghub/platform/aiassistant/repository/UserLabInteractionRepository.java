package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.UserLabInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserLabInteractionRepository extends JpaRepository<UserLabInteraction, UUID> {

    List<UserLabInteraction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<UserLabInteraction> findByLabIdOrderByCreatedAtDesc(UUID labId);

    long countByUserId(UUID userId);
}
