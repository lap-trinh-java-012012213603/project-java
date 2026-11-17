package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.DiscussionPost;
import com.analoghub.platform.community.domain.DiscussionPostType;
import com.analoghub.platform.community.domain.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscussionPostRepository extends JpaRepository<DiscussionPost, UUID> {

    Optional<DiscussionPost> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT p FROM DiscussionPost p
            WHERE p.deletedAt IS NULL
              AND p.moderationStatus = :modStatus
              AND (:postType IS NULL OR p.postType = :postType)
              AND (:unansweredOnly = false OR (p.postType = com.analoghub.platform.community.domain.DiscussionPostType.QUESTION_QA AND p.acceptedAnswerCommentId IS NULL))
            ORDER BY p.createdAt DESC
            """)
    Page<DiscussionPost> searchPublicDiscussions(
            @Param("modStatus") ModerationStatus modStatus,
            @Param("postType") DiscussionPostType postType,
            @Param("unansweredOnly") boolean unansweredOnly,
            Pageable pageable
    );
}
