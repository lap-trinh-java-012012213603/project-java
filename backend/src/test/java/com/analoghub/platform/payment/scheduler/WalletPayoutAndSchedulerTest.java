package com.analoghub.platform.payment.scheduler;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.DuplicateResourceException;
import com.analoghub.platform.payment.controller.PayoutController;
import com.analoghub.platform.payment.controller.WalletController;
import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransaction;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;
import com.analoghub.platform.payment.domain.PayoutRequest;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.CreatePayoutRequest;
import com.analoghub.platform.payment.dto.PayoutResponse;
import com.analoghub.platform.payment.dto.PayoutSummaryDto;
import com.analoghub.platform.payment.dto.WalletLedgerEntryDto;
import com.analoghub.platform.payment.dto.WalletSummaryResponse;
import com.analoghub.platform.payment.event.EscrowReleasedEvent;
import com.analoghub.platform.payment.event.PaymentFailedEvent;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PaymentTransactionRepository;
import com.analoghub.platform.payment.repository.PaymentWebhookLogRepository;
import com.analoghub.platform.payment.repository.PayoutRequestRepository;
import com.analoghub.platform.payment.repository.RefundRequestRepository;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import com.analoghub.platform.payment.service.EscrowHoldService;
import com.analoghub.platform.payment.service.PaymentCheckoutService;
import com.analoghub.platform.payment.service.WalletAndPayoutService;
import com.analoghub.platform.payment.service.WalletLedgerService;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPayoutAndSchedulerTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Mock
    private EscrowHoldRepository escrowHoldRepository;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentWebhookLogRepository paymentWebhookLogRepository;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WalletLedgerService walletLedgerService;
    private EscrowHoldService escrowHoldService;
    private PaymentCheckoutService paymentCheckoutService;
    private WalletAndPayoutService walletAndPayoutService;
    private WalletController walletController;
    private PayoutController payoutController;
    private PaymentExpirationScheduler paymentExpirationScheduler;
    private EscrowAutoReleaseScheduler escrowAutoReleaseScheduler;

    private final Map<UUID, Wallet> walletStore = new HashMap<>();
    private final List<WalletLedgerEntry> ledgerStore = new ArrayList<>();
    private final Map<UUID, EscrowHold> escrowByRefStore = new HashMap<>();
    private final List<PayoutRequest> payoutStore = new ArrayList<>();
    private final List<PaymentTransaction> txnStore = new ArrayList<>();

    private Wallet systemEscrowWallet;
    private Wallet systemRevenueWallet;

    @BeforeEach
    void setUp() {
        walletStore.clear();
        ledgerStore.clear();
        escrowByRefStore.clear();
        payoutStore.clear();
        txnStore.clear();

        walletLedgerService = new WalletLedgerService(walletRepository, walletLedgerEntryRepository);
        escrowHoldService = new EscrowHoldService(
                escrowHoldRepository,
                refundRequestRepository,
                walletLedgerService,
                platformConfigFacade,
                eventPublisher
        );
        paymentCheckoutService = new PaymentCheckoutService(
                paymentTransactionRepository,
                escrowHoldRepository,
                paymentWebhookLogRepository,
                walletLedgerService,
                null,
                null,
                platformConfigFacade,
                eventPublisher
        );
        walletAndPayoutService = new WalletAndPayoutService(
                walletRepository,
                escrowHoldRepository,
                payoutRequestRepository,
                walletLedgerService
        );
        walletController = new WalletController(walletAndPayoutService);
        payoutController = new PayoutController(walletAndPayoutService);
        paymentExpirationScheduler = new PaymentExpirationScheduler(paymentCheckoutService);
        escrowAutoReleaseScheduler = new EscrowAutoReleaseScheduler(escrowHoldService);

        systemEscrowWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                null,
                WalletType.SYSTEM_ESCROW,
                new BigDecimal("2000000.00")
        );
        systemRevenueWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000002"),
                null,
                WalletType.SYSTEM_REVENUE,
                new BigDecimal("0.00")
        );

        setupMocks();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Wallet createStoredWallet(UUID id, UUID ownerId, WalletType type, BigDecimal availableBalance) {
        Wallet wallet = Wallet.builder()
                .ownerUserId(ownerId)
                .walletType(type)
                .availableBalanceVnd(availableBalance)
                .lockedBalanceVnd(new BigDecimal("0.00"))
                .currency("VND")
                .build();
        wallet.setId(id);
        wallet.setUpdatedAt(Instant.now());
        walletStore.put(id, wallet);
        return wallet;
    }

    private void authenticateAs(UUID userId, Set<String> roles) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .roles(roles)
                .labIds(Set.of())
                .build();
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities)
        );
    }

    private void setupMocks() {
        lenient().when(walletRepository.findByWalletType(any(WalletType.class))).thenAnswer(inv -> {
            WalletType type = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> w.getWalletType() == type).findFirst();
        });

        lenient().when(walletRepository.findByOwnerUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> ownerId.equals(w.getOwnerUserId())).findFirst();
        });

        lenient().when(walletRepository.findByOwnerUserIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> ownerId.equals(w.getOwnerUserId())).findFirst();
        });

        lenient().when(walletRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(walletStore.get(inv.getArgument(0))));

        lenient().when(walletRepository.findAllByIdInOrderByIdAscForUpdate(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .map(walletStore::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(w -> w.getId().toString()))
                    .toList();
        });

        lenient().when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            if (w.getUpdatedAt() == null) {
                w.setUpdatedAt(Instant.now());
            }
            walletStore.put(w.getId(), w);
            return w;
        });

        lenient().when(walletRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<Wallet> wallets = inv.getArgument(0);
            wallets.forEach(w -> {
                if (w.getId() == null) {
                    w.setId(UUID.randomUUID());
                }
                walletStore.put(w.getId(), w);
            });
            return new ArrayList<>(wallets);
        });

        lenient().when(walletLedgerEntryRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<WalletLedgerEntry> entries = inv.getArgument(0);
            List<WalletLedgerEntry> saved = new ArrayList<>();
            for (WalletLedgerEntry e : entries) {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
                ledgerStore.add(e);
                saved.add(e);
            }
            return saved;
        });

        lenient().when(walletLedgerEntryRepository.searchWalletLedger(
                any(UUID.class), any(), any(), any(), any(), any(Pageable.class)
        )).thenAnswer(inv -> {
            UUID walletId = inv.getArgument(0);
            LedgerEntryDirection direction = inv.getArgument(1);
            LedgerEntryType type = inv.getArgument(2);
            Pageable pageable = inv.getArgument(5);
            List<WalletLedgerEntry> filtered = ledgerStore.stream()
                    .filter(e -> walletId.equals(e.getWalletId()))
                    .filter(e -> direction == null || e.getEntryDirection() == direction)
                    .filter(e -> type == null || e.getEntryType() == type)
                    .toList();
            return new PageImpl<>(filtered, pageable, filtered.size());
        });

        lenient().when(escrowHoldRepository.sumPendingReceivableByPayeeUserId(any(UUID.class), anyCollection()))
                .thenAnswer(inv -> {
                    UUID payeeId = inv.getArgument(0);
                    Collection<EscrowStatus> statuses = inv.getArgument(1);
                    return escrowByRefStore.values().stream()
                            .filter(e -> payeeId.equals(e.getPayeeUserId()) && statuses.contains(e.getStatus()))
                            .map(EscrowHold::getRemainingAmountVnd)
                            .reduce(new BigDecimal("0.00"), BigDecimal::add);
                });

        lenient().when(escrowHoldRepository.findDueForAutoRelease(anyCollection(), any(Instant.class)))
                .thenAnswer(inv -> {
                    Collection<EscrowStatus> statuses = inv.getArgument(0);
                    Instant now = inv.getArgument(1);
                    return escrowByRefStore.values().stream()
                            .filter(e -> statuses.contains(e.getStatus()))
                            .filter(e -> e.getAutoReleaseAt() != null && !e.getAutoReleaseAt().isAfter(now))
                            .toList();
                });

        lenient().when(escrowHoldRepository.findByReferenceTypeAndReferenceIdForUpdate(anyString(), any(UUID.class)))
                .thenAnswer(inv -> {
                    String refType = inv.getArgument(0);
                    UUID refId = inv.getArgument(1);
                    EscrowHold hold = escrowByRefStore.get(refId);
                    if (hold != null && refType.equalsIgnoreCase(hold.getReferenceType())) {
                        return Optional.of(hold);
                    }
                    return Optional.empty();
                });

        lenient().when(escrowHoldRepository.findByReferenceIdForUpdate(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(escrowByRefStore.get(inv.getArgument(0))));

        lenient().when(escrowHoldRepository.save(any(EscrowHold.class))).thenAnswer(inv -> {
            EscrowHold e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            escrowByRefStore.put(e.getReferenceId(), e);
            return e;
        });

        lenient().when(payoutRequestRepository.countByRequesterUserIdAndStatusIn(any(UUID.class), anyCollection()))
                .thenAnswer(inv -> {
                    UUID userId = inv.getArgument(0);
                    Collection<PayoutRequestStatus> statuses = inv.getArgument(1);
                    return payoutStore.stream()
                            .filter(p -> userId.equals(p.getRequesterUserId()) && statuses.contains(p.getStatus()))
                            .count();
                });

        lenient().when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(inv -> {
            PayoutRequest p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            if (p.getCreatedAt() == null) {
                p.setCreatedAt(Instant.now());
            }
            payoutStore.add(p);
            return p;
        });

        lenient().when(payoutRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(any(UUID.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    UUID userId = inv.getArgument(0);
                    Pageable pageable = inv.getArgument(1);
                    List<PayoutRequest> list = payoutStore.stream()
                            .filter(p -> userId.equals(p.getRequesterUserId()))
                            .toList();
                    return new PageImpl<>(list, pageable, list.size());
                });

        lenient().when(paymentTransactionRepository.findExpiredInitiatedTransactions(any(Instant.class)))
                .thenAnswer(inv -> {
                    Instant now = inv.getArgument(0);
                    return txnStore.stream()
                            .filter(t -> t.getStatus() == PaymentTransactionStatus.INITIATED
                                    && t.getExpiresAt() != null
                                    && !t.getExpiresAt().isAfter(now))
                            .toList();
                });

        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("WalletController + PayoutController: should view wallet summary, lock balance on POST /payouts, and list ledger entries")
    void shouldGetWalletSummaryAndLockBalanceOnPayoutRequest() {
        UUID labOwnerId = UUID.randomUUID();
        authenticateAs(labOwnerId, Set.of("LAB_OWNER"));

        Wallet labWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000050"),
                labOwnerId,
                WalletType.LAB_WALLET,
                new BigDecimal("1000000.00")
        );

        // Add a pending escrow receivable of 300,000 VND for this labOwner
        EscrowHold pendingHold = EscrowHold.builder()
                .paymentTxnId(UUID.randomUUID())
                .referenceType("LAB_ORDER")
                .referenceId(UUID.randomUUID())
                .payerUserId(UUID.randomUUID())
                .payeeUserId(labOwnerId)
                .grossAmountVnd(new BigDecimal("350000.00"))
                .refundedAmountVnd(new BigDecimal("50000.00"))
                .status(EscrowStatus.PARTIALLY_REFUNDED)
                .build();
        pendingHold.setId(UUID.randomUUID());
        escrowByRefStore.put(pendingHold.getReferenceId(), pendingHold);

        // 1. GET /api/v1/wallets/me
        ResponseEntity<ApiResponse<WalletSummaryResponse>> walletRes = walletController.getMyWallet();
        assertThat(walletRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletSummaryResponse summary = walletRes.getBody().getData();
        assertThat(summary.walletType()).isEqualTo(WalletType.LAB_WALLET);
        assertThat(summary.availableBalanceVnd()).isEqualByComparingTo("1000000.00");
        assertThat(summary.lockedBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(summary.totalBalanceVnd()).isEqualByComparingTo("1000000.00");
        assertThat(summary.pendingEscrowReceivableVnd()).isEqualByComparingTo("300000.00");

        // 2. POST /api/v1/payouts -> locks 400,000 VND in locked_balance_vnd
        CreatePayoutRequest payoutReq = new CreatePayoutRequest(
                new BigDecimal("400000.00"),
                "VCB",
                "0071001234567",
                "CONG TY TNHH SAIGON FILM LAB"
        );
        ResponseEntity<ApiResponse<PayoutResponse>> payoutRes = payoutController.requestPayout(payoutReq);
        assertThat(payoutRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PayoutResponse payoutData = payoutRes.getBody().getData();
        assertThat(payoutData.status()).isEqualTo(PayoutRequestStatus.PENDING_APPROVAL);
        assertThat(payoutData.availableBalanceAfterVnd()).isEqualByComparingTo("600000.00");
        assertThat(payoutData.lockedBalanceAfterVnd()).isEqualByComparingTo("400000.00");

        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("600000.00");
        assertThat(labWallet.getLockedBalanceVnd()).isEqualByComparingTo("400000.00");

        // 3. Verify ledger entries via GET /api/v1/wallets/me/ledger
        ResponseEntity<ApiResponse<PageResponse<WalletLedgerEntryDto>>> ledgerRes =
                walletController.getMyWalletLedger(null, LedgerEntryType.PAYOUT_LOCK, null, null, 0, 20);
        assertThat(ledgerRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ledgerRes.getBody().getData().item()).hasSize(2);

        // 4. Verify invalid date range throws BusinessException
        assertThatThrownBy(() -> walletController.getMyWalletLedger(
                null, null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), 0, 20
        )).isInstanceOf(BusinessException.class);

        // 5. GET /api/v1/payouts/me
        ResponseEntity<ApiResponse<PageResponse<PayoutSummaryDto>>> myPayoutsRes =
                payoutController.getMyPayouts(null, 0, 20);
        assertThat(myPayoutsRes.getBody().getData().item()).hasSize(1);
    }

    @Test
    @DisplayName("EscrowAutoReleaseScheduler should automatically release Escrows overdue by 72h without dispute and skip FROZEN_BY_DISPUTE Escrows")
    void shouldAutoReleaseOverdueEscrowsAfter72HoursWithoutDispute() {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID dueOrderId = UUID.randomUUID();
        UUID frozenOrderId = UUID.randomUUID();

        Wallet sellerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000060"),
                sellerId,
                WalletType.USER_WALLET,
                new BigDecimal("0.00")
        );

        // Escrow 1: Overdue by > 72h (delivered 73 hours ago -> autoReleaseAt is 1 hour in the past), status = HELD
        Instant delivered73HoursAgo = Instant.now().minus(Duration.ofHours(73));
        EscrowHold dueEscrow = EscrowHold.builder()
                .paymentTxnId(UUID.randomUUID())
                .referenceType("MARKETPLACE_ORDER")
                .referenceId(dueOrderId)
                .payerUserId(buyerId)
                .payeeUserId(sellerId)
                .grossAmountVnd(new BigDecimal("1000000.00"))
                .refundedAmountVnd(new BigDecimal("0.00"))
                .commissionFeeVnd(new BigDecimal("0.00"))
                .netPayoutAmountVnd(new BigDecimal("0.00"))
                .status(EscrowStatus.HELD)
                .autoReleaseAt(delivered73HoursAgo.plus(Duration.ofHours(72)))
                .build();
        dueEscrow.setId(UUID.randomUUID());
        escrowByRefStore.put(dueOrderId, dueEscrow);

        // Escrow 2: Also past 72h, but FROZEN_BY_DISPUTE -> must NOT be auto-released
        EscrowHold frozenEscrow = EscrowHold.builder()
                .paymentTxnId(UUID.randomUUID())
                .referenceType("MARKETPLACE_ORDER")
                .referenceId(frozenOrderId)
                .payerUserId(buyerId)
                .payeeUserId(sellerId)
                .grossAmountVnd(new BigDecimal("500000.00"))
                .refundedAmountVnd(new BigDecimal("0.00"))
                .commissionFeeVnd(new BigDecimal("0.00"))
                .netPayoutAmountVnd(new BigDecimal("0.00"))
                .status(EscrowStatus.FROZEN_BY_DISPUTE)
                .autoReleaseAt(delivered73HoursAgo.plus(Duration.ofHours(72)))
                .build();
        frozenEscrow.setId(UUID.randomUUID());
        escrowByRefStore.put(frozenOrderId, frozenEscrow);

        when(platformConfigFacade.calculateCommissionFee(eq(FeeScope.MARKETPLACE_SALE), eq(new BigDecimal("1000000.00"))))
                .thenReturn(new BigDecimal("50000.00"));

        // Run Scheduler #3: EscrowAutoReleaseScheduler
        escrowAutoReleaseScheduler.scanAndAutoReleaseDueEscrows();

        // Verify dueEscrow was released with 5% commission (50,000 VND) and net 950,000 VND
        assertThat(dueEscrow.getStatus()).isEqualTo(EscrowStatus.RELEASED_TO_PAYEE);
        assertThat(dueEscrow.getCommissionFeeVnd()).isEqualByComparingTo("50000.00");
        assertThat(dueEscrow.getNetPayoutAmountVnd()).isEqualByComparingTo("950000.00");
        assertThat(dueEscrow.getSettledAt()).isNotNull();

        assertThat(sellerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("950000.00");
        assertThat(systemRevenueWallet.getAvailableBalanceVnd()).isEqualByComparingTo("50000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("1000000.00");

        // Verify frozenEscrow remained untouched in FROZEN_BY_DISPUTE
        assertThat(frozenEscrow.getStatus()).isEqualTo(EscrowStatus.FROZEN_BY_DISPUTE);

        ArgumentCaptor<EscrowReleasedEvent> eventCaptor = ArgumentCaptor.forClass(EscrowReleasedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().referenceId()).isEqualTo(dueOrderId);
        assertThat(eventCaptor.getValue().netVnd()).isEqualByComparingTo("950000.00");
    }

    @Test
    @DisplayName("PaymentExpirationScheduler should expire overdue INITIATED transactions and publish PaymentFailedEvent")
    void shouldExpireOverdueInitiatedTransactions() {
        PaymentTransaction overdueTxn = PaymentTransaction.builder()
                .txnCode("PAY202611050000001111")
                .payerUserId(UUID.randomUUID())
                .payeeUserId(UUID.randomUUID())
                .paymentPurpose(PaymentPurpose.LAB_ORDER)
                .referenceId(UUID.randomUUID())
                .gatewayProvider(GatewayProvider.VNPAY)
                .amountVnd(new BigDecimal("250000.00"))
                .status(PaymentTransactionStatus.INITIATED)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5)))
                .build();
        overdueTxn.setId(UUID.randomUUID());
        txnStore.add(overdueTxn);

        paymentExpirationScheduler.scanAndExpireOverdueTransactions();

        assertThat(overdueTxn.getStatus()).isEqualTo(PaymentTransactionStatus.EXPIRED);
        ArgumentCaptor<PaymentFailedEvent> failedEventCaptor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishEvent(failedEventCaptor.capture());
        assertThat(failedEventCaptor.getValue().txnId()).isEqualTo(overdueTxn.getId());
    }
}
