package com.analoghub.platform.messaging.repository;

import com.analoghub.platform.messaging.domain.Conversation;
import com.analoghub.platform.messaging.domain.ConversationContextType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.contextType = :contextType
              AND c.contextReferenceId = :contextRefId
              AND EXISTS (SELECT 1 FROM ConversationParticipant p1 WHERE p1.conversationId = c.id AND p1.userId = :userA)
              AND EXISTS (SELECT 1 FROM ConversationParticipant p2 WHERE p2.conversationId = c.id AND p2.userId = :userB)
            """)
    Optional<Conversation> findByContextAndParticipantPair(
            @Param("contextType") ConversationContextType contextType,
            @Param("contextRefId") UUID contextRefId,
            @Param("userA") UUID userA,
            @Param("userB") UUID userB
    );

    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationParticipant mp ON mp.conversationId = c.id
            WHERE mp.userId = :userId
              AND (:contextType IS NULL OR c.contextType = :contextType)
              AND (:unreadOnly = false OR mp.unreadCount > 0)
            ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC
            """)
    Page<Conversation> findUserConversations(
            @Param("userId") UUID userId,
            @Param("contextType") ConversationContextType contextType,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );
}
