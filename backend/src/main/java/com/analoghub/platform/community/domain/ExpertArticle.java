package com.analoghub.platform.community.domain;

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
@Table(name = "com_expert_articles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertArticle extends BaseAuditEntity {

    @Column(name = "author_expert_id", nullable = false)
    private UUID authorExpertId;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "content_markdown", columnDefinition = "text", nullable = false)
    private String contentMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 64, nullable = false)
    private ArticleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ArticleStatus status;

    @Column(name = "approved_for_rag", nullable = false)
    private boolean approvedForRag;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
