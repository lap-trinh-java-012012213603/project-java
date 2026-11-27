package com.analoghub.platform.admin.service;

import com.analoghub.platform.admin.audit.AdminAuditLogAspect;
import com.analoghub.platform.admin.audit.AdminAudited;
import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.dto.AdminWalletLedgerEntryViewDto;
import com.analoghub.platform.admin.dto.DecidePayoutRequest;
import com.analoghub.platform.admin.dto.DecidePayoutResponse;
import com.analoghub.platform.admin.dto.PayoutDecision;
import com.analoghub.platform.admin.dto.PayoutRequestAdminDto;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.payment.dto.AdminLedgerEntryDto;
import com.analoghub.platform.payment.dto.LedgerReconciliationReportDto;
import com.analoghub.platform.payment.dto.PayoutAdminItemDto;
import com.analoghub.platform.payment.dto.PayoutSettlementDto;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.security.SecurityContextUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tier-5 orchestrator for Admin double-entry ledger reconciliation, ledger auditing,
 * payout request listing, and synchronous payout approval/rejection via {@link PaymentFacade}.
 */
@Service
public class AdminPaymentMonitorService {

    private static final String DEFAULT_PAYOUT_STATUS = "PENDING_APPROVAL";

    private final PaymentFacade paymentFacade;
    private final AdminAuditLogService adminAuditLogService;
    private final IamFacade iamFacade;
    private final @Nullable NotificationFacade notificationFacade;

    @Autowired
    public AdminPaymentMonitorService(
            PaymentFacade paymentFacade,
            AdminAuditLogService adminAuditLogService,
            IamFacade iamFacade,
            @Nullable NotificationFacade notificationFacade
    ) {
        this.paymentFacade = paymentFacade;
        this.adminAuditLogService = adminAuditLogService;
        this.iamFacade = iamFacade;
        this.notificationFacade = notificationFacade;
    }

