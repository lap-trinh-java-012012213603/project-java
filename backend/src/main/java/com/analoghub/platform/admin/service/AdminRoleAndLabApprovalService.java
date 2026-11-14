package com.analoghub.platform.admin.service;

import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.ModerationTicket;
import com.analoghub.platform.admin.dto.ApprovalDecision;
import com.analoghub.platform.admin.dto.DecideRoleApplicationRequest;
import com.analoghub.platform.admin.dto.RoleApplicationAdminDto;
import com.analoghub.platform.admin.dto.RoleApplicationDecisionResponse;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.ModerationTicketRepository;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.event.RoleApplicationSubmittedEvent;
import com.analoghub.platform.iam.facade.IamFacade;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tier-5 orchestrator handling {@link RoleApplicationSubmittedEvent} ticket ingestion,
 * synchronous RoleApplication review decisions (via {@link IamFacade} and {@link FilmLabFacade}),
 * and standalone Film Lab opening approval with immutable {@link AuditLog} trail.
 */
@Slf4j
@Service
public class AdminRoleAndLabApprovalService {

    private static final String TICKET_ENTITY_ROLE_APP = "ROLE_APPLICATION";
    private static final String TICKET_ENTITY_LAB_APPROVAL = "LAB_APPROVAL";
    private static final String AUDIT_TARGET_ROLE_APP = "IAM_ROLE_APPLICATION";
    private static final String AUDIT_TARGET_LAB = "LAB_FILM_LAB";

    private static final Map<String, String> EVIDENCE_KEY_TO_URL_FIELD = Map.of(
            "labPhotoS3Keys", "labPhotoUrls",
            "evidenceDocumentKeys", "evidenceDocumentUrls",
            "idCardImageKeys", "idCardImageUrls"
    );

    private final IamFacade iamFacade;
    private final FilmLabFacade filmLabFacade;
    private final ModerationTicketRepository moderationTicketRepository;
    private final AuditLogRepository auditLogRepository;
    private final @Nullable StorageService storageService;
    private final @Nullable NotificationFacade notificationFacade;

    public AdminRoleAndLabApprovalService(
            IamFacade iamFacade,
            FilmLabFacade filmLabFacade,
            ModerationTicketRepository moderationTicketRepository,
            AuditLogRepository auditLogRepository
    ) {
        this(iamFacade, filmLabFacade, moderationTicketRepository, auditLogRepository, null, null);
    }

    @Autowired
    public AdminRoleAndLabApprovalService(
            IamFacade iamFacade,
            FilmLabFacade filmLabFacade,
            ModerationTicketRepository moderationTicketRepository,
            AuditLogRepository auditLogRepository,
            @Nullable StorageService storageService,
            @Nullable NotificationFacade notificationFacade
    ) {
        this.iamFacade = iamFacade;
        this.filmLabFacade = filmLabFacade;
        this.moderationTicketRepository = moderationTicketRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageService = storageService;
        this.notificationFacade = notificationFacade;
    }

