package com.analoghub.platform.admin;

import com.analoghub.platform.admin.audit.AdminAuditLogAspect;
import com.analoghub.platform.admin.controller.AdminPaymentMonitorController;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.dto.DecidePayoutRequest;
import com.analoghub.platform.admin.dto.PayoutDecision;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.service.AdminAuditLogService;
import com.analoghub.platform.admin.service.AdminPaymentMonitorService;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.payment.dto.AdminLedgerEntryDto;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutAdminItemDto;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.security.AuthenticatedUser;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPaymentMonitorAndPayoutTest {

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private NotificationFacade notificationFacade;

    private final List<AuditLog> auditLogStore = new ArrayList<>();

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
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

        AdminAuditLogService adminAuditLogService = new AdminAuditLogService(auditLogRepository, iamFacade);
        AdminPaymentMonitorService targetService = new AdminPaymentMonitorService(
                paymentFacade,
                adminAuditLogService,
                iamFacade,
                notificationFacade
        );

        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(targetService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new AdminAuditLogAspect(adminAuditLogService, objectMapper));
        AdminPaymentMonitorService proxiedService = proxyFactory.getProxy();

        AdminPaymentMonitorController controller = new AdminPaymentMonitorController(proxiedService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/admin/payments/payouts returns enriched pending payout queue")
    void shouldListAndEnrichPayoutRequests() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID labOwnerId = UUID.randomUUID();

        PayoutAdminItemDto rawItem = new PayoutAdminItemDto(
                payoutId,
                walletId,
                "LAB_WALLET",
                labOwnerId,
                new BigDecimal("4500000.00"),
                "VCB",
                "0071001234567",
                "CONG TY TNHH LLAB SAIGON",
                "PENDING_APPROVAL",
                null,
                null,
                Instant.now(),
                null
        );

        when(paymentFacade.queryPayoutRequests("PENDING_APPROVAL", null, 0, 20))
                .thenReturn(PageResponse.from(List.of(rawItem), 0, 20, 1, 1));
        when(iamFacade.findUsersByIds(Set.of(labOwnerId))).thenReturn(Map.of(
                labOwnerId,
                new UserSummaryDto(labOwnerId, "ops@llabsaigon.vn", "0909", "LLab Saigon Analog",
                        null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.LAB_OWNER), Map.of(), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/admin/payments/payouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item.length()").value(1))
                .andExpect(jsonPath("$.data.item[0].payoutRequestId").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.item[0].walletType").value("LAB_WALLET"))
                .andExpect(jsonPath("$.data.item[0].requester.fullName").value("LLab Saigon Analog"))
                .andExpect(jsonPath("$.data.item[0].requester.email").value("ops@llabsaigon.vn"))
                .andExpect(jsonPath("$.data.item[0].amountVnd").value(4500000.00))
                .andExpect(jsonPath("$.data.item[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/payments/payouts/{id}/decide (APPROVE_COMPLETED) settles payout, notifies user and writes AuditLog")
    void shouldApprovePayoutNotifyUserAndRecordAuditLog() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID labOwnerId = UUID.randomUUID();
        UUID journalGroupId = UUID.randomUUID();

        PayoutSettlementDto settlement = new PayoutSettlementDto(
                payoutId,
                walletId,
                labOwnerId,
                new BigDecimal("4500000.00"),
                "PENDING_APPROVAL",
                "COMPLETED",
                "FT263059988112233-VCB",
                journalGroupId,
                "PAYOUT_SETTLE",
                new BigDecimal("1250000.00"),
                new BigDecimal("0.00"),
                adminUserId,
                Instant.now()
        );

        when(paymentFacade.completePayoutRequest(
                eq(payoutId),
                eq(adminUserId),
                eq("FT263059988112233-VCB"),
                contains("Napas 247")
        )).thenReturn(settlement);

        DecidePayoutRequest request = new DecidePayoutRequest(
                PayoutDecision.APPROVE_COMPLETED,
                "FT263059988112233-VCB",
                "Đã đối soát sổ cái ví LAB_WALLET và hoàn tất chuyển khoản Napas 247."
        );

        mockMvc.perform(post("/api/v1/admin/payments/payouts/{id}/decide", payoutId)
                        .header("Idempotency-Key", "idem-payout-approve-1")
                        .header("X-Forwarded-For", "118.69.182.45")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.payoutRequestId").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.previousStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.newStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.bankTransferRef").value("FT263059988112233-VCB"))
                .andExpect(jsonPath("$.data.ledgerSettlement.journalGroupId").value(journalGroupId.toString()))
                .andExpect(jsonPath("$.data.ledgerSettlement.entryType").value("PAYOUT_SETTLE"))
                .andExpect(jsonPath("$.data.ledgerSettlement.lockedBalanceAfterVnd").value(0.00))
                .andExpect(jsonPath("$.data.auditLogId").isNotEmpty());

        verify(notificationFacade).sendSystemAlert(
                eq(labOwnerId),
                contains("chuyển khoản hoàn tất"),
                contains("FT263059988112233-VCB")
        );

        assertThat(auditLogStore).hasSize(1);
        AuditLog savedAudit = auditLogStore.getFirst();
        assertThat(savedAudit.getAdminId()).isEqualTo(adminUserId);
        assertThat(savedAudit.getActionType()).isEqualTo("DECIDE_PAYOUT_REQUEST");
        assertThat(savedAudit.getTargetEntity()).isEqualTo("PAY_PAYOUT_REQUEST");
        assertThat(savedAudit.getTargetId()).isEqualTo(payoutId);
        assertThat(savedAudit.getIpAddress()).isEqualTo("118.69.182.45");
        assertThat(savedAudit.getNewValues())
                .containsEntry("status", "COMPLETED")
                .containsEntry("bankTransferRef", "FT263059988112233-VCB");
    }

    @Test
    @DisplayName("POST /api/v1/admin/payments/payouts/{id}/decide (REJECT_UNLOCK) unlocks balance, notifies user and writes AuditLog")
    void shouldRejectPayoutNotifyUserAndRecordAuditLog() throws Exception {
        UUID payoutId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID journalGroupId = UUID.randomUUID();

        PayoutSettlementDto rejection = new PayoutSettlementDto(
                payoutId,
                walletId,
                requesterId,
                new BigDecimal("1000000.00"),
                "PENDING_APPROVAL",
                "REJECTED",
                null,
                journalGroupId,
                "PAYOUT_LOCK",
                new BigDecimal("2500000.00"),
                new BigDecimal("0.00"),
                adminUserId,
                Instant.now()
        );

        when(paymentFacade.rejectPayoutRequest(eq(payoutId), eq(adminUserId), contains("Sai tên")))
                .thenReturn(rejection);

        DecidePayoutRequest request = new DecidePayoutRequest(
                PayoutDecision.REJECT_UNLOCK,
                null,
                "Sai tên chủ tài khoản ngân hàng thụ hưởng."
        );

        mockMvc.perform(post("/api/v1/admin/payments/payouts/{id}/decide", payoutId)
                        .header("Idempotency-Key", "idem-payout-reject-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.ledgerSettlement.availableBalanceAfterVnd").value(2500000.00))
                .andExpect(jsonPath("$.data.ledgerSettlement.lockedBalanceAfterVnd").value(0.00))
                .andExpect(jsonPath("$.data.auditLogId").isNotEmpty());

        verify(notificationFacade).sendSystemAlert(
                eq(requesterId),
                contains("bị từ chối"),
                contains("hoàn trả về ví khả dụng")
        );
        assertThat(auditLogStore).hasSize(1);
    }

    @Test
    @DisplayName("GET /ledger/reconcile and GET /ledger return double-entry reconciliation report and owner-enriched ledger entries")
    void shouldReturnLedgerReconciliationAndEnrichedLedgerEntries() throws Exception {
        LedgerReconciliationReportDto report = new LedgerReconciliationReportDto(
                true,
                new BigDecimal("10000000.00"),
                new BigDecimal("10000000.00"),
                new BigDecimal("0.00"),
                12,
                6,
                List.of(),
                new BigDecimal("1500000.00"),
                new BigDecimal("350000.00"),
                new BigDecimal("6000000.00"),
                new BigDecimal("500000.00"),
                1,
                new BigDecimal("500000.00"),
                Instant.now()
        );
        when(paymentFacade.reconcileLedger()).thenReturn(report);

        mockMvc.perform(get("/api/v1/admin/payments/ledger/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalDebitVnd").value(10000000.00))
                .andExpect(jsonPath("$.data.totalCreditVnd").value(10000000.00))
                .andExpect(jsonPath("$.data.discrepancyVnd").value(0.00));

        UUID ownerId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        AdminLedgerEntryDto rawEntry = new AdminLedgerEntryDto(
                entryId,
                groupId,
                walletId,
                ownerId,
                "LAB_WALLET",
                null,
                "DEBIT",
                "PAYOUT_SETTLE",
                new BigDecimal("4500000.00"),
                new BigDecimal("0.00"),
                "PAYOUT_REQUEST",
                UUID.randomUUID(),
                Instant.now()
        );
        when(paymentFacade.queryLedgerEntries(null, null, null, "DEBIT", "PAYOUT_SETTLE", null, null, 0, 25))
                .thenReturn(PageResponse.from(List.of(rawEntry), 0, 25, 1, 1));
        when(iamFacade.findUsersByIds(Set.of(ownerId))).thenReturn(Map.of(
                ownerId,
                new UserSummaryDto(ownerId, "ops@llabsaigon.vn", "0909", "LLab Saigon Analog",
                        null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.LAB_OWNER), Map.of(), Instant.now())
        ));

        mockMvc.perform(get("/api/v1/admin/payments/ledger")
                        .param("entryDirection", "DEBIT")
                        .param("entryType", "PAYOUT_SETTLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.length()").value(1))
                .andExpect(jsonPath("$.data.item[0].ledgerEntryId").value(entryId.toString()))
                .andExpect(jsonPath("$.data.item[0].owner.fullName").value("LLab Saigon Analog"))
                .andExpect(jsonPath("$.data.item[0].entryType").value("PAYOUT_SETTLE"));
    }

    @Test
    @DisplayName("Validation (400 missing bankTransferRef), not-found (404) and already-processed (409) write zero audit logs")
    void shouldRejectInvalidAndAlreadyProcessedPayoutDecisionsWithoutWritingAuditLog() throws Exception {
        UUID payoutId = UUID.randomUUID();

        // Missing bankTransferRef for APPROVE_COMPLETED -> 400
        mockMvc.perform(post("/api/v1/admin/payments/payouts/{id}/decide", payoutId)
                        .header("Idempotency-Key", "idem-payout-400")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecidePayoutRequest(
                                PayoutDecision.APPROVE_COMPLETED,
                                null,
                                "Thiếu mã FT ngân hàng khi phê duyệt hoàn tất."
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_400"));

        verify(paymentFacade, never()).completePayoutRequest(any(), any(), any(), any());

        // Not found -> 404
        when(paymentFacade.rejectPayoutRequest(eq(payoutId), eq(adminUserId), any()))
                .thenThrow(new ResourceNotFoundException("Yêu cầu rút tiền", payoutId));
        mockMvc.perform(post("/api/v1/admin/payments/payouts/{id}/decide", payoutId)
                        .header("Idempotency-Key", "idem-payout-404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecidePayoutRequest(
                                PayoutDecision.REJECT_UNLOCK,
                                null,
                                "Không tìm thấy yêu cầu rút tiền trên hệ thống."
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RES_404"));

        // Conflict -> 409
        when(paymentFacade.completePayoutRequest(eq(payoutId), eq(adminUserId), eq("FT2630599"), any()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Yêu cầu rút tiền đã được xử lý trước đó."));
        mockMvc.perform(post("/api/v1/admin/payments/payouts/{id}/decide", payoutId)
                        .header("Idempotency-Key", "idem-payout-409")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecidePayoutRequest(
                                PayoutDecision.APPROVE_COMPLETED,
                                "FT2630599",
                                "Thử duyệt lại lệnh rút tiền đã hoàn tất trước đó."
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RES_409"));

        assertThat(auditLogStore).isEmpty();
    }
}
