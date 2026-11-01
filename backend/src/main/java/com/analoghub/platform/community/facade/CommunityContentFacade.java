package com.analoghub.platform.community.facade;

import com.analoghub.platform.community.dto.ExpertArticleDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityContentFacade {

    Optional<ExpertArticleDto> getArticleById(UUID articleId);

    List<ExpertArticleDto> getApprovedArticlesForRag();

    void moderateContent(String targetType, UUID targetId, String moderationAction, String reason);

    void markReportResolved(UUID reportId, String resolutionStatus);
}
