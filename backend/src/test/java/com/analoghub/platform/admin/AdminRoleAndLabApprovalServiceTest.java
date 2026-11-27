package com.analoghub.platform.admin;

import com.analoghub.platform.admin.controller.AdminApprovalController;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.ModerationTicket;
import com.analoghub.platform.admin.dto.ApprovalDecision;
import com.analoghub.platform.admin.dto.ApproveLabOpeningRequest;
import com.analoghub.platform.admin.dto.DecideRoleApplicationRequest;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.ModerationTicketRepository;
import com.analoghub.platform.admin.service.AdminRoleAndLabApprovalService;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.event.RoleApplicationSubmittedEvent;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRoleAndLabApprovalServiceTest {

    @Mock
    private IamFacade iamFacade;

    @Mock
    private FilmLabFacade filmLabFacade;

    @Mock
    private ModerationTicketRepository moderationTicketRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private NotificationFacade notificationFacade;

    private AdminRoleAndLabApprovalService approvalService;
    private AdminApprovalController approvalController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID adminUserId;

    @BeforeEach
    void setUp() {
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

        approvalService = new AdminRoleAndLabApprovalService(
                iamFacade,
                filmLabFacade,
                moderationTicketRepository,
                auditLogRepository,
                storageService,
                notificationFacade
        );
        approvalController = new AdminApprovalController(approvalService);
        mockMvc = MockMvcBuilders.standaloneSetup(approvalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("onRoleApplicationSubmitted creates a PENDING ModerationTicket and deduplicates repeated events")
    void shouldCreateModerationTicketOnRoleApplicationSubmittedAndDeduplicate() {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        RoleApplicationSubmittedEvent event = new RoleApplicationSubmittedEvent(
                applicationId,
                applicantId,
                RoleCode.LAB_OWNER
        );

        ModerationTicket savedTicket = ModerationTicket.builder()
                .id(UUID.randomUUID())
                .entityType("ROLE_APPLICATION")
                .entityId(applicationId)
                .reportedBy(applicantId)
                .issueType("ROLE_UPGRADE_LAB_OWNER")
                .status("PENDING")
                .build();

        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ROLE_APPLICATION",
                applicationId
        )).thenReturn(Optional.empty()).thenReturn(Optional.of(savedTicket));

        approvalService.onRoleApplicationSubmitted(event);
        approvalService.onRoleApplicationSubmitted(event);

        ArgumentCaptor<ModerationTicket> ticketCaptor = ArgumentCaptor.forClass(ModerationTicket.class);
        verify(moderationTicketRepository).save(ticketCaptor.capture());

        ModerationTicket created = ticketCaptor.getValue();
        assertThat(created.getEntityType()).isEqualTo("ROLE_APPLICATION");
        assertThat(created.getTargetType()).isEqualTo("ROLE_APPLICATION");
        assertThat(created.getEntityId()).isEqualTo(applicationId);
        assertThat(created.getTargetId()).isEqualTo(applicationId);
        assertThat(created.getReportedBy()).isEqualTo(applicantId);
        assertThat(created.getIssueType()).isEqualTo("ROLE_UPGRADE_LAB_OWNER");
        assertThat(created.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("GET /api/v1/admin/approvals/role-applications joins applicant summary, moderation ticket ID and signed evidence URLs")
    void shouldListRoleApplicationsWithApplicantTicketAndSignedEvidenceUrls() throws Exception {
        UUID app1 = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID ticket1 = UUID.randomUUID();
        String evidenceKey = "private/applications/" + user1 + "/darkroom-1.jpg";
        String signedUrl = "https://cdn.analoghub.vn/signed/darkroom-1.jpg";

        RoleApplicationDto labOwnerApp = new RoleApplicationDto(
                app1,
                user1,
                RoleCode.LAB_OWNER,
                Map.of(
                        "proposedLabName", "Saigon Analog",
                        "labPhotoS3Keys", List.of(evidenceKey)
                ),
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
        RoleApplicationDto expertApp = new RoleApplicationDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RoleCode.EXPERT,
                Map.of("specialtyAreas", List.of("C-41")),
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now().minusSeconds(60)
        );

        when(iamFacade.getRoleApplicationsByStatus(RoleApplicationStatus.PENDING))
                .thenReturn(List.of(labOwnerApp, expertApp));
        when(iamFacade.findUsersByIds(List.of(user1))).thenReturn(Map.of(
                user1,
                new UserSummaryDto(
                        user1,
                        "owner@analoghub.vn",
                        "0909123456",
                        "Nguyễn Hoàng Minh",
                        null,
                        AccountStatus.ACTIVE,
                        AuthProvider.LOCAL,
                        Set.of(RoleCode.CUSTOMER),
                        Map.of(),
                        Instant.now()
                )
        ));
        when(moderationTicketRepository.findByEntityTypeAndEntityIdIn("ROLE_APPLICATION", List.of(app1)))
                .thenReturn(List.of(ModerationTicket.builder()
                        .id(ticket1)
                        .entityType("ROLE_APPLICATION")
                        .entityId(app1)
                        .reportedBy(user1)
                        .status("PENDING")
                        .build()));
        when(storageService.resolveReadUrl(evidenceKey)).thenReturn(signedUrl);

        mockMvc.perform(get("/api/v1/admin/approvals/role-applications")
                        .param("status", "PENDING")
                        .param("requestedRole", "LAB_OWNER")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.item.length()").value(1))
                .andExpect(jsonPath("$.data.item[0].applicationId").value(app1.toString()))
                .andExpect(jsonPath("$.data.item[0].moderationTicketId").value(ticket1.toString()))
                .andExpect(jsonPath("$.data.item[0].applicant.email").value("owner@analoghub.vn"))
                .andExpect(jsonPath("$.data.item[0].applicant.currentRoles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.item[0].portfolioPayload.labPhotoUrls[0]").value(signedUrl));
    }

    @Test
    @DisplayName("POST /api/v1/admin/approvals/role-applications/{id}/decide APPROVE for LAB_OWNER calls IamFacade and FilmLabFacade, saves AuditLog with IP, and notifies applicant")
    void shouldApproveLabOwnerApplicationAndProvisionActiveLab() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        UUID createdLabId = UUID.randomUUID();
        String reviewNote = "Đã xác minh giấy phép kinh doanh và thiết bị tráng scan Noritsu HS-1800 đạt chuẩn.";

        Map<String, Object> portfolio = Map.of(
                "proposedLabName", "Sài Gòn Vintage Darkroom Lab",
                "fullAddress", "45A Lý Tự Trọng, Quận 1, TP.HCM",
                "districtCode", "760",
                "provinceCode", "79",
                "latitude", 10.7785,
                "longitude", 106.7012,
                "supportedProcesses", List.of("C41", "BW")
        );

        RoleApplicationDto pendingApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.LAB_OWNER,
                portfolio,
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now().minusSeconds(3600)
        );
        RoleApplicationDto approvedApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.LAB_OWNER,
                portfolio,
                RoleApplicationStatus.APPROVED,
                adminUserId,
                reviewNote,
                Instant.now(),
                pendingApp.createdAt()
        );
        FilmLabFacade.ProvisionedLabInfo provisionedLab = new FilmLabFacade.ProvisionedLabInfo(
                createdLabId,
                applicantId,
                "sai-gon-vintage-darkroom-lab",
                "Sài Gòn Vintage Darkroom Lab",
                "ACTIVE"
        );

        when(iamFacade.getRoleApplicationById(applicationId)).thenReturn(pendingApp);
        when(iamFacade.approveRoleApplication(applicationId, adminUserId, reviewNote)).thenReturn(approvedApp);
        when(filmLabFacade.provisionLabFromApprovedApplication(applicantId, portfolio)).thenReturn(provisionedLab);

        ModerationTicket ticket = ModerationTicket.builder()
                .id(ticketId)
                .entityType("ROLE_APPLICATION")
                .entityId(applicationId)
                .reportedBy(applicantId)
                .issueType("ROLE_UPGRADE_LAB_OWNER")
                .status("PENDING")
                .build();
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ROLE_APPLICATION",
                applicationId
        )).thenReturn(Optional.of(ticket));

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog audit = inv.getArgument(0);
            audit.setId(auditId);
            return audit;
        });

        DecideRoleApplicationRequest req = new DecideRoleApplicationRequest(ApprovalDecision.APPROVE, reviewNote);

        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", applicationId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Forwarded-For", "118.69.182.45, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(applicationId.toString()))
                .andExpect(jsonPath("$.data.applicantUserId").value(applicantId.toString()))
                .andExpect(jsonPath("$.data.requestedRole").value("LAB_OWNER"))
                .andExpect(jsonPath("$.data.previousStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.newStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.grantedRoleCode").value("LAB_OWNER"))
                .andExpect(jsonPath("$.data.reviewedByAdminId").value(adminUserId.toString()))
                .andExpect(jsonPath("$.data.reviewNote").value(reviewNote))
                .andExpect(jsonPath("$.data.auditLogId").value(auditId.toString()))
                .andExpect(jsonPath("$.data.provisionedLab.labId").value(createdLabId.toString()))
                .andExpect(jsonPath("$.data.provisionedLab.slug").value("sai-gon-vintage-darkroom-lab"))
                .andExpect(jsonPath("$.data.provisionedLab.status").value("ACTIVE"));

        verify(iamFacade).approveRoleApplication(applicationId, adminUserId, reviewNote);
        verify(filmLabFacade).provisionLabFromApprovedApplication(applicantId, portfolio);
        verify(moderationTicketRepository).save(ticket);
        assertThat(ticket.getStatus()).isEqualTo("APPROVED");
        assertThat(ticket.getAssignedAdminId()).isEqualTo(adminUserId);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAdminId()).isEqualTo(adminUserId);
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo("DECIDE_ROLE_APPLICATION");
        assertThat(auditCaptor.getValue().getTargetEntity()).isEqualTo("IAM_ROLE_APPLICATION");
        assertThat(auditCaptor.getValue().getTargetId()).isEqualTo(applicationId);
        assertThat(auditCaptor.getValue().getIpAddress()).isEqualTo("118.69.182.45");

        verify(notificationFacade).sendSystemAlert(eq(applicantId), any(), contains("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/approvals/role-applications/{id}/decide APPROVE for EXPERT grants EXPERT without provisioning a Lab")
    void shouldApproveExpertApplicationWithoutProvisioningLab() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        String reviewNote = "Đã xác minh hồ sơ chuyên gia buồng tối C-41 và ECN-2.";

        RoleApplicationDto pendingApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.EXPERT,
                Map.of("specialtyAreas", List.of("C-41", "ECN-2"), "yearsOfExperience", 8),
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
        RoleApplicationDto approvedApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.EXPERT,
                pendingApp.portfolioPayload(),
                RoleApplicationStatus.APPROVED,
                adminUserId,
                reviewNote,
                Instant.now(),
                pendingApp.createdAt()
        );

        when(iamFacade.getRoleApplicationById(applicationId)).thenReturn(pendingApp);
        when(iamFacade.approveRoleApplication(applicationId, adminUserId, reviewNote)).thenReturn(approvedApp);
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ROLE_APPLICATION",
                applicationId
        )).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog audit = inv.getArgument(0);
            audit.setId(auditId);
            return audit;
        });

        DecideRoleApplicationRequest req = new DecideRoleApplicationRequest(ApprovalDecision.APPROVE, reviewNote);

        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", applicationId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.grantedRoleCode").value("EXPERT"))
                .andExpect(jsonPath("$.data.provisionedLab").isEmpty());

        verify(iamFacade).approveRoleApplication(applicationId, adminUserId, reviewNote);
        verify(filmLabFacade, never()).provisionLabFromApprovedApplication(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/admin/approvals/role-applications/{id}/decide REJECT updates status and ticket without provisioning a Lab")
    void shouldRejectRoleApplicationWithoutProvisioningLab() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        String rejectReason = "Hồ sơ thiếu ảnh chụp máy scan thực tế và giấy phép hoạt động phòng Lab.";

        RoleApplicationDto pendingApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.LAB_OWNER,
                Map.of("proposedLabName", "Incomplete Lab"),
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
        RoleApplicationDto rejectedApp = new RoleApplicationDto(
                applicationId,
                applicantId,
                RoleCode.LAB_OWNER,
                pendingApp.portfolioPayload(),
                RoleApplicationStatus.REJECTED,
                adminUserId,
                rejectReason,
                Instant.now(),
                pendingApp.createdAt()
        );

        when(iamFacade.getRoleApplicationById(applicationId)).thenReturn(pendingApp);
        when(iamFacade.rejectRoleApplication(applicationId, adminUserId, rejectReason)).thenReturn(rejectedApp);
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ROLE_APPLICATION",
                applicationId
        )).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog audit = inv.getArgument(0);
            audit.setId(auditId);
            return audit;
        });

        DecideRoleApplicationRequest req = new DecideRoleApplicationRequest(ApprovalDecision.REJECT, rejectReason);

        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", applicationId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.grantedRoleCode").isEmpty())
                .andExpect(jsonPath("$.data.provisionedLab").isEmpty())
                .andExpect(jsonPath("$.data.auditLogId").value(auditId.toString()));

        verify(filmLabFacade, never()).provisionLabFromApprovedApplication(any(), any());
        verify(iamFacade).rejectRoleApplication(applicationId, adminUserId, rejectReason);

        ArgumentCaptor<ModerationTicket> ticketCaptor = ArgumentCaptor.forClass(ModerationTicket.class);
        verify(moderationTicketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(ticketCaptor.getValue().getAssignedAdminId()).isEqualTo(adminUserId);
    }

    @Test
    @DisplayName("POST /api/v1/admin/approvals/role-applications/{id}/decide returns 400, 404 and 409 for invalid input, missing app, already decided app and duplicate Lab slug")
    void shouldReturnProperHttpErrorsOnValidationNotFoundAndConflicts() throws Exception {
        UUID appId = UUID.randomUUID();

        // 400 Bad Request: reviewNote shorter than 10 chars & null decision
        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", appId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":null,\"reviewNote\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        // 404 Not Found
        when(iamFacade.getRoleApplicationById(appId))
                .thenThrow(new ResourceNotFoundException("RoleApplication", appId));
        DecideRoleApplicationRequest validApprove = new DecideRoleApplicationRequest(
                ApprovalDecision.APPROVE,
                "Ghi chú xét duyệt hợp lệ dài hơn 10 ký tự."
        );
        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", appId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApprove)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));

        // 409 Conflict: already decided
        RoleApplicationDto alreadyApproved = new RoleApplicationDto(
                appId,
                UUID.randomUUID(),
                RoleCode.LAB_OWNER,
                Map.of("proposedLabName", "Test Lab"),
                RoleApplicationStatus.APPROVED,
                adminUserId,
                "Already approved",
                Instant.now(),
                Instant.now().minusSeconds(60)
        );
        doReturn(alreadyApproved).when(iamFacade).getRoleApplicationById(appId);
        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", appId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApprove)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));

        // 409 Conflict: duplicate Lab slug during LAB_OWNER provisioning
        UUID applicantId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("proposedLabName", "Duplicate Slug Lab", "proposedSlug", "duplicate-slug");
        RoleApplicationDto pendingApp = new RoleApplicationDto(
                appId,
                applicantId,
                RoleCode.LAB_OWNER,
                payload,
                RoleApplicationStatus.PENDING,
                null,
                null,
                null,
                Instant.now()
        );
        RoleApplicationDto newlyApproved = new RoleApplicationDto(
                appId,
                applicantId,
                RoleCode.LAB_OWNER,
                payload,
                RoleApplicationStatus.APPROVED,
                adminUserId,
                validApprove.reviewNote(),
                Instant.now(),
                pendingApp.createdAt()
        );
        doReturn(pendingApp).when(iamFacade).getRoleApplicationById(appId);
        when(iamFacade.approveRoleApplication(appId, adminUserId, validApprove.reviewNote())).thenReturn(newlyApproved);
        when(filmLabFacade.provisionLabFromApprovedApplication(applicantId, payload))
                .thenThrow(new BusinessException(ErrorCode.LAB_SLUG_ALREADY_EXISTS, "Slug Film Lab đã tồn tại"));

        mockMvc.perform(post("/api/v1/admin/approvals/role-applications/{id}/decide", appId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validApprove)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.LAB_SLUG_ALREADY_EXISTS.getCode()));
    }

    @Test
    @DisplayName("GET /api/v1/admin/approvals/labs and POST /api/v1/admin/approvals/labs/{labId}/approve transition Lab to ACTIVE, grant LAB_OWNER and write AuditLog")
    void shouldListPendingLabsAndApproveLabOpeningTransitionToActiveAndGrantRole() throws Exception {
        UUID labId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String reviewNote = "Lab đạt chuẩn quy trình vận hành.";

        FilmLabFacade.ProvisionedLabInfo pendingInfo = new FilmLabFacade.ProvisionedLabInfo(
                labId,
                ownerId,
                "hanoi-analog-lab",
                "Hanoi Analog Lab",
                "PENDING_APPROVAL"
        );
        FilmLabFacade.ProvisionedLabInfo activeInfo = new FilmLabFacade.ProvisionedLabInfo(
                labId,
                ownerId,
                "hanoi-analog-lab",
                "Hanoi Analog Lab",
                "ACTIVE"
        );

        when(filmLabFacade.getLabsByStatus("PENDING_APPROVAL")).thenReturn(List.of(pendingInfo));

        mockMvc.perform(get("/api/v1/admin/approvals/labs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].labId").value(labId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"));

        ModerationTicket labTicket = ModerationTicket.builder()
                .id(UUID.randomUUID())
                .entityType("LAB_APPROVAL")
                .entityId(labId)
                .reportedBy(ownerId)
                .issueType("LAB_OPENING_APPROVAL")
                .status("PENDING")
                .build();

        when(filmLabFacade.approveLabOpening(labId)).thenReturn(activeInfo);
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc("LAB_APPROVAL", labId))
                .thenReturn(Optional.of(labTicket));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/admin/approvals/labs/{labId}/approve", labId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveLabOpeningRequest(reviewNote))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.labId").value(labId.toString()))
                .andExpect(jsonPath("$.data.ownerUserId").value(ownerId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(filmLabFacade).approveLabOpening(labId);
        verify(iamFacade).grantRole(ownerId, RoleCode.LAB_OWNER);
        assertThat(labTicket.getStatus()).isEqualTo("APPROVED");
        verify(moderationTicketRepository).save(labTicket);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo("APPROVE_LAB_OPENING");
        assertThat(auditCaptor.getValue().getTargetEntity()).isEqualTo("LAB_FILM_LAB");
        assertThat(auditCaptor.getValue().getTargetId()).isEqualTo(labId);

        verify(notificationFacade).sendSystemAlert(eq(ownerId), any(), contains("ACTIVE"));
    }
}
