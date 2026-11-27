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
@Table(name = "com_discussion_posts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionPost extends BaseAuditEntity {

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", length = 32, nullable = false)
    private DiscussionPostType postType;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "body_markdown", columnDefinition = "text", nullable = false)
    private String bodyMarkdown;

    @Column(name = "attached_archive_frame_id")
    private UUID attachedArchiveFrameId;

    @Column(name = "accepted_answer_comment_id")
    private UUID acceptedAnswerCommentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 32, nullable = false)
    private ModerationStatus moderationStatus;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
