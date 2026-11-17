package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.CommunityReaction;
import com.analoghub.platform.community.domain.ReactionTargetType;
import com.analoghub.platform.community.domain.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityReactionRepository extends JpaRepository<CommunityReaction, UUID> {

    Optional<CommunityReaction> findByTargetTypeAndTargetIdAndUserIdAndReactionType(
            ReactionTargetType targetType,
            UUID targetId,
            UUID userId,
            ReactionType reactionType
    );

    boolean existsByTargetTypeAndTargetIdAndUserIdAndReactionType(
            ReactionTargetType targetType,
            UUID targetId,
            UUID userId,
            ReactionType reactionType
    );

    long countByTargetTypeAndTargetIdAndReactionType(
            ReactionTargetType targetType,
            UUID targetId,
            ReactionType reactionType
    );
}
