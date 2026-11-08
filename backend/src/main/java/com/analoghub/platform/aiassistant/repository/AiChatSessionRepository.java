package com.analoghub.platform.aiassistant.repository;

import com.analoghub.platform.aiassistant.domain.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {

    List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
