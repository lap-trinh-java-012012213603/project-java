package com.analoghub.platform.aiassistant.domain;

import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_knowledge_chunks")
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Transient
    private String sourceType;

    @Transient
    private UUID sourceRefId;

    @Transient
    private Integer chunkIndex;

    @Transient
    private String chunkTitle;

    @Transient
    private String contentSha256;

    @Transient
    private String embeddingModel;

    @Transient
    @Builder.Default
    private Integer dimensions = EmbeddingClient.REQUIRED_DIMENSIONS;

    @Transient
    private float[] embedding;

    public String getChunkText() {
        return chunkContent;
    }

    public void setChunkText(String chunkText) {
        this.chunkContent = chunkText;
    }

    @PrePersist
    @PreUpdate
    public void syncFieldsToMetadata() {
        Map<String, Object> merged = metadata != null
                ? new LinkedHashMap<>(metadata)
                : new LinkedHashMap<>();

        if (sourceType != null) {
            merged.put("sourceType", sourceType);
        }
        if (sourceRefId != null) {
            merged.put("sourceRefId", sourceRefId.toString());
        }
        if (chunkIndex != null) {
            merged.put("chunkIndex", chunkIndex);
        }
        if (chunkTitle != null) {
            merged.put("chunkTitle", chunkTitle);
        }
        if (contentSha256 != null) {
            merged.put("contentSha256", contentSha256);
        }
        if (embeddingModel != null) {
            merged.put("embeddingModel", embeddingModel);
        }
        if (dimensions != null) {
            merged.put("dimensions", dimensions);
        }
        this.metadata = merged;
    }

    @PostLoad
    public void syncMetadataToFields() {
        if (metadata == null) {
            return;
        }
        if (sourceType == null && metadata.get("sourceType") instanceof String s) {
            sourceType = s;
        }
        if (sourceRefId == null && metadata.get("sourceRefId") instanceof String refStr) {
            try {
                sourceRefId = UUID.fromString(refStr);
            } catch (IllegalArgumentException ignored) {
                // ignore malformed UUID in legacy metadata
            }
        }
        if (chunkIndex == null && metadata.get("chunkIndex") instanceof Number n) {
            chunkIndex = n.intValue();
        }
        if (chunkTitle == null && metadata.get("chunkTitle") instanceof String t) {
            chunkTitle = t;
        }
        if (contentSha256 == null && metadata.get("contentSha256") instanceof String h) {
            contentSha256 = h;
        }
        if (embeddingModel == null && metadata.get("embeddingModel") instanceof String m) {
            embeddingModel = m;
        }
        if (dimensions == null && metadata.get("dimensions") instanceof Number d) {
            dimensions = d.intValue();
        }
    }
}
