package com.analoghub.platform.admin.service;

import com.analoghub.platform.admin.audit.AdminAuditLogAspect;
import com.analoghub.platform.admin.audit.AdminAudited;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.ModerationTicket;
import com.analoghub.platform.admin.dto.ModerationAction;
import com.analoghub.platform.admin.dto.ModerationTicketAdminDto;
import com.analoghub.platform.admin.dto.ModerationTicketResolutionResponse;
import com.analoghub.platform.admin.dto.ResolveModerationTicketRequest;
import com.analoghub.platform.admin.repository.ModerationTicketRepository;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.community.dto.ExpertArticleDto;
import com.analoghub.platform.community.event.ContentReportedEvent;
import com.analoghub.platform.community.facade.CommunityContentFacade;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tier-5 orchestrator for community/marketplace/filmlab reported-content moderation (APIs 8 & 9):
 * <ul>
 *     <li>Ingests {@link ContentReportedEvent} into {@code adm_moderation_tickets} with report-count aggregation</li>
 *     <li>Lists & enriches moderation tickets prioritized by {@code reportCount DESC, createdAt ASC}</li>
 *     <li>Dispatches Admin sanctions synchronously across {@link CommunityContentFacade},
 *     {@link MarketplaceFacade}, {@link FilmLabFacade}, and {@link IamFacade} while recording
 *     immutable audit logs via {@link AdminAudited}</li>
 * </ul>
 */
@Slf4j
@Service
public class ContentModerationService {

    private static final String TARGET_TYPE_CONTENT_REPORT = "CONTENT_REPORT";
    private static final String SUBTYPE_MARKET_LISTING = "MARKET_LISTING";
    private static final String SUBTYPE_LAB_REVIEW = "LAB_REVIEW";
    private static final String SUBTYPE_DISCUSSION_POST = "DISCUSSION_POST";
    private static final String SUBTYPE_COMMENT = "COMMENT";
    private static final String SUBTYPE_EXPERT_ARTICLE = "EXPERT_ARTICLE";
    private static final String ENTITY_ROLE_APPLICATION = "ROLE_APPLICATION";
    private static final String ENTITY_LAB_APPROVAL = "LAB_APPROVAL";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_ACTION_TAKEN = "ACTION_TAKEN";

    private static final Set<String> VALID_FILTER_STATUSES = Set.of(
            STATUS_OPEN, STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED, STATUS_ACTION_TAKEN
    );

    private static final Set<String> VALID_FILTER_TARGET_TYPES = Set.of(
            ENTITY_ROLE_APPLICATION,
            ENTITY_LAB_APPROVAL,
            TARGET_TYPE_CONTENT_REPORT,
            SUBTYPE_DISCUSSION_POST,
            SUBTYPE_COMMENT,
            SUBTYPE_EXPERT_ARTICLE,
            SUBTYPE_MARKET_LISTING,
            SUBTYPE_LAB_REVIEW
    );

    private final ModerationTicketRepository moderationTicketRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final IamFacade iamFacade;
    private final @Nullable CommunityContentFacade communityContentFacade;
    private final @Nullable MarketplaceFacade marketplaceFacade;
    private final @Nullable FilmLabFacade filmLabFacade;
    private final @Nullable NotificationFacade notificationFacade;

    @Autowired
    public ContentModerationService(
            ModerationTicketRepository moderationTicketRepository,
            AdminAuditLogService adminAuditLogService,
            IamFacade iamFacade,
            @Nullable CommunityContentFacade communityContentFacade,
            @Nullable MarketplaceFacade marketplaceFacade,
            @Nullable FilmLabFacade filmLabFacade,
            @Nullable NotificationFacade notificationFacade
    ) {
        this.moderationTicketRepository = moderationTicketRepository;
        this.adminAuditLogService = adminAuditLogService;
        this.iamFacade = iamFacade;
        this.communityContentFacade = communityContentFacade;
        this.marketplaceFacade = marketplaceFacade;
        this.filmLabFacade = filmLabFacade;
        this.notificationFacade = notificationFacade;
    }

