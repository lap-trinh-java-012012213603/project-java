package com.analoghub.platform.admin.service;

import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.domain.Dispute;
import com.analoghub.platform.admin.domain.DisputeEvidence;
import com.analoghub.platform.admin.dto.DisputeDetailAdminDto;
import com.analoghub.platform.admin.dto.DisputeEvidenceDto;
import com.analoghub.platform.admin.dto.DisputeOpenedResponse;
import com.analoghub.platform.admin.dto.DisputeResolutionDecision;
import com.analoghub.platform.admin.dto.DisputeStatus;
import com.analoghub.platform.admin.dto.DisputeSummaryAdminDto;
import com.analoghub.platform.admin.dto.DisputeType;
import com.analoghub.platform.admin.dto.OpenDisputeRequest;
import com.analoghub.platform.admin.dto.ResolveDisputeRequest;
import com.analoghub.platform.admin.dto.ResolveDisputeResponse;
import com.analoghub.platform.admin.dto.SubmitDisputeEvidenceRequest;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.admin.repository.DisputeEvidenceRepository;
import com.analoghub.platform.admin.repository.DisputeRepository;
import com.analoghub.platform.booking.dto.LabOrderResponse;
import com.analoghub.platform.booking.facade.BookingOrderFacade;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.dto.MarketplaceOrderDto;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.payment.dto.EscrowSnapshotDto;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tier-5 orchestrator for Dispute opening (freezing Escrow via {@link PaymentFacade}),
 * evidence submission, Admin dispute inspection, and 3-way Escrow arbitration
 * ({@code REFUND_BUYER}, {@code RELEASE_SELLER}, {@code SPLIT}) with immutable {@link AuditLog} trail.
 */
@Slf4j
@Service
public class DisputeResolutionService {

