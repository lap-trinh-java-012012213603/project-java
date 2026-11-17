package com.analoghub.platform.community.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.community.domain.ArticleCategory;
import com.analoghub.platform.community.domain.ArticleStatus;
import com.analoghub.platform.community.domain.ExpertArticle;
import com.analoghub.platform.community.dto.CreateExpertArticleRequest;
import com.analoghub.platform.community.dto.DeleteExpertArticleResponse;
import com.analoghub.platform.community.dto.ExpertArticleDetailResponse;
import com.analoghub.platform.community.dto.ExpertArticleDto;
import com.analoghub.platform.community.dto.ExpertArticleSummaryResponse;
import com.analoghub.platform.community.dto.UpdateExpertArticleRequest;
import com.analoghub.platform.community.event.ArticleApprovedForRagEvent;
import com.analoghub.platform.community.event.ArticleRetractedEvent;
import com.analoghub.platform.community.repository.ExpertArticleRepository;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpertArticleService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s_]+");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script.*?>.*?</script>");

    private final ExpertArticleRepository articleRepository;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ExpertArticleDetailResponse createArticle(UUID authorExpertId, CreateExpertArticleRequest request) {
        ArticleCategory category = parseCategory(request.category());
        String coverImageUrl = verifyAndResolveCoverImage(request.coverImageKey());

        boolean publishNow = request.publishNow() == null || request.publishNow();
        ArticleStatus status = publishNow ? ArticleStatus.PUBLISHED : ArticleStatus.DRAFT;
        boolean approvedForRag = publishNow;

        String sanitizedMarkdown = sanitizeMarkdown(request.contentMarkdown());
        String slug = generateUniqueSlug(request.title());

        ExpertArticle article = ExpertArticle.builder()
                .authorExpertId(authorExpertId)
                .slug(slug)
                .title(request.title().trim())
                .contentMarkdown(sanitizedMarkdown)
                .category(category)
                .status(status)
                .approvedForRag(approvedForRag)
                .build();
        article = articleRepository.saveAndFlush(article);

        if (status == ArticleStatus.PUBLISHED && approvedForRag) {
            eventPublisher.publishEvent(new ArticleApprovedForRagEvent(
                    article.getId(),
                    article.getSlug(),
                    article.getTitle(),
                    article.getCategory().name(),
                    article.getContentMarkdown()
            ));
        }

        return buildDetailResponse(article, coverImageUrl);
    }

    @Transactional
    public ExpertArticleDetailResponse updateArticle(
            UUID authorExpertId,
            UUID articleId,
            UpdateExpertArticleRequest request
    ) {
        ExpertArticle article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài viết chuyên môn"));

        if (!article.getAuthorExpertId().equals(authorExpertId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không phải là tác giả của bài viết chuyên môn này");
        }

        if (article.getStatus() == ArticleStatus.RETRACTED) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bài viết đã bị gỡ do vi phạm (RETRACTED), không thể tự xuất bản lại"
            );
        }

        if (!Objects.equals(article.getVersion(), request.version())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Phiên bản bài viết đã thay đổi, vui lòng tải lại dữ liệu mới nhất"
            );
        }

        ArticleCategory category = parseCategory(request.category());
        ArticleStatus targetStatus = parseMutableStatus(request.status());
        String coverImageUrl = verifyAndResolveCoverImage(request.coverImageKey());

        ArticleStatus previousStatus = article.getStatus();
        boolean approvedForRag = targetStatus == ArticleStatus.PUBLISHED;

        article.setTitle(request.title().trim());
        article.setContentMarkdown(sanitizeMarkdown(request.contentMarkdown()));
        article.setCategory(category);
        article.setStatus(targetStatus);
        article.setApprovedForRag(approvedForRag);

        article = articleRepository.saveAndFlush(article);

        if (targetStatus == ArticleStatus.PUBLISHED) {
            eventPublisher.publishEvent(new ArticleApprovedForRagEvent(
                    article.getId(),
                    article.getSlug(),
                    article.getTitle(),
                    article.getCategory().name(),
                    article.getContentMarkdown()
            ));
        } else if (previousStatus == ArticleStatus.PUBLISHED && targetStatus == ArticleStatus.DRAFT) {
            eventPublisher.publishEvent(new ArticleRetractedEvent(article.getId(), article.getSlug()));
        }

        return buildDetailResponse(article, coverImageUrl);
    }

    @Transactional
    public DeleteExpertArticleResponse retractOrDeleteArticle(UUID actorUserId, boolean isAdmin, UUID articleId) {
        ExpertArticle article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND, "Không tìm thấy bài viết chuyên môn"));

        if (!isAdmin && !article.getAuthorExpertId().equals(actorUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền gỡ bài viết chuyên môn này");
        }

        Instant deletedAt = Instant.now();
        article.setStatus(ArticleStatus.RETRACTED);
        article.setApprovedForRag(false);
        article.setDeletedAt(deletedAt);
        articleRepository.saveAndFlush(article);

        eventPublisher.publishEvent(new ArticleRetractedEvent(article.getId(), article.getSlug()));

        return new DeleteExpertArticleResponse(
                article.getId(),
                article.getStatus().name(),
                article.isApprovedForRag(),
                deletedAt
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpertArticleSummaryResponse> listArticles(
            String categoryStr,
            UUID authorExpertId,
            String keyword,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }

        ArticleCategory categoryFilter = null;
        if (categoryStr != null && !categoryStr.isBlank()) {
            categoryFilter = parseCategory(categoryStr);
        }

        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<ExpertArticle> articlePage = articleRepository.searchPublishedArticles(
                ArticleStatus.PUBLISHED,
                categoryFilter,
                authorExpertId,
                normalizedKeyword,
                PageRequest.of(page, size)
        );

        List<ExpertArticle> articles = articlePage.getContent();
        if (articles.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        Set<UUID> authorIds = articles.stream().map(ExpertArticle::getAuthorExpertId).collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> authorMap = authorIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(authorIds);

        List<ExpertArticleSummaryResponse> items = articles.stream()
                .map(a -> {
                    UserSummaryDto author = authorMap.get(a.getAuthorExpertId());
                    ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                            a.getAuthorExpertId(),
                            author != null ? author.fullName() : "Chuyên gia AnalogHub",
                            author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
                    );
                    return new ExpertArticleSummaryResponse(
                            a.getId(),
                            a.getSlug(),
                            a.getTitle(),
                            buildExcerpt(a.getContentMarkdown(), 180),
                            a.getCategory().name(),
                            null,
                            authorSummary,
                            0L,
                            0L,
                            0L,
                            a.isApprovedForRag(),
                            a.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                page,
                size,
                (int) articlePage.getTotalElements(),
                articlePage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ExpertArticleDetailResponse getArticleDetail(UUID viewerUserId, boolean isAdmin, String slugOrId) {
        Optional<ExpertArticle> articleOpt = tryParseUuid(slugOrId)
                .flatMap(articleRepository::findByIdAndDeletedAtIsNull);

        if (articleOpt.isEmpty()) {
            articleOpt = articleRepository.findBySlugAndDeletedAtIsNull(slugOrId.trim());
        }

        ExpertArticle article = articleOpt.orElseThrow(() -> new BusinessException(
                ErrorCode.POST_NOT_FOUND,
                "Không tìm thấy bài viết chuyên môn yêu cầu"
        ));

        boolean isAuthor = viewerUserId != null && viewerUserId.equals(article.getAuthorExpertId());
        if (article.getStatus() != ArticleStatus.PUBLISHED && !isAuthor && !isAdmin) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bài viết đang ở trạng thái nháp hoặc đã bị gỡ"
            );
        }

        return buildDetailResponse(article, null);
    }

    @Transactional(readOnly = true)
    public Optional<ExpertArticleDto> findArticleDtoById(UUID articleId) {
        return articleRepository.findByIdAndDeletedAtIsNull(articleId).map(this::toArticleDto);
    }

    @Transactional(readOnly = true)
    public List<ExpertArticleDto> getApprovedArticlesForRag() {
        return articleRepository.findByStatusAndApprovedForRagTrueAndDeletedAtIsNull(ArticleStatus.PUBLISHED)
                .stream()
                .map(this::toArticleDto)
                .toList();
    }

    @Transactional
    public void retractArticleByModeration(UUID articleId, String moderationAction) {
        articleRepository.findByIdAndDeletedAtIsNull(articleId).ifPresent(article -> {
            article.setStatus(ArticleStatus.RETRACTED);
            article.setApprovedForRag(false);
            if ("DELETE".equalsIgnoreCase(moderationAction)) {
                article.setDeletedAt(Instant.now());
            }
            articleRepository.save(article);
            eventPublisher.publishEvent(new ArticleRetractedEvent(article.getId(), article.getSlug()));
        });
    }

    private ExpertArticleDto toArticleDto(ExpertArticle article) {
        return new ExpertArticleDto(
                article.getId(),
                article.getAuthorExpertId(),
                article.getSlug(),
                article.getTitle(),
                article.getContentMarkdown(),
                article.getCategory().name(),
                article.getStatus().name(),
                article.isApprovedForRag()
        );
    }

    private ExpertArticleDetailResponse buildDetailResponse(ExpertArticle article, String coverImageUrl) {
        UserSummaryDto author = iamFacade.findUserById(article.getAuthorExpertId()).orElse(null);
        ExpertArticleDetailResponse.AuthorSummary authorSummary = new ExpertArticleDetailResponse.AuthorSummary(
                article.getAuthorExpertId(),
                author != null ? author.fullName() : "Chuyên gia AnalogHub",
                author != null ? storageService.resolveReadUrl(author.avatarKey()) : null
        );
        ExpertArticleDetailResponse.ArticleStats stats = new ExpertArticleDetailResponse.ArticleStats(
                0L, 0L, 0L, false, false
        );

        return new ExpertArticleDetailResponse(
                article.getId(),
                article.getSlug(),
                article.getTitle(),
                article.getContentMarkdown(),
                article.getCategory().name(),
                coverImageUrl,
                article.getStatus().name(),
                article.isApprovedForRag(),
                authorSummary,
                stats,
                article.getVersion(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private String verifyAndResolveCoverImage(String coverImageKey) {
        if (coverImageKey == null || coverImageKey.isBlank()) {
            return null;
        }
        String key = coverImageKey.trim();
        if (!key.startsWith("public/community/")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đường dẫn ảnh bìa phải nằm trong thư mục public/community/"
            );
        }
        if (!storageService.exists(key)) {
            throw new BusinessException(
                    ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                    "Ảnh bìa bài viết chưa được tải lên kho lưu trữ"
            );
        }
        return storageService.resolveReadUrl(key);
    }

    private ArticleCategory parseCategory(String raw) {
        try {
            return ArticleCategory.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "category phải thuộc một trong: FILM_REVIEW, DARKROOM_TECHNIQUE, CAMERA_REPAIR, BEGINNER_GUIDE"
            );
        }
    }

    private ArticleStatus parseMutableStatus(String raw) {
        try {
            ArticleStatus status = ArticleStatus.valueOf(raw.trim().toUpperCase());
            if (status == ArticleStatus.RETRACTED) {
                throw new IllegalArgumentException();
            }
            return status;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "status chỉ chấp nhận DRAFT hoặc PUBLISHED"
            );
        }
    }

    private String sanitizeMarkdown(String markdown) {
        return SCRIPT_TAG.matcher(markdown.trim()).replaceAll("");
    }

    private String buildExcerpt(String markdown, int maxLen) {
        if (markdown == null) {
            return "";
        }
        String plain = markdown.replaceAll("(?m)^#+\\s*", "").replaceAll("\\s+", " ").trim();
        if (plain.length() <= maxLen) {
            return plain;
        }
        return plain.substring(0, maxLen) + "...";
    }

    private String generateUniqueSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd');
        String base = WHITESPACE.matcher(normalized.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
        base = NON_LATIN.matcher(base).replaceAll("");
        base = MULTI_DASH.matcher(base).replaceAll("-").replaceAll("^-|-$", "");
        if (base.length() > 180) {
            base = base.substring(0, 180);
        }
        if (base.isBlank()) {
            base = "expert-article";
        }
        String suffix = String.format("%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        String candidate = base + "-" + suffix;
        while (articleRepository.existsBySlug(candidate)) {
            suffix = String.format("%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private Optional<UUID> tryParseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