    /**
     * Ingests {@link ContentReportedEvent} and aggregates repeated reports for the same
     * open target entity into a single hotspot {@link ModerationTicket}.
     */
    @ApplicationModuleListener
    @Transactional
    public void onContentReported(@Nullable ContentReportedEvent event) {
        if (event == null || event.targetId() == null) {
            return;
        }
        String subType = normalizeTargetSubType(event.targetType());
        UUID reporterId = event.reporterId() != null ? event.reporterId() : event.targetId();

        Optional<ModerationTicket> existingOpt = moderationTicketRepository
                .findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(subType, event.targetId())
                .filter(ticket -> isOpenStatus(ticket.getStatus()));

        if (existingOpt.isPresent()) {
            ModerationTicket existing = existingOpt.get();
            ParsedIssueMetadata meta = parseIssueMetadata(existing.getIssueType(), existing.getReportCount());
            int newCount = Math.max(1, meta.reportCount()) + 1;
            List<String> updatedReasons = mergeReasons(meta.reasons(), event.reason());
            UUID latestReportId = event.reportId() != null ? event.reportId() : meta.latestReportId();

            existing.setStatus(STATUS_OPEN);
            existing.setReportCount(newCount);
            existing.setIssueType(encodeIssueMetadata(newCount, latestReportId, updatedReasons));
            moderationTicketRepository.save(existing);
            return;
        }

        List<String> reasons = mergeReasons(List.of(), event.reason());
        ModerationTicket created = ModerationTicket.builder()
                .entityType(subType)
                .entityId(event.targetId())
                .reportedBy(reporterId)
                .issueType(encodeIssueMetadata(1, event.reportId(), reasons))
                .status(STATUS_OPEN)
                .reportCount(1)
                .build();
        moderationTicketRepository.save(created);
    }

