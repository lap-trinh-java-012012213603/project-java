package com.analoghub.platform.community.facade;

import com.analoghub.platform.community.dto.ExpertArticleDto;
import com.analoghub.platform.community.service.CommunityInteractionService;
import com.analoghub.platform.community.service.DiscussionPostService;
import com.analoghub.platform.community.service.ExpertArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CommunityContentFacadeImpl implements CommunityContentFacade {

    private final ExpertArticleService articleService;
    private final DiscussionPostService discussionPostService;
    private final CommunityInteractionService interactionService;

    @Override
    public Optional<ExpertArticleDto> getArticleById(UUID articleId) {
        return articleService.findArticleDtoById(articleId);
    }

    @Override
    public List<ExpertArticleDto> getApprovedArticlesForRag() {
        return articleService.getApprovedArticlesForRag();
    }

    @Override
    public void moderateContent(String targetType, UUID targetId, String moderationAction, String reason) {
        if (targetType == null || targetId == null) {
            return;
        }
        String normalized = targetType.trim().toUpperCase();
        switch (normalized) {
            case "EXPERT_ARTICLE" -> articleService.retractArticleByModeration(targetId, moderationAction);
            case "DISCUSSION_POST" -> discussionPostService.moderateDiscussionPost(targetId, moderationAction);
            case "COMMENT" -> interactionService.moderateComment(targetId, moderationAction);
            default -> {
            }
        }
    }

    @Override
    public void markReportResolved(UUID reportId, String resolutionStatus) {
        interactionService.markReportResolved(reportId, resolutionStatus);
    }
}
