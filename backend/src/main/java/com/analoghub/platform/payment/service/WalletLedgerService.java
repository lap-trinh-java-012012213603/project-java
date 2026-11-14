package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.LedgerEntryType;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Double-Entry Ledger Service for {@code payment} module (Principle 5 & Principle 7).
 * <p>
 * Guarantees that:
 * <ul>
 *   <li>Every financial mutation locks participating wallets in deterministic {@code wallet.id ASC} order
 *       using {@code SELECT ... FOR UPDATE} to eliminate deadlocks and race conditions.</li>
 *   <li>Every journal group produces both {@code DEBIT} and {@code CREDIT} entries sharing a single
 *       {@code journal_group_id} where {@code SUM(DEBIT) == SUM(CREDIT)} strictly holds.</li>
 *   <li>All rows in {@code pay_wallet_ledger_entries} are immutable append-only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WalletLedgerService {

    public static final String REF_TYPE_FILM_ROLL = "FILM_ROLL";
    public static final String REF_TYPE_PAYOUT_REQUEST = "PAYOUT_REQUEST";

    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;

    public record JournalLeg(
            UUID walletId,
            LedgerEntryDirection direction,
            LedgerEntryType entryType,
            BigDecimal amountVnd,
            BigDecimal balanceAfterVnd
    ) {
        public JournalLeg {
            Objects.requireNonNull(walletId, "walletId must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
            Objects.requireNonNull(entryType, "entryType must not be null");
            if (amountVnd == null || amountVnd.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Ledger leg amountVnd must be strictly positive (> 0)");
            }
            Objects.requireNonNull(balanceAfterVnd, "balanceAfterVnd must not be null");
        }
    }

    @Transactional
    public Wallet getOrCreateUserWallet(UUID ownerUserId) {
        return getOrCreateUserWallet(ownerUserId, WalletType.USER_WALLET);
    }

    @Transactional
    public Wallet getOrCreateUserWallet(UUID ownerUserId, WalletType defaultWalletType) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        WalletType resolvedType = (defaultWalletType == WalletType.LAB_WALLET)
                ? WalletType.LAB_WALLET
                : WalletType.USER_WALLET;

        return walletRepository.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder()
                                .ownerUserId(ownerUserId)
                                .walletType(resolvedType)
                                .availableBalanceVnd(new BigDecimal("0.00"))
                                .lockedBalanceVnd(new BigDecimal("0.00"))
                                .currency("VND")
                                .build()
                ));
    }

    @Transactional
    public Wallet getOrCreateSystemWallet(WalletType systemWalletType) {
        if (systemWalletType == null || !systemWalletType.isSystemWallet()) {
            throw new IllegalArgumentException("Expected SYSTEM_ESCROW or SYSTEM_REVENUE wallet type");
        }
        return walletRepository.findByWalletType(systemWalletType)
                .orElseGet(() -> walletRepository.save(
                        Wallet.builder()
                                .ownerUserId(null)
                                .walletType(systemWalletType)
                                .availableBalanceVnd(new BigDecimal("0.00"))
                                .lockedBalanceVnd(new BigDecimal("0.00"))
                                .currency("VND")
                                .build()
                ));
    }

    @Transactional
    public Map<UUID, Wallet> lockWalletsInOrder(Collection<UUID> walletIds) {
        if (walletIds == null || walletIds.isEmpty()) {
            throw new IllegalArgumentException("walletIds must not be empty");
        }
        List<UUID> sortedIds = walletIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .toList();

        List<Wallet> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(sortedIds);
        if (lockedWallets.size() != sortedIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "One or more wallets not found for locking");
        }

        Map<UUID, Wallet> walletMap = new LinkedHashMap<>();
        for (Wallet wallet : lockedWallets) {
            walletMap.put(wallet.getId(), wallet);
        }
        return walletMap;
    }

    /**
     * Records external gateway (e.g. VNPay IPN) payment inflow into {@code SYSTEM_ESCROW}.
     * Generates a balanced pair: DEBIT on payer's wallet identity account and CREDIT on {@code SYSTEM_ESCROW}.
     */
    @Transactional
    public List<WalletLedgerEntry> recordGatewayEscrowIn(
            UUID payerUserId,
            BigDecimal amountVnd,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        BigDecimal normalizedAmount = normalizePositiveAmount(amountVnd);
        Wallet payerUnlocked = getOrCreateUserWallet(payerUserId, WalletType.USER_WALLET);
        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);

        Map<UUID, Wallet> locked = lockWalletsInOrder(List.of(payerUnlocked.getId(), escrowUnlocked.getId()));
        Wallet payerWallet = locked.get(payerUnlocked.getId());
        Wallet escrowWallet = locked.get(escrowUnlocked.getId());

        escrowWallet.creditAvailable(normalizedAmount);
        walletRepository.save(escrowWallet);

        List<JournalLeg> legs = List.of(
                new JournalLeg(
                        payerWallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        LedgerEntryType.ESCROW_IN,
                        normalizedAmount,
                        payerWallet.getAvailableBalanceVnd()
                ),
                new JournalLeg(
                        escrowWallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        LedgerEntryType.ESCROW_IN,
                        normalizedAmount,
                        escrowWallet.getAvailableBalanceVnd()
                )
        );

        return postBalancedJournal(legs, paymentTxnId, referenceType, referenceId);
    }

    /**
     * Records direct checkout payment from user's internal wallet into {@code SYSTEM_ESCROW}.
     * Debits {@code payerWallet.availableBalanceVnd} and credits {@code SYSTEM_ESCROW.availableBalanceVnd}.
     */
    @Transactional
    public List<WalletLedgerEntry> recordInternalWalletEscrowIn(
            UUID payerUserId,
            BigDecimal amountVnd,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        Wallet payerUnlocked = getOrCreateUserWallet(payerUserId, WalletType.USER_WALLET);
        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);

        return recordTransfer(
                payerUnlocked.getId(),
                escrowUnlocked.getId(),
                amountVnd,
                LedgerEntryType.ESCROW_IN,
                LedgerEntryType.ESCROW_IN,
                paymentTxnId,
                referenceType,
                referenceId
        );
    }

    /**
     * Records partial refund of scan fee for a blank film roll from {@code SYSTEM_ESCROW} to customer wallet.
     */
    @Transactional
    public List<WalletLedgerEntry> recordBlankRollRefund(
            UUID customerId,
            BigDecimal scanFeeRefundVnd,
            UUID paymentTxnId,
            UUID rollId
    ) {
        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);
        Wallet customerUnlocked = getOrCreateUserWallet(customerId, WalletType.USER_WALLET);

        return recordTransfer(
                escrowUnlocked.getId(),
                customerUnlocked.getId(),
                scanFeeRefundVnd,
                LedgerEntryType.BLANK_ROLL_REFUND,
                LedgerEntryType.BLANK_ROLL_REFUND,
                paymentTxnId,
                REF_TYPE_FILM_ROLL,
                rollId
        );
    }

    /**
     * Records full refund of remaining escrow balance from {@code SYSTEM_ESCROW} back to payer's wallet.
     */
    @Transactional
    public List<WalletLedgerEntry> recordFullRefund(
            UUID payerUserId,
            BigDecimal refundAmountVnd,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);
        Wallet payerUnlocked = getOrCreateUserWallet(payerUserId, WalletType.USER_WALLET);

        return recordTransfer(
                escrowUnlocked.getId(),
                payerUnlocked.getId(),
                refundAmountVnd,
                LedgerEntryType.FULL_REFUND,
                LedgerEntryType.FULL_REFUND,
                paymentTxnId,
                referenceType,
                referenceId
        );
    }

    /**
     * Records a 2-wallet transfer with identical entryType on both DEBIT and CREDIT legs.
     */
    @Transactional
    public List<WalletLedgerEntry> recordTransfer(
            UUID fromWalletId,
            UUID toWalletId,
            BigDecimal amountVnd,
            LedgerEntryType entryType,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        return recordTransfer(
                fromWalletId,
                toWalletId,
                amountVnd,
                entryType,
                entryType,
                paymentTxnId,
                referenceType,
                referenceId
        );
    }

    /**
     * Records a 2-wallet transfer, locking both wallets in ascending ID order before mutating balances.
     */
    @Transactional
    public List<WalletLedgerEntry> recordTransfer(
            UUID fromWalletId,
            UUID toWalletId,
            BigDecimal amountVnd,
            LedgerEntryType debitEntryType,
            LedgerEntryType creditEntryType,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        Objects.requireNonNull(fromWalletId, "fromWalletId must not be null");
        Objects.requireNonNull(toWalletId, "toWalletId must not be null");
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("fromWalletId and toWalletId must be distinct");
        }
        BigDecimal normalizedAmount = normalizePositiveAmount(amountVnd);

        Map<UUID, Wallet> locked = lockWalletsInOrder(List.of(fromWalletId, toWalletId));
        Wallet fromWallet = locked.get(fromWalletId);
        Wallet toWallet = locked.get(toWalletId);

        fromWallet.debitAvailable(normalizedAmount);
        toWallet.creditAvailable(normalizedAmount);
        walletRepository.saveAll(List.of(fromWallet, toWallet));

        List<JournalLeg> legs = List.of(
                new JournalLeg(
                        fromWallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        debitEntryType,
                        normalizedAmount,
                        fromWallet.getAvailableBalanceVnd()
                ),
                new JournalLeg(
                        toWallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        creditEntryType,
                        normalizedAmount,
                        toWallet.getAvailableBalanceVnd()
                )
        );

        return postBalancedJournal(legs, paymentTxnId, referenceType, referenceId);
    }

    /**
     * Records Escrow Release settlement:
     * <ul>
     *   <li>DEBIT {@code SYSTEM_ESCROW} by {@code releasableGrossVnd} ({@code ESCROW_RELEASE})</li>
     *   <li>CREDIT {@code payeeWallet} by {@code releasableGrossVnd - commissionFeeVnd} ({@code ESCROW_RELEASE})</li>
     *   <li>CREDIT {@code SYSTEM_REVENUE} by {@code commissionFeeVnd} ({@code PLATFORM_FEE}, if {@code > 0})</li>
     * </ul>
     */
    @Transactional
    public List<WalletLedgerEntry> recordEscrowRelease(
            UUID payeeUserId,
            WalletType payeeWalletType,
            BigDecimal releasableGrossVnd,
            BigDecimal commissionFeeVnd,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        BigDecimal gross = normalizePositiveAmount(releasableGrossVnd);
        BigDecimal fee = commissionFeeVnd != null
                ? commissionFeeVnd.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("0.00");
        if (fee.compareTo(BigDecimal.ZERO) < 0 || fee.compareTo(gross) > 0) {
            throw new IllegalArgumentException("commissionFeeVnd must be between 0 and releasableGrossVnd");
        }
        BigDecimal netPayout = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);
        Wallet payeeUnlocked = getOrCreateUserWallet(payeeUserId, payeeWalletType);
        Wallet revenueUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_REVENUE);

        List<UUID> idsToLock = new ArrayList<>();
        idsToLock.add(escrowUnlocked.getId());
        idsToLock.add(payeeUnlocked.getId());
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            idsToLock.add(revenueUnlocked.getId());
        }

        Map<UUID, Wallet> locked = lockWalletsInOrder(idsToLock);
        Wallet escrowWallet = locked.get(escrowUnlocked.getId());
        Wallet payeeWallet = locked.get(payeeUnlocked.getId());

        escrowWallet.debitAvailable(gross);
        List<Wallet> modifiedWallets = new ArrayList<>();
        modifiedWallets.add(escrowWallet);

        List<JournalLeg> legs = new ArrayList<>();
        legs.add(new JournalLeg(
                escrowWallet.getId(),
                LedgerEntryDirection.DEBIT,
                LedgerEntryType.ESCROW_RELEASE,
                gross,
                escrowWallet.getAvailableBalanceVnd()
        ));

        if (netPayout.compareTo(BigDecimal.ZERO) > 0) {
            payeeWallet.creditAvailable(netPayout);
            modifiedWallets.add(payeeWallet);
            legs.add(new JournalLeg(
                    payeeWallet.getId(),
                    LedgerEntryDirection.CREDIT,
                    LedgerEntryType.ESCROW_RELEASE,
                    netPayout,
                    payeeWallet.getAvailableBalanceVnd()
            ));
        }

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            Wallet revenueWallet = locked.get(revenueUnlocked.getId());
            revenueWallet.creditAvailable(fee);
            modifiedWallets.add(revenueWallet);
            legs.add(new JournalLeg(
                    revenueWallet.getId(),
                    LedgerEntryDirection.CREDIT,
                    LedgerEntryType.PLATFORM_FEE,
                    fee,
                    revenueWallet.getAvailableBalanceVnd()
            ));
        }

        walletRepository.saveAll(modifiedWallets);
        return postBalancedJournal(legs, paymentTxnId, referenceType, referenceId);
    }

    /**
     * Records multi-party Dispute Split settlement from {@code SYSTEM_ESCROW} to buyer, seller, and platform revenue
     * in a single atomic {@code journal_group_id}.
     */
    @Transactional
    public List<WalletLedgerEntry> recordDisputeSplit(
            UUID buyerUserId,
            UUID sellerUserId,
            WalletType sellerWalletType,
            BigDecimal refundToBuyerVnd,
            BigDecimal sellerNetPayoutVnd,
            BigDecimal commissionFeeVnd,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        BigDecimal buyerRefund = normalizeNonNegativeAmount(refundToBuyerVnd);
        BigDecimal sellerNet = normalizeNonNegativeAmount(sellerNetPayoutVnd);
        BigDecimal fee = normalizeNonNegativeAmount(commissionFeeVnd);

        BigDecimal totalEscrowOut = buyerRefund.add(sellerNet).add(fee).setScale(2, RoundingMode.HALF_UP);
        if (totalEscrowOut.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total dispute settlement amount must be strictly positive (> 0)");
        }

        Wallet escrowUnlocked = getOrCreateSystemWallet(WalletType.SYSTEM_ESCROW);
        Wallet buyerUnlocked = buyerRefund.compareTo(BigDecimal.ZERO) > 0
                ? getOrCreateUserWallet(buyerUserId, WalletType.USER_WALLET)
                : null;
        Wallet sellerUnlocked = sellerNet.compareTo(BigDecimal.ZERO) > 0
                ? getOrCreateUserWallet(sellerUserId, sellerWalletType)
                : null;
        Wallet revenueUnlocked = fee.compareTo(BigDecimal.ZERO) > 0
                ? getOrCreateSystemWallet(WalletType.SYSTEM_REVENUE)
                : null;

        List<UUID> idsToLock = new ArrayList<>();
        idsToLock.add(escrowUnlocked.getId());
        if (buyerUnlocked != null) {
            idsToLock.add(buyerUnlocked.getId());
        }
        if (sellerUnlocked != null) {
            idsToLock.add(sellerUnlocked.getId());
        }
        if (revenueUnlocked != null) {
            idsToLock.add(revenueUnlocked.getId());
        }

        Map<UUID, Wallet> locked = lockWalletsInOrder(idsToLock);
        Wallet escrowWallet = locked.get(escrowUnlocked.getId());
        escrowWallet.debitAvailable(totalEscrowOut);

        List<Wallet> modifiedWallets = new ArrayList<>();
        modifiedWallets.add(escrowWallet);

        List<JournalLeg> legs = new ArrayList<>();
        LedgerEntryType escrowDebitType = (sellerNet.compareTo(BigDecimal.ZERO) == 0 && fee.compareTo(BigDecimal.ZERO) == 0)
                ? LedgerEntryType.FULL_REFUND
                : LedgerEntryType.ESCROW_RELEASE;
        legs.add(new JournalLeg(
                escrowWallet.getId(),
                LedgerEntryDirection.DEBIT,
                escrowDebitType,
                totalEscrowOut,
                escrowWallet.getAvailableBalanceVnd()
        ));

        if (buyerUnlocked != null) {
            Wallet buyerWallet = locked.get(buyerUnlocked.getId());
            buyerWallet.creditAvailable(buyerRefund);
            modifiedWallets.add(buyerWallet);
            legs.add(new JournalLeg(
                    buyerWallet.getId(),
                    LedgerEntryDirection.CREDIT,
                    LedgerEntryType.FULL_REFUND,
                    buyerRefund,
                    buyerWallet.getAvailableBalanceVnd()
            ));
        }

        if (sellerUnlocked != null) {
            Wallet sellerWallet = locked.get(sellerUnlocked.getId());
            sellerWallet.creditAvailable(sellerNet);
            modifiedWallets.add(sellerWallet);
            legs.add(new JournalLeg(
                    sellerWallet.getId(),
                    LedgerEntryDirection.CREDIT,
                    LedgerEntryType.ESCROW_RELEASE,
                    sellerNet,
                    sellerWallet.getAvailableBalanceVnd()
            ));
        }

        if (revenueUnlocked != null) {
            Wallet revenueWallet = locked.get(revenueUnlocked.getId());
            revenueWallet.creditAvailable(fee);
            modifiedWallets.add(revenueWallet);
            legs.add(new JournalLeg(
                    revenueWallet.getId(),
                    LedgerEntryDirection.CREDIT,
                    LedgerEntryType.PLATFORM_FEE,
                    fee,
                    revenueWallet.getAvailableBalanceVnd()
            ));
        }

        walletRepository.saveAll(modifiedWallets);
        return postBalancedJournal(legs, paymentTxnId, referenceType, referenceId);
    }

    /**
     * Moves funds from {@code available_balance_vnd} to {@code locked_balance_vnd} when a payout request is created.
     * Records a balanced DEBIT (available) and CREDIT (locked) pair with {@code entry_type = PAYOUT_LOCK}.
     */
    @Transactional
    public List<WalletLedgerEntry> recordPayoutLock(
            UUID walletId,
            BigDecimal amountVnd,
            UUID payoutRequestId
    ) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(payoutRequestId, "payoutRequestId must not be null");
        BigDecimal normalizedAmount = normalizePositiveAmount(amountVnd);

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));

        wallet.lockBalance(normalizedAmount);
        walletRepository.save(wallet);

        List<JournalLeg> legs = List.of(
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        LedgerEntryType.PAYOUT_LOCK,
                        normalizedAmount,
                        wallet.getAvailableBalanceVnd()
                ),
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        LedgerEntryType.PAYOUT_LOCK,
                        normalizedAmount,
                        wallet.getLockedBalanceVnd()
                )
        );

        return postBalancedJournal(legs, null, REF_TYPE_PAYOUT_REQUEST, payoutRequestId);
    }

    /**
     * Settles locked balance when a payout request is completed via bank transfer.
     * Records a balanced DEBIT (locked balance decreases) and CREDIT (payout settlement counterpart) pair
     * with {@code entry_type = PAYOUT_SETTLE}.
     */
    @Transactional
    public List<WalletLedgerEntry> recordPayoutSettle(
            UUID walletId,
            BigDecimal amountVnd,
            UUID payoutRequestId
    ) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(payoutRequestId, "payoutRequestId must not be null");
        BigDecimal normalizedAmount = normalizePositiveAmount(amountVnd);

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));

        wallet.settleLockedBalance(normalizedAmount);
        walletRepository.save(wallet);

        List<JournalLeg> legs = List.of(
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        LedgerEntryType.PAYOUT_SETTLE,
                        normalizedAmount,
                        wallet.getLockedBalanceVnd()
                ),
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        LedgerEntryType.PAYOUT_SETTLE,
                        normalizedAmount,
                        wallet.getAvailableBalanceVnd()
                )
        );

        return postBalancedJournal(legs, null, REF_TYPE_PAYOUT_REQUEST, payoutRequestId);
    }

    /**
     * Unlocks funds from {@code locked_balance_vnd} back to {@code available_balance_vnd} when a payout is rejected.
     */
    @Transactional
    public List<WalletLedgerEntry> recordPayoutRejectUnlock(
            UUID walletId,
            BigDecimal amountVnd,
            UUID payoutRequestId
    ) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(payoutRequestId, "payoutRequestId must not be null");
        BigDecimal normalizedAmount = normalizePositiveAmount(amountVnd);

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));

        wallet.unlockBalance(normalizedAmount);
        walletRepository.save(wallet);

        List<JournalLeg> legs = List.of(
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.DEBIT,
                        LedgerEntryType.PAYOUT_LOCK,
                        normalizedAmount,
                        wallet.getLockedBalanceVnd()
                ),
                new JournalLeg(
                        wallet.getId(),
                        LedgerEntryDirection.CREDIT,
                        LedgerEntryType.PAYOUT_LOCK,
                        normalizedAmount,
                        wallet.getAvailableBalanceVnd()
                )
        );

        return postBalancedJournal(legs, null, REF_TYPE_PAYOUT_REQUEST, payoutRequestId);
    }

    /**
     * Validates and appends a balanced multi-leg journal under a single {@code journal_group_id}.
     * Throws {@link IllegalArgumentException} if {@code SUM(DEBIT) != SUM(CREDIT)} or if either side is missing.
     */
    @Transactional
    public List<WalletLedgerEntry> postBalancedJournal(
            List<JournalLeg> legs,
            UUID paymentTxnId,
            String referenceType,
            UUID referenceId
    ) {
        if (legs == null || legs.size() < 2) {
            throw new IllegalArgumentException("Double-entry journal requires at least 2 legs (DEBIT and CREDIT)");
        }
        if (referenceType == null || referenceType.isBlank()) {
            throw new IllegalArgumentException("referenceType must not be blank");
        }
        Objects.requireNonNull(referenceId, "referenceId must not be null");

        UUID journalGroupId = UUID.randomUUID();
        Instant now = Instant.now();

        List<WalletLedgerEntry> entries = new ArrayList<>(legs.size());
        for (JournalLeg leg : legs) {
            entries.add(WalletLedgerEntry.builder()
                    .journalGroupId(journalGroupId)
                    .walletId(leg.walletId())
                    .paymentTxnId(paymentTxnId)
                    .entryDirection(leg.direction())
                    .entryType(leg.entryType())
                    .amountVnd(leg.amountVnd().setScale(2, RoundingMode.HALF_UP))
                    .balanceAfterVnd(leg.balanceAfterVnd().setScale(2, RoundingMode.HALF_UP))
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .createdAt(now)
                    .build());
        }

        assertEntriesBalanced(entries);
        return walletLedgerEntryRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public boolean verifyJournalBalanced(UUID journalGroupId) {
        Objects.requireNonNull(journalGroupId, "journalGroupId must not be null");
        List<WalletLedgerEntry> entries = walletLedgerEntryRepository.findByJournalGroupIdOrderByCreatedAtAsc(journalGroupId);
        if (entries.size() < 2) {
            return false;
        }
        try {
            assertEntriesBalanced(entries);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void assertEntriesBalanced(List<WalletLedgerEntry> entries) {
        if (entries == null || entries.size() < 2) {
            throw new IllegalArgumentException("Double-entry journal requires at least 2 entries");
        }

        UUID expectedGroupId = entries.getFirst().getJournalGroupId();
        if (expectedGroupId == null) {
            throw new IllegalArgumentException("journalGroupId must not be null");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int debitCount = 0;
        int creditCount = 0;

        for (WalletLedgerEntry entry : entries) {
            if (!expectedGroupId.equals(entry.getJournalGroupId())) {
                throw new IllegalArgumentException("All entries in a journal batch must share the same journalGroupId");
            }
            if (entry.getAmountVnd() == null || entry.getAmountVnd().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Ledger entry amountVnd must be strictly positive (> 0)");
            }
            if (entry.getEntryDirection() == LedgerEntryDirection.DEBIT) {
                totalDebit = totalDebit.add(entry.getAmountVnd());
                debitCount++;
            } else if (entry.getEntryDirection() == LedgerEntryDirection.CREDIT) {
                totalCredit = totalCredit.add(entry.getAmountVnd());
                creditCount++;
            } else {
                throw new IllegalArgumentException("Unknown entryDirection: " + entry.getEntryDirection());
            }
        }

        if (debitCount == 0 || creditCount == 0) {
            throw new IllegalArgumentException(
                    "Double-entry journal must contain at least one DEBIT and at least one CREDIT entry"
            );
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Double-entry invariant violation for journalGroupId=%s: total DEBIT (%s VND) != total CREDIT (%s VND)",
                    expectedGroupId,
                    totalDebit.toPlainString(),
                    totalCredit.toPlainString()
            ));
        }
    }

    @Transactional(readOnly = true)
    public Page<WalletLedgerEntry> searchWalletLedger(
            UUID walletId,
            LedgerEntryDirection entryDirection,
            LedgerEntryType entryType,
            Instant fromTime,
            Instant toTime,
            Pageable pageable
    ) {
        return walletLedgerEntryRepository.searchWalletLedger(
                walletId,
                entryDirection,
                entryType,
                fromTime,
                toTime,
                pageable
        );
    }

    private static BigDecimal normalizePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amountVnd must be strictly positive (> 0)");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeNonNegativeAmount(BigDecimal amount) {
        if (amount == null) {
            return new BigDecimal("0.00");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
