package com.analoghub.platform.payment.service;

import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.RefundRequest;
import com.analoghub.platform.payment.domain.RefundRequestStatus;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.EscrowHoldRequest;
import com.analoghub.platform.payment.event.BlankRollRefundProcessedEvent;
import com.analoghub.platform.payment.event.EscrowReleasedEvent;
import com.analoghub.platform.payment.event.RefundProcessedEvent;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.payment.facade.PaymentFacadeImpl;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.RefundRequestRepository;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
class EscrowHoldServiceAndFacadeTest {

    @Mock
    private EscrowHoldRepository escrowHoldRepository;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WalletLedgerService walletLedgerService;
    private EscrowHoldService escrowHoldService;
    private PaymentFacade paymentFacade;

    private final Map<UUID, Wallet> walletStore = new HashMap<>();
    private final List<WalletLedgerEntry> ledgerStore = new ArrayList<>();
    private final Map<UUID, EscrowHold> escrowByRefStore = new HashMap<>();
    private final List<RefundRequest> refundStore = new ArrayList<>();

    private Wallet systemEscrowWallet;
    private Wallet systemRevenueWallet;

    @BeforeEach
    void setUp() {
        walletStore.clear();
        ledgerStore.clear();
        escrowByRefStore.clear();
        refundStore.clear();

        walletLedgerService = new WalletLedgerService(walletRepository, walletLedgerEntryRepository);
        escrowHoldService = new EscrowHoldService(
                escrowHoldRepository,
                refundRequestRepository,
                walletLedgerService,
                platformConfigFacade,
                eventPublisher
        );
        paymentFacade = new PaymentFacadeImpl(escrowHoldService);

        systemEscrowWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                null,
                WalletType.SYSTEM_ESCROW,
                new BigDecimal("1000000.00")
        );
        systemRevenueWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000002"),
                null,
                WalletType.SYSTEM_REVENUE,
                new BigDecimal("0.00")
        );

        setupMocks();
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
        walletStore.put(id, wallet);
        return wallet;
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

        lenient().when(escrowHoldRepository.save(any(EscrowHold.class))).thenAnswer(inv -> {
            EscrowHold e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            escrowByRefStore.put(e.getReferenceId(), e);
            return e;
        });

        lenient().when(escrowHoldRepository.findByReferenceId(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(escrowByRefStore.get(inv.getArgument(0))));

        lenient().when(escrowHoldRepository.findByReferenceIdForUpdate(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(escrowByRefStore.get(inv.getArgument(0))));

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

        lenient().when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            refundStore.add(r);
            return r;
        });
    }

    @Test
    @DisplayName("processBlankRollPartialRefund + releaseEscrow should refund scan fee to customer, keep dev fee in Escrow, and settle remaining gross with platform commission")
    void shouldProcessBlankRollPartialRefundAndReleaseRemainingEscrowWithCommission() {
        UUID orderId = UUID.randomUUID();
        UUID rollId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID labOwnerId = UUID.randomUUID();
        UUID txnId = UUID.randomUUID();

        Wallet customerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000010"),
                customerId,
                WalletType.USER_WALLET,
                new BigDecimal("0.00")
        );
        Wallet labWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000020"),
                labOwnerId,
                WalletType.LAB_WALLET,
                new BigDecimal("100000.00")
        );

        // Hold 315,000 VND in Escrow for LAB_ORDER
        UUID escrowId = paymentFacade.hold(new EscrowHoldRequest(
                txnId,
                "LAB_ORDER",
                orderId,
                customerId,
                labOwnerId,
                new BigDecimal("315000.00")
        ));
        assertThat(escrowId).isNotNull();

        // 1. Blank roll detected -> partial refund of scanFeeSnapshotVnd = 65,000 VND
        paymentFacade.processBlankRollPartialRefund(
                orderId,
                rollId,
                customerId,
                new BigDecimal("65000.00"),
                "BLANK_ROLL_SCAN_FEE"
        );

        EscrowHold escrowAfterPartial = escrowByRefStore.get(orderId);
        assertThat(escrowAfterPartial.getStatus()).isEqualTo(EscrowStatus.PARTIALLY_REFUNDED);
        assertThat(escrowAfterPartial.getRefundedAmountVnd()).isEqualByComparingTo("65000.00");
        assertThat(escrowAfterPartial.getRemainingAmountVnd()).isEqualByComparingTo("250000.00");

        assertThat(customerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("65000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("935000.00");
        assertThat(refundStore).hasSize(1);
        assertThat(refundStore.getFirst().getStatus()).isEqualTo(RefundRequestStatus.PROCESSED);
        assertThat(refundStore.getFirst().getRefundAmountVnd()).isEqualByComparingTo("65000.00");

        ArgumentCaptor<BlankRollRefundProcessedEvent> blankRollEventCaptor =
                ArgumentCaptor.forClass(BlankRollRefundProcessedEvent.class);
        verify(eventPublisher).publishEvent(blankRollEventCaptor.capture());
        assertThat(blankRollEventCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(blankRollEventCaptor.getValue().rollId()).isEqualTo(rollId);
        assertThat(blankRollEventCaptor.getValue().refundedScanFeeVnd()).isEqualByComparingTo("65000.00");

        // 2. Order completes -> releaseEscrow on remaining 250,000 VND with 5% commission (12,500 VND)
        when(platformConfigFacade.calculateCommissionFee(eq(FeeScope.LAB_ORDER), eq(new BigDecimal("250000.00"))))
                .thenReturn(new BigDecimal("12500.00"));

        paymentFacade.releaseEscrow(orderId, "LAB_ORDER");

        EscrowHold escrowAfterRelease = escrowByRefStore.get(orderId);
        assertThat(escrowAfterRelease.getStatus()).isEqualTo(EscrowStatus.RELEASED_TO_PAYEE);
        assertThat(escrowAfterRelease.getCommissionFeeVnd()).isEqualByComparingTo("12500.00");
        assertThat(escrowAfterRelease.getNetPayoutAmountVnd()).isEqualByComparingTo("237500.00");
        assertThat(escrowAfterRelease.getSettledAt()).isNotNull();

        // Lab wallet received 237,500 VND (100,000 + 237,500 = 337,500 VND)
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("337500.00");
        // System revenue wallet received 12,500 VND
        assertThat(systemRevenueWallet.getAvailableBalanceVnd()).isEqualByComparingTo("12500.00");
        // System escrow decreased by 250,000 VND (935,000 - 250,000 = 685,000 VND)
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("685000.00");

        // Verify 3-leg balanced journal for Escrow release
        List<WalletLedgerEntry> releaseEntries = ledgerStore.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.ESCROW_RELEASE
                        || e.getEntryType() == LedgerEntryType.PLATFORM_FEE)
                .toList();
        assertThat(releaseEntries).hasSize(3);
        BigDecimal releaseDebit = releaseEntries.stream()
                .filter(e -> e.getEntryDirection() == LedgerEntryDirection.DEBIT)
                .map(WalletLedgerEntry::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal releaseCredit = releaseEntries.stream()
                .filter(e -> e.getEntryDirection() == LedgerEntryDirection.CREDIT)
                .map(WalletLedgerEntry::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(releaseDebit).isEqualByComparingTo("250000.00");
        assertThat(releaseCredit).isEqualByComparingTo("250000.00");

        ArgumentCaptor<EscrowReleasedEvent> releaseEventCaptor =
                ArgumentCaptor.forClass(EscrowReleasedEvent.class);
        verify(eventPublisher).publishEvent(releaseEventCaptor.capture());
        assertThat(releaseEventCaptor.getValue().referenceId()).isEqualTo(orderId);
        assertThat(releaseEventCaptor.getValue().netVnd()).isEqualByComparingTo("237500.00");

        // Calling releaseEscrow a second time should be idempotent
        paymentFacade.releaseEscrow(orderId, "LAB_ORDER");
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("337500.00");
    }

    @Test
    @DisplayName("refundEscrow should refund 100% of remaining escrow amount back to payer wallet and publish RefundProcessedEvent")
    void shouldRefundFullEscrowToPayer() {
        UUID orderId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID payeeId = UUID.randomUUID();

        Wallet payerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000030"),
                payerId,
                WalletType.USER_WALLET,
                new BigDecimal("50000.00")
        );

        paymentFacade.hold(new EscrowHoldRequest(
                UUID.randomUUID(),
                "LAB_ORDER",
                orderId,
                payerId,
                payeeId,
                new BigDecimal("200000.00")
        ));

        paymentFacade.refundEscrow(orderId, "LAB_ORDER", "ORDER_CANCELLED");

        EscrowHold escrow = escrowByRefStore.get(orderId);
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.REFUNDED_TO_PAYER);
        assertThat(escrow.getRefundedAmountVnd()).isEqualByComparingTo("200000.00");
        assertThat(escrow.getSettledAt()).isNotNull();

        assertThat(payerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("250000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("800000.00");

        ArgumentCaptor<RefundProcessedEvent> refundEventCaptor =
                ArgumentCaptor.forClass(RefundProcessedEvent.class);
        verify(eventPublisher).publishEvent(refundEventCaptor.capture());
        assertThat(refundEventCaptor.getValue().referenceId()).isEqualTo(orderId);
        assertThat(refundEventCaptor.getValue().refundAmountVnd()).isEqualByComparingTo("200000.00");
    }

    @Test
    @DisplayName("freezeForDispute and resolveDisputeSplit should freeze Escrow and settle split between buyer, seller and platform fee")
    void shouldFreezeForDisputeAndResolveDisputeSplit() {
        UUID marketOrderId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();

        Wallet buyerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000040"),
                buyerId,
                WalletType.USER_WALLET,
                new BigDecimal("0.00")
        );
        Wallet sellerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000050"),
                sellerId,
                WalletType.USER_WALLET,
                new BigDecimal("0.00")
        );

        paymentFacade.hold(new EscrowHoldRequest(
                UUID.randomUUID(),
                "MARKETPLACE_ORDER",
                marketOrderId,
                buyerId,
                sellerId,
                new BigDecimal("500000.00")
        ));

        paymentFacade.freezeForDispute(marketOrderId, "MARKETPLACE_ORDER", disputeId);
        assertThat(escrowByRefStore.get(marketOrderId).getStatus()).isEqualTo(EscrowStatus.FROZEN_BY_DISPUTE);

        // Normal release while frozen must be rejected
        assertThatThrownBy(() -> paymentFacade.releaseEscrow(marketOrderId, "MARKETPLACE_ORDER"))
                .isInstanceOf(IllegalStateException.class);

        // Resolve dispute: 200,000 VND to buyer, 300,000 VND gross to seller (5% fee = 15,000 VND, net = 285,000 VND)
        when(platformConfigFacade.calculateCommissionFee(eq(FeeScope.MARKETPLACE_SALE), eq(new BigDecimal("300000.00"))))
                .thenReturn(new BigDecimal("15000.00"));

        paymentFacade.resolveDisputeSplit(
                marketOrderId,
                "MARKETPLACE_ORDER",
                new BigDecimal("200000.00"),
                new BigDecimal("300000.00")
        );

        EscrowHold settled = escrowByRefStore.get(marketOrderId);
        assertThat(settled.getStatus()).isEqualTo(EscrowStatus.SPLIT_SETTLED);
        assertThat(settled.getRefundedAmountVnd()).isEqualByComparingTo("200000.00");
        assertThat(settled.getCommissionFeeVnd()).isEqualByComparingTo("15000.00");
        assertThat(settled.getNetPayoutAmountVnd()).isEqualByComparingTo("285000.00");

        assertThat(buyerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("200000.00");
        assertThat(sellerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("285000.00");
        assertThat(systemRevenueWallet.getAvailableBalanceVnd()).isEqualByComparingTo("15000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("500000.00");
    }
}