    @Transactional(readOnly = true)
    public LedgerReconciliationReportDto reconcileWalletLedger() {
        return paymentFacade.reconcileLedger();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletLedgerEntryViewDto> searchLedgerEntries(
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
        PageResponse<AdminLedgerEntryDto> rawPage = paymentFacade.queryLedgerEntries(
                walletId,
                ownerUserId,
                journalGroupId,
                entryDirection,
                entryType,
                fromTime,
                toTime,
                page,
                size
        );

        Set<UUID> ownerIds = rawPage.item().stream()
                .map(AdminLedgerEntryDto::walletOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> usersById = ownerIds.isEmpty()
                ? Map.of()
                : iamFacade.findUsersByIds(ownerIds);

        List<AdminWalletLedgerEntryViewDto> items = rawPage.item().stream()
                .map(entry -> {
                    UUID ownerId = entry.walletOwnerUserId();
                    UserSummaryDto user = ownerId != null ? usersById.get(ownerId) : null;
                    AdminWalletLedgerEntryViewDto.WalletOwnerSummary ownerSummary = ownerId != null
                            ? new AdminWalletLedgerEntryViewDto.WalletOwnerSummary(
                                    ownerId,
                                    user != null ? user.fullName() : null,
                                    user != null ? user.email() : null
                            )
                            : null;
                    return new AdminWalletLedgerEntryViewDto(
                            entry.ledgerEntryId(),
                            entry.journalGroupId(),
                            entry.walletId(),
                            entry.walletType(),
                            ownerSummary,
                            entry.paymentTxnId(),
                            entry.entryDirection(),
                            entry.entryType(),
                            entry.amountVnd(),
                            entry.balanceAfterVnd(),
                            entry.referenceType(),
                            entry.referenceId(),
                            entry.createdAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                rawPage.page(),
                rawPage.size(),
                rawPage.totalElements(),
                rawPage.totalPages()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutRequestAdminDto> listPayoutRequests(
            @Nullable String status,
            @Nullable UUID requesterUserId,
            int page,
            int size
    ) {
        String effectiveStatus = (status == null || status.isBlank()) ? DEFAULT_PAYOUT_STATUS : status.trim();
        PageResponse<PayoutAdminItemDto> rawPage = paymentFacade.queryPayoutRequests(
                effectiveStatus,
                requesterUserId,
                page,
                size
        );

        Set<UUID> requesterIds = rawPage.item().stream()
                .map(PayoutAdminItemDto::requesterUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> usersById = requesterIds.isEmpty()
                ? Map.of()
                : iamFacade.findUsersByIds(requesterIds);

        List<PayoutRequestAdminDto> items = rawPage.item().stream()
                .map(item -> {
                    UserSummaryDto user = item.requesterUserId() != null
                            ? usersById.get(item.requesterUserId())
                            : null;
                    PayoutRequestAdminDto.RequesterSummary requester = new PayoutRequestAdminDto.RequesterSummary(
                            item.requesterUserId(),
                            user != null ? user.fullName() : null,
                            user != null ? user.email() : null
                    );
                    return new PayoutRequestAdminDto(
                            item.payoutRequestId(),
                            item.walletId(),
                            item.walletType(),
                            requester,
                            item.amountVnd(),
                            item.bankCode(),
                            item.bankAccountNo(),
                            item.bankAccountName(),
                            item.status(),
                            item.bankTransferRef(),
                            item.reviewedByAdminId(),
                            item.createdAt(),
                            item.completedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                rawPage.page(),
                rawPage.size(),
                rawPage.totalElements(),
                rawPage.totalPages()
        );
    }

    @Transactional
    @AdminAudited(
            actionType = "DECIDE_PAYOUT_REQUEST",
            targetEntity = "PAY_PAYOUT_REQUEST",
            targetIdParam = "payoutId"
    )
    public DecidePayoutResponse decidePayoutRequest(UUID payoutId, DecidePayoutRequest request) {
        if (payoutId == null || request == null || request.decision() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thông tin phê duyệt yêu cầu rút tiền không hợp lệ."
            );
        }

        UUID adminId = SecurityContextUtils.getRequiredCurrentUserId();
        String note = request.reviewNote().trim();

        PayoutSettlementDto settlement;
        if (request.decision() == PayoutDecision.APPROVE_COMPLETED) {
            String trimmedRef = request.bankTransferRef() != null ? request.bankTransferRef().trim() : null;
            if (trimmedRef == null || trimmedRef.length() < 5) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Mã tham chiếu giao dịch ngân hàng (bankTransferRef) là bắt buộc khi phê duyệt hoàn tất rút tiền."
                );
            }
            settlement = paymentFacade.completePayoutRequest(payoutId, adminId, trimmedRef, note);
        } else {
            settlement = paymentFacade.rejectPayoutRequest(payoutId, adminId, note);
        }

        if (notificationFacade != null && settlement.requesterUserId() != null) {
            String title = request.decision() == PayoutDecision.APPROVE_COMPLETED
                    ? "Yêu cầu rút tiền đã được chuyển khoản hoàn tất"
                    : "Yêu cầu rút tiền bị từ chối";
            String body = request.decision() == PayoutDecision.APPROVE_COMPLETED
                    ? String.format(
                            "Lệnh rút tiền %s VND đã được tất toán qua ngân hàng (Mã tham chiếu: %s). Ghi chú: %s",
                            settlement.amountVnd(), settlement.bankTransferRef(), note
                    )
                    : String.format(
                            "Lệnh rút tiền %s VND đã bị từ chối và số dư đã được hoàn trả về ví khả dụng. Lý do: %s",
                            settlement.amountVnd(), note
                    );
            notificationFacade.sendSystemAlert(settlement.requesterUserId(), title, body);
        }

        DecidePayoutResponse response = new DecidePayoutResponse(
                settlement.payoutRequestId(),
                settlement.walletId(),
                settlement.amountVnd(),
                settlement.previousStatus(),
                settlement.newStatus(),
                settlement.bankTransferRef(),
                new DecidePayoutResponse.LedgerSettlement(
                        settlement.journalGroupId(),
                        settlement.entryType(),
                        settlement.availableBalanceAfterVnd(),
                        settlement.lockedBalanceAfterVnd()
                ),
                settlement.reviewedByAdminId(),
                null,
                settlement.completedAt(),
                note
        );

        if (!AdminAuditLogAspect.isAspectActive()) {
            AuditLog auditLog = adminAuditLogService.recordAuditLog(
                    adminId,
                    "DECIDE_PAYOUT_REQUEST",
                    "PAY_PAYOUT_REQUEST",
                    payoutId,
                    response.toAuditOldValues(),
                    response.toAuditNewValues(),
                    null
            );
            response = response.withAuditLogId(auditLog.getId());
        }

        return response;
    }
}
