package com.analoghub.platform.admin;

import com.analoghub.platform.admin.controller.AdminDisputeController;
import com.analoghub.platform.admin.controller.DisputeController;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.Dispute;
import com.analoghub.platform.admin.domain.DisputeEvidence;
import com.analoghub.platform.admin.dto.DisputeReasonCategory;
import com.analoghub.platform.admin.dto.DisputeResolutionDecision;
import com.analoghub.platform.admin.dto.DisputeType;
import com.analoghub.platform.admin.dto.OpenDisputeRequest;
import com.analoghub.platform.admin.dto.ResolveDisputeRequest;
import com.analoghub.platform.admin.dto.SubmitDisputeEvidenceRequest;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.DisputeEvidenceRepository;
import com.analoghub.platform.admin.repository.DisputeRepository;
import com.analoghub.platform.admin.service.DisputeResolutionService;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.facade.BookingOrderFacade;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.GlobalExceptionHandler;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.dto.MarketplaceOrderDto;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.payment.dto.EscrowSnapshotDto;
import com.analoghub.platform.payment.facade.PaymentFacade;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDisputeResolutionServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private BookingOrderFacade bookingOrderFacade;

    @Mock
    private MarketplaceFacade marketplaceFacade;

    @Mock
    private FilmLabFacade filmLabFacade;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private StorageService storageService;

    @Mock
    private NotificationFacade notificationFacade;

    private final Map<UUID, Dispute> disputeStore = new ConcurrentHashMap<>();
    private final Map<UUID, DisputeEvidence> evidenceStore = new ConcurrentHashMap<>();
    private final Map<UUID, EscrowSnapshotDto> escrowStore = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> walletBalances = new ConcurrentHashMap<>();
    private BigDecimal platformRevenueBalance = BigDecimal.ZERO;

    private DisputeResolutionService disputeService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID labOwnerId;
    private UUID labId;
    private UUID buyerId;
    private UUID sellerId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        disputeStore.clear();
        evidenceStore.clear();
        escrowStore.clear();
        walletBalances.clear();
        platformRevenueBalance = BigDecimal.ZERO;

        customerId = UUID.randomUUID();
        labOwnerId = UUID.randomUUID();
        labId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        walletBalances.put(customerId, new BigDecimal("0.00"));
        walletBalances.put(labOwnerId, new BigDecimal("0.00"));
        walletBalances.put(buyerId, new BigDecimal("0.00"));
        walletBalances.put(sellerId, new BigDecimal("0.00"));

        wireRepositories();
        wireStatefulPaymentFacade();

        disputeService = new DisputeResolutionService(
                disputeRepository,
                disputeEvidenceRepository,
                auditLogRepository,
                paymentFacade,
                bookingOrderFacade,
                marketplaceFacade,
                filmLabFacade,
                iamFacade,
                storageService,
                notificationFacade
        );

        DisputeController disputeController = new DisputeController(disputeService);
        AdminDisputeController adminDisputeController = new AdminDisputeController(disputeService);
        mockMvc = MockMvcBuilders.standaloneSetup(disputeController, adminDisputeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, Set<String> roles) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .jti(UUID.randomUUID().toString())
                .roles(roles)
                .labIds(Set.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void wireRepositories() {
        lenient().when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            if (d.getCreatedAt() == null) {
                d.setCreatedAt(Instant.now());
            }
            d.setUpdatedAt(Instant.now());
            d.syncSerializedFields();
            disputeStore.put(d.getId(), d);
            return d;
        });
        lenient().when(disputeRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(disputeStore.get(inv.getArgument(0))));
        lenient().when(disputeRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(disputeStore.get(inv.getArgument(0))));
        lenient().when(disputeRepository.findFirstByOrderIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID orderId = inv.getArgument(0);
                    return disputeStore.values().stream()
                            .filter(d -> orderId.equals(d.getOrderId()))
                            .max(Comparator.comparing(Dispute::getCreatedAt));
                });
        lenient().when(disputeRepository.findAllByOrderByCreatedAtDesc())
                .thenAnswer(inv -> disputeStore.values().stream()
                        .sorted(Comparator.comparing(Dispute::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList());

        lenient().when(disputeEvidenceRepository.save(any(DisputeEvidence.class))).thenAnswer(inv -> {
            DisputeEvidence ev = inv.getArgument(0);
            if (ev.getId() == null) {
                ev.setId(UUID.randomUUID());
            }
            if (ev.getCreatedAt() == null) {
                ev.setCreatedAt(Instant.now());
            }
            evidenceStore.put(ev.getId(), ev);
            return ev;
        });
        lenient().when(disputeEvidenceRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<DisputeEvidence> items = (Iterable<DisputeEvidence>) inv.getArgument(0);
            List<DisputeEvidence> saved = new ArrayList<>();
            for (DisputeEvidence ev : items) {
                saved.add(disputeEvidenceRepository.save(ev));
            }
            return saved;
        });
        lenient().when(disputeEvidenceRepository.findByDisputeIdOrderByCreatedAtAsc(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID dId = inv.getArgument(0);
                    return evidenceStore.values().stream()
                            .filter(e -> dId.equals(e.getDisputeId()))
                            .sorted(Comparator.comparing(DisputeEvidence::getCreatedAt))
                            .toList();
                });
        lenient().when(disputeEvidenceRepository.findByDisputeIdInOrderByCreatedAtAsc(anyCollection()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Set<UUID> ids = Set.copyOf((java.util.Collection<UUID>) inv.getArgument(0));
                    return evidenceStore.values().stream()
                            .filter(e -> ids.contains(e.getDisputeId()))
                            .sorted(Comparator.comparing(DisputeEvidence::getCreatedAt))
                            .toList();
                });

        lenient().when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog audit = inv.getArgument(0);
            if (audit.getId() == null) {
                audit.setId(UUID.randomUUID());
            }
            return audit;
        });
    }

    private void wireStatefulPaymentFacade() {
        lenient().when(paymentFacade.getEscrowSnapshot(any(UUID.class), anyString())).thenAnswer(inv ->
                Optional.ofNullable(escrowStore.get(inv.getArgument(0))));

        lenient().doAnswer(inv -> {
            UUID refId = inv.getArgument(0);
            EscrowSnapshotDto current = escrowStore.get(refId);
            if (current == null) {
                throw new IllegalStateException("Escrow hold not found for " + refId);
            }
            if (current.terminal()) {
                throw new IllegalStateException("Cannot freeze terminal Escrow");
            }
            escrowStore.put(refId, new EscrowSnapshotDto(
                    current.escrowId(),
                    current.referenceType(),
                    current.referenceId(),
                    current.payerUserId(),
                    current.payeeUserId(),
                    "FROZEN_BY_DISPUTE",
                    current.grossAmountVnd(),
                    current.refundedAmountVnd(),
                    current.remainingAmountVnd(),
                    current.commissionFeeVnd(),
                    current.netPayoutAmountVnd(),
                    false,
                    null
            ));
            return null;
        }).when(paymentFacade).freezeForDispute(any(UUID.class), anyString(), any(UUID.class));

        lenient().doAnswer(inv -> {
            UUID refId = inv.getArgument(0);
            EscrowSnapshotDto current = escrowStore.get(refId);
            BigDecimal refund = current.remainingAmountVnd();
            walletBalances.merge(current.payerUserId(), refund, BigDecimal::add);
            escrowStore.put(refId, new EscrowSnapshotDto(
                    current.escrowId(),
                    current.referenceType(),
                    current.referenceId(),
                    current.payerUserId(),
                    current.payeeUserId(),
                    "REFUNDED_TO_PAYER",
                    current.grossAmountVnd(),
                    current.grossAmountVnd(),
                    new BigDecimal("0.00"),
                    new BigDecimal("0.00"),
                    new BigDecimal("0.00"),
                    true,
                    Instant.now()
            ));
            return null;
        }).when(paymentFacade).fullRefund(any(UUID.class), anyString(), anyString());

        lenient().doAnswer(inv -> {
            UUID refId = inv.getArgument(0);
            BigDecimal buyerRefund = inv.getArgument(2);
            BigDecimal sellerGross = inv.getArgument(3);
            EscrowSnapshotDto current = escrowStore.get(refId);

            BigDecimal fee = sellerGross.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellerNet = sellerGross.subtract(fee).setScale(2, RoundingMode.HALF_UP);

            if (buyerRefund.compareTo(BigDecimal.ZERO) > 0) {
                walletBalances.merge(current.payerUserId(), buyerRefund, BigDecimal::add);
            }
            if (sellerNet.compareTo(BigDecimal.ZERO) > 0) {
                walletBalances.merge(current.payeeUserId(), sellerNet, BigDecimal::add);
            }
            if (fee.compareTo(BigDecimal.ZERO) > 0) {
                platformRevenueBalance = platformRevenueBalance.add(fee);
            }

            String nextStatus = (buyerRefund.compareTo(BigDecimal.ZERO) > 0 && sellerGross.compareTo(BigDecimal.ZERO) > 0)
                    ? "SPLIT_SETTLED"
                    : "RELEASED_TO_PAYEE";

            escrowStore.put(refId, new EscrowSnapshotDto(
                    current.escrowId(),
                    current.referenceType(),
                    current.referenceId(),
                    current.payerUserId(),
                    current.payeeUserId(),
                    nextStatus,
                    current.grossAmountVnd(),
                    current.refundedAmountVnd().add(buyerRefund),
                    new BigDecimal("0.00"),
                    fee,
                    sellerNet,
                    true,
                    Instant.now()
            ));
            return null;
        }).when(paymentFacade).resolveDisputeSplit(any(UUID.class), anyString(), any(BigDecimal.class), any(BigDecimal.class));
    }

    private UUID seedLabOrder(BigDecimal amountVnd) {
        UUID orderId = UUID.randomUUID();
        when(bookingOrderFacade.getOrderDetails(orderId)).thenReturn(new LabOrderResponse(
                orderId,
                "LAB-20261101-1024",
                customerId,
                labId,
                "SCAN_DELIVERED",
                "PICKUP_DELIVERY",
                "DIGITAL_ONLY",
                "HOLD_30_DAYS",
                1,
                amountVnd,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                amountVnd,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of()
        ));
        when(filmLabFacade.findLabById(labId)).thenReturn(Optional.of(new FilmLabFacade.ProvisionedLabInfo(
                labId,
                labOwnerId,
                "saigon-analog-lab",
                "Saigon Analog Lab",
                "ACTIVE"
        )));
        escrowStore.put(orderId, new EscrowSnapshotDto(
                UUID.randomUUID(),
                "LAB_ORDER",
                orderId,
                customerId,
                labOwnerId,
                "HELD",
                amountVnd,
                new BigDecimal("0.00"),
                amountVnd,
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                false,
                null
        ));
        return orderId;
    }

    private UUID seedMarketOrder(BigDecimal amountVnd) {
        UUID marketOrderId = UUID.randomUUID();
        when(marketplaceFacade.getOrderById(marketOrderId)).thenReturn(Optional.of(new MarketplaceOrderDto(
                marketOrderId,
                "MKT-20261101-2048",
                buyerId,
                sellerId,
                "DIRECT_PURCHASE",
                amountVnd,
                "DELIVERED_INSPECTING"
        )));
        escrowStore.put(marketOrderId, new EscrowSnapshotDto(
                UUID.randomUUID(),
                "MARKETPLACE_ORDER",
                marketOrderId,
                buyerId,
                sellerId,
                "HELD",
                amountVnd,
                new BigDecimal("0.00"),
                amountVnd,
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                false,
                null
        ));
        return marketOrderId;
    }

    @Test
    @DisplayName("POST /api/v1/disputes opens LAB_ORDER_DISPUTE, freezes Escrow, marks LabOrder DISPUTED, blocks duplicates and non-participants")
    void shouldOpenLabOrderDisputeFreezeEscrowAndEnforceGuards() throws Exception {
        UUID orderId = seedLabOrder(new BigDecimal("280000.00"));

        // Non-participant is rejected with 403 Forbidden
        authenticateAs(UUID.randomUUID(), Set.of("CUSTOMER"));
        OpenDisputeRequest openReq = new OpenDisputeRequest(
                DisputeType.LAB_ORDER_DISPUTE,
                orderId,
                DisputeReasonCategory.SEVERE_SCAN_DEFECT,
                "Cuộn Kodak Portra 400 bị dính nhiều vết bụi lớn và đường xước dọc xuyên suốt 18 frames khi scan.",
                new BigDecimal("120000.00"),
                List.of("private/disputes/0195f150/scratch-frame18.jpg"),
                null
        );
        mockMvc.perform(post("/api/v1/disputes")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));

        // Customer opens dispute -> 201 Created
        authenticateAs(customerId, Set.of("CUSTOMER"));
        String resp = mockMvc.perform(post("/api/v1/disputes")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disputeType").value("LAB_ORDER_DISPUTE"))
                .andExpect(jsonPath("$.data.referenceOrderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.orderCode").value("LAB-20261101-1024"))
                .andExpect(jsonPath("$.data.complainantUserId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.respondentUserId").value(labOwnerId.toString()))
                .andExpect(jsonPath("$.data.status").value("AWAITING_RESPONDENT_EVIDENCE"))
                .andExpect(jsonPath("$.data.escrowHoldStatus").value("FROZEN_BY_DISPUTE"))
                .andExpect(jsonPath("$.data.frozenEscrowAmountVnd").value(280000.0))
                .andExpect(jsonPath("$.data.evidenceCount").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID disputeId = UUID.fromString(objectMapper.readTree(resp).path("data").path("disputeId").asText());
        assertThat(escrowStore.get(orderId).status()).isEqualTo("FROZEN_BY_DISPUTE");
        verify(paymentFacade).freezeForDispute(orderId, "LAB_ORDER", disputeId);
        verify(bookingOrderFacade).updateOrderStatus(orderId, "DISPUTED");
        verify(notificationFacade).sendSystemAlert(eq(labOwnerId), contains("DSP-"), contains("LAB-20261101-1024"));

        // Verify Dispute metadata serialization/deserialization round-trip on @PostLoad
        Dispute saved = disputeStore.get(disputeId);
        Dispute reloaded = Dispute.builder()
                .id(saved.getId())
                .orderId(saved.getOrderId())
                .reporterId(saved.getReporterId())
                .reason(saved.getReason())
                .status(saved.getStatus())
                .resolutionNotes(saved.getResolutionNotes())
                .createdAt(saved.getCreatedAt())
                .build();
        reloaded.hydrateTransientFields();
        assertThat(reloaded.getDisputeCode()).isEqualTo(saved.getDisputeCode());
        assertThat(reloaded.getDisputeType()).isEqualTo("LAB_ORDER_DISPUTE");
        assertThat(reloaded.getRespondentUserId()).isEqualTo(labOwnerId);
        assertThat(reloaded.getRequestedRefundAmountVnd()).isEqualByComparingTo("120000.00");

        // Duplicate dispute for same order -> 409 Conflict
        mockMvc.perform(post("/api/v1/disputes")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/disputes/{id}/evidences verifies S3 object, advances status to UNDER_ADMIN_REVIEW on respondent upload")
    void shouldSubmitEvidenceVerifyStorageAndAdvanceStatus() throws Exception {
        UUID orderId = seedLabOrder(new BigDecimal("280000.00"));
        authenticateAs(customerId, Set.of("CUSTOMER"));
        var opened = disputeService.openDispute(new OpenDisputeRequest(
                DisputeType.LAB_ORDER_DISPUTE,
                orderId,
                DisputeReasonCategory.SEVERE_SCAN_DEFECT,
                "Cuộn Kodak Portra 400 bị dính nhiều vết bụi lớn và đường xước dọc xuyên suốt 18 frames khi scan.",
                new BigDecimal("120000.00"),
                List.of(),
                null
        ));

        String missingKey = "private/disputes/0195f150/missing.jpg";
        String validKey = "private/disputes/0195f150/lab-lightbox-macro.jpg";
        when(storageService.exists(missingKey)).thenReturn(false);
        when(storageService.exists(validKey)).thenReturn(true);
        when(storageService.resolveReadUrl(validKey))
                .thenReturn("https://minio.analoghub.vn/analoghub-bucket/" + validKey + "?X-Amz-Expires=900");

        authenticateAs(labOwnerId, Set.of("LAB_OWNER"));
        mockMvc.perform(post("/api/v1/disputes/{id}/evidences", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitDisputeEvidenceRequest(
                                missingKey,
                                "Ảnh chụp âm bản trên bàn soi đèn Lightbox chứng minh vết xước từ máy ảnh."
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.STORAGE_OBJECT_NOT_FOUND.getCode()));

        mockMvc.perform(post("/api/v1/disputes/{id}/evidences", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitDisputeEvidenceRequest(
                                validKey,
                                "Ảnh chụp âm bản trên bàn soi đèn Lightbox chứng minh vết xước nằm chéo trên mặt đế film."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partyRole").value("RESPONDENT"))
                .andExpect(jsonPath("$.data.mediaS3Key").value(validKey))
                .andExpect(jsonPath("$.data.presignedViewUrl").value(
                        "https://minio.analoghub.vn/analoghub-bucket/" + validKey + "?X-Amz-Expires=900"))
                .andExpect(jsonPath("$.data.disputeStatusAfterUpload").value("UNDER_ADMIN_REVIEW"));

        assertThat(disputeStore.get(opened.disputeId()).getStatus()).isEqualTo("UNDER_ADMIN_REVIEW");
    }

    @Test
    @DisplayName("GET /api/v1/admin/disputes & /{id} and POST /{id}/resolve SPLIT settles Escrow proportionally into buyer, lab and revenue wallets")
    void shouldListInspectAndResolveSplitDisputeProportionally() throws Exception {
        UUID orderId = seedLabOrder(new BigDecimal("280000.00"));
        authenticateAs(customerId, Set.of("CUSTOMER"));
        var opened = disputeService.openDispute(new OpenDisputeRequest(
                DisputeType.LAB_ORDER_DISPUTE,
                orderId,
                DisputeReasonCategory.SEVERE_SCAN_DEFECT,
                "Cuộn Kodak Portra 400 bị dính nhiều vết bụi lớn và đường xước dọc xuyên suốt 18 frames khi scan.",
                new BigDecimal("120000.00"),
                List.of("private/disputes/0195f150/scratch.jpg"),
                null
        ));

        when(storageService.resolveReadUrl("private/disputes/0195f150/scratch.jpg"))
                .thenReturn("https://minio.analoghub.vn/signed/scratch.jpg");
        when(iamFacade.findUsersByIds(anyCollection())).thenReturn(Map.of(
                customerId, new UserSummaryDto(customerId, "customer@analoghub.vn", "0901112222",
                        "Trần Gia Huy", null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.CUSTOMER), Map.of(), Instant.now()),
                labOwnerId, new UserSummaryDto(labOwnerId, "ops@saigonlab.vn", "0903334444",
                        "Saigon Analog Lab", null, AccountStatus.ACTIVE, AuthProvider.LOCAL, Set.of(RoleCode.LAB_OWNER), Map.of(), Instant.now())
        ));

        authenticateAs(adminId, Set.of("ADMIN"));
        mockMvc.perform(get("/api/v1/admin/disputes")
                        .param("status", "AWAITING_RESPONDENT_EVIDENCE")
                        .param("disputeType", "LAB_ORDER_DISPUTE")
                        .param("disputeCode", opened.disputeCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.item[0].disputeId").value(opened.disputeId().toString()))
                .andExpect(jsonPath("$.data.item[0].evidenceCount").value(1));

        mockMvc.perform(get("/api/v1/admin/disputes/{id}", opened.disputeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disputeId").value(opened.disputeId().toString()))
                .andExpect(jsonPath("$.data.complainant.email").value("customer@analoghub.vn"))
                .andExpect(jsonPath("$.data.respondent.email").value("ops@saigonlab.vn"))
                .andExpect(jsonPath("$.data.escrowSnapshot.status").value("FROZEN_BY_DISPUTE"))
                .andExpect(jsonPath("$.data.escrowSnapshot.disputableRemainingAmountVnd").value(280000.0))
                .andExpect(jsonPath("$.data.evidences[0].presignedViewUrl").value("https://minio.analoghub.vn/signed/scratch.jpg"));

        // Resolve SPLIT: 120,000 VND refund to buyer + 160,000 VND gross to Lab (10% fee = 16,000, net = 144,000)
        ResolveDisputeRequest splitReq = new ResolveDisputeRequest(
                DisputeResolutionDecision.SPLIT,
                new BigDecimal("120000.00"),
                new BigDecimal("160000.00"),
                false,
                "Quá trình tráng hóa chất C-41 đạt chuẩn nên giữ phần phí tráng 160.000đ cho Lab, hoàn 120.000đ phí scan cho khách."
        );
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Forwarded-For", "118.69.182.45")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(splitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newStatus").value("RESOLVED_SPLIT"))
                .andExpect(jsonPath("$.data.orderStatusUpdatedTo").value("COMPLETED"))
                .andExpect(jsonPath("$.data.financialSettlement.escrowStatusAfter").value("SPLIT_SETTLED"))
                .andExpect(jsonPath("$.data.financialSettlement.refundedToBuyerVnd").value(120000.0))
                .andExpect(jsonPath("$.data.financialSettlement.grossReleasedToSellerVnd").value(160000.0))
                .andExpect(jsonPath("$.data.financialSettlement.platformCommissionFeeVnd").value(16000.0))
                .andExpect(jsonPath("$.data.financialSettlement.netCreditedToSellerWalletVnd").value(144000.0));

        assertThat(walletBalances.get(customerId)).isEqualByComparingTo("120000.00");
        assertThat(walletBalances.get(labOwnerId)).isEqualByComparingTo("144000.00");
        assertThat(platformRevenueBalance).isEqualByComparingTo("16000.00");
        assertThat(escrowStore.get(orderId).status()).isEqualTo("SPLIT_SETTLED");
        assertThat(disputeStore.get(opened.disputeId()).getStatus()).isEqualTo("RESOLVED_SPLIT");

        verify(paymentFacade).resolveDisputeSplit(
                orderId, "LAB_ORDER", new BigDecimal("120000.00"), new BigDecimal("160000.00"));
        verify(bookingOrderFacade).updateOrderStatus(orderId, "COMPLETED");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getActionType()).isEqualTo("RESOLVE_DISPUTE");
        assertThat(auditCaptor.getValue().getTargetEntity()).isEqualTo("ADM_DISPUTE");
        assertThat(auditCaptor.getValue().getIpAddress()).isEqualTo("118.69.182.45");

        verify(notificationFacade).sendSystemAlert(eq(customerId), any(), contains("RESOLVED_SPLIT"));
        verify(notificationFacade).sendSystemAlert(eq(labOwnerId), any(), contains("RESOLVED_SPLIT"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/disputes/{id}/resolve handles REFUND_BUYER and RELEASE_SELLER for MARKETPLACE_ORDER_DISPUTE")
    void shouldResolveMarketplaceDisputesForRefundBuyerAndReleaseSeller() throws Exception {
        // 1. REFUND_BUYER on 500,000 VND Marketplace Order
        UUID orderRefundId = seedMarketOrder(new BigDecimal("500000.00"));
        authenticateAs(buyerId, Set.of("CUSTOMER"));
        var refundDispute = disputeService.openDispute(new OpenDisputeRequest(
                DisputeType.MARKETPLACE_ORDER_DISPUTE,
                orderRefundId,
                DisputeReasonCategory.SHUTTER_METER_DEFECT,
                "Máy ảnh film nhận được bị kẹt màn trập tốc độ 1/1000s và đo sáng sai 3 stops so với mô tả.",
                new BigDecimal("500000.00"),
                List.of(),
                null
        ));

        authenticateAs(adminId, Set.of("ADMIN"));
        ResolveDisputeRequest refundReq = new ResolveDisputeRequest(
                DisputeResolutionDecision.REFUND_BUYER,
                new BigDecimal("500000.00"),
                new BigDecimal("0.00"),
                false,
                "Xác nhận lỗi màn trập kẹt ở tốc độ cao qua video mở hộp, chấp thuận hoàn tiền 100% cho người mua."
        );
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", refundDispute.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("RESOLVED_REFUND_BUYER"))
                .andExpect(jsonPath("$.data.orderStatusUpdatedTo").value("REFUNDED"))
                .andExpect(jsonPath("$.data.financialSettlement.escrowStatusAfter").value("REFUNDED_TO_PAYER"))
                .andExpect(jsonPath("$.data.financialSettlement.refundedToBuyerVnd").value(500000.0));

        assertThat(walletBalances.get(buyerId)).isEqualByComparingTo("500000.00");
        verify(paymentFacade).fullRefund(eq(orderRefundId), eq("MARKETPLACE_ORDER"), contains("DISPUTE_REFUND_BUYER"));
        verify(marketplaceFacade).resolveOrderDispute(orderRefundId, "REFUND_BUYER");

        // 2. RELEASE_SELLER on 400,000 VND Marketplace Order
        UUID orderReleaseId = seedMarketOrder(new BigDecimal("400000.00"));
        authenticateAs(buyerId, Set.of("CUSTOMER"));
        var releaseDispute = disputeService.openDispute(new OpenDisputeRequest(
                DisputeType.MARKETPLACE_ORDER_DISPUTE,
                orderReleaseId,
                DisputeReasonCategory.ITEM_NOT_AS_DESCRIBED,
                "Khiếu nại ngoại hình thân máy có vết xước nhỏ ở mặt đáy không ảnh hưởng hoạt động.",
                new BigDecimal("400000.00"),
                List.of(),
                null
        ));

        authenticateAs(adminId, Set.of("ADMIN"));
        ResolveDisputeRequest releaseReq = new ResolveDisputeRequest(
                DisputeResolutionDecision.RELEASE_SELLER,
                new BigDecimal("0.00"),
                new BigDecimal("400000.00"),
                false,
                "Ảnh chụp tin đăng của người bán đã thể hiện rõ vết xước đáy máy trước khi giao dịch, giải ngân cho người bán."
        );
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", releaseDispute.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newStatus").value("RESOLVED_RELEASE_SELLER"))
                .andExpect(jsonPath("$.data.orderStatusUpdatedTo").value("COMPLETED"))
                .andExpect(jsonPath("$.data.financialSettlement.escrowStatusAfter").value("RELEASED_TO_PAYEE"))
                .andExpect(jsonPath("$.data.financialSettlement.grossReleasedToSellerVnd").value(400000.0))
                .andExpect(jsonPath("$.data.financialSettlement.platformCommissionFeeVnd").value(40000.0))
                .andExpect(jsonPath("$.data.financialSettlement.netCreditedToSellerWalletVnd").value(360000.0));

        assertThat(walletBalances.get(sellerId)).isEqualByComparingTo("360000.00");
        assertThat(platformRevenueBalance).isEqualByComparingTo("40000.00");
        verify(paymentFacade).resolveDisputeSplit(
                orderReleaseId, "MARKETPLACE_ORDER", new BigDecimal("0.00"), new BigDecimal("400000.00"));
        verify(marketplaceFacade).resolveOrderDispute(orderReleaseId, "RELEASE_SELLER");
    }

    @Test
    @DisplayName("POST /api/v1/admin/disputes/{id}/resolve enforces amount mismatch (400), not found (404), and already resolved (409)")
    void shouldRejectInvalidAmountsMissingDisputeAndDuplicateResolution() throws Exception {
        authenticateAs(adminId, Set.of("ADMIN"));
        UUID missingDisputeId = UUID.randomUUID();

        ResolveDisputeRequest validSplit = new ResolveDisputeRequest(
                DisputeResolutionDecision.SPLIT,
                new BigDecimal("100000.00"),
                new BigDecimal("180000.00"),
                false,
                "Bản án phân xử hợp lệ có độ dài lớn hơn 20 ký tự để kiểm tra lỗi."
        );

        // 404 Not Found
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", missingDisputeId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSplit)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));

        UUID orderId = seedLabOrder(new BigDecimal("280000.00"));
        authenticateAs(customerId, Set.of("CUSTOMER"));
        var opened = disputeService.openDispute(new OpenDisputeRequest(
                DisputeType.LAB_ORDER_DISPUTE,
                orderId,
                DisputeReasonCategory.DAMAGED_NEGATIVE,
                "Âm bản bị trầy xước trong quá trình xử lý hóa chất tại Lab và cần được bồi hoàn hợp lý.",
                new BigDecimal("140000.00"),
                List.of(),
                null
        ));

        authenticateAs(adminId, Set.of("ADMIN"));

        // 400: sum != remaining Escrow (150k + 150k = 300k != 280k)
        ResolveDisputeRequest mismatchSum = new ResolveDisputeRequest(
                DisputeResolutionDecision.SPLIT,
                new BigDecimal("150000.00"),
                new BigDecimal("150000.00"),
                false,
                "Tổng tiền phân chia không khớp với số dư Escrow đang bị đóng băng trên hệ thống."
        );
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchSum)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        // 400: REFUND_BUYER with releaseToSellerVnd > 0
        ResolveDisputeRequest invalidRefundCombo = new ResolveDisputeRequest(
                DisputeResolutionDecision.REFUND_BUYER,
                new BigDecimal("200000.00"),
                new BigDecimal("80000.00"),
                false,
                "Quyết định REFUND_BUYER không được phép chia phần giải ngân cho người bán."
        );
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefundCombo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        // Valid resolution succeeds -> 200 OK
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSplit)))
                .andExpect(status().isOk());

        // 409 Conflict when resolving a second time
        mockMvc.perform(post("/api/v1/admin/disputes/{id}/resolve", opened.disputeId())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSplit)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }
}
