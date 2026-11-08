package com.analoghub.platform.aiassistant.domain;

import com.analoghub.platform.aiassistant.dto.RagCitationDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final String ROLE_SYSTEM = "SYSTEM";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "created_at")
    private Instant createdAt;

    // Logical RAG telemetry & citation fields kept @Transient to strictly match V4__init_ai_and_admin_schema.sql
    @Transient
    private List<RagCitationDto> citations;

    @Transient
    private boolean safetyWarningAttached;

    @Transient
    private boolean abstained;

    @Transient
    private String abstentionReason;

    @Transient
    private Integer promptTokens;

    @Transient
    private Integer completionTokens;

    @Transient
    private Long latencyMs;

    @Transient
    private Double confidenceScore;

    @Transient
    private String promptVersion;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.tokensUsed == null) {
            this.tokensUsed = 0;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
