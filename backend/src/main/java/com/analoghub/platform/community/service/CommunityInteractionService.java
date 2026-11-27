package com.analoghub.platform.community.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.community.domain.CommentTargetType;
import com.analoghub.platform.community.domain.CommunityComment;
import com.analoghub.platform.community.domain.CommunityReaction;
import com.analoghub.platform.community.domain.ContentReport;
import com.analoghub.platform.community.domain.ReactionTargetType;
import com.analoghub.platform.community.domain.ReactionType;
import com.analoghub.platform.community.domain.ReportStatus;
import com.analoghub.platform.community.domain.ReportTargetType;
import com.analoghub.platform.community.dto.CommentResponse;
import com.analoghub.platform.community.dto.ContentReportResponse;
import com.analoghub.platform.community.dto.CreateCommentRequest;
import com.analoghub.platform.community.dto.CreateContentReportRequest;
import com.analoghub.platform.community.dto.ExpertArticleDetailResponse;
import com.analoghub.platform.community.dto.ReactionUpdatedResponse;
import com.analoghub.platform.community.dto.ToggleReactionRequest;
import com.analoghub.platform.community.event.ContentReportedEvent;
import com.analoghub.platform.community.repository.CommunityCommentRepository;
import com.analoghub.platform.community.repository.CommunityReactionRepository;
import com.analoghub.platform.community.repository.ContentReportRepository;
import com.analoghub.platform.community.repository.DiscussionPostRepository;
import com.analoghub.platform.community.repository.ExpertArticleRepository;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityInteractionService {

    private static final Set<String> ALLOWED_REASON_CODES = Set.of(
            "COUNTERFEIT_OR_SCAM",
            "HARASSMENT",
            "SPAM_ADVERTISING",
            "INACCURATE_CONDITION",
            "INAPPROPRIATE_CONTENT"
    );

    private final CommunityCommentRepository commentRepository;
    private final CommunityReactionRepository reactionRepository;
    private final ContentReportRepository reportRepository;
    private final ExpertArticleRepository articleRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentResponse createComment(UUID authorUserId, CreateCommentRequest request) {
        CommentTargetType targetType = parseCommentTargetType(request.targetType());
        verifyCommentTargetExists(targetType, request.targetId());

        if (request.parentCommentId() != null) {
            CommunityComment parent = commentRepository.findByIdAndDeletedAtIsNull(request.parentCommentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND, "Không tìm thấy bình luận cha"));
            if (parent.getTargetType() != targetType || !parent.getTargetId().equals(request.targetId())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Bình luận cha không thuộc về bài viết hiện tại"
                );
            }
        }

        CommunityComment comment = CommunityComment.builder()
                .targetType(targetType)
                .targetId(request.targetId())
                .parentCommentId(request.parentCommentId())
                .authorUserId(authorUserId)
                .content(request.content().trim())
                .build();
        comment = commentRepository.saveAndFlush(comment);

        UserSummaryDto author = iamFacade.findUserById(authorUserId).orElse(null);
        ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                authorUserId,
                author != null ? author.fullName() : "Thành viên AnalogHub",
                author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
        );

        return new CommentResponse(
                comment.getId(),
                comment.getTargetType().name(),
                comment.getTargetId(),
                comment.getParentCommentId(),
                authorSummary,
                comment.getContent(),
                0L,
                comment.getCreatedAt(),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listComments(
            String targetTypeStr,
            UUID targetId,
            int page,
            int size
    ) {
        if (targetTypeStr == null || targetTypeStr.isBlank() || targetId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số targetType và targetId là bắt buộc");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }

        CommentTargetType targetType = parseCommentTargetType(targetTypeStr);
        Page<CommunityComment> rootPage = commentRepository
                .findByTargetTypeAndTargetIdAndParentCommentIdIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(
                        targetType, targetId, PageRequest.of(page, size)
                );

        List<CommunityComment> roots = rootPage.getContent();
        if (roots.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        List<UUID> rootIds = roots.stream().map(CommunityComment::getId).toList();
        List<CommunityComment> replies = commentRepository.findByParentCommentIdInAndDeletedAtIsNullOrderByCreatedAtAsc(rootIds);

        Set<UUID> authorIds = new HashSet<>();
        for (CommunityComment c : roots) {
            authorIds.add(c.getAuthorUserId());
        }
        for (CommunityComment r : replies) {
            authorIds.add(r.getAuthorUserId());
        }
        Map<UUID, UserSummaryDto> userMap = authorIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(authorIds);

        Map<UUID, List<CommentResponse>> repliesByParentId = new HashMap<>();
        for (CommunityComment r : replies) {
            CommentResponse replyDto = toCommentDto(r, userMap.get(r.getAuthorUserId()), List.of());
            repliesByParentId.computeIfAbsent(r.getParentCommentId(), k -> new ArrayList<>()).add(replyDto);
        }

        List<CommentResponse> items = roots.stream()
                .map(root -> toCommentDto(
                        root,
                        userMap.get(root.getAuthorUserId()),
                        repliesByParentId.getOrDefault(root.getId(), List.of())
                ))
                .toList();

        return PageResponse.from(
                items,
                page,
                size,
                (int) rootPage.getTotalElements(),
                rootPage.getTotalPages()
        );
    }

    @Transactional
    public ReactionUpdatedResponse toggleReaction(UUID userId, ToggleReactionRequest request) {
        ReactionTargetType targetType = parseReactionTargetType(request.targetType());
        ReactionType reactionType = parseReactionType(request.reactionType());
        verifyReactionTargetExists(targetType, request.targetId());

        Optional<CommunityReaction> existingOpt = reactionRepository.findByTargetTypeAndTargetIdAndUserIdAndReactionType(
                targetType, request.targetId(), userId, reactionType
        );

        boolean active = Boolean.TRUE.equals(request.active());
        if (active && existingOpt.isEmpty()) {
            CommunityReaction reaction = CommunityReaction.builder()
                    .targetType(targetType)
                    .targetId(request.targetId())
                    .userId(userId)
                    .reactionType(reactionType)
                    .createdAt(Instant.now())
                    .build();
            reactionRepository.saveAndFlush(reaction);
        } else if (!active && existingOpt.isPresent()) {
            reactionRepository.delete(existingOpt.get());
            reactionRepository.flush();
        }

        long currentCount = reactionRepository.countByTargetTypeAndTargetIdAndReactionType(
                targetType, request.targetId(), reactionType
        );

        return new ReactionUpdatedResponse(
                targetType.name(),
                request.targetId(),
                reactionType.name(),
                active,
                currentCount
        );
    }

    @Transactional
    public ContentReportResponse submitReport(UUID reporterUserId, CreateContentReportRequest request) {
        ReportTargetType targetType = parseReportTargetType(request.targetType());
        String reasonCode = normalizeReasonCode(request.reasonCode());

        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterUserIdAndStatus(
                targetType, request.targetId(), reporterUserId, ReportStatus.PENDING
        )) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bạn đã gửi báo cáo cho nội dung này và hệ thống đang trong quá trình xem xét"
            );
        }

        ContentReport report = ContentReport.builder()
                .reporterUserId(reporterUserId)
                .targetType(targetType)
                .targetId(request.targetId())
                .reason(reasonCode)
                .details(request.details().trim())
                .status(ReportStatus.PENDING)
                .build();
        report = reportRepository.saveAndFlush(report);

        eventPublisher.publishEvent(new ContentReportedEvent(
                report.getId(),
                report.getTargetType().name(),
                report.getTargetId(),
                reporterUserId,
                report.getReason()
        ));

        return new ContentReportResponse(
                report.getId(),
                report.getTargetType().name(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }

    @Transactional
    public void moderateComment(UUID commentId, String moderationAction) {
        commentRepository.findByIdAndDeletedAtIsNull(commentId).ifPresent(comment -> {
            if (!"APPROVE".equalsIgnoreCase(moderationAction)) {
                comment.setDeletedAt(Instant.now());
                commentRepository.save(comment);
            }
        });
    }

    @Transactional
    public void markReportResolved(UUID reportId, String resolutionStatus) {
        ContentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy báo cáo vi phạm"));

        String normalized = resolutionStatus != null ? resolutionStatus.trim().toUpperCase() : "RESOLVED";
        if ("DISMISSED".equals(normalized)) {
            report.setStatus(ReportStatus.DISMISSED);
        } else {
            report.setStatus(ReportStatus.RESOLVED);
        }
        reportRepository.save(report);
    }

    private CommentResponse toCommentDto(
            CommunityComment comment,
            UserSummaryDto author,
            List<CommentResponse> replies
    ) {
        ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                comment.getAuthorUserId(),
                author != null ? author.fullName() : "Thành viên AnalogHub",
                author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
        );
        long helpfulCount = reactionRepository.countByTargetTypeAndTargetIdAndReactionType(
                ReactionTargetType.COMMENT, comment.getId(), ReactionType.HELPFUL
        );
        return new CommentResponse(
                comment.getId(),
                comment.getTargetType().name(),
                comment.getTargetId(),
                comment.getParentCommentId(),
                authorSummary,
                comment.getContent(),
                helpfulCount,
                comment.getCreatedAt(),
                replies
        );
    }

    private void verifyCommentTargetExists(CommentTargetType targetType, UUID targetId) {
        if (targetType == CommentTargetType.EXPERT_ARTICLE) {
            if (articleRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài viết chuyên môn");
            }
        } else if (targetType == CommentTargetType.DISCUSSION_POST) {
            if (discussionPostRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài thảo luận");
            }
        }
    }

    private void verifyReactionTargetExists(ReactionTargetType targetType, UUID targetId) {
        switch (targetType) {
            case EXPERT_ARTICLE -> {
                if (articleRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài viết chuyên môn");
                }
            }
            case DISCUSSION_POST -> {
                if (discussionPostRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài thảo luận");
                }
            }
            case COMMENT -> {
                if (commentRepository.findByIdAndDeletedAtIsNull(targetId).isEmpty()) {
                    throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND, "Không tìm thấy bình luận");
                }
            }
            default -> {
            }
        }
    }

    private CommentTargetType parseCommentTargetType(String raw) {
        try {
            return CommentTargetType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetType bình luận không hợp lệ: " + raw);
        }
    }

    private ReactionTargetType parseReactionTargetType(String raw) {
        try {
            return ReactionTargetType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetType tương tác không hợp lệ: " + raw);
        }
    }

    private ReactionType parseReactionType(String raw) {
        try {
            return ReactionType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reactionType chỉ hỗ trợ LIKE, BOOKMARK hoặc HELPFUL");
        }
    }

    private ReportTargetType parseReportTargetType(String raw) {
        String normalized = raw.trim().toUpperCase();
        if ("MARKET_LISTING".equals(normalized)) {
            return ReportTargetType.MARKETPLACE_LISTING;
        }
        try {
            return ReportTargetType.valueOf(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetType báo cáo không hợp lệ: " + raw);
        }
    }

    private String normalizeReasonCode(String raw) {
        String normalized = raw.trim().toUpperCase();
        if (!ALLOWED_REASON_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã lý do báo cáo (reasonCode) không hợp lệ");
        }
        return normalized;
    }
}