    /**
     * Lists and enriches moderation tickets filtered by status, targetType and minimum reportCount (API 8).
     */
    @Transactional(readOnly = true)
    public PageResponse<ModerationTicketAdminDto> listModerationTickets(
            @Nullable String status,
            @Nullable String targetType,
            int minReportCount,
            int page,
            int size
    ) {
        if (minReportCount < 1) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Số lượng báo cáo tối thiểu phải lớn hơn hoặc bằng 1."
            );
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ.");
        }

        String normalizedStatus = status != null && !status.isBlank()
                ? status.trim().toUpperCase(Locale.ROOT)
                : STATUS_OPEN;
        if (!VALID_FILTER_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trạng thái lọc phiếu kiểm duyệt không hợp lệ: " + status
            );
        }

        String normalizedTargetType = null;
        if (targetType != null && !targetType.isBlank()) {
            normalizedTargetType = targetType.trim().toUpperCase(Locale.ROOT);
            if (!VALID_FILTER_TARGET_TYPES.contains(normalizedTargetType)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Loại đối tượng lọc phiếu kiểm duyệt không hợp lệ: " + targetType
                );
            }
        }

        final String targetFilter = normalizedTargetType;
        List<TicketWithMeta> matching = moderationTicketRepository.findAll().stream()
                .filter(ticket -> matchesStatusFilter(ticket.getStatus(), normalizedStatus))
                .filter(ticket -> matchesTargetTypeFilter(ticket.getEntityType(), targetFilter))
                .map(ticket -> new TicketWithMeta(
                        ticket,
                        parseIssueMetadata(ticket.getIssueType(), ticket.getReportCount())
                ))
                .filter(entry -> entry.meta().reportCount() >= minReportCount)
                .sorted(Comparator
                        .comparingInt((TicketWithMeta entry) -> entry.meta().reportCount()).reversed()
                        .thenComparing(
                                entry -> entry.ticket().getCreatedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .toList();

        int totalElements = matching.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<TicketWithMeta> pageSlice = matching.subList(fromIndex, toIndex);

        List<ContentSummaryDraft> drafts = pageSlice.stream()
                .map(this::buildContentSummaryDraft)
                .toList();

        Set<UUID> authorIds = drafts.stream()
                .map(ContentSummaryDraft::authorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> usersById = authorIds.isEmpty()
                ? Map.of()
                : iamFacade.findUsersByIds(authorIds);

        List<ModerationTicketAdminDto> items = new ArrayList<>(pageSlice.size());
        for (int i = 0; i < pageSlice.size(); i++) {
            TicketWithMeta entry = pageSlice.get(i);
            ContentSummaryDraft draft = drafts.get(i);
            ModerationTicket ticket = entry.ticket();
            String subType = ticket.getEntityType();
            String topLevelType = toApiTargetType(subType);

            String authorFullName = draft.authorUserId() != null && usersById.containsKey(draft.authorUserId())
                    ? usersById.get(draft.authorUserId()).fullName()
                    : null;

            ModerationTicketAdminDto.ReportedContentSummary summary =
                    new ModerationTicketAdminDto.ReportedContentSummary(
                            draft.title(),
                            draft.authorUserId(),
                            authorFullName,
                            draft.currentModerationStatus()
                    );

            items.add(new ModerationTicketAdminDto(
                    ticket.getId(),
                    topLevelType,
                    ticket.getEntityId(),
                    subType,
                    summary,
                    entry.meta().reportCount(),
                    entry.meta().reasons(),
                    ticket.getStatus(),
                    ticket.getAssignedAdminId(),
                    ticket.getResolutionNote(),
                    ticket.getCreatedAt(),
                    ticket.getCreatedAt()
            ));
        }

        return PageResponse.from(items, page, size, totalElements, totalPages);
    }

    /**
     * Resolves an open moderation ticket synchronously across downstream Facades
     * and records an immutable AuditLog entry via {@link AdminAudited} (API 9).
     */
    @Transactional
    @AdminAudited(
            actionType = "MODERATE_TICKET",
            targetEntity = "ADM_MODERATION_TICKET",
            targetIdParam = "ticketId"
    )
    public ModerationTicketResolutionResponse resolveModerationTicket(
            UUID ticketId,
            ResolveModerationTicketRequest request
    ) {
        if (ticketId == null || request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin xử lý phiếu kiểm duyệt không hợp lệ.");
        }
        UUID adminId = SecurityContextUtils.getRequiredCurrentUserId();

        ModerationAction action = request.moderationAction();
        if (action == ModerationAction.HIDE_CONTENT_AND_SUSPEND_USER && request.suspensionDays() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Phải chỉ định suspensionDays (1-365) khi chọn HIDE_CONTENT_AND_SUSPEND_USER."
            );
        }

        ModerationTicket ticket = moderationTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu kiểm duyệt", ticketId));

        String previousStatus = ticket.getStatus();
        if (!isOpenStatus(previousStatus)) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Phiếu kiểm duyệt này đã được xử lý trước đó (trạng thái: " + previousStatus + ")."
            );
        }

        String subType = ticket.getEntityType();
        UUID targetId = ticket.getEntityId();
        String note = request.resolutionNote().trim();
        ParsedIssueMetadata meta = parseIssueMetadata(ticket.getIssueType(), ticket.getReportCount());

        UUID violatorUserId = resolveTargetAuthorUserId(subType, targetId, request.violatorUserId());
        boolean contentHidden = false;
        boolean removedFromSearchIndex = false;
        boolean removedFromVectorIndex = false;
        String newAccountStatus = null;
        boolean allActiveSessionsRevoked = false;
        String newTicketStatus;

        switch (action) {
            case DISMISS_REPORTS -> {
                if (communityContentFacade != null && meta.latestReportId() != null) {
                    communityContentFacade.markReportResolved(meta.latestReportId(), "DISMISSED");
                }
                newTicketStatus = STATUS_REJECTED;
            }
            case APPROVE_ARTICLE_FOR_RAG -> {
                requireCommunityFacade().moderateContent(
                        SUBTYPE_EXPERT_ARTICLE,
                        targetId,
                        "APPROVE_FOR_RAG",
                        note
                );
                if (meta.latestReportId() != null) {
                    requireCommunityFacade().markReportResolved(meta.latestReportId(), STATUS_APPROVED);
                }
                removedFromSearchIndex = false;
                removedFromVectorIndex = false;
                newTicketStatus = STATUS_APPROVED;
            }
            case HIDE_CONTENT, HIDE_CONTENT_AND_SUSPEND_USER, HIDE_CONTENT_AND_BAN_USER -> {
                if (SUBTYPE_MARKET_LISTING.equalsIgnoreCase(subType)) {
                    requireMarketplaceFacade().hideListingByAdmin(targetId, note);
                    removedFromSearchIndex = true;
                    removedFromVectorIndex = true;
                } else if (SUBTYPE_LAB_REVIEW.equalsIgnoreCase(subType)) {
                    Optional<FilmLabFacade.LabReviewModerationInfo> hiddenReview =
                            requireFilmLabFacade().hideReviewByAdmin(targetId);
                    if (violatorUserId == null && hiddenReview.isPresent()) {
                        violatorUserId = hiddenReview.get().authorUserId();
                    }
                } else {
                    requireCommunityFacade().moderateContent(subType, targetId, "HIDE_CONTENT", note);
                    if (meta.latestReportId() != null) {
                        requireCommunityFacade().markReportResolved(meta.latestReportId(), STATUS_ACTION_TAKEN);
                    }
                    removedFromVectorIndex = SUBTYPE_EXPERT_ARTICLE.equalsIgnoreCase(subType);
                }

                contentHidden = true;
                newTicketStatus = STATUS_ACTION_TAKEN;

                if (action == ModerationAction.HIDE_CONTENT_AND_SUSPEND_USER) {
                    if (violatorUserId == null) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "Không xác định được tài khoản vi phạm để tạm đình chỉ."
                        );
                    }
                    String suspendReason = note + " (Tạm khóa " + request.suspensionDays() + " ngày)";
                    iamFacade.updateAccountStatus(violatorUserId, AccountStatus.SUSPENDED, adminId, suspendReason);
                    newAccountStatus = AccountStatus.SUSPENDED.name();
                    allActiveSessionsRevoked = true;
                } else if (action == ModerationAction.HIDE_CONTENT_AND_BAN_USER) {
                    if (violatorUserId == null) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "Không xác định được tài khoản vi phạm để khóa vĩnh viễn."
                        );
                    }
                    iamFacade.updateAccountStatus(violatorUserId, AccountStatus.BANNED, adminId, note);
                    newAccountStatus = AccountStatus.BANNED.name();
                    allActiveSessionsRevoked = true;
                }
            }
            default -> throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Hành động kiểm duyệt không được hỗ trợ: " + action
            );
        }

        ticket.setStatus(newTicketStatus);
        ticket.setAssignedAdminId(adminId);
        ticket.setResolutionNote(note);
        moderationTicketRepository.save(ticket);

        if (notificationFacade != null && violatorUserId != null && contentHidden) {
            notificationFacade.sendSystemAlert(
                    violatorUserId,
                    "Thông báo xử lý vi phạm nội dung",
                    "Nội dung (" + subType + ") của bạn đã bị ẩn bởi Quản trị viên. Lý do: " + note
            );
        }

        ModerationTicketResolutionResponse response = new ModerationTicketResolutionResponse(
                ticket.getId(),
                toApiTargetType(subType),
                targetId,
                subType,
                previousStatus,
                newTicketStatus,
                action,
                new ModerationTicketResolutionResponse.DownstreamEffects(
                        contentHidden,
                        removedFromSearchIndex,
                        removedFromVectorIndex,
                        violatorUserId,
                        newAccountStatus,
                        allActiveSessionsRevoked
                ),
                adminId,
                null,
                Instant.now(),
                note
        );

        // When invoked directly without AOP proxy (e.g., plain unit call), still append immutable audit log.
        if (!AdminAuditLogAspect.isAspectActive()) {
            AuditLog auditLog = adminAuditLogService.recordAuditLog(
                    adminId,
                    "MODERATE_TICKET",
                    "ADM_MODERATION_TICKET",
                    ticket.getId(),
                    response.toAuditOldValues(),
                    response.toAuditNewValues(),
                    null
            );
            response = response.withAuditLogId(auditLog.getId());
        }

        return response;
    }

    private CommunityContentFacade requireCommunityFacade() {
        if (communityContentFacade == null) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Dịch vụ kiểm duyệt cộng đồng (CommunityContentFacade) hiện chưa sẵn sàng."
            );
        }
        return communityContentFacade;
    }

    private MarketplaceFacade requireMarketplaceFacade() {
        if (marketplaceFacade == null) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Dịch vụ kiểm duyệt sàn thiết bị (MarketplaceFacade) hiện chưa sẵn sàng."
            );
        }
        return marketplaceFacade;
    }

    private FilmLabFacade requireFilmLabFacade() {
        if (filmLabFacade == null) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Dịch vụ kiểm duyệt đánh giá Film Lab (FilmLabFacade) hiện chưa sẵn sàng."
            );
        }
        return filmLabFacade;
    }

    private @Nullable UUID resolveTargetAuthorUserId(
            String subType,
            UUID targetId,
            @Nullable UUID fallbackAuthorId
    ) {
        if (SUBTYPE_MARKET_LISTING.equalsIgnoreCase(subType) && marketplaceFacade != null) {
            Optional<MarketplaceListingDto> listing = marketplaceFacade.getListingById(targetId);
            if (listing.isPresent() && listing.get().sellerUserId() != null) {
                return listing.get().sellerUserId();
            }
        }
        if (SUBTYPE_LAB_REVIEW.equalsIgnoreCase(subType) && filmLabFacade != null) {
            Optional<FilmLabFacade.LabReviewModerationInfo> review = filmLabFacade.findReviewById(targetId);
            if (review.isPresent() && review.get().authorUserId() != null) {
                return review.get().authorUserId();
            }
        }
        if (SUBTYPE_EXPERT_ARTICLE.equalsIgnoreCase(subType) && communityContentFacade != null) {
            Optional<ExpertArticleDto> article = communityContentFacade.getArticleById(targetId);
            if (article.isPresent() && article.get().authorExpertId() != null) {
                return article.get().authorExpertId();
            }
        }
        return fallbackAuthorId;
    }

    private ContentSummaryDraft buildContentSummaryDraft(TicketWithMeta entry) {
        ModerationTicket ticket = entry.ticket();
        String subType = ticket.getEntityType();
        UUID targetId = ticket.getEntityId();

        if (SUBTYPE_MARKET_LISTING.equalsIgnoreCase(subType) && marketplaceFacade != null) {
            Optional<MarketplaceListingDto> listing = marketplaceFacade.getListingById(targetId);
            if (listing.isPresent()) {
                return new ContentSummaryDraft(
                        listing.get().title(),
                        listing.get().sellerUserId(),
                        listing.get().status()
                );
            }
        }
        if (SUBTYPE_LAB_REVIEW.equalsIgnoreCase(subType) && filmLabFacade != null) {
            Optional<FilmLabFacade.LabReviewModerationInfo> review = filmLabFacade.findReviewById(targetId);
            if (review.isPresent()) {
                return new ContentSummaryDraft(
                        review.get().comment(),
                        review.get().authorUserId(),
                        review.get().moderationStatus()
                );
            }
        }
        if (SUBTYPE_EXPERT_ARTICLE.equalsIgnoreCase(subType) && communityContentFacade != null) {
            Optional<ExpertArticleDto> article = communityContentFacade.getArticleById(targetId);
            if (article.isPresent()) {
                return new ContentSummaryDraft(
                        article.get().title(),
                        article.get().authorExpertId(),
                        article.get().status()
                );
            }
        }
        return new ContentSummaryDraft(null, null, null);
    }

    private static boolean matchesStatusFilter(@Nullable String ticketStatus, String filterStatus) {
        if (ticketStatus == null) {
            return false;
        }
        if (STATUS_OPEN.equals(filterStatus)) {
            return isOpenStatus(ticketStatus);
        }
        return filterStatus.equalsIgnoreCase(ticketStatus);
    }

    private static boolean matchesTargetTypeFilter(@Nullable String entityType, @Nullable String targetFilter) {
        if (entityType == null) {
            return false;
        }
        if (targetFilter == null) {
            return !ENTITY_ROLE_APPLICATION.equalsIgnoreCase(entityType)
                    && !ENTITY_LAB_APPROVAL.equalsIgnoreCase(entityType);
        }
        if (TARGET_TYPE_CONTENT_REPORT.equals(targetFilter)) {
            return !ENTITY_ROLE_APPLICATION.equalsIgnoreCase(entityType)
                    && !ENTITY_LAB_APPROVAL.equalsIgnoreCase(entityType);
        }
        return targetFilter.equalsIgnoreCase(entityType);
    }

    private static boolean isOpenStatus(@Nullable String status) {
        return STATUS_OPEN.equalsIgnoreCase(status) || STATUS_PENDING.equalsIgnoreCase(status);
    }

    private static String toApiTargetType(String entityType) {
        if (ENTITY_ROLE_APPLICATION.equalsIgnoreCase(entityType)
                || ENTITY_LAB_APPROVAL.equalsIgnoreCase(entityType)) {
            return entityType.toUpperCase(Locale.ROOT);
        }
        return TARGET_TYPE_CONTENT_REPORT;
    }

    private static String normalizeTargetSubType(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return TARGET_TYPE_CONTENT_REPORT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    private static ParsedIssueMetadata parseIssueMetadata(@Nullable String issueType, int fallbackCount) {
        int count = Math.max(1, fallbackCount);
        UUID reportId = null;
        List<String> reasons = new ArrayList<>();

        if (issueType == null || issueType.isBlank()) {
            return new ParsedIssueMetadata(count, null, List.of());
        }

        if (issueType.startsWith("CNT:")) {
            String[] parts = issueType.split("\\|", 3);
            try {
                count = Math.max(count, Integer.parseInt(parts[0].substring(4).trim()));
            } catch (NumberFormatException ignored) {
                // ignore malformed prefix
            }
            if (parts.length > 1 && !parts[1].isBlank()) {
                try {
                    reportId = UUID.fromString(parts[1].trim());
                } catch (IllegalArgumentException ignored) {
                    // ignore malformed UUID
                }
            }
            if (parts.length > 2 && !parts[2].isBlank()) {
                Arrays.stream(parts[2].split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(reasons::add);
            }
            return new ParsedIssueMetadata(count, reportId, List.copyOf(reasons));
        }

        reasons.add(issueType.trim());
        return new ParsedIssueMetadata(count, null, List.copyOf(reasons));
    }

    private static List<String> mergeReasons(List<String> existing, @Nullable String newReason) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(existing);
        if (newReason != null && !newReason.isBlank()) {
            merged.add(newReason.trim());
        }
        return List.copyOf(merged);
    }

    private static String encodeIssueMetadata(
            int count,
            @Nullable UUID reportId,
            List<String> reasons
    ) {
        String prefix = "CNT:" + count + "|" + (reportId != null ? reportId : "") + "|";
        String reasonsJoined = String.join(",", reasons);
        int maxReasonsLen = Math.max(0, 100 - prefix.length());
        if (reasonsJoined.length() > maxReasonsLen) {
            reasonsJoined = reasonsJoined.substring(0, maxReasonsLen);
        }
        return prefix + reasonsJoined;
    }

    private record TicketWithMeta(ModerationTicket ticket, ParsedIssueMetadata meta) {
    }

    private record ParsedIssueMetadata(int reportCount, @Nullable UUID latestReportId, List<String> reasons) {
    }

    private record ContentSummaryDraft(
            @Nullable String title,
            @Nullable UUID authorUserId,
            @Nullable String currentModerationStatus
    ) {
    }
}
