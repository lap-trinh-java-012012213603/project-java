package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.CommentTargetType;
import com.analoghub.platform.community.domain.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, UUID> {

    Optional<CommunityComment> findByIdAndDeletedAtIsNull(UUID id);

    Page<CommunityComment> findByTargetTypeAndTargetIdAndParentCommentIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(
            CommentTargetType targetType,
            UUID targetId,
            Pageable pageable
    );

    List<CommunityComment> findByParentCommentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(Collection<UUID> parentCommentIds);

    long countByTargetTypeAndTargetIdAndDeletedAtIsNull(CommentTargetType targetType, UUID targetId);
}