    private static final String AUDIT_TARGET_DISPUTE = "ADM_DISPUTE";
    private static final Set<String> INELIGIBLE_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "CANCELLED", "REFUNDED");
    private static final DateTimeFormatter DISPUTE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final AuditLogRepository auditLogRepository;
    private final PaymentFacade paymentFacade;
    private final BookingOrderFacade bookingOrderFacade;
    private final MarketplaceFacade marketplaceFacade;
    private final FilmLabFacade filmLabFacade;
    private final IamFacade iamFacade;
    private final @Nullable StorageService storageService;
    private final @Nullable NotificationFacade notificationFacade;

    public DisputeResolutionService(
            DisputeRepository disputeRepository,
            DisputeEvidenceRepository disputeEvidenceRepository,
            AuditLogRepository auditLogRepository,
            PaymentFacade paymentFacade,
            BookingOrderFacade bookingOrderFacade,
            MarketplaceFacade marketplaceFacade,
            FilmLabFacade filmLabFacade,
            IamFacade iamFacade
    ) {
        this(
                disputeRepository,
                disputeEvidenceRepository,
                auditLogRepository,
                paymentFacade,
                bookingOrderFacade,
                marketplaceFacade,
                filmLabFacade,
                iamFacade,
                null,
                null
        );
    }

    @Autowired
    public DisputeResolutionService(
            DisputeRepository disputeRepository,
            DisputeEvidenceRepository disputeEvidenceRepository,
            AuditLogRepository auditLogRepository,
            PaymentFacade paymentFacade,
            BookingOrderFacade bookingOrderFacade,
            MarketplaceFacade marketplaceFacade,
            FilmLabFacade filmLabFacade,
            IamFacade iamFacade,
            @Nullable StorageService storageService,
            @Nullable NotificationFacade notificationFacade
    ) {
        this.disputeRepository = disputeRepository;
        this.disputeEvidenceRepository = disputeEvidenceRepository;
        this.auditLogRepository = auditLogRepository;
        this.paymentFacade = paymentFacade;
        this.bookingOrderFacade = bookingOrderFacade;
        this.marketplaceFacade = marketplaceFacade;
        this.filmLabFacade = filmLabFacade;
        this.iamFacade = iamFacade;
        this.storageService = storageService;
        this.notificationFacade = notificationFacade;
    }

    @Transactional
    public DisputeOpenedResponse openDispute(OpenDisputeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin mở tranh chấp không được để trống.");
        }
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        UUID orderId = request.referenceOrderId();

        Optional<Dispute> existingDispute = disputeRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (existingDispute.isPresent()) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Đơn hàng này đã có hồ sơ tranh chấp tồn tại trên hệ thống."
            );
        }

        OrderPartyContext orderContext = resolveAndAuthorizeOrderParty(request.disputeType(), orderId, currentUserId);
        String escrowRefType = request.disputeType().toEscrowReferenceType();
        BigDecimal requestedRefund = request.requestedRefundAmountVnd().setScale(2, RoundingMode.HALF_UP);

        Optional<EscrowSnapshotDto> preEscrowOpt = paymentFacade.getEscrowSnapshot(orderId, escrowRefType);
        UUID respondentUserId = orderContext.respondentUserId();
        if (preEscrowOpt.isPresent()) {
            EscrowSnapshotDto snapshot = preEscrowOpt.get();
            if (snapshot.terminal()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Không thể mở tranh chấp: Khoản tiền tạm giữ Escrow của đơn hàng đã được giải ngân hoặc hoàn tất."
                );
            }
            if (requestedRefund.compareTo(snapshot.remainingAmountVnd()) > 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Số tiền đề nghị hoàn trả (" + requestedRefund.toPlainString()
                                + " VND) vượt quá số dư Escrow còn lại (" + snapshot.remainingAmountVnd().toPlainString() + " VND)."
                );
            }
            if (respondentUserId == null) {
                respondentUserId = currentUserId.equals(snapshot.payerUserId())
                        ? snapshot.payeeUserId()
                        : snapshot.payerUserId();
            }
        }

        String disputeCode = generateDisputeCode();
        Dispute dispute = Dispute.builder()
                .orderId(orderId)
                .reporterId(currentUserId)
                .status(DisputeStatus.AWAITING_RESPONDENT_EVIDENCE.name())
                .disputeCode(disputeCode)
                .disputeType(request.disputeType().name())
                .orderCode(orderContext.orderCode())
                .respondentUserId(respondentUserId)
                .reasonCategory(request.reasonCategory().name())
                .claimDescription(request.claimDescription().trim())
                .requestedRefundAmountVnd(requestedRefund)
                .attachedAiScanReportId(request.attachedAiScanReportId())
                .build();
        dispute.syncSerializedFields();
        Dispute savedDispute = disputeRepository.save(dispute);
        savedDispute.hydrateTransientFields();

        try {
            paymentFacade.freezeForDispute(orderId, escrowRefType, savedDispute.getId());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }

        if (request.disputeType() == DisputeType.LAB_ORDER_DISPUTE) {
            bookingOrderFacade.updateOrderStatus(orderId, "DISPUTED");
        } else {
            marketplaceFacade.markOrderDisputed(orderId, savedDispute.getId());
        }

        int savedEvidenceCount = 0;
        if (request.initialEvidenceS3Keys() != null && !request.initialEvidenceS3Keys().isEmpty()) {
            List<DisputeEvidence> evidences = new ArrayList<>();
            for (String rawKey : request.initialEvidenceS3Keys()) {
                if (rawKey == null || rawKey.isBlank()) {
                    continue;
                }
                evidences.add(DisputeEvidence.builder()
                        .disputeId(savedDispute.getId())
                        .uploaderId(currentUserId)
                        .fileUrl(rawKey.trim())
                        .description("Bằng chứng khởi tạo tranh chấp (" + request.reasonCategory().name() + ")")
                        .build());
            }
            if (!evidences.isEmpty()) {
                disputeEvidenceRepository.saveAll(evidences);
                savedEvidenceCount = evidences.size();
            }
        }

        Optional<EscrowSnapshotDto> postEscrowOpt = paymentFacade.getEscrowSnapshot(orderId, escrowRefType);
        String escrowHoldStatus = postEscrowOpt.map(EscrowSnapshotDto::status).orElse("FROZEN_BY_DISPUTE");
        BigDecimal frozenAmount = postEscrowOpt.map(EscrowSnapshotDto::remainingAmountVnd).orElse(requestedRefund);

        Instant createdAt = savedDispute.getCreatedAt() != null ? savedDispute.getCreatedAt() : Instant.now();
        Instant respondentDeadlineAt = createdAt.plus(48, ChronoUnit.HOURS);

        sendAlertSafely(
                respondentUserId,
                "Đơn hàng có khiếu nại tranh chấp mới (" + disputeCode + ")",
                "Đơn hàng " + orderContext.orderCode() + " đã bị mở tranh chấp và đóng băng khoản tiền Escrow. "
                        + "Vui lòng cung cấp bằng chứng phản hồi trong vòng 48 giờ."
        );

        return new DisputeOpenedResponse(
                savedDispute.getId(),
                disputeCode,
                request.disputeType(),
                orderId,
                orderContext.orderCode(),
                currentUserId,
                respondentUserId,
                savedDispute.getStatus(),
                escrowHoldStatus,
                frozenAmount,
                requestedRefund,
                savedEvidenceCount,
                respondentDeadlineAt,
                createdAt
        );
    }

    @Transactional
    public DisputeEvidenceDto submitEvidence(UUID disputeId, SubmitDisputeEvidenceRequest request) {
        if (disputeId == null || request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin nộp bằng chứng không hợp lệ.");
        }
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        UUID currentUserId = currentUser.getUserId();

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        dispute.hydrateTransientFields();

        boolean isComplainant = currentUserId.equals(dispute.getComplainantUserId());
        boolean isRespondent = currentUserId.equals(dispute.getRespondentUserId());
        if (!currentUser.isAdmin() && !isComplainant && !isRespondent) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không phải bên liên quan của hồ sơ tranh chấp này."
            );
        }

        if (DisputeStatus.isResolvedStatus(dispute.getStatus())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Hồ sơ tranh chấp đã kết thúc phân xử, không thể nộp thêm bằng chứng mới."
            );
        }

        String s3Key = request.mediaS3Key().trim();
        if (storageService != null && !storageService.exists(s3Key)) {
            throw new BusinessException(
                    ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                    "Tệp bằng chứng '" + s3Key + "' chưa tồn tại trên Storage. Vui lòng tải tệp lên trước."
            );
        }

        DisputeEvidence evidence = disputeEvidenceRepository.save(DisputeEvidence.builder()
                .disputeId(disputeId)
                .uploaderId(currentUserId)
                .fileUrl(s3Key)
                .description(request.description().trim())
                .build());

        if (isRespondent && isAwaitingEvidenceStatus(dispute.getStatus())) {
            dispute.setStatus(DisputeStatus.UNDER_ADMIN_REVIEW.name());
            dispute.syncSerializedFields();
            disputeRepository.save(dispute);
        }

        String partyRole = isRespondent ? "RESPONDENT" : (isComplainant ? "COMPLAINANT" : "ADMIN");
        UUID counterpartyId = isRespondent ? dispute.getComplainantUserId() : dispute.getRespondentUserId();
        sendAlertSafely(
                counterpartyId,
                "Hồ sơ tranh chấp có bằng chứng mới",
                "Bên đối phương vừa tải lên bằng chứng bổ sung cho hồ sơ tranh chấp "
                        + (dispute.getDisputeCode() != null ? dispute.getDisputeCode() : disputeId) + "."
        );

        return new DisputeEvidenceDto(
                evidence.getId(),
                disputeId,
                currentUserId,
                partyRole,
                s3Key,
                resolveReadUrlSafely(s3Key),
                evidence.getDescription(),
                dispute.getStatus(),
                evidence.getCreatedAt() != null ? evidence.getCreatedAt() : Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeSummaryAdminDto> listDisputes(
            @Nullable DisputeStatus status,
            @Nullable DisputeType disputeType,
            @Nullable String disputeCode,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);

        String trimmedCode = (disputeCode != null && !disputeCode.isBlank()) ? disputeCode.trim() : null;
        List<Dispute> all = disputeRepository.findAllByOrderByCreatedAtDesc();
        if (all.isEmpty()) {
            return PageResponse.from(List.of(), safePage, safeSize, 0, 0);
        }

        List<Dispute> filtered = new ArrayList<>();
        for (Dispute dispute : all) {
            dispute.hydrateTransientFields();
            if (status != null && !status.name().equalsIgnoreCase(dispute.getStatus())) {
                continue;
            }
            if (disputeType != null && !disputeType.name().equalsIgnoreCase(dispute.getDisputeType())) {
                continue;
            }
            if (trimmedCode != null && !trimmedCode.equalsIgnoreCase(dispute.getDisputeCode())) {
                continue;
            }
            filtered.add(dispute);
        }

        int totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = safePage * safeSize;
        if (fromIndex >= totalElements) {
            return PageResponse.from(List.of(), safePage, safeSize, totalElements, totalPages);
        }
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<Dispute> slice = filtered.subList(fromIndex, toIndex);

        List<UUID> sliceIds = slice.stream().map(Dispute::getId).toList();
        Map<UUID, Long> evidenceCounts = new LinkedHashMap<>();
        if (!sliceIds.isEmpty()) {
            for (DisputeEvidence ev : disputeEvidenceRepository.findByDisputeIdInOrderByCreatedAtAsc(sliceIds)) {
                evidenceCounts.merge(ev.getDisputeId(), 1L, Long::sum);
            }
        }

        List<DisputeSummaryAdminDto> items = slice.stream()
                .map(d -> new DisputeSummaryAdminDto(
                        d.getId(),
                        d.getDisputeCode(),
                        d.getDisputeType(),
                        d.getOrderId(),
                        d.getComplainantUserId(),
                        d.getRespondentUserId(),
                        d.getStatus(),
                        evidenceCounts.getOrDefault(d.getId(), 0L),
                        d.getCreatedAt(),
                        d.getUpdatedAt()
                ))
                .toList();
        return PageResponse.from(items, safePage, safeSize, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public DisputeDetailAdminDto getDisputeDetail(UUID disputeId) {
        if (disputeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "disputeId không được để trống.");
        }
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        dispute.hydrateTransientFields();

        List<UUID> userIds = new ArrayList<>();
        if (dispute.getComplainantUserId() != null) {
            userIds.add(dispute.getComplainantUserId());
        }
        if (dispute.getRespondentUserId() != null) {
            userIds.add(dispute.getRespondentUserId());
        }
        Map<UUID, UserSummaryDto> usersById = userIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(userIds);

        DisputeDetailAdminDto.PartySummary complainant = toPartySummary(
                dispute.getComplainantUserId(),
                usersById.get(dispute.getComplainantUserId())
        );
        DisputeDetailAdminDto.PartySummary respondent = toPartySummary(
                dispute.getRespondentUserId(),
                usersById.get(dispute.getRespondentUserId())
        );

        String escrowRefType = resolveEscrowReferenceType(dispute);
        DisputeDetailAdminDto.EscrowSnapshotInfo escrowSnapshot = paymentFacade
                .getEscrowSnapshot(dispute.getOrderId(), escrowRefType)
                .map(s -> new DisputeDetailAdminDto.EscrowSnapshotInfo(
                        s.escrowId(),
                        s.status(),
                        s.grossAmountVnd(),
                        s.refundedAmountVnd(),
                        s.remainingAmountVnd(),
                        s.commissionFeeVnd(),
                        s.netPayoutAmountVnd()
                ))
                .orElse(null);

        List<DisputeEvidenceDto> evidences = disputeEvidenceRepository
                .findByDisputeIdOrderByCreatedAtAsc(disputeId)
                .stream()
                .map(ev -> new DisputeEvidenceDto(
                        ev.getId(),
                        disputeId,
                        ev.getUploaderId(),
                        resolvePartyRole(dispute, ev.getUploaderId()),
                        ev.getFileUrl(),
                        resolveReadUrlSafely(ev.getFileUrl()),
                        ev.getDescription(),
                        dispute.getStatus(),
                        ev.getCreatedAt()
                ))
                .toList();

        DisputeDetailAdminDto.ResolutionSummary resolution = new DisputeDetailAdminDto.ResolutionSummary(
                dispute.getRefundToBuyerVnd(),
                dispute.getReleaseToSellerVnd(),
                dispute.getResolvedByAdminId(),
                dispute.getResolutionNote(),
                dispute.getResolvedAt()
        );

        return new DisputeDetailAdminDto(
                dispute.getId(),
                dispute.getDisputeCode(),
                dispute.getDisputeType(),
                dispute.getOrderId(),
                dispute.getOrderCode(),
                dispute.getStatus(),
                dispute.getClaimDescription(),
                complainant,
                respondent,
                escrowSnapshot,
                evidences,
                resolution
        );
    }

    @Transactional
    public ResolveDisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request) {
        if (disputeId == null || request == null || request.resolutionDecision() == null
                || request.refundToBuyerVnd() == null || request.releaseToSellerVnd() == null
                || request.resolutionNote() == null || request.resolutionNote().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thông tin phán quyết tranh chấp không hợp lệ.");
        }

        UUID adminUserId = SecurityContextUtils.getRequiredCurrentUserId();
        String resolutionNote = request.resolutionNote().trim();
        DisputeResolutionDecision decision = request.resolutionDecision();

        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .or(() -> disputeRepository.findById(disputeId))
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", disputeId));
        dispute.hydrateTransientFields();

        if (DisputeStatus.isResolvedStatus(dispute.getStatus())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Hồ sơ tranh chấp này đã được phân xử trước đó (trạng thái: " + dispute.getStatus() + ")."
            );
        }

        BigDecimal buyerRefund = request.refundToBuyerVnd().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerGross = request.releaseToSellerVnd().setScale(2, RoundingMode.HALF_UP);
        validateDecisionAmounts(decision, buyerRefund, sellerGross);

        UUID orderId = dispute.getOrderId();
        String escrowRefType = resolveEscrowReferenceType(dispute);

        Optional<EscrowSnapshotDto> preEscrowOpt = paymentFacade.getEscrowSnapshot(orderId, escrowRefType);
        if (preEscrowOpt.isPresent()) {
            EscrowSnapshotDto preSnapshot = preEscrowOpt.get();
            if (preSnapshot.terminal()) {
                throw new BusinessException(
                        ErrorCode.DUPLICATE_RESOURCE,
                        "Khoản Escrow của đơn hàng đã được tất toán trước đó (" + preSnapshot.status() + ")."
                );
            }
            BigDecimal totalAllocated = buyerRefund.add(sellerGross).setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = preSnapshot.remainingAmountVnd().setScale(2, RoundingMode.HALF_UP);
            if (totalAllocated.compareTo(remaining) != 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Tổng số tiền hoàn trả (" + buyerRefund.toPlainString() + ") và giải ngân ("
                                + sellerGross.toPlainString() + ") phải bằng đúng số dư Escrow đang đóng băng ("
                                + remaining.toPlainString() + " VND)."
                );
            }
        }

        try {
            switch (decision) {
                case REFUND_BUYER -> paymentFacade.fullRefund(
                        orderId,
                        escrowRefType,
                        "DISPUTE_REFUND_BUYER:" + disputeId
                );
                case RELEASE_SELLER -> paymentFacade.resolveDisputeSplit(
                        orderId,
                        escrowRefType,
                        new BigDecimal("0.00"),
                        sellerGross
                );
                case SPLIT -> paymentFacade.resolveDisputeSplit(
                        orderId,
                        escrowRefType,
                        buyerRefund,
                        sellerGross
                );
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, ex.getMessage());
        }

        Optional<EscrowSnapshotDto> postEscrowOpt = paymentFacade.getEscrowSnapshot(orderId, escrowRefType);
        String orderStatusUpdatedTo = updateUnderlyingOrderStatusOnResolution(orderId, escrowRefType, decision);

        String previousStatus = dispute.getStatus();
        String newStatus = "RESOLVED_" + decision.name();
        Instant resolvedAt = Instant.now();

        dispute.setStatus(newStatus);
        dispute.setRefundToBuyerVnd(buyerRefund);
        dispute.setReleaseToSellerVnd(sellerGross);
        dispute.setResolvedByAdminId(adminUserId);
        dispute.setResolvedAt(resolvedAt);
        dispute.setResolutionNote(resolutionNote);
        dispute.syncSerializedFields();
        disputeRepository.save(dispute);

        BigDecimal commissionFee = postEscrowOpt.map(EscrowSnapshotDto::commissionFeeVnd).orElse(new BigDecimal("0.00"));
        BigDecimal sellerNet = postEscrowOpt.map(EscrowSnapshotDto::netPayoutAmountVnd)
                .orElse(sellerGross.subtract(commissionFee).max(BigDecimal.ZERO));
        UUID escrowId = postEscrowOpt.map(EscrowSnapshotDto::escrowId).orElse(null);
        String escrowStatusAfter = postEscrowOpt.map(EscrowSnapshotDto::status)
                .orElse(switch (decision) {
                    case REFUND_BUYER -> "REFUNDED_TO_PAYER";
                    case RELEASE_SELLER -> "RELEASED_TO_PAYEE";
                    case SPLIT -> "SPLIT_SETTLED";
                });

        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("status", previousStatus != null ? previousStatus : "OPEN");
        preEscrowOpt.ifPresent(s -> oldValues.put("escrowStatus", s.status()));

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", newStatus);
        newValues.put("decision", decision.name());
        newValues.put("refundToBuyerVnd", buyerRefund.toPlainString());
        newValues.put("releaseToSellerVnd", sellerGross.toPlainString());
        newValues.put("platformCommissionFeeVnd", commissionFee.toPlainString());
        newValues.put("netCreditedToSellerWalletVnd", sellerNet.toPlainString());
        newValues.put("escrowStatusAfter", escrowStatusAfter);
        newValues.put("orderStatusUpdatedTo", orderStatusUpdatedTo);
        newValues.put("resolutionNote", resolutionNote);

        AuditLog savedAudit = auditLogRepository.save(AuditLog.builder()
                .adminId(adminUserId)
                .actionType("RESOLVE_DISPUTE")
                .targetEntity(AUDIT_TARGET_DISPUTE)
                .targetId(disputeId)
                .oldValues(oldValues)
                .newValues(newValues)
                .ipAddress(resolveClientIpAddress())
                .build());

        notifyDisputePartiesResolved(dispute, newStatus, buyerRefund, sellerNet, resolutionNote);

        return new ResolveDisputeResponse(
                dispute.getId(),
                dispute.getDisputeCode(),
                previousStatus,
                newStatus,
                new ResolveDisputeResponse.FinancialSettlement(
                        escrowId,
                        escrowStatusAfter,
                        buyerRefund,
                        sellerGross,
                        commissionFee,
                        sellerNet
                ),
                orderStatusUpdatedTo,
                adminUserId,
                savedAudit.getId(),
                resolvedAt
        );
    }

    private OrderPartyContext resolveAndAuthorizeOrderParty(
            DisputeType disputeType,
            UUID orderId,
            UUID currentUserId
    ) {
        if (disputeType == DisputeType.LAB_ORDER_DISPUTE) {
            LabOrderResponse order = bookingOrderFacade.getOrderDetails(orderId);
            if (order == null) {
                throw ResourceNotFoundException.order(orderId);
            }
            if (order.status() != null && INELIGIBLE_ORDER_STATUSES.contains(order.status().toUpperCase(Locale.ROOT))) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Đơn hàng ở trạng thái " + order.status() + " không đủ điều kiện mở tranh chấp."
                );
            }
            UUID labOwnerUserId = filmLabFacade.findLabById(order.labId())
                    .map(FilmLabFacade.ProvisionedLabInfo::ownerUserId)
                    .orElse(null);

            boolean isCustomer = currentUserId.equals(order.customerId());
            boolean isLabOwner = labOwnerUserId != null && currentUserId.equals(labOwnerUserId);
            if (!isCustomer && !isLabOwner) {
                throw new BusinessException(
                        ErrorCode.ACCESS_DENIED,
                        "Bạn không phải khách hàng hoặc chủ Film Lab của đơn hàng này."
                );
            }
            UUID respondentId = isCustomer ? labOwnerUserId : order.customerId();
            return new OrderPartyContext(order.orderCode(), respondentId);
        }

        MarketplaceOrderDto order = marketplaceFacade.getOrderById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.order(orderId));
        if (order.status() != null && INELIGIBLE_ORDER_STATUSES.contains(order.status().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đơn hàng C2C ở trạng thái " + order.status() + " không đủ điều kiện mở tranh chấp."
            );
        }
        boolean isBuyer = currentUserId.equals(order.buyerUserId());
        boolean isSeller = currentUserId.equals(order.sellerUserId());
        if (!isBuyer && !isSeller) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không phải người mua hoặc người bán của đơn hàng C2C này."
            );
        }
        UUID respondentId = isBuyer ? order.sellerUserId() : order.buyerUserId();
        return new OrderPartyContext(order.orderCode(), respondentId);
    }

    private String updateUnderlyingOrderStatusOnResolution(
            UUID orderId,
            String escrowRefType,
            DisputeResolutionDecision decision
    ) {
        if ("MARKETPLACE_ORDER".equalsIgnoreCase(escrowRefType)) {
            marketplaceFacade.resolveOrderDispute(orderId, decision.name());
            return decision == DisputeResolutionDecision.REFUND_BUYER ? "REFUNDED" : "COMPLETED";
        }
        String targetLabOrderStatus = decision == DisputeResolutionDecision.REFUND_BUYER ? "REFUNDED" : "COMPLETED";
        bookingOrderFacade.updateOrderStatus(orderId, targetLabOrderStatus);
        return targetLabOrderStatus;
    }

    private String resolveEscrowReferenceType(Dispute dispute) {
        if (dispute.getDisputeType() != null && !dispute.getDisputeType().isBlank()) {
            try {
                return DisputeType.valueOf(dispute.getDisputeType().trim().toUpperCase(Locale.ROOT))
                        .toEscrowReferenceType();
            } catch (IllegalArgumentException ignored) {
                if ("MARKETPLACE_ORDER".equalsIgnoreCase(dispute.getDisputeType().trim())) {
                    return "MARKETPLACE_ORDER";
                }
            }
        }
        return "LAB_ORDER";
    }

    private static void validateDecisionAmounts(
            DisputeResolutionDecision decision,
            BigDecimal buyerRefund,
            BigDecimal sellerGross
    ) {
        if (buyerRefund.compareTo(BigDecimal.ZERO) < 0 || sellerGross.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền hoàn trả hoặc giải ngân không được âm.");
        }
        switch (decision) {
            case REFUND_BUYER -> {
                if (buyerRefund.compareTo(BigDecimal.ZERO) <= 0 || sellerGross.compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Phán quyết REFUND_BUYER yêu cầu refundToBuyerVnd > 0 và releaseToSellerVnd == 0."
                    );
                }
            }
            case RELEASE_SELLER -> {
                if (sellerGross.compareTo(BigDecimal.ZERO) <= 0 || buyerRefund.compareTo(BigDecimal.ZERO) != 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Phán quyết RELEASE_SELLER yêu cầu releaseToSellerVnd > 0 và refundToBuyerVnd == 0."
                    );
                }
            }
            case SPLIT -> {
                if (buyerRefund.compareTo(BigDecimal.ZERO) <= 0 || sellerGross.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Phán quyết SPLIT yêu cầu cả refundToBuyerVnd và releaseToSellerVnd đều phải > 0."
                    );
                }
            }
        }
    }

    private static boolean isAwaitingEvidenceStatus(String status) {
        return "AWAITING_RESPONDENT_EVIDENCE".equalsIgnoreCase(status)
                || "OPENED".equalsIgnoreCase(status)
                || "OPEN".equalsIgnoreCase(status);
    }

    private static String resolvePartyRole(Dispute dispute, UUID userId) {
        if (userId == null) {
            return "UNKNOWN";
        }
        if (userId.equals(dispute.getComplainantUserId())) {
            return "COMPLAINANT";
        }
        if (userId.equals(dispute.getRespondentUserId())) {
            return "RESPONDENT";
        }
        return "ADMIN";
    }

    private static DisputeDetailAdminDto.PartySummary toPartySummary(UUID userId, @Nullable UserSummaryDto user) {
        if (user != null) {
            return new DisputeDetailAdminDto.PartySummary(user.id(), user.fullName(), user.email());
        }
        return new DisputeDetailAdminDto.PartySummary(userId, null, null);
    }

    private @Nullable String resolveReadUrlSafely(@Nullable String key) {
        if (storageService == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            return storageService.resolveReadUrl(key.trim());
        } catch (Exception ex) {
            log.debug("Failed to resolve read URL for key={}: {}", key, ex.getMessage());
            return null;
        }
    }

    private void notifyDisputePartiesResolved(
            Dispute dispute,
            String newStatus,
            BigDecimal buyerRefund,
            BigDecimal sellerNet,
            String resolutionNote
    ) {
        String code = dispute.getDisputeCode() != null ? dispute.getDisputeCode() : dispute.getId().toString();
        String title = "Kết quả phân xử tranh chấp " + code;
        String body = "Hồ sơ tranh chấp " + code + " đã được Admin phân xử (" + newStatus
                + "). Hoàn trả người mua: " + buyerRefund.toPlainString()
                + " VND; Giải ngân ròng người bán/Lab: " + sellerNet.toPlainString()
                + " VND. Ghi chú: " + resolutionNote;

        sendAlertSafely(dispute.getComplainantUserId(), title, body);
        if (!Objects.equals(dispute.getRespondentUserId(), dispute.getComplainantUserId())) {
            sendAlertSafely(dispute.getRespondentUserId(), title, body);
        }
    }

    private void sendAlertSafely(@Nullable UUID userId, String title, String body) {
        if (notificationFacade == null || userId == null) {
            return;
        }
        try {
            notificationFacade.sendSystemAlert(userId, title, body);
        } catch (Exception ex) {
            log.warn("Failed to send dispute notification to userId={}: {}", userId, ex.getMessage());
        }
    }

    private static String generateDisputeCode() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DISPUTE_DATE_FORMAT);
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "DSP-" + datePart + "-" + suffix;
    }

    private static @Nullable String resolveClientIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        return trimmed.length() > 45 ? trimmed.substring(0, 45) : trimmed;
    }

    private record OrderPartyContext(String orderCode, @Nullable UUID respondentUserId) {
    }
}
