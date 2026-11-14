package com.analoghub.platform.admin;

import com.analoghub.platform.admin.controller.AdminApprovalController;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.ModerationTicket;
import com.analoghub.platform.admin.dto.ApprovalDecision;
import com.analoghub.platform.admin.dto.ApproveLabOpeningRequest;
import com.analoghub.platform.admin.dto.DecideRoleApplicationRequest;
import com.analoghub.platform.admin.dto.RoleApplicationAdminDto;
import com.analoghub.platform.admin.dto.RoleApplicationDecisionResponse;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.ModerationTicketRepository;
import com.analoghub.platform.admin.service.AdminRoleAndLabApprovalService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.filmlab.facade.FilmLabFacadeImpl;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleAndLabApprovalServiceTest {

    @Mock
    private IamFacade iamFacade;

    @Mock
    private LabCapacityRuleRepository capacityRuleRepository;

    @Mock
    private ServicePackageRepository servicePackageRepository;

    @Mock
    private LabRepository labRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ModerationTicketRepository moderationTicketRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private NotificationFacade notificationFacade;

    private FilmLabFacadeImpl filmLabFacade;
    private AdminRoleAndLabApprovalService approvalService;
    private AdminApprovalController approvalController;

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

        filmLabFacade = new FilmLabFacadeImpl(
                capacityRuleRepository,
                servicePackageRepository,
                labRepository,
                eventPublisher
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
    @DisplayName("decideRoleApplication APPROVE for LAB_OWNER calls IamFacade and FilmLabFacade to activate Lab and record AuditLog")
    void shouldApproveLabOwnerApplicationAndProvisionActiveLab() {
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

        when(iamFacade.getRoleApplicationById(applicationId)).thenReturn(pendingApp);
        when(iamFacade.approveRoleApplication(applicationId, adminUserId, reviewNote)).thenReturn(approvedApp);

        when(labRepository.findBySlug("sai-gon-vintage-darkroom-lab")).thenReturn(Optional.empty());
        when(labRepository.findFirstByOwnerUserIdOrderByCreatedAtDesc(applicantId)).thenReturn(Optional.empty());
        when(labRepository.save(any(Lab.class))).thenAnswer(inv -> {
            Lab lab = inv.getArgument(0);
            if (lab.getId() == null) {
                lab.setId(createdLabId);
            }
            return lab;
        });
        when(capacityRuleRepository.findByLabIdAndProcessType(eq(createdLabId), any())).thenReturn(Optional.empty());

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

        ResponseEntity<ApiResponse<RoleApplicationDecisionResponse>> httpResponse =
                approvalController.decideRoleApplication(
                        applicationId,
                        new DecideRoleApplicationRequest(ApprovalDecision.APPROVE, reviewNote)
                );

        assertThat(httpResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(httpResponse.getBody()).isNotNull();
        RoleApplicationDecisionResponse response = httpResponse.getBody().getData();

        assertThat(response).isNotNull();
        assertThat(response.applicationId()).isEqualTo(applicationId);
        assertThat(response.applicantUserId()).isEqualTo(applicantId);
        assertThat(response.requestedRole()).isEqualTo(RoleCode.LAB_OWNER);
        assertThat(response.previousStatus()).isEqualTo(RoleApplicationStatus.PENDING);
        assertThat(response.newStatus()).isEqualTo(RoleApplicationStatus.APPROVED);
        assertThat(response.grantedRoleCode()).isEqualTo(RoleCode.LAB_OWNER);
        assertThat(response.reviewedByAdminId()).isEqualTo(adminUserId);
        assertThat(response.reviewNote()).isEqualTo(reviewNote);
        assertThat(response.auditLogId()).isEqualTo(auditId);
        assertThat(response.provisionedLab()).isNotNull();
        assertThat(response.provisionedLab().labId()).isEqualTo(createdLabId);
        assertThat(response.provisionedLab().slug()).isEqualTo("sai-gon-vintage-darkroom-lab");
        assertThat(response.provisionedLab().status()).isEqualTo("ACTIVE");

        verify(iamFacade).approveRoleApplication(applicationId, adminUserId, reviewNote);

        ArgumentCaptor<Lab> labCaptor = ArgumentCaptor.forClass(Lab.class);
        verify(labRepository).save(labCaptor.capture());
        assertThat(labCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(labCaptor.getValue().getOwnerUserId()).isEqualTo(applicantId);
        assertThat(labCaptor.getValue().getDistrictCode()).isEqualTo("760");
        assertThat(labCaptor.getValue().getProvinceCode()).isEqualTo("79");

        ArgumentCaptor<LabCapacityRule> capacityCaptor = ArgumentCaptor.forClass(LabCapacityRule.class);
        verify(capacityRuleRepository, atLeastOnce()).save(capacityCaptor.capture());
        assertThat(capacityCaptor.getAllValues())
                .extracting(LabCapacityRule::getProcessType)
                .containsExactlyInAnyOrder("C41", "BW");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(FilmLabCreatedEvent.class);

        verify(moderationTicketRepository).save(ticket);
        assertThat(ticket.getStatus()).isEqualTo("APPROVED");
        assertThat(ticket.getAssignedAdminId()).isEqualTo(adminUserId);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAdminId()).isEqualTo(adminUserId);
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo("DECIDE_ROLE_APPLICATION");
        assertThat(auditCaptor.getValue().getTargetEntity()).isEqualTo("IAM_ROLE_APPLICATION");
        assertThat(auditCaptor.getValue().getTargetId()).isEqualTo(applicationId);

        verify(notificationFacade).sendSystemAlert(eq(applicantId), any(), contains("APPROVED"));
    }

    @Test
    @DisplayName("decideRoleApplication REJECT updates status and ticket without provisioning a Lab")
    void shouldRejectRoleApplicationWithoutProvisioningLab() {
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
            AuditLog log = inv.getArgument(0);
            log.setId(auditId);
            return log;
        });

        RoleApplicationDecisionResponse response = approvalService.decideRoleApplication(
                applicationId,
                new DecideRoleApplicationRequest(ApprovalDecision.REJECT, rejectReason)
        );

        assertThat(response.newStatus()).isEqualTo(RoleApplicationStatus.REJECTED);
        assertThat(response.grantedRoleCode()).isNull();
        assertThat(response.provisionedLab()).isNull();
        assertThat(response.auditLogId()).isEqualTo(auditId);

        verify(labRepository, never()).save(any());
        verify(iamFacade).rejectRoleApplication(applicationId, adminUserId, rejectReason);

        ArgumentCaptor<ModerationTicket> ticketCaptor = ArgumentCaptor.forClass(ModerationTicket.class);
        verify(moderationTicketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(ticketCaptor.getValue().getAssignedAdminId()).isEqualTo(adminUserId);
    }

    @Test
    @DisplayName("approveLabOpening transitions PENDING_APPROVAL Lab to ACTIVE, grants LAB_OWNER and writes AuditLog")
    void shouldApproveLabOpeningTransitionToActiveAndGrantRole() {
        UUID labId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String reviewNote = "Lab đạt chuẩn quy trình vận hành.";

        Lab pendingLab = Lab.builder()
                .id(labId)
                .ownerUserId(ownerId)
                .slug("hanoi-analog-lab")
                .name("Hanoi Analog Lab")
                .fullAddress("12 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                .districtCode("002")
                .provinceCode("01")
                .status("PENDING_APPROVAL")
                .build();

        ModerationTicket labTicket = ModerationTicket.builder()
                .id(UUID.randomUUID())
                .entityType("LAB_APPROVAL")
                .entityId(labId)
                .reportedBy(ownerId)
                .issueType("LAB_OPENING_APPROVAL")
                .status("PENDING")
                .build();

        when(labRepository.findById(labId)).thenReturn(Optional.of(pendingLab));
        when(labRepository.save(any(Lab.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moderationTicketRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc("LAB_APPROVAL", labId))
                .thenReturn(Optional.of(labTicket));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ApiResponse<FilmLabFacade.ProvisionedLabInfo>> httpResponse =
                approvalController.approveLabOpening(labId, new ApproveLabOpeningRequest(reviewNote));

        assertThat(httpResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(httpResponse.getBody()).isNotNull();
        FilmLabFacade.ProvisionedLabInfo info = httpResponse.getBody().getData();

        assertThat(info).isNotNull();
        assertThat(info.labId()).isEqualTo(labId);
        assertThat(info.ownerUserId()).isEqualTo(ownerId);
        assertThat(info.status()).isEqualTo("ACTIVE");
        assertThat(pendingLab.getStatus()).isEqualTo("ACTIVE");

        verify(iamFacade).grantRole(ownerId, RoleCode.LAB_OWNER);
        verify(eventPublisher).publishEvent(any(FilmLabCreatedEvent.class));
        assertThat(labTicket.getStatus()).isEqualTo("APPROVED");
        verify(moderationTicketRepository).save(labTicket);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo("APPROVE_LAB_OPENING");
        assertThat(auditCaptor.getValue().getTargetEntity()).isEqualTo("LAB_FILM_LAB");
        assertThat(auditCaptor.getValue().getTargetId()).isEqualTo(labId);
    }

    @Test
    @DisplayName("listRoleApplications joins applicant summary and moderation ticket ID and signs evidence URLs")
    void shouldListRoleApplicationsWithApplicantTicketAndSignedEvidenceUrls() {
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

        ResponseEntity<ApiResponse<PageResponse<RoleApplicationAdminDto>>> httpResponse =
                approvalController.listRoleApplications(RoleApplicationStatus.PENDING, RoleCode.LAB_OWNER, 0, 20);

        assertThat(httpResponse.getBody()).isNotNull();
        PageResponse<RoleApplicationAdminDto> page = httpResponse.getBody().getData();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.item()).hasSize(1);

        RoleApplicationAdminDto dto = page.item().getFirst();
        assertThat(dto.applicationId()).isEqualTo(app1);
        assertThat(dto.moderationTicketId()).isEqualTo(ticket1);
        assertThat(dto.applicant().email()).isEqualTo("owner@analoghub.vn");
        assertThat(dto.applicant().currentRoles()).containsExactly(RoleCode.CUSTOMER);
        assertThat(dto.portfolioPayload().get("labPhotoUrls")).isEqualTo(List.of(signedUrl));
    }

    @Test
    @DisplayName("provisionLabFromApprovedApplication throws LAB_SLUG_ALREADY_EXISTS when slug belongs to another user")
    void shouldRejectDuplicateSlugOwnedByAnotherUser() {
        UUID applicantId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        String slug = "saigon-darkroom";

        when(labRepository.findBySlug(slug)).thenReturn(Optional.of(
                Lab.builder()
                        .id(UUID.randomUUID())
                        .ownerUserId(otherOwnerId)
                        .slug(slug)
                        .name("Saigon Darkroom")
                        .fullAddress("123 Nguyễn Huệ")
                        .districtCode("760")
                        .provinceCode("79")
                        .status("ACTIVE")
                        .build()
        ));

        assertThatThrownBy(() -> filmLabFacade.provisionLabFromApprovedApplication(
                applicantId,
                Map.of(
                        "proposedLabName", "Saigon Darkroom",
                        "proposedSlug", slug,
                        "fullAddress", "45A Lý Tự Trọng"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LAB_SLUG_ALREADY_EXISTS);
    }
}
