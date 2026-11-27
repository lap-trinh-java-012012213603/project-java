package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.DuplicateResourceException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.PayoutRequest;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.CreatePayoutRequest;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutResponse;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;
import com.analoghub.platform.payment.event.PayoutCompletedEvent;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.payment.facade.PaymentFacadeImpl;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PayoutRequestRepository;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentPayoutAdminFacadeTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Mock
    private EscrowHoldRepository escrowHoldRepository;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WalletLedgerService walletLedgerService;
    private WalletAndPayoutService walletAndPayoutService;
    private PaymentFacade paymentFacade;

    private final Map<UUID, Wallet> walletStore = new HashMap<>();
    private final List<WalletLedgerEntry> ledgerStore = new ArrayList<>();
    private final Map<UUID, PayoutRequest> payoutStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        walletStore.clear();
        ledgerStore.clear();
        payoutStore.clear();

        walletLedgerService = new WalletLedgerService(walletRepository, walletLedgerEntryRepository);
        EscrowHoldService escrowHoldService = new EscrowHoldService(
                escrowHoldRepository,
                mock(com.analoghub.platform.payment.repository.RefundRequestRepository.class),
                walletLedgerService,
                platformConfigFacade,
                eventPublisher
        );
        walletAndPayoutService = new WalletAndPayoutService(
                walletRepository,
                escrowHoldRepository,
                payoutRequestRepository,
                walletLedgerService,
                eventPublisher
        );
        paymentFacade = new PaymentFacadeImpl(escrowHoldService, walletAndPayoutService);

        createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                null,
                WalletType.SYSTEM_ESCROW,
                new BigDecimal("1000000.00")
        );
        createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000002"),
                null,
                WalletType.SYSTEM_REVENUE,
                new BigDecimal("250000.00")
        );

        when(walletRepository.findAll()).thenAnswer(inv -> new ArrayList<>(walletStore.values()));
        when(walletRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.stream().map(walletStore::get).filter(java.util.Objects::nonNull).toList();
        });
        when(walletRepository.findById(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(walletStore.get(inv.getArgument(0)))
        );
        when(walletRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(walletStore.get(inv.getArgument(0)))
        );
        when(walletRepository.findByOwnerUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> ownerId.equals(w.getOwnerUserId())).findFirst();
        });
        when(walletRepository.findByOwnerUserIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> ownerId.equals(w.getOwnerUserId())).findFirst();
        });
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            walletStore.put(w.getId(), w);
            return w;
        });

        when(walletLedgerEntryRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<WalletLedgerEntry> entries = inv.getArgument(0);
            List<WalletLedgerEntry> saved = new ArrayList<>();
            for (WalletLedgerEntry e : entries) {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
                if (e.getCreatedAt() == null) {
                    e.setCreatedAt(Instant.now());
                }
                ledgerStore.add(e);
                saved.add(e);
            }
            return saved;
        });
        when(walletLedgerEntryRepository.findAll()).thenAnswer(inv -> new ArrayList<>(ledgerStore));
        when(walletLedgerEntryRepository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            Pageable pageable = inv.getArgument(1);
            return new PageImpl<>(new ArrayList<>(ledgerStore), pageable, ledgerStore.size());
        });

        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(inv -> {
            PayoutRequest p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            if (p.getCreatedAt() == null) {
                p.setCreatedAt(Instant.now());
            }
            payoutStore.put(p.getId(), p);
            return p;
        });
        when(payoutRequestRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(payoutStore.get(inv.getArgument(0)))
        );
        when(payoutRequestRepository.findAll()).thenAnswer(inv -> new ArrayList<>(payoutStore.values()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Wallet createStoredWallet(UUID id, UUID ownerId, WalletType type, BigDecimal available) {
        Wallet wallet = Wallet.builder()
                .ownerUserId(ownerId)
                .walletType(type)
                .availableBalanceVnd(available)
                .lockedBalanceVnd(new BigDecimal("0.00"))
                .currency("VND")
                .build();
        wallet.setId(id);
        walletStore.put(id, wallet);
        return wallet;
    }

    private void authenticateAs(UUID userId, String role) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .jti(UUID.randomUUID().toString())
                .roles(Set.of(role))
                .labIds(Set.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    @DisplayName("completePayoutRequest deducts lockedBalanceVnd, records balanced PAYOUT_SETTLE ledger entries and publishes PayoutCompletedEvent")
    void shouldCompletePayoutSettleLockedBalanceAndPublishPayoutCompletedEvent() {
        UUID labOwnerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Wallet labWallet = createStoredWallet(
                UUID.randomUUID(),
                labOwnerId,
                WalletType.LAB_WALLET,
                new BigDecimal("5750000.00")
        );

        authenticateAs(labOwnerId, "LAB_OWNER");
        PayoutResponse payoutReq = walletAndPayoutService.requestPayout(new CreatePayoutRequest(
                new BigDecimal("4500000.00"),
                "VCB",
                "0071001234567",
                "CONG TY TNHH LLAB SAIGON"
        ));

        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("1250000.00");
        assertThat(labWallet.getLockedBalanceVnd()).isEqualByComparingTo("4500000.00");

        PayoutSettlementDto settlement = paymentFacade.completePayoutRequest(
                payoutReq.payoutId(),
                adminId,
                "FT263059988112233-VCB",
                "Đã chuyển khoản Napas 247 thành công"
        );

        assertThat(settlement.previousStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(settlement.newStatus()).isEqualTo("COMPLETED");
        assertThat(settlement.bankTransferRef()).isEqualTo("FT263059988112233-VCB");
        assertThat(settlement.entryType()).isEqualTo("PAYOUT_SETTLE");
        assertThat(settlement.availableBalanceAfterVnd()).isEqualByComparingTo("1250000.00");
        assertThat(settlement.lockedBalanceAfterVnd()).isEqualByComparingTo("0.00");
        assertThat(labWallet.getLockedBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("1250000.00");

        ArgumentCaptor<PayoutCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PayoutCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payoutId()).isEqualTo(payoutReq.payoutId());
        assertThat(eventCaptor.getValue().requesterUserId()).isEqualTo(labOwnerId);
        assertThat(eventCaptor.getValue().amountVnd()).isEqualByComparingTo("4500000.00");
        assertThat(eventCaptor.getValue().bankRef()).isEqualTo("FT263059988112233-VCB");

        LedgerReconciliationReportDto report = paymentFacade.reconcileLedger();
        assertThat(report.balanced()).isTrue();
        assertThat(report.discrepancyVnd()).isEqualByComparingTo("0.00");
        assertThat(report.totalDebitVnd()).isEqualByComparingTo("9000000.00");
        assertThat(report.totalCreditVnd()).isEqualByComparingTo("9000000.00");
        assertThat(report.unbalancedJournalGroupIds()).isEmpty();
        assertThat(report.pendingPayoutCount()).isZero();
        assertThat(report.systemEscrowBalanceVnd()).isEqualByComparingTo("1000000.00");
        assertThat(report.systemRevenueBalanceVnd()).isEqualByComparingTo("250000.00");

        // Re-approving the same payout must throw DuplicateResourceException (409)
        assertThatThrownBy(() -> paymentFacade.completePayoutRequest(
                payoutReq.payoutId(), adminId, "FT-RETRY", "Retry"
        )).isInstanceOf(DuplicateResourceException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("rejectPayoutRequest unlocks lockedBalanceVnd back to availableBalanceVnd without emitting PayoutCompletedEvent")
    void shouldRejectPayoutAndUnlockBalanceWithoutPublishingCompletedEvent() {
        UUID sellerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Wallet sellerWallet = createStoredWallet(
                UUID.randomUUID(),
                sellerId,
                WalletType.USER_WALLET,
                new BigDecimal("2000000.00")
        );

        authenticateAs(sellerId, "CUSTOMER");
        PayoutResponse payoutReq = walletAndPayoutService.requestPayout(new CreatePayoutRequest(
                new BigDecimal("500000.00"),
                "TCB",
                "190311223344",
                "NGUYEN VAN B"
        ));
        assertThat(sellerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("1500000.00");
        assertThat(sellerWallet.getLockedBalanceVnd()).isEqualByComparingTo("500000.00");

        PayoutSettlementDto rejection = paymentFacade.rejectPayoutRequest(
                payoutReq.payoutId(),
                adminId,
                "Tên chủ tài khoản không khớp CCCD"
        );

        assertThat(rejection.newStatus()).isEqualTo("REJECTED");
        assertThat(rejection.availableBalanceAfterVnd()).isEqualByComparingTo("2000000.00");
        assertThat(rejection.lockedBalanceAfterVnd()).isEqualByComparingTo("0.00");
        assertThat(sellerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("2000000.00");
        assertThat(sellerWallet.getLockedBalanceVnd()).isEqualByComparingTo("0.00");

        verify(eventPublisher, never()).publishEvent(any(PayoutCompletedEvent.class));

        LedgerReconciliationReportDto report = paymentFacade.reconcileLedger();
        assertThat(report.balanced()).isTrue();
        assertThat(report.pendingPayoutCount()).isZero();
    }

    @Test
    @DisplayName("reconcileLedger detects unbalanced journal groups and missing payout throws 404")
    void shouldDetectUnbalancedJournalInReconciliationAndThrowOnMissingPayout() {
        UUID corruptedGroupId = UUID.randomUUID();
        ledgerStore.add(WalletLedgerEntry.builder()
                .id(UUID.randomUUID())
                .journalGroupId(corruptedGroupId)
                .walletId(UUID.randomUUID())
                .entryDirection(LedgerEntryDirection.DEBIT)
                .entryType(LedgerEntryType.PAYOUT_SETTLE)
                .amountVnd(new BigDecimal("100000.00"))
                .balanceAfterVnd(new BigDecimal("0.00"))
                .referenceType("PAYOUT_REQUEST")
                .referenceId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        LedgerReconciliationReportDto report = paymentFacade.reconcileLedger();
        assertThat(report.balanced()).isFalse();
        assertThat(report.discrepancyVnd()).isEqualByComparingTo("100000.00");
        assertThat(report.unbalancedJournalGroupIds()).containsExactly(corruptedGroupId);

        assertThatThrownBy(() -> paymentFacade.completePayoutRequest(
                UUID.randomUUID(), UUID.randomUUID(), "FT12345", "Note"
        )).isInstanceOf(ResourceNotFoundException.class);
    }
}
