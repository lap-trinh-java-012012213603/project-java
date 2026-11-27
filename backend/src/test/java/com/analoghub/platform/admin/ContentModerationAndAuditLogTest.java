package com.analoghub.platform.admin;

import com.analoghub.platform.admin.audit.AdminAuditLogAspect;
import com.analoghub.platform.admin.controller.AdminAuditLogController;
import com.analoghub.platform.admin.controller.AdminModerationController;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.ModerationTicket;
import com.analoghub.platform.admin.dto.ModerationAction;
import com.analoghub.platform.admin.dto.ResolveModerationTicketRequest;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.ModerationTicketRepository;
import com.analoghub.platform.admin.service.AdminAuditLogService;
import com.analoghub.platform.admin.service.ContentModerationService;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.community.dto.ExpertArticleDto;
import com.analoghub.platform.community.event.ContentReportedEvent;
import com.analoghub.platform.community.facade.CommunityContentFacade;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.security.AuthenticatedUser;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentModerationAndAuditLogTest {

    @Mock
    private ModerationTicketRepository moderationTicketRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private CommunityContentFacade communityContentFacade;

    @Mock
    private MarketplaceFacade marketplaceFacade;

    @Mock
    private FilmLabFacade filmLabFacade;

    @Mock
    private NotificationFacade notificationFacade;

    private final Map<UUID, ModerationTicket> ticketStore = new LinkedHashMap<>();
    private final List<AuditLog> auditLogStore = new ArrayList<>();

    private AdminAuditLogService adminAuditLogService;
    private ContentModerationService proxiedModerationService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        ticketStore.clear();
        auditLogStore.clear();

        adminUserId = UUID.randomUUID();
        AuthenticatedUser adminPrincipal = AuthenticatedUser.builder()
                .userId(adminUserId)
                .jti(UUID.randomUUID().toString())
                .roles(Set.of("ADMIN"))
                .labIds(Set.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities())
        );

        objectMapper = new ObjectMapper();

        when(moderationTicketRepository.save(any(ModerationTicket.class))).thenAnswer(inv -> {
            ModerationTicket ticket = inv.getArgument(0);
            if (ticket.getId() == null) {
                ticket.setId(UUID.randomUUID());
            }
            if (ticket.getCreatedAt() == null) {
                ticket.setCreatedAt(Instant.now());
            }
            ticketStore.put(ticket.getId(), ticket);
            return ticket;
        });
        when(moderationTicketRepository.findById(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(ticketStore.get(inv.getArgument(0)))
        );
        when(moderationTicketRepository.findAll()).thenAnswer(inv -> new ArrayList<>(ticketStore.values()));
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(inv -> {
                    String entityType = inv.getArgument(0);
                    UUID entityId = inv.getArgument(1);
                    return ticketStore.values().stream()
                            .filter(t -> entityType.equals(t.getEntityType()) && entityId.equals(t.getEntityId()))
                            .max(Comparator.comparing(ModerationTicket::getCreatedAt));
                });

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog entry = inv.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(Instant.now());
            }
            auditLogStore.add(entry);
            return entry;
        });
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            List<AuditLog> sorted = auditLogStore.stream()
                    .sorted(Comparator.comparing(AuditLog::getCreatedAt).reversed())
                    .toList();
            return (Page<AuditLog>) new PageImpl<>(sorted, pageable, sorted.size());
        });

        adminAuditLogService = new AdminAuditLogService(auditLogRepository, iamFacade);
        ContentModerationService targetService = new ContentModerationService(
                moderationTicketRepository,
                adminAuditLogService,
                iamFacade,
                communityContentFacade,
                marketplaceFacade,
                filmLabFacade,
                notificationFacade
        );

        AdminAuditLogAspect aspect = new AdminAuditLogAspect(adminAuditLogService, objectMapper);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(targetService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(aspect);
        proxiedModerationService = proxyFactory.getProxy();

        AdminModerationController moderationController = new AdminModerationController(proxiedModerationService);
        AdminAuditLogController auditLogController = new AdminAuditLogController(adminAuditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(moderationController, auditLogController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("onContentReported aggregates repeated reports onto one OPEN ticket and sorts hotspot queue by reportCount DESC")
    void shouldAggregateReportedContentAndSortQueueByReportCount() throws Exception {
        UUID hotListingId = UUID.randomUUID();
        UUID normalPostId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID report1 = UUID.randomUUID();
        UUID report2 = UUID.randomUUID();
        UUID report3 = UUID.randomUUID();

        proxiedModerationService.onContentReported(new ContentReportedEvent(
                UUID.randomUUID(), "DISCUSSION_POST", normalPostId, UUID.randomUUID(), "SPAM_ADVERTISING"
        ));
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                report1, "MARKET_LISTING", hotListingId, UUID.randomUUID(), "SCAM_PREPAYMENT"
        ));
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                report2, "MARKET_LISTING", hotListingId, UUID.randomUUID(), "STOLEN_PHOTOS"
        ));
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                report3, "MARKET_LISTING", hotListingId, UUID.randomUUID(), "SCAM_PREPAYMENT"
        ));

        assertThat(ticketStore).hasSize(2);
        ModerationTicket hotTicket = ticketStore.values().stream()
                .filter(t -> hotListingId.equals(t.getEntityId()))
                .findFirst()
                .orElseThrow();
        assertThat(hotTicket.getStatus()).isEqualTo("OPEN");
        assertThat(hotTicket.getReportCount()).isEqualTo(3);

        when(marketplaceFacade.getListingById(hotListingId)).thenReturn(Optional.of(new MarketplaceListingDto(
                hotListingId, sellerId, "leica-m6", "Leica M6 giá rẻ bất thường",
                "desc", "RANGEFINDER", "Leica", "M6", "A+",
                new BigDecimal("12000000"), true, false, "79", "760", "ACTIVE"
        )));
        when(iamFacade.findUsersByIds(Set.of(sellerId))).thenReturn(Map.of(
                sellerId,
                new UserSummaryDto(sellerId, "seller@analoghub.vn", "0909", "Lê Văn Tùng",
                        null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.CUSTOMER), Map.of(), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/admin/moderation/tickets")
                        .param("status", "OPEN")
                        .param("targetType", "CONTENT_REPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item.length()").value(2))
                .andExpect(jsonPath("$.data.item[0].targetId").value(hotListingId.toString()))
                .andExpect(jsonPath("$.data.item[0].reportCount").value(3))
                .andExpect(jsonPath("$.data.item[0].reportedContentSubType").value("MARKET_LISTING"))
                .andExpect(jsonPath("$.data.item[0].reportedContentSummary.authorFullName").value("Lê Văn Tùng"))
                .andExpect(jsonPath("$.data.item[0].topReasonCodes[0]").value("SCAM_PREPAYMENT"))
                .andExpect(jsonPath("$.data.item[1].targetId").value(normalPostId.toString()))
                .andExpect(jsonPath("$.data.item[1].reportCount").value(1));
    }

    @Test
    @DisplayName("Admin hides violating community post -> CommunityContentFacade hides post and AdminAuditLogAspect persists immutable AuditLog")
    void shouldHideViolatingPostAndRecordImmutableAuditLogViaAspect() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();

        proxiedModerationService.onContentReported(new ContentReportedEvent(
                reportId, "DISCUSSION_POST", postId, UUID.randomUUID(), "HATE_SPEECH"
        ));
        ModerationTicket ticket = ticketStore.values().iterator().next();

        ResolveModerationTicketRequest request = new ResolveModerationTicketRequest(
                ModerationAction.HIDE_CONTENT,
                null,
                "Bài viết chứa ngôn từ kích động thù địch và công kích cá nhân, đã ẩn khỏi trang chủ."
        );

        mockMvc.perform(patch("/api/v1/admin/moderation/tickets/{id}", ticket.getId())
                        .header("Idempotency-Key", "idem-hide-post-1")
                        .header("X-Forwarded-For", "118.69.182.45, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.data.previousTicketStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.newTicketStatus").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.data.downstreamEffects.contentHidden").value(true))
                .andExpect(jsonPath("$.data.auditLogId").isNotEmpty());

        verify(communityContentFacade).moderateContent(
                eq("DISCUSSION_POST"),
                eq(postId),
                eq("HIDE_CONTENT"),
                contains("ngôn từ kích động")
        );
        verify(communityContentFacade).markReportResolved(reportId, "ACTION_TAKEN");

        assertThat(ticket.getStatus()).isEqualTo("ACTION_TAKEN");
        assertThat(ticket.getAssignedAdminId()).isEqualTo(adminUserId);

        assertThat(auditLogStore).hasSize(1);
        AuditLog savedAudit = auditLogStore.getFirst();
        assertThat(savedAudit.getAdminId()).isEqualTo(adminUserId);
        assertThat(savedAudit.getActionType()).isEqualTo("MODERATE_TICKET");
        assertThat(savedAudit.getTargetEntity()).isEqualTo("ADM_MODERATION_TICKET");
        assertThat(savedAudit.getTargetId()).isEqualTo(ticket.getId());
        assertThat(savedAudit.getIpAddress()).isEqualTo("118.69.182.45");
        assertThat(savedAudit.getOldValues()).containsEntry("status", "OPEN");
        assertThat(savedAudit.getNewValues())
                .containsEntry("status", "ACTION_TAKEN")
                .containsEntry("moderationAction", "HIDE_CONTENT")
                .containsEntry("contentHidden", true);
    }

    @Test
    @DisplayName("Admin hides scam MARKET_LISTING and suspends seller account via MarketplaceFacade and IamFacade")
    void shouldHideScamListingAndSuspendSeller() throws Exception {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        proxiedModerationService.onContentReported(new ContentReportedEvent(
                UUID.randomUUID(), "MARKET_LISTING", listingId, UUID.randomUUID(), "SCAM_PREPAYMENT"
        ));
        ModerationTicket ticket = ticketStore.values().iterator().next();

        when(marketplaceFacade.getListingById(listingId)).thenReturn(Optional.of(new MarketplaceListingDto(
                listingId, sellerId, "leica-m6", "Leica M6 Fake",
                "desc", "RANGEFINDER", "Leica", "M6", "A+",
                new BigDecimal("12000000"), true, false, "79", "760", "ACTIVE"
        )));

        ResolveModerationTicketRequest request = new ResolveModerationTicketRequest(
                ModerationAction.HIDE_CONTENT_AND_SUSPEND_USER,
                30,
                "Tin đăng sử dụng ảnh đánh cắp và yêu cầu đặt cọc ngoài Escrow. Ẩn tin và khóa tài khoản 30 ngày."
        );

        mockMvc.perform(patch("/api/v1/admin/moderation/tickets/{id}", ticket.getId())
                        .header("Idempotency-Key", "idem-hide-listing-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newTicketStatus").value("ACTION_TAKEN"))
                .andExpect(jsonPath("$.data.downstreamEffects.contentHidden").value(true))
                .andExpect(jsonPath("$.data.downstreamEffects.removedFromSearchIndex").value(true))
                .andExpect(jsonPath("$.data.downstreamEffects.removedFromVectorIndex").value(true))
                .andExpect(jsonPath("$.data.downstreamEffects.violatorUserId").value(sellerId.toString()))
                .andExpect(jsonPath("$.data.downstreamEffects.newAccountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.downstreamEffects.allActiveSessionsRevoked").value(true));

        verify(marketplaceFacade).hideListingByAdmin(eq(listingId), contains("đặt cọc ngoài Escrow"));
        verify(iamFacade).updateAccountStatus(
                eq(sellerId),
                eq(AccountStatus.SUSPENDED),
                eq(adminUserId),
                contains("30 ngày")
        );
        verify(notificationFacade).sendSystemAlert(eq(sellerId), any(), contains("MARKET_LISTING"));
        assertThat(auditLogStore).hasSize(1);
    }

    @Test
    @DisplayName("Supports LAB_REVIEW ban, APPROVE_ARTICLE_FOR_RAG, and DISMISS_REPORTS workflows")
    void shouldHandleLabReviewBanRagApprovalAndReportDismissal() {
        // 1. LAB_REVIEW + BAN
        UUID reviewId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                UUID.randomUUID(), "LAB_REVIEW", reviewId, UUID.randomUUID(), "ABUSIVE_HARASSMENT"
        ));
        ModerationTicket reviewTicket = ticketStore.values().stream()
                .filter(t -> reviewId.equals(t.getEntityId()))
                .findFirst()
                .orElseThrow();

        when(filmLabFacade.findReviewById(reviewId)).thenReturn(Optional.of(
                new FilmLabFacade.LabReviewModerationInfo(reviewId, UUID.randomUUID(), reviewerId, (short) 1, "Spam", "PUBLISHED")
        ));
        when(filmLabFacade.hideReviewByAdmin(reviewId)).thenReturn(Optional.of(
                new FilmLabFacade.LabReviewModerationInfo(reviewId, UUID.randomUUID(), reviewerId, (short) 1, "Spam", "HIDDEN")
        ));

        var banRes = proxiedModerationService.resolveModerationTicket(
                reviewTicket.getId(),
                new ResolveModerationTicketRequest(
                        ModerationAction.HIDE_CONTENT_AND_BAN_USER,
                        null,
                        "Đánh giá chứa nội dung quấy rối nghiêm trọng, ẩn đánh giá và khóa vĩnh viễn tài khoản."
                )
        );
        assertThat(banRes.newTicketStatus()).isEqualTo("ACTION_TAKEN");
        assertThat(banRes.downstreamEffects().newAccountStatus()).isEqualTo("BANNED");
        verify(filmLabFacade).hideReviewByAdmin(reviewId);
        verify(iamFacade).updateAccountStatus(eq(reviewerId), eq(AccountStatus.BANNED), eq(adminUserId), any());

        // 2. APPROVE_ARTICLE_FOR_RAG
        UUID articleId = UUID.randomUUID();
        UUID articleReportId = UUID.randomUUID();
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                articleReportId, "EXPERT_ARTICLE", articleId, UUID.randomUUID(), "CHECK_RAG"
        ));
        ModerationTicket articleTicket = ticketStore.values().stream()
                .filter(t -> articleId.equals(t.getEntityId()))
                .findFirst()
                .orElseThrow();
        when(communityContentFacade.getArticleById(articleId)).thenReturn(Optional.of(
                new ExpertArticleDto(articleId, UUID.randomUUID(), "c41-guide", "C41 Guide", "# MD", "DEV", "PUBLISHED", false)
        ));

        var approveRes = proxiedModerationService.resolveModerationTicket(
                articleTicket.getId(),
                new ResolveModerationTicketRequest(
                        ModerationAction.APPROVE_ARTICLE_FOR_RAG,
                        null,
                        "Bài viết đạt chuẩn kỹ thuật buồng tối AnalogHub, phê duyệt nạp vào kho tri thức RAG."
                )
        );
        assertThat(approveRes.newTicketStatus()).isEqualTo("APPROVED");
        verify(communityContentFacade).moderateContent(eq("EXPERT_ARTICLE"), eq(articleId), eq("APPROVE_FOR_RAG"), any());
        verify(communityContentFacade).markReportResolved(articleReportId, "APPROVED");

        // 3. DISMISS_REPORTS
        UUID cleanPostId = UUID.randomUUID();
        UUID dismissReportId = UUID.randomUUID();
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                dismissReportId, "DISCUSSION_POST", cleanPostId, UUID.randomUUID(), "FALSE_REPORT"
        ));
        ModerationTicket dismissTicket = ticketStore.values().stream()
                .filter(t -> cleanPostId.equals(t.getEntityId()))
                .findFirst()
                .orElseThrow();

        var dismissRes = proxiedModerationService.resolveModerationTicket(
                dismissTicket.getId(),
                new ResolveModerationTicketRequest(
                        ModerationAction.DISMISS_REPORTS,
                        null,
                        "Đã kiểm tra nội dung hoàn toàn hợp lệ, bác bỏ báo cáo sai sự thật từ cộng đồng."
                )
        );
        assertThat(dismissRes.newTicketStatus()).isEqualTo("REJECTED");
        assertThat(dismissRes.downstreamEffects().contentHidden()).isFalse();
        verify(communityContentFacade).markReportResolved(dismissReportId, "DISMISSED");
        assertThat(auditLogStore).hasSize(3);
    }

    @Test
    @DisplayName("Validation, missing ticket (404) and already-closed ticket (409) write zero audit logs")
    void shouldRejectInvalidAndClosedTicketsWithoutWritingAuditLog() throws Exception {
        UUID postId = UUID.randomUUID();
        proxiedModerationService.onContentReported(new ContentReportedEvent(
                UUID.randomUUID(), "DISCUSSION_POST", postId, UUID.randomUUID(), "SPAM"
        ));
        ModerationTicket ticket = ticketStore.values().iterator().next();

        // Missing suspensionDays for HIDE_CONTENT_AND_SUSPEND_USER -> 400
        mockMvc.perform(patch("/api/v1/admin/moderation/tickets/{id}", ticket.getId())
                        .header("Idempotency-Key", "idem-err-400")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResolveModerationTicketRequest(
                                ModerationAction.HIDE_CONTENT_AND_SUSPEND_USER,
                                null,
                                "Thiếu số ngày tạm đình chỉ nên phải trả về lỗi validation 400."
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_400"));

        // Unknown ticket -> 404
        mockMvc.perform(patch("/api/v1/admin/moderation/tickets/{id}", UUID.randomUUID())
                        .header("Idempotency-Key", "idem-err-404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResolveModerationTicketRequest(
                                ModerationAction.DISMISS_REPORTS,
                                null,
                                "Phiếu kiểm duyệt không tồn tại trên hệ thống phải báo 404."
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RES_404"));

        // Close ticket -> 409 on second attempt
        ticket.setStatus("ACTION_TAKEN");
        mockMvc.perform(patch("/api/v1/admin/moderation/tickets/{id}", ticket.getId())
                        .header("Idempotency-Key", "idem-err-409")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResolveModerationTicketRequest(
                                ModerationAction.DISMISS_REPORTS,
                                null,
                                "Phiếu đã đóng trước đó thì không thể xử lý lại lần nữa."
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES_409"));

        verify(communityContentFacade, never()).moderateContent(any(), any(), any(), any());
        assertThat(auditLogStore).isEmpty();
    }

    @Test
    @DisplayName("AuditLog entity/repository forbid deletion and GET /api/v1/admin/audit-logs filters & enriches actor info")
    void shouldEnforceAuditLogImmutabilityAndExposeFilteredSearch() throws Exception {
        assertThat(AuditLog.class.getAnnotation(Immutable.class)).isNotNull();

        AuditLogRepository repoProxy = mock(AuditLogRepository.class, org.mockito.Mockito.CALLS_REAL_METHODS);

        assertThatThrownBy(() -> repoProxy.deleteById(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(repoProxy::deleteAll)
                .isInstanceOf(UnsupportedOperationException.class);

        UUID targetTicketId = UUID.randomUUID();
        adminAuditLogService.recordAuditLog(
                adminUserId,
                "MODERATE_TICKET",
                "ADM_MODERATION_TICKET",
                targetTicketId,
                Map.of("status", "OPEN"),
                Map.of("status", "ACTION_TAKEN"),
                "118.69.182.45"
        );

        when(iamFacade.findUsersByIds(any(Collection.class))).thenReturn(Map.of(
                adminUserId,
                new UserSummaryDto(adminUserId, "superadmin@analoghub.vn", "0900", "Quản trị viên Hệ thống",
                        null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.ADMIN), Map.of(), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorUserId", adminUserId.toString())
                        .param("actionCode", "MODERATE_TICKET")
                        .param("targetEntityType", "ADM_MODERATION_TICKET")
                        .param("targetEntityId", targetTicketId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item.length()").value(1))
                .andExpect(jsonPath("$.data.item[0].actor.email").value("superadmin@analoghub.vn"))
                .andExpect(jsonPath("$.data.item[0].actor.fullName").value("Quản trị viên Hệ thống"))
                .andExpect(jsonPath("$.data.item[0].actionCode").value("MODERATE_TICKET"))
                .andExpect(jsonPath("$.data.item[0].targetEntityType").value("ADM_MODERATION_TICKET"))
                .andExpect(jsonPath("$.data.item[0].ipAddress").value("118.69.182.45"));

        // Invalid time range -> 400 VALIDATION_ERROR
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("fromTime", "2026-11-05T00:00:00Z")
                        .param("toTime", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_400"));
    }
}
