package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransaction;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;
import com.analoghub.platform.payment.domain.PaymentWebhookLog;
import com.analoghub.platform.payment.domain.PayoutRequest;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import com.analoghub.platform.payment.domain.RefundRequest;
import com.analoghub.platform.payment.domain.RefundRequestStatus;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletLedgerServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    private WalletLedgerService walletLedgerService;

    private final Map<UUID, Wallet> walletStore = new HashMap<>();
    private final List<WalletLedgerEntry> ledgerStore = new ArrayList<>();

    private Wallet systemEscrowWallet;
    private Wallet systemRevenueWallet;

    @BeforeEach
    void setUp() {
        walletStore.clear();
        ledgerStore.clear();

        walletLedgerService = new WalletLedgerService(walletRepository, walletLedgerEntryRepository);

        systemEscrowWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                null,
                WalletType.SYSTEM_ESCROW,
                new BigDecimal("0.00")
        );
        systemRevenueWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000002"),
                null,
                WalletType.SYSTEM_REVENUE,
                new BigDecimal("0.00")
        );

        lenient().when(walletRepository.findByWalletType( any(WalletType.class))).thenAnswer(inv -> {
            WalletType type = inv.getArgument(0);
            return walletStore.values().stream()
                    .filter(w -> w.getWalletType() == type)
                    .findFirst();
        });

        lenient().when(walletRepository.findByWalletTypeForUpdate(any(WalletType.class))).thenAnswer(inv -> {
            WalletType type = inv.getArgument(0);
            return walletStore.values().stream()
                    .filter(w -> w.getWalletType() == type)
                    .findFirst();
        });

        lenient().when(walletRepository.findByOwnerUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream()
                    .filter(w -> ownerId.equals(w.getOwnerUserId()))
                    .findFirst();
        });

        lenient().when(walletRepository.findByOwnerUserIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream()
                    .filter(w -> ownerId.equals(w.getOwnerUserId()))
                    .findFirst();
        });

        lenient().when(walletRepository.findByIdForUpdate(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(walletStore.get(id));
        });

        lenient().when(walletRepository.findAllByIdInOrderByIdAscForUpdate(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .map(walletStore::get)
                    .filter(java.util.Objects::nonNull)
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
            List<Wallet> saved = new ArrayList<>();
            for (Wallet w : wallets) {
                if (w.getId() == null) {
                    w.setId(UUID.randomUUID());
                }
                walletStore.put(w.getId(), w);
                saved.add(w);
            }
            return saved;
        });

        lenient().when(walletLedgerEntryRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<WalletLedgerEntry> entries = inv.getArgument(0);
            List<WalletLedgerEntry> saved = new ArrayList<>();
            for (WalletLedgerEntry entry : entries) {
                if (entry.getId() == null) {
                    entry.setId(UUID.randomUUID());
                }
                if (entry.getCreatedAt() == null) {
                    entry.setCreatedAt(Instant.now());
                }
                ledgerStore.add(entry);
                saved.add(entry);
            }
            return saved;
        });

        lenient().when(walletLedgerEntryRepository.findByJournalGroupIdOrderByCreatedAtAsc(any(UUID.class))).thenAnswer(inv -> {
            UUID groupId = inv.getArgument(0);
            return ledgerStore.stream()
                    .filter(e -> groupId.equals(e.getJournalGroupId()))
                    .toList();
        });
    }

    private Wallet createStoredWallet(UUID id, UUID ownerUserId, WalletType type, BigDecimal availableBalance) {
        Wallet wallet = Wallet.builder()
                .ownerUserId(ownerUserId)
                .walletType(type)
                .availableBalanceVnd(availableBalance)
                .lockedBalanceVnd(new BigDecimal("0.00"))
                .currency("VND")
                .build();
        wallet.setId(id);
        walletStore.put(id, wallet);
        return wallet;
    }

    @Test
    @DisplayName("All 7 pay_* domain entities and enums should initialize with valid defaults and immutability guards")
    void shouldInitializeAllPaymentDomainEntitiesAndEnforceImmutability() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .ownerUserId(userId)
                .walletType(WalletType.USER_WALLET)
                .build();
        assertThat(wallet.getAvailableBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(wallet.getLockedBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(wallet.getTotalBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(wallet.getCurrency()).isEqualTo("VND");

        PaymentTransaction txn = PaymentTransaction.builder()
                .txnCode("PAY202611050325001234")
                .payerUserId(userId)
                .paymentPurpose(PaymentPurpose.LAB_ORDER)
                .referenceId(UUID.randomUUID())
                .gatewayProvider(GatewayProvider.VNPAY)
                .amountVnd(new BigDecimal("315000.00"))
                .status(PaymentTransactionStatus.INITIATED)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        assertThat(txn.getStatus()).isEqualTo(PaymentTransactionStatus.INITIATED);

        EscrowHold escrow = EscrowHold.builder()
                .paymentTxnId(UUID.randomUUID())
                .referenceType("LAB_ORDER")
                .referenceId(txn.getReferenceId())
                .payerUserId(userId)
                .payeeUserId(UUID.randomUUID())
                .grossAmountVnd(new BigDecimal("315000.00"))
                .status(EscrowStatus.HELD)
                .build();
        assertThat(escrow.getRefundedAmountVnd()).isEqualByComparingTo("0.00");
        assertThat(escrow.getCommissionFeeVnd()).isEqualByComparingTo("0.00");
        assertThat(escrow.getNetPayoutAmountVnd()).isEqualByComparingTo("0.00");
        assertThat(escrow.getRemainingAmountVnd()).isEqualByComparingTo("315000.00");

        RefundRequest refund = RefundRequest.builder()
                .escrowId(UUID.randomUUID())
                .refundReason("BLANK_ROLL_SCAN_FEE")
                .refundAmountVnd(new BigDecimal("60000.00"))
                .status(RefundRequestStatus.PROCESSED)
                .processedAt(Instant.now())
                .build();
        assertThat(refund.getStatus()).isEqualTo(RefundRequestStatus.PROCESSED);

        PayoutRequest payout = PayoutRequest.builder()
                .walletId(UUID.randomUUID())
                .requesterUserId(userId)
                .amountVnd(new BigDecimal("500000.00"))
                .bankCode("VCB")
                .bankAccountNo("0071001234567")
                .bankAccountName("NGUYEN VAN A")
                .status(PayoutRequestStatus.PENDING_APPROVAL)
                .build();
        assertThat(payout.getStatus()).isEqualTo(PayoutRequestStatus.PENDING_APPROVAL);

        PaymentWebhookLog webhookLog = PaymentWebhookLog.builder()
                .provider("VNPAY")
                .eventIdempotencyKey("VNPAY:PAY123:14398512:00")
                .rawPayload(Map.of("vnp_ResponseCode", "00"))
                .signatureValid(true)
                .processingStatus("PROCESSED")
                .build();
        assertThat(webhookLog.isSignatureValid()).isTrue();

        WalletLedgerEntry ledgerEntry = WalletLedgerEntry.builder()
                .journalGroupId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .entryDirection(LedgerEntryDirection.DEBIT)
                .entryType(LedgerEntryType.ESCROW_IN)
                .amountVnd(new BigDecimal("315000.00"))
                .balanceAfterVnd(new BigDecimal("0.00"))
                .referenceType("LAB_ORDER")
                .referenceId(UUID.randomUUID())
                .build();
        assertThatThrownBy(ledgerEntry::preventMutation)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Double-Entry Invariant: Every transaction type must produce equal total DEBIT and total CREDIT within the same journal_group_id")
    void shouldMaintainDoubleEntryInvariantAcrossAllTransactionTypes() {
        UUID customerId = UUID.randomUUID();
        UUID labOwnerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        Wallet customerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000010"),
                customerId,
                WalletType.USER_WALLET,
                new BigDecimal("500000.00")
        );
        Wallet labWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000020"),
                labOwnerId,
                WalletType.LAB_WALLET,
                new BigDecimal("100000.00")
        );
        Wallet sellerWallet = createStoredWallet(
                UUID.fromString("00000000-0000-7000-8000-000000000030"),
                sellerId,
                WalletType.USER_WALLET,
                new BigDecimal("0.00")
        );

        // 1. Gateway Escrow-In (VNPay -> SYSTEM_ESCROW: 315,000 VND)
        UUID txn1 = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        List<WalletLedgerEntry> gatewayEntries = walletLedgerService.recordGatewayEscrowIn(
                customerId,
                new BigDecimal("315000.00"),
                txn1,
                "LAB_ORDER",
                order1
        );
        assertJournalBalanced(gatewayEntries, new BigDecimal("315000.00"));
        assertThat(customerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("500000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("315000.00");

        // 2. Blank Roll Partial Refund (SYSTEM_ESCROW -> Customer Wallet: 65,000 VND)
        UUID roll1 = UUID.randomUUID();
        List<WalletLedgerEntry> blankRollEntries = walletLedgerService.recordBlankRollRefund(
                customerId,
                new BigDecimal("65000.00"),
                txn1,
                roll1
        );
        assertJournalBalanced(blankRollEntries, new BigDecimal("65000.00"));
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("250000.00");
        assertThat(customerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("565000.00");

        // 3. Escrow Release with Platform Commission (SYSTEM_ESCROW -> Lab Wallet 237,500 + SYSTEM_REVENUE 12,500)
        List<WalletLedgerEntry> releaseEntries = walletLedgerService.recordEscrowRelease(
                labOwnerId,
                WalletType.LAB_WALLET,
                new BigDecimal("250000.00"),
                new BigDecimal("12500.00"),
                txn1,
                "LAB_ORDER",
                order1
        );
        assertThat(releaseEntries).hasSize(3);
        assertJournalBalanced(releaseEntries, new BigDecimal("250000.00"));
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("337500.00");
        assertThat(systemRevenueWallet.getAvailableBalanceVnd()).isEqualByComparingTo("12500.00");

        // 4. Internal Wallet Escrow-In (Customer Wallet -> SYSTEM_ESCROW: 400,000 VND)
        UUID txn2 = UUID.randomUUID();
        UUID mktOrder = UUID.randomUUID();
        List<WalletLedgerEntry> internalEscrowEntries = walletLedgerService.recordInternalWalletEscrowIn(
                customerId,
                new BigDecimal("400000.00"),
                txn2,
                "MARKETPLACE_ORDER",
                mktOrder
        );
        assertJournalBalanced(internalEscrowEntries, new BigDecimal("400000.00"));
        assertThat(customerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("165000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("400000.00");

        // 5. Dispute Split Resolution (SYSTEM_ESCROW 400,000 -> Buyer 150,000 + Seller 237,500 + Fee 12,500)
        List<WalletLedgerEntry> disputeEntries = walletLedgerService.recordDisputeSplit(
                customerId,
                sellerId,
                WalletType.USER_WALLET,
                new BigDecimal("150000.00"),
                new BigDecimal("237500.00"),
                new BigDecimal("12500.00"),
                txn2,
                "MARKETPLACE_ORDER",
                mktOrder
        );
        assertThat(disputeEntries).hasSize(4);
        assertJournalBalanced(disputeEntries, new BigDecimal("400000.00"));
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("0.00");
        assertThat(customerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("315000.00");
        assertThat(sellerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("237500.00");
        assertThat(systemRevenueWallet.getAvailableBalanceVnd()).isEqualByComparingTo("25000.00");

        // 6. Payout Lock & Settle on Lab Wallet (200,000 VND)
        UUID payoutId = UUID.randomUUID();
        List<WalletLedgerEntry> payoutLockEntries = walletLedgerService.recordPayoutLock(
                labWallet.getId(),
                new BigDecimal("200000.00"),
                payoutId
        );
        assertJournalBalanced(payoutLockEntries, new BigDecimal("200000.00"));
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("137500.00");
        assertThat(labWallet.getLockedBalanceVnd()).isEqualByComparingTo("200000.00");

        List<WalletLedgerEntry> payoutSettleEntries = walletLedgerService.recordPayoutSettle(
                labWallet.getId(),
                new BigDecimal("200000.00"),
                payoutId
        );
        assertJournalBalanced(payoutSettleEntries, new BigDecimal("200000.00"));
        assertThat(labWallet.getAvailableBalanceVnd()).isEqualByComparingTo("137500.00");
        assertThat(labWallet.getLockedBalanceVnd()).isEqualByComparingTo("0.00");

        // 7. Global Ledger Invariant Check across all recorded journal groups
        Map<UUID, List<WalletLedgerEntry>> byJournalGroup = ledgerStore.stream()
                .collect(Collectors.groupingBy(WalletLedgerEntry::getJournalGroupId));
        assertThat(byJournalGroup).hasSize(7);
        byJournalGroup.forEach((groupId, entries) -> {
            assertThat(walletLedgerService.verifyJournalBalanced(groupId)).isTrue();
            BigDecimal groupDebit = sumByDirection(entries, LedgerEntryDirection.DEBIT);
            BigDecimal groupCredit = sumByDirection(entries, LedgerEntryDirection.CREDIT);
            assertThat(groupDebit).isEqualByComparingTo(groupCredit);
        });

        BigDecimal globalDebit = sumByDirection(ledgerStore, LedgerEntryDirection.DEBIT);
        BigDecimal globalCredit = sumByDirection(ledgerStore, LedgerEntryDirection.CREDIT);
        assertThat(globalDebit).isEqualByComparingTo(globalCredit);
    }

    @Test
    @DisplayName("lockWalletsInOrder should sort wallet IDs ascending before SELECT FOR UPDATE to prevent deadlocks")
    void shouldLockWalletsInAscendingIdOrder() {
        UUID highId = UUID.fromString("ffffffff-ffff-7fff-8fff-ffffffffffff");
        UUID lowId = UUID.fromString("00000000-0000-7000-8000-000000000005");
        createStoredWallet(highId, UUID.randomUUID(), WalletType.USER_WALLET, new BigDecimal("500000.00"));
        createStoredWallet(lowId, UUID.randomUUID(), WalletType.USER_WALLET, new BigDecimal("100000.00"));

        walletLedgerService.recordTransfer(
                highId,
                lowId,
                new BigDecimal("50000.00"),
                LedgerEntryType.FULL_REFUND,
                UUID.randomUUID(),
                "LAB_ORDER",
                UUID.randomUUID()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(walletRepository).findAllByIdInOrderByIdAscForUpdate(idsCaptor.capture());

        List<UUID> requestedIds = new ArrayList<>(idsCaptor.getValue());
        assertThat(requestedIds).containsExactly(lowId, highId);
    }

    @Test
    @DisplayName("WalletLedgerService should reject unbalanced journals or insufficient wallet balance")
    void shouldRejectUnbalancedJournalOrInsufficientBalance() {
        UUID userId = UUID.randomUUID();
        Wallet userWallet = createStoredWallet(
                UUID.randomUUID(),
                userId,
                WalletType.USER_WALLET,
                new BigDecimal("50000.00")
        );

        // Insufficient balance for internal wallet checkout
        assertThatThrownBy(() -> walletLedgerService.recordInternalWalletEscrowIn(
                userId,
                new BigDecimal("100000.00"),
                UUID.randomUUID(),
                "LAB_ORDER",
                UUID.randomUUID()
        ))
                .isInstanceOf(BusinessException.class);

        verify(walletLedgerEntryRepository, never()).saveAll(anyCollection());

        // Unbalanced custom journal legs (DEBIT 100,000 != CREDIT 90,000)
        List<WalletLedgerService.JournalLeg> unbalancedLegs = List.of(
                new WalletLedgerService.JournalLeg(
                        userWallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        LedgerEntryType.ESCROW_IN,
                        new BigDecimal("100000.00"),
                        new BigDecimal("0.00")
                ),
                new WalletLedgerService.JournalLeg(
                        systemEscrowWallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        LedgerEntryType.ESCROW_IN,
                        new BigDecimal("90000.00"),
                        new BigDecimal("90000.00")
                )
        );

        assertThatThrownBy(() -> walletLedgerService.postBalancedJournal(
                unbalancedLegs,
                UUID.randomUUID(),
                "LAB_ORDER",
                UUID.randomUUID()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEBIT")
                .hasMessageContaining("CREDIT");
    }

    private void assertJournalBalanced(List<WalletLedgerEntry> entries, BigDecimal expectedTotal) {
        assertThat(entries).hasSizeGreaterThanOrEqualTo(2);
        UUID journalGroupId = entries.getFirst().getJournalGroupId();
        assertThat(journalGroupId).isNotNull();
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getJournalGroupId()).isEqualTo(journalGroupId);
            assertThat(e.getAmountVnd()).isPositive();
        });

        BigDecimal totalDebit = sumByDirection(entries, LedgerEntryDirection.DEBIT);
        BigDecimal totalCredit = sumByDirection(entries, LedgerEntryDirection.CREDIT);
        assertThat(totalDebit).isEqualByComparingTo(expectedTotal);
        assertThat(totalCredit).isEqualByComparingTo(expectedTotal);
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    }

    private BigDecimal sumByDirection(List<WalletLedgerEntry> entries, LedgerEntryDirection direction) {
        return entries.stream()
                .filter(e -> e.getEntryDirection() == direction)
                .map(WalletLedgerEntry::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
