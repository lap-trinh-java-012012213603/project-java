package com.analoghub.platform.messaging.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "msg_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", length = 32, nullable = false)
    private ConversationContextType contextType;

    @Column(name = "context_reference_id", nullable = false)
    private UUID contextReferenceId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