    @ApplicationModuleListener
    public void onRoleApplicationSubmitted(RoleApplicationSubmittedEvent event) {
        if (event == null || event.applicationId() == null) {
            return;
        }
        Optional<ModerationTicket> existing = moderationTicketRepository
                .findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(TICKET_ENTITY_ROLE_APP, event.applicationId());
        if (existing.isPresent()) {
            log.debug("ModerationTicket already exists for applicationId={}, skipping duplicate creation",
                    event.applicationId());
            return;
        }

        String requestedRole = (event.requestedRole() != null && !event.requestedRole().isBlank())
                ? event.requestedRole().trim().toUpperCase(java.util.Locale.ROOT)
                : "UNKNOWN";
        String issueType = "ROLE_UPGRADE_" + requestedRole;
        ModerationTicket ticket = ModerationTicket.builder()
                .entityType(TICKET_ENTITY_ROLE_APP)
                .entityId(event.applicationId())
                .reportedBy(event.userId())
                .issueType(issueType)
                .status("PENDING")
                .build();
        moderationTicketRepository.save(ticket);
        log.info("Created ModerationTicket for role applicationId={}, userId={}, issueType={}",
                event.applicationId(), event.userId(), issueType);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleApplicationAdminDto> listRoleApplications(
            RoleApplicationStatus status,
            RoleCode requestedRole,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);

        RoleApplicationStatus effectiveStatus = status != null ? status : RoleApplicationStatus.PENDING;
        List<RoleApplicationDto> rawList = iamFacade.getRoleApplicationsByStatus(effectiveStatus);
        if (rawList == null || rawList.isEmpty()) {
            return PageResponse.from(List.of(), safePage, safeSize, 0, 0);
        }

        List<RoleApplicationDto> filtered = rawList.stream()
                .filter(app -> requestedRole == null || requestedRole == app.requestedRole())
                .sorted(Comparator.comparing(
                        RoleApplicationDto::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = safePage * safeSize;
        if (fromIndex >= totalElements) {
            return PageResponse.from(List.of(), safePage, safeSize, totalElements, totalPages);
        }
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<RoleApplicationDto> pageSlice = filtered.subList(fromIndex, toIndex);

        List<UUID> userIds = pageSlice.stream()
                .map(RoleApplicationDto::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserSummaryDto> usersById = userIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(userIds);

        List<UUID> appIds = pageSlice.stream()
                .map(RoleApplicationDto::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UUID> ticketIdsByAppId = new LinkedHashMap<>();
        if (!appIds.isEmpty()) {
            for (ModerationTicket ticket : moderationTicketRepository
                    .findByEntityTypeAndEntityIdIn(TICKET_ENTITY_ROLE_APP, appIds)) {
                ticketIdsByAppId.putIfAbsent(ticket.getEntityId(), ticket.getId());
            }
        }

        List<RoleApplicationAdminDto> items = pageSlice.stream()
                .map(app -> toAdminDto(app, usersById.get(app.userId()), ticketIdsByAppId.get(app.id())))
                .toList();
        return PageResponse.from(items, safePage, safeSize, totalElements, totalPages);
    }

    @Transactional
    public RoleApplicationDecisionResponse decideRoleApplication(
            UUID applicationId,
            DecideRoleApplicationRequest request
    ) {
        if (applicationId == null || request == null || request.decision() == null
                || request.reviewNote() == null || request.reviewNote().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin xét duyệt đơn không hợp lệ.");
        }

        UUID adminUserId = SecurityContextUtils.getRequiredCurrentUserId();
        String reviewNote = request.reviewNote().trim();

        RoleApplicationDto existing = iamFacade.getRoleApplicationById(applicationId);
        if (existing.status() != RoleApplicationStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Đơn xin nâng cấp vai trò này đã được xử lý trước đó (trạng thái hiện tại: "
                            + existing.status() + ")."
            );
        }

        RoleApplicationDto updated;
        RoleCode grantedRoleCode = null;
        FilmLabFacade.ProvisionedLabInfo provisionedLab = null;

        if (request.decision() == ApprovalDecision.APPROVE) {
            updated = iamFacade.approveRoleApplication(applicationId, adminUserId, reviewNote);
            grantedRoleCode = existing.requestedRole();
            if (existing.requestedRole() == RoleCode.LAB_OWNER) {
                provisionedLab = filmLabFacade.provisionLabFromApprovedApplication(
                        existing.userId(),
                        existing.portfolioPayload()
                );
            }
        } else {
            updated = iamFacade.rejectRoleApplication(applicationId, adminUserId, reviewNote);
        }

        String resolvedTicketStatus = request.decision() == ApprovalDecision.APPROVE ? "APPROVED" : "REJECTED";
        ModerationTicket ticket = moderationTicketRepository
                .findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(TICKET_ENTITY_ROLE_APP, applicationId)
                .orElseGet(() -> ModerationTicket.builder()
                        .entityType(TICKET_ENTITY_ROLE_APP)
                        .entityId(applicationId)
                        .reportedBy(existing.userId())
                        .issueType("ROLE_UPGRADE_" + existing.requestedRole().name())
                        .build());
        ticket.setStatus(resolvedTicketStatus);
        ticket.setAssignedAdminId(adminUserId);
        ticket.setResolutionNote(reviewNote);
        moderationTicketRepository.save(ticket);

        Map<String, Object> oldValues = Map.of("status", existing.status().name());
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", updated.status().name());
        newValues.put("decision", request.decision().name());
        newValues.put("reviewNote", reviewNote);
        if (grantedRoleCode != null) {
            newValues.put("grantedRole", grantedRoleCode.name());
        }
        if (provisionedLab != null) {
            newValues.put("provisionedLabId", provisionedLab.labId().toString());
            newValues.put("labStatus", provisionedLab.status());
        }

        AuditLog savedAudit = auditLogRepository.save(AuditLog.builder()
                .adminId(adminUserId)
                .actionType("DECIDE_ROLE_APPLICATION")
                .targetEntity(AUDIT_TARGET_ROLE_APP)
                .targetId(applicationId)
                .oldValues(oldValues)
                .newValues(newValues)
                .build());

        notifyApplicantRoleDecision(existing.userId(), existing.requestedRole(), updated.status(), reviewNote);

        Instant decidedAt = updated.reviewedAt() != null ? updated.reviewedAt() : Instant.now();
        return new RoleApplicationDecisionResponse(
                updated.id(),
                existing.userId(),
                existing.requestedRole(),
                existing.status(),
                updated.status(),
                grantedRoleCode,
                provisionedLab,
                adminUserId,
                reviewNote,
                savedAudit.getId(),
                decidedAt
        );
    }

    @Transactional
    public FilmLabFacade.ProvisionedLabInfo approveLabOpening(UUID labId, String reviewNote) {
        if (labId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "labId không được để trống.");
        }
        UUID adminUserId = SecurityContextUtils.getRequiredCurrentUserId();
        String effectiveNote = (reviewNote != null && !reviewNote.isBlank())
                ? reviewNote.trim()
                : "Approved by Admin";

        FilmLabFacade.ProvisionedLabInfo provisioned = filmLabFacade.approveLabOpening(labId);
        iamFacade.grantRole(provisioned.ownerUserId(), RoleCode.LAB_OWNER);

        ModerationTicket ticket = moderationTicketRepository
                .findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(TICKET_ENTITY_LAB_APPROVAL, labId)
                .orElseGet(() -> ModerationTicket.builder()
                        .entityType(TICKET_ENTITY_LAB_APPROVAL)
                        .entityId(labId)
                        .reportedBy(provisioned.ownerUserId())
                        .issueType("LAB_OPENING_APPROVAL")
                        .build());
        ticket.setStatus("APPROVED");
        ticket.setAssignedAdminId(adminUserId);
        ticket.setResolutionNote(effectiveNote);
        moderationTicketRepository.save(ticket);

        Map<String, Object> oldValues = Map.of("status", "PENDING_APPROVAL");
        Map<String, Object> newValues = Map.of(
                "status", provisioned.status(),
                "ownerUserId", provisioned.ownerUserId().toString(),
                "reviewNote", effectiveNote
        );
        auditLogRepository.save(AuditLog.builder()
                .adminId(adminUserId)
                .actionType("APPROVE_LAB_OPENING")
                .targetEntity(AUDIT_TARGET_LAB)
                .targetId(labId)
                .oldValues(oldValues)
                .newValues(newValues)
                .build());

        notifyLabOpeningApproved(provisioned, effectiveNote);
        return provisioned;
    }

    private RoleApplicationAdminDto toAdminDto(
            RoleApplicationDto app,
            @Nullable UserSummaryDto user,
            @Nullable UUID ticketId
    ) {
        RoleApplicationAdminDto.ApplicantInfo applicant = user != null
                ? new RoleApplicationAdminDto.ApplicantInfo(
                        user.id(),
                        user.email(),
                        user.fullName(),
                        user.phoneNumber(),
                        user.roles() != null ? user.roles() : Set.of()
                )
                : new RoleApplicationAdminDto.ApplicantInfo(app.userId(), null, null, null, Set.of());

        return new RoleApplicationAdminDto(
                app.id(),
                ticketId,
                applicant,
                app.requestedRole(),
                app.status(),
                enrichPortfolioPayloadWithSignedUrls(app.portfolioPayload()),
                app.createdAt(),
                app.reviewedByAdminId(),
                app.reviewNote(),
                app.reviewedAt()
        );
    }

    private Map<String, Object> enrichPortfolioPayloadWithSignedUrls(@Nullable Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        if (storageService == null) {
            return enriched;
        }
        for (Map.Entry<String, String> mapping : EVIDENCE_KEY_TO_URL_FIELD.entrySet()) {
            Object rawKeys = payload.get(mapping.getKey());
            if (rawKeys instanceof Collection<?> keys && !keys.isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (Object keyObj : keys) {
                    if (keyObj == null || keyObj.toString().isBlank()) {
                        continue;
                    }
                    String signedUrl = storageService.resolveReadUrl(keyObj.toString().trim());
                    if (signedUrl != null && !signedUrl.isBlank()) {
                        urls.add(signedUrl);
                    }
                }
                enriched.put(mapping.getValue(), List.copyOf(urls));
            }
        }
        return enriched;
    }

    private void notifyApplicantRoleDecision(
            UUID applicantUserId,
            RoleCode requestedRole,
            RoleApplicationStatus newStatus,
            String reviewNote
    ) {
        if (notificationFacade == null) {
            return;
        }
        String title = newStatus == RoleApplicationStatus.APPROVED
                ? "Đơn nâng cấp vai trò đã được phê duyệt"
                : "Đơn nâng cấp vai trò bị từ chối";
        String body = "Đơn xin nâng cấp vai trò " + requestedRole.name()
                + " của bạn hiện ở trạng thái " + newStatus.name()
                + ". Nhận xét của Admin: " + reviewNote;
        try {
            notificationFacade.sendSystemAlert(applicantUserId, title, body);
        } catch (Exception ex) {
            log.warn("Failed to send role application notification to userId={}: {}",
                    applicantUserId, ex.getMessage());
        }
    }

    private void notifyLabOpeningApproved(FilmLabFacade.ProvisionedLabInfo provisioned, String reviewNote) {
        if (notificationFacade == null) {
            return;
        }
        String title = "Hồ sơ Film Lab đã được kích hoạt";
        String body = "Film Lab '" + provisioned.name() + "' đã chuyển sang trạng thái "
                + provisioned.status() + ". Nhận xét: " + reviewNote;
        try {
            notificationFacade.sendSystemAlert(provisioned.ownerUserId(), title, body);
        } catch (Exception ex) {
            log.warn("Failed to send lab approval notification to ownerUserId={}: {}",
                    provisioned.ownerUserId(), ex.getMessage());
        }
    }
}
