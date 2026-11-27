package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.dto.PageResponse;
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
import com.analoghub.platform.payment.dto.AdminLedgerEntryDto;
import com.analoghub.platform.payment.dto.CreatePayoutRequest;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutAdminItemDto;
import com.analoghub.platform.payment.dto.PayoutResponse;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;
import com.analoghub.platform.payment.dto.PayoutSummaryDto;
import com.analoghub.platform.payment.dto.WalletLedgerEntryDto;
import com.analoghub.platform.payment.dto.WalletSummaryResponse;
import com.analoghub.platform.payment.event.PayoutCompletedEvent;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PayoutRequestRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WalletAndPayoutService {

    private static final int MAX_PENDING_PAYOUTS = 3;
    private static final BigDecimal MIN_PAYOUT_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal MAX_PAYOUT_AMOUNT = new BigDecimal("200000000.00");
    private static final BigDecimal PAYOUT_STEP_VND = new BigDecimal("1000");

    private static final Set<EscrowStatus> PENDING_RECEIVABLE_ESCROW_STATUSES = EnumSet.of(
            EscrowStatus.HELD,
            EscrowStatus.PARTIALLY_REFUNDED
    );

    private static final Set<PayoutRequestStatus> ACTIVE_PAYOUT_STATUSES = EnumSet.of(
            PayoutRequestStatus.PENDING_APPROVAL,
            PayoutRequestStatus.PROCESSING
    );

    private final WalletRepository walletRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final WalletLedgerService walletLedgerService;
    private final @Nullable ApplicationEventPublisher eventPublisher;

    public WalletAndPayoutService(
            WalletRepository walletRepository,
            EscrowHoldRepository escrowHoldRepository,
            PayoutRequestRepository payoutRequestRepository,
            WalletLedgerService walletLedgerService
    ) {
        this(walletRepository, escrowHoldRepository, payoutRequestRepository, walletLedgerService, null);
    }

    @Autowired
    public WalletAndPayoutService(
            WalletRepository walletRepository,
            EscrowHoldRepository escrowHoldRepository,
            PayoutRequestRepository payoutRequestRepository,
            WalletLedgerService walletLedgerService,
            @Nullable ApplicationEventPublisher eventPublisher
    ) {
        this.walletRepository = walletRepository;
        this.escrowHoldRepository = escrowHoldRepository;
        this.payoutRequestRepository = payoutRequestRepository;
        this.walletLedgerService = walletLedgerService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WalletSummaryResponse getMyWallet() {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        WalletType defaultType = currentUser.hasRole("LAB_OWNER")
                ? WalletType.LAB_WALLET
                : WalletType.USER_WALLET;

        Wallet wallet = walletLedgerService.getOrCreateUserWallet(currentUser.getUserId(), defaultType);
        BigDecimal pendingReceivable = escrowHoldRepository.sumPendingReceivableByPayeeUserId(
                currentUser.getUserId(),
                PENDING_RECEIVABLE_ESCROW_STATUSES
        );
        BigDecimal normalizedPending = pendingReceivable != null
                ? pendingReceivable.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal("0.00");

        return new WalletSummaryResponse(
                wallet.getId(),
                wallet.getOwnerUserId(),
                wallet.getWalletType(),
                wallet.getAvailableBalanceVnd(),
                wallet.getLockedBalanceVnd(),
                wallet.getTotalBalanceVnd(),
                normalizedPending,
                wallet.getCurrency(),
                wallet.getUpdatedAt() != null ? wallet.getUpdatedAt() : wallet.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletLedgerEntryDto> getMyWalletLedger(
            LedgerEntryDirection entryDirection,
            LedgerEntryType entryType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "fromDate must be before or equal to toDate"
            );
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid pagination parameters: page must be >= 0 and size between 1 and 100"
            );
        }

        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        Optional<Wallet> walletOpt = walletRepository.findByOwnerUserId(currentUserId);
        if (walletOpt.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        Instant fromTime = fromDate != null
                ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
                : null;
        Instant toTime = toDate != null
                ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1)
                : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<WalletLedgerEntry> ledgerPage = walletLedgerService.searchWalletLedger(
                walletOpt.get().getId(),
                entryDirection,
                entryType,
                fromTime,
                toTime,
                pageable
        );

        List<WalletLedgerEntryDto> items = ledgerPage.getContent().stream()
                .map(this::toLedgerEntryDto)
                .toList();

        return PageResponse.from(
                items,
                ledgerPage.getNumber(),
                ledgerPage.getSize(),
                (int) ledgerPage.getTotalElements(),
                ledgerPage.getTotalPages()
        );
    }

    @Transactional
    public PayoutResponse requestPayout(CreatePayoutRequest request) {
        Objects.requireNonNull(request, "CreatePayoutRequest must not be null");
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        BigDecimal amountVnd = validateAndNormalizePayoutAmount(request.amountVnd());

        Wallet wallet = walletRepository.findByOwnerUserIdForUpdate(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Wallet not found for current user"
                ));

        long pendingPayoutCount = payoutRequestRepository.countByRequesterUserIdAndStatusIn(
                currentUserId,
                ACTIVE_PAYOUT_STATUSES
        );
        if (pendingPayoutCount >= MAX_PENDING_PAYOUTS) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Maximum number of pending payout requests (3) reached"
            );
        }

        if (wallet.getAvailableBalanceVnd().compareTo(amountVnd) < 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    "Insufficient available balance in wallet for payout"
            );
        }

        PayoutRequest payoutRequest = PayoutRequest.builder()
                .walletId(wallet.getId())
                .requesterUserId(currentUserId)
                .amountVnd(amountVnd)
                .bankCode(request.bankCode().trim())
                .bankAccountNo(request.bankAccountNo().trim())
                .bankAccountName(request.bankAccountName().trim())
                .status(PayoutRequestStatus.PENDING_APPROVAL)
                .build();
        PayoutRequest savedPayout = payoutRequestRepository.save(payoutRequest);

        walletLedgerService.recordPayoutLock(wallet.getId(), amountVnd, savedPayout.getId());

        return new PayoutResponse(
                savedPayout.getId(),
                wallet.getId(),
                currentUserId,
                amountVnd,
                savedPayout.getBankCode(),
                savedPayout.getBankAccountNo(),
                savedPayout.getBankAccountName(),
                savedPayout.getStatus(),
                wallet.getAvailableBalanceVnd(),
                wallet.getLockedBalanceVnd(),
                savedPayout.getCreatedAt() != null ? savedPayout.getCreatedAt() : Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutSummaryDto> getMyPayouts(PayoutRequestStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid pagination parameters: page must be >= 0 and size between 1 and 50"
            );
        }

        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        Page<PayoutRequest> payoutPage = (status != null)
                ? payoutRequestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(currentUserId, status, pageable)
                : payoutRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(currentUserId, pageable);

        List<PayoutSummaryDto> items = payoutPage.getContent().stream()
                .map(this::toPayoutSummaryDto)
                .toList();

        return PageResponse.from(
                items,
                payoutPage.getNumber(),
                payoutPage.getSize(),
                (int) payoutPage.getTotalElements(),
                payoutPage.getTotalPages()
        );
    }

    private BigDecimal validateAndNormalizePayoutAmount(BigDecimal rawAmount) {
        if (rawAmount == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amountVnd is required");
        }
        BigDecimal scaled = rawAmount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(MIN_PAYOUT_AMOUNT) < 0 || scaled.compareTo(MAX_PAYOUT_AMOUNT) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Payout amount must be between 100,000.00 VND and 200,000,000.00 VND"
            );
        }
        if (scaled.remainder(PAYOUT_STEP_VND).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Payout amount must be a multiple of 1,000 VND"
            );
        }
        return scaled;
    }

    private WalletLedgerEntryDto toLedgerEntryDto(WalletLedgerEntry entry) {
        return new WalletLedgerEntryDto(
                entry.getId(),
                entry.getJournalGroupId(),
                entry.getWalletId(),
                entry.getPaymentTxnId(),
                entry.getEntryDirection(),
                entry.getEntryType(),
                entry.getAmountVnd(),
                entry.getBalanceAfterVnd(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getCreatedAt()
        );
    }

    private PayoutSummaryDto toPayoutSummaryDto(PayoutRequest payout) {
        return new PayoutSummaryDto(
                payout.getId(),
                payout.getAmountVnd(),
                payout.getBankCode(),
                payout.getBankAccountNo(),
                payout.getBankAccountName(),
                payout.getStatus(),
                payout.getBankTransferRef(),
                payout.getCreatedAt(),
                payout.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutAdminItemDto> queryPayoutRequests(
            @Nullable String status,
            @Nullable UUID requesterUserId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số phân trang danh sách yêu cầu rút tiền không hợp lệ."
            );
        }

        PayoutRequestStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = PayoutRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Trạng thái lọc yêu cầu rút tiền không hợp lệ: " + status
                );
            }
        }

        final PayoutRequestStatus effectiveStatus = statusFilter;
        Specification<PayoutRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveStatus != null) {
                predicates.add(cb.equal(root.get("status"), effectiveStatus));
            }
            if (requesterUserId != null) {
                predicates.add(cb.equal(root.get("requesterUserId"), requesterUserId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<PayoutRequest> payoutPage = payoutRequestRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Set<UUID> walletIds = payoutPage.getContent().stream()
                .map(PayoutRequest::getWalletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Wallet> walletsById = walletIds.isEmpty()
                ? Map.of()
                : walletRepository.findAllById(walletIds).stream()
                        .collect(Collectors.toMap(Wallet::getId, w -> w, (a, b) -> a));

        List<PayoutAdminItemDto> items = payoutPage.getContent().stream()
                .map(payout -> {
                    Wallet wallet = walletsById.get(payout.getWalletId());
                    String walletType = wallet != null && wallet.getWalletType() != null
                            ? wallet.getWalletType().name()
                            : null;
                    return new PayoutAdminItemDto(
                            payout.getId(),
                            payout.getWalletId(),
                            walletType,
                            payout.getRequesterUserId(),
                            payout.getAmountVnd(),
                            payout.getBankCode(),
                            payout.getBankAccountNo(),
                            payout.getBankAccountName(),
                            payout.getStatus() != null ? payout.getStatus().name() : null,
                            payout.getBankTransferRef(),
                            payout.getUpdatedBy(),
                            payout.getCreatedAt(),
                            payout.getCompletedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                payoutPage.getNumber(),
                payoutPage.getSize(),
                (int) payoutPage.getTotalElements(),
                payoutPage.getTotalPages()
        );
    }

    @Transactional
    public PayoutSettlementDto completePayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            String bankTransferRef,
            @Nullable String reviewNote
    ) {
        if (payoutId == null || adminUserId == null || bankTransferRef == null || bankTransferRef.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mã tham chiếu giao dịch ngân hàng (bankTransferRef) là bắt buộc khi phê duyệt hoàn tất rút tiền."
            );
        }

        PayoutRequest payout = payoutRequestRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Yêu cầu rút tiền", payoutId));

        if (!ACTIVE_PAYOUT_STATUSES.contains(payout.getStatus())) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Yêu cầu rút tiền này đã được xử lý trước đó (trạng thái: " + payout.getStatus() + ")."
            );
        }

        String previousStatus = payout.getStatus().name();
        String trimmedBankRef = bankTransferRef.trim();

        List<WalletLedgerEntry> settlementEntries = walletLedgerService.recordPayoutSettle(
                payout.getWalletId(),
                payout.getAmountVnd(),
                payout.getId()
        );

        Wallet updatedWallet = walletRepository.findById(payout.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Ví người dùng", payout.getWalletId()));

        Instant completedAt = Instant.now();
        payout.setStatus(PayoutRequestStatus.COMPLETED);
        payout.setBankTransferRef(trimmedBankRef);
        payout.setCompletedAt(completedAt);
        payout.setUpdatedBy(adminUserId);
        payoutRequestRepository.save(payout);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new PayoutCompletedEvent(
                    payout.getId(),
                    payout.getRequesterUserId(),
                    payout.getAmountVnd(),
                    trimmedBankRef
            ));
        }

        UUID journalGroupId = settlementEntries.isEmpty() ? null : settlementEntries.getFirst().getJournalGroupId();
        return new PayoutSettlementDto(
                payout.getId(),
                payout.getWalletId(),
                payout.getRequesterUserId(),
                payout.getAmountVnd(),
                previousStatus,
                PayoutRequestStatus.COMPLETED.name(),
                trimmedBankRef,
                journalGroupId,
                LedgerEntryType.PAYOUT_SETTLE.name(),
                updatedWallet.getAvailableBalanceVnd(),
                updatedWallet.getLockedBalanceVnd(),
                adminUserId,
                completedAt
        );
    }

    @Transactional
    public PayoutSettlementDto rejectPayoutRequest(
            UUID payoutId,
            UUID adminUserId,
            @Nullable String reviewNote
    ) {
        if (payoutId == null || adminUserId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin từ chối yêu cầu rút tiền không hợp lệ.");
        }

        PayoutRequest payout = payoutRequestRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Yêu cầu rút tiền", payoutId));

        if (!ACTIVE_PAYOUT_STATUSES.contains(payout.getStatus())) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Yêu cầu rút tiền này đã được xử lý trước đó (trạng thái: " + payout.getStatus() + ")."
            );
        }

        String previousStatus = payout.getStatus().name();
        List<WalletLedgerEntry> unlockEntries = walletLedgerService.recordPayoutRejectUnlock(
                payout.getWalletId(),
                payout.getAmountVnd(),
                payout.getId()
        );

        Wallet updatedWallet = walletRepository.findById(payout.getWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Ví người dùng", payout.getWalletId()));

        Instant completedAt = Instant.now();
        payout.setStatus(PayoutRequestStatus.REJECTED);
        payout.setCompletedAt(completedAt);
        payout.setUpdatedBy(adminUserId);
        payoutRequestRepository.save(payout);

        UUID journalGroupId = unlockEntries.isEmpty() ? null : unlockEntries.getFirst().getJournalGroupId();
        return new PayoutSettlementDto(
                payout.getId(),
                payout.getWalletId(),
                payout.getRequesterUserId(),
                payout.getAmountVnd(),
                previousStatus,
                PayoutRequestStatus.REJECTED.name(),
                null,
                journalGroupId,
                LedgerEntryType.PAYOUT_LOCK.name(),
                updatedWallet.getAvailableBalanceVnd(),
                updatedWallet.getLockedBalanceVnd(),
                adminUserId,
                completedAt
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminLedgerEntryDto> queryLedgerEntries(
            @Nullable UUID walletId,
            @Nullable UUID ownerUserId,
            @Nullable UUID journalGroupId,
            @Nullable String entryDirection,
            @Nullable String entryType,
            @Nullable Instant fromTime,
            @Nullable Instant toTime,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang sổ cái không hợp lệ.");
        }
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Khoảng thời gian tra cứu sổ cái không hợp lệ: fromTime không được lớn hơn toTime."
            );
        }

        LedgerEntryDirection parsedDirection = null;
        if (entryDirection != null && !entryDirection.isBlank()) {
            try {
                parsedDirection = LedgerEntryDirection.valueOf(entryDirection.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "entryDirection không hợp lệ: " + entryDirection
                );
            }
        }

        LedgerEntryType parsedType = null;
        if (entryType != null && !entryType.isBlank()) {
            try {
                parsedType = LedgerEntryType.valueOf(entryType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "entryType không hợp lệ: " + entryType);
            }
        }

        UUID effectiveWalletId = walletId;
        if (effectiveWalletId == null && ownerUserId != null) {
            Optional<Wallet> ownerWallet = walletRepository.findByOwnerUserId(ownerUserId);
            if (ownerWallet.isEmpty()) {
                return PageResponse.from(List.of(), page, size, 0, 0);
            }
            effectiveWalletId = ownerWallet.get().getId();
        }

        final UUID walletFilter = effectiveWalletId;
        final LedgerEntryDirection directionFilter = parsedDirection;
        final LedgerEntryType typeFilter = parsedType;

        Specification<WalletLedgerEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (walletFilter != null) {
                predicates.add(cb.equal(root.get("walletId"), walletFilter));
            }
            if (journalGroupId != null) {
                predicates.add(cb.equal(root.get("journalGroupId"), journalGroupId));
            }
            if (directionFilter != null) {
                predicates.add(cb.equal(root.get("entryDirection"), directionFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("entryType"), typeFilter));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toTime));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<WalletLedgerEntry> ledgerPage = walletLedgerService.searchLedgerBySpecification(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Set<UUID> walletIds = ledgerPage.getContent().stream()
                .map(WalletLedgerEntry::getWalletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Wallet> walletsById = walletIds.isEmpty()
                ? Map.of()
                : walletRepository.findAllById(walletIds).stream()
                        .collect(Collectors.toMap(Wallet::getId, w -> w, (a, b) -> a));

        List<AdminLedgerEntryDto> items = ledgerPage.getContent().stream()
                .map(entry -> {
                    Wallet wallet = walletsById.get(entry.getWalletId());
                    return new AdminLedgerEntryDto(
                            entry.getId(),
                            entry.getJournalGroupId(),
                            entry.getWalletId(),
                            wallet != null ? wallet.getOwnerUserId() : null,
                            wallet != null && wallet.getWalletType() != null ? wallet.getWalletType().name() : null,
                            entry.getPaymentTxnId(),
                            entry.getEntryDirection() != null ? entry.getEntryDirection().name() : null,
                            entry.getEntryType() != null ? entry.getEntryType().name() : null,
                            entry.getAmountVnd(),
                            entry.getBalanceAfterVnd(),
                            entry.getReferenceType(),
                            entry.getReferenceId(),
                            entry.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                ledgerPage.getNumber(),
                ledgerPage.getSize(),
                (int) ledgerPage.getTotalElements(),
                ledgerPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public LedgerReconciliationReportDto reconcileLedger() {
        List<WalletLedgerEntry> entries = walletLedgerService.findAllLedgerEntries();
        List<Wallet> wallets = walletRepository.findAll();
        List<PayoutRequest> payouts = payoutRequestRepository.findAll();

        BigDecimal totalDebit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCredit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Map<UUID, List<WalletLedgerEntry>> entriesByGroup = new LinkedHashMap<>();
        for (WalletLedgerEntry entry : entries) {
            BigDecimal amt = entry.getAmountVnd() != null ? entry.getAmountVnd() : BigDecimal.ZERO;
            if (entry.getEntryDirection() == LedgerEntryDirection.DEBIT) {
                totalDebit = totalDebit.add(amt);
            } else if (entry.getEntryDirection() == LedgerEntryDirection.CREDIT) {
                totalCredit = totalCredit.add(amt);
            }
            if (entry.getJournalGroupId() != null) {
                entriesByGroup.computeIfAbsent(entry.getJournalGroupId(), ignored -> new ArrayList<>()).add(entry);
            }
        }

        List<UUID> unbalancedGroupIds = new ArrayList<>();
        for (Map.Entry<UUID, List<WalletLedgerEntry>> group : entriesByGroup.entrySet()) {
            try {
                walletLedgerService.assertEntriesBalanced(group.getValue());
            } catch (IllegalArgumentException ex) {
                unbalancedGroupIds.add(group.getKey());
            }
        }
        unbalancedGroupIds.sort(Comparator.comparing(UUID::toString));

        BigDecimal systemEscrow = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal systemRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal userAvailable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal userLocked = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Wallet wallet : wallets) {
            BigDecimal avail = wallet.getAvailableBalanceVnd() != null ? wallet.getAvailableBalanceVnd() : BigDecimal.ZERO;
            BigDecimal locked = wallet.getLockedBalanceVnd() != null ? wallet.getLockedBalanceVnd() : BigDecimal.ZERO;
            if (wallet.getWalletType() == WalletType.SYSTEM_ESCROW) {
                systemEscrow = systemEscrow.add(avail);
            } else if (wallet.getWalletType() == WalletType.SYSTEM_REVENUE) {
                systemRevenue = systemRevenue.add(avail);
            } else {
                userAvailable = userAvailable.add(avail);
                userLocked = userLocked.add(locked);
            }
        }

        int pendingPayoutCount = 0;
        BigDecimal pendingPayoutAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (PayoutRequest payout : payouts) {
            if (ACTIVE_PAYOUT_STATUSES.contains(payout.getStatus())) {
                pendingPayoutCount++;
                if (payout.getAmountVnd() != null) {
                    pendingPayoutAmount = pendingPayoutAmount.add(payout.getAmountVnd());
                }
            }
        }

        BigDecimal discrepancy = totalDebit.subtract(totalCredit).setScale(2, RoundingMode.HALF_UP);
        boolean balanced = discrepancy.compareTo(BigDecimal.ZERO) == 0 && unbalancedGroupIds.isEmpty();

        return new LedgerReconciliationReportDto(
                balanced,
                totalDebit.setScale(2, RoundingMode.HALF_UP),
                totalCredit.setScale(2, RoundingMode.HALF_UP),
                discrepancy,
                entries.size(),
                entriesByGroup.size(),
                List.copyOf(unbalancedGroupIds),
                systemEscrow.setScale(2, RoundingMode.HALF_UP),
                systemRevenue.setScale(2, RoundingMode.HALF_UP),
                userAvailable.setScale(2, RoundingMode.HALF_UP),
                userLocked.setScale(2, RoundingMode.HALF_UP),
                pendingPayoutCount,
                pendingPayoutAmount.setScale(2, RoundingMode.HALF_UP),
                Instant.now()
        );
    }
}
