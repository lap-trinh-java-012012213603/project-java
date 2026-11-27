package com.analoghub.platform.community.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.community.domain.CommentTargetType;
import com.analoghub.platform.community.domain.CommunityComment;
import com.analoghub.platform.community.domain.DiscussionPost;
import com.analoghub.platform.community.domain.DiscussionPostType;
import com.analoghub.platform.community.domain.ModerationStatus;
import com.analoghub.platform.community.domain.ReactionTargetType;
import com.analoghub.platform.community.domain.ReactionType;
import com.analoghub.platform.community.dto.AcceptAnswerRequest;
import com.analoghub.platform.community.dto.AcceptAnswerResponse;
import com.analoghub.platform.community.dto.CreateDiscussionPostRequest;
import com.analoghub.platform.community.dto.DiscussionPostDetailResponse;
import com.analoghub.platform.community.dto.DiscussionPostSummaryResponse;
import com.analoghub.platform.community.dto.ExpertArticleDetailResponse;
import com.analoghub.platform.community.repository.CommunityCommentRepository;
import com.analoghub.platform.community.repository.CommunityReactionRepository;
import com.analoghub.platform.community.repository.DiscussionPostRepository;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscussionPostService {

    private final DiscussionPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityReactionRepository reactionRepository;
    private final IamFacade iamFacade;
    private final StorageService storageService;

    @Transactional
    public DiscussionPostDetailResponse createDiscussionPost(
            UUID authorUserId,
            CreateDiscussionPostRequest request
    ) {
        DiscussionPostType postType = parsePostType(request.postType());
        if (postType == DiscussionPostType.PHOTO_SHOWCASE && request.attachedArchiveFrameId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bài đăng khoe ảnh (PHOTO_SHOWCASE) bắt buộc phải đính kèm bức ảnh từ Digital Archive (attachedArchiveFrameId)"
            );
        }

        DiscussionPost post = DiscussionPost.builder()
                .authorUserId(authorUserId)
                .postType(postType)
                .title(request.title().trim())
                .bodyMarkdown(request.bodyMarkdown().trim())
                .attachedArchiveFrameId(request.attachedArchiveFrameId())
                .moderationStatus(ModerationStatus.APPROVED)
                .build();
        post = postRepository.saveAndFlush(post);

        return buildDetailResponse(post, authorUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<DiscussionPostSummaryResponse> listDiscussionPosts(
            String postTypeStr,
            boolean unansweredOnly,
            String sortBy,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }
        if (sortBy != null && !sortBy.isBlank()) {
            String s = sortBy.trim().toUpperCase();
            if (!"NEWEST".equals(s) && !"MOST_LIKED".equals(s) && !"MOST_DISCUSSED".equals(s)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số sortBy không hợp lệ");
            }
        }

        DiscussionPostType postTypeFilter = null;
        if (postTypeStr != null && !postTypeStr.isBlank()) {
            postTypeFilter = parsePostType(postTypeStr);
        }

        Page<DiscussionPost> postPage = postRepository.searchPublicDiscussions(
                ModerationStatus.APPROVED,
                postTypeFilter,
                unansweredOnly,
                PageRequest.of(page, size)
        );

        List<DiscussionPost> posts = postPage.getContent();
        if (posts.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        Set<UUID> authorIds = posts.stream().map(DiscussionPost::getAuthorUserId).collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> authorMap = authorIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(authorIds);

        List<DiscussionPostSummaryResponse> items = posts.stream()
                .map(p -> {
                    UserSummaryDto author = authorMap.get(p.getAuthorUserId());
                    ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                            p.getAuthorUserId(),
                            author != null ? author.fullName() : "Thành viên AnalogHub",
                            author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
                    );
                    long likeCount = reactionRepository.countByTargetTypeAndTargetIdAndReactionType(
                            ReactionTargetType.DISCUSSION_POST, p.getId(), ReactionType.LIKE
                    );
                    long commentCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedAtIsNull(
                            CommentTargetType.DISCUSSION_POST, p.getId()
                    );
                    return new DiscussionPostSummaryResponse(
                            p.getId(),
                            p.getPostType().name(),
                            p.getTitle(),
                            buildExcerpt(p.getBodyMarkdown(), 160),
                            authorSummary,
                            p.getAttachedArchiveFrameId(),
                            p.getAcceptedAnswerCommentId() != null,
                            likeCount,
                            commentCount,
                            p.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                page,
                size,
                (int) postPage.getTotalElements(),
                postPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public DiscussionPostDetailResponse getDiscussionDetail(UUID viewerUserId, UUID postId) {
        DiscussionPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .filter(p -> p.getModerationStatus() == ModerationStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND,
                        "Bài viết thảo luận không tồn tại hoặc đã bị gỡ"
                ));

        return buildDetailResponse(post, viewerUserId);
    }

    @Transactional
    public AcceptAnswerResponse acceptAnswer(UUID currentUserId, UUID postId, AcceptAnswerRequest request) {
        DiscussionPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài thảo luận"));

        if (!post.getAuthorUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ tác giả câu hỏi mới có quyền chọn câu trả lời đúng");
        }

        if (post.getPostType() != DiscussionPostType.QUESTION_QA) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Chỉ bài đăng thuộc thể loại Hỏi đáp kỹ thuật (QUESTION_QA) mới có thể chọn câu trả lời đúng"
            );
        }

        CommunityComment comment = commentRepository.findByIdAndDeletedAtIsNull(request.commentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND, "Không tìm thấy bình luận"));

        if (comment.getTargetType() != CommentTargetType.DISCUSSION_POST || !comment.getTargetId().equals(post.getId())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bình luận được chọn không thuộc về câu hỏi này"
            );
        }

        post.setAcceptedAnswerCommentId(comment.getId());
        post = postRepository.saveAndFlush(post);

        return new AcceptAnswerResponse(
                post.getId(),
                comment.getId(),
                comment.getAuthorUserId(),
                post.getUpdatedAt()
        );
    }

    @Transactional
    public void moderateDiscussionPost(UUID postId, String moderationAction) {
        postRepository.findByIdAndDeletedAtIsNull(postId).ifPresent(post -> {
            String action = moderationAction != null ? moderationAction.trim().toUpperCase() : "HIDE";
            if ("DELETE".equals(action)) {
                post.setModerationStatus(ModerationStatus.REJECTED);
                post.setDeletedAt(Instant.now());
            } else if ("APPROVE".equals(action)) {
                post.setModerationStatus(ModerationStatus.APPROVED);
            } else {
                post.setModerationStatus(ModerationStatus.HIDDEN);
            }
            postRepository.save(post);
        });
    }

    private DiscussionPostDetailResponse buildDetailResponse(DiscussionPost post, UUID viewerUserId) {
        UserSummaryDto author = iamFacade.findUserById(post.getAuthorUserId()).orElse(null);
        ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                post.getAuthorUserId(),
                author != null ? author.fullName() : "Thành viên AnalogHub",
                author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
        );

        DiscussionPostDetailResponse.AttachedFrameSummary attachedFrame = post.getAttachedArchiveFrameId() != null
                ? new DiscussionPostDetailResponse.AttachedFrameSummary(post.getAttachedArchiveFrameId())
                : null;

        long likeCount = reactionRepository.countByTargetTypeAndTargetIdAndReactionType(
                ReactionTargetType.DISCUSSION_POST, post.getId(), ReactionType.LIKE
        );
        long commentCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedAtIsNull(
                CommentTargetType.DISCUSSION_POST, post.getId()
        );

        boolean likedByMe = viewerUserId != null && reactionRepository.existsByTargetTypeAndTargetIdAndUserIdAndReactionType(
                ReactionTargetType.DISCUSSION_POST, post.getId(), viewerUserId, ReactionType.LIKE
        );
        boolean bookmarkedByMe = viewerUserId != null && reactionRepository.existsByTargetTypeAndTargetIdAndUserIdAndReactionType(
                ReactionTargetType.DISCUSSION_POST, post.getId(), viewerUserId, ReactionType.BOOKMARK
        );

        return new DiscussionPostDetailResponse(
                post.getId(),
                post.getPostType().name(),
                post.getTitle(),
                post.getBodyMarkdown(),
                authorSummary,
                attachedFrame,
                post.getAcceptedAnswerCommentId(),
                likeCount,
                commentCount,
                likedByMe,
                bookmarkedByMe,
                post.getModerationStatus().name(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private DiscussionPostType parsePostType(String raw) {
        try {
            return DiscussionPostType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "postType chỉ chấp nhận DISCUSSION, QUESTION_QA hoặc PHOTO_SHOWCASE"
            );
        }
    }

    private String buildExcerpt(String markdown, int maxLen) {
        if (markdown == null) {
            return "";
        }
        String plain = markdown.replaceAll("\\s+", " ").trim();
        return plain.length() <= maxLen ? plain : plain.substring(0, maxLen) + "...";
    }
}
