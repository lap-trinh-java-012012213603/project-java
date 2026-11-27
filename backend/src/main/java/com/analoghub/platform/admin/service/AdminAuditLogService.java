package com.analoghub.platform.admin.service;

import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.dto.AdminAuditLogDto;
import com.analoghub.platform.admin.repository.AuditLogRepository;
import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Append-only audit logging and read-only search service backed by {@code adm_audit_logs}.
 */
@Slf4j
@Service
public class AdminAuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final @Nullable IamFacade iamFacade;

    public AdminAuditLogService(AuditLogRepository auditLogRepository) {
        this(auditLogRepository, null);
    }

    @Autowired
    public AdminAuditLogService(
            AuditLogRepository auditLogRepository,
            @Nullable IamFacade iamFacade
    ) {
        this.auditLogRepository = auditLogRepository;
        this.iamFacade = iamFacade;
    }

    /**
     * Appends an immutable audit entry into {@code adm_audit_logs}.
     */
    @Transactional
    public AuditLog recordAuditLog(
            @Nullable UUID adminId,
            String actionType,
            @Nullable String targetEntity,
            @Nullable UUID targetId,
            @Nullable Map<String, Object> oldValues,
            @Nullable Map<String, Object> newValues,
            @Nullable String ipAddress
    ) {
        UUID effectiveAdminId = adminId != null ? adminId : SecurityContextUtils.getRequiredCurrentUserId();
        if (actionType == null || actionType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "actionType không được để trống.");
        }

        String normalizedAction = truncate(actionType.trim().toUpperCase(Locale.ROOT), 100);
        String normalizedTarget = targetEntity != null && !targetEntity.isBlank()
                ? truncate(targetEntity.trim().toUpperCase(Locale.ROOT), 50)
                : null;
        String resolvedIp = truncate(
                (ipAddress != null && !ipAddress.isBlank()) ? ipAddress.trim() : resolveClientIpAddress(),
                45
        );

        AuditLog entry = AuditLog.builder()
                .adminId(effectiveAdminId)
                .actionType(normalizedAction)
                .targetEntity(normalizedTarget)
                .targetId(targetId)
                .oldValues(copyMap(oldValues))
                .newValues(copyMap(newValues))
                .ipAddress(resolvedIp)
                .build();

        return auditLogRepository.save(entry);
    }

    /**
     * Read-only filtered search over immutable Admin audit logs (API 14).
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogDto> searchAuditLogs(
            @Nullable UUID actorUserId,
            @Nullable String actionCode,
            @Nullable String targetEntityType,
            @Nullable UUID targetEntityId,
            @Nullable Instant fromTime,
            @Nullable Instant toTime,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ.");
        }
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Khoảng thời gian tra cứu nhật ký kiểm toán không hợp lệ: fromTime không được lớn hơn toTime."
            );
        }

        Specification<AuditLog> spec = buildSpecification(
                actorUserId,
                actionCode,
                targetEntityType,
                targetEntityId,
                fromTime,
                toTime
        );

        Page<AuditLog> resultPage = auditLogRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        Set<UUID> actorIds = resultPage.getContent().stream()
                .map(AuditLog::getAdminId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserSummaryDto> actorsById = (iamFacade != null && !actorIds.isEmpty())
                ? iamFacade.findUsersByIds(actorIds)
                : Map.of();

        List<AdminAuditLogDto> items = resultPage.getContent().stream()
                .map(logEntry -> toDto(logEntry, actorsById))
                .toList();

        return PageResponse.from(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                (int) resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    private Specification<AuditLog> buildSpecification(
            @Nullable UUID actorUserId,
            @Nullable String actionCode,
            @Nullable String targetEntityType,
            @Nullable UUID targetEntityId,
            @Nullable Instant fromTime,
            @Nullable Instant toTime
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("adminId"), actorUserId));
            }
            if (actionCode != null && !actionCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("actionType")), actionCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (targetEntityType != null && !targetEntityType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("targetEntity")), targetEntityType.trim().toUpperCase(Locale.ROOT)));
            }
            if (targetEntityId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetEntityId));
            }
            if (fromTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toTime));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private AdminAuditLogDto toDto(AuditLog entry, Map<UUID, UserSummaryDto> actorsById) {
        UserSummaryDto user = entry.getAdminId() != null ? actorsById.get(entry.getAdminId()) : null;
        AdminAuditLogDto.ActorSummary actor = user != null
                ? new AdminAuditLogDto.ActorSummary(user.id(), user.email(), user.fullName())
                : new AdminAuditLogDto.ActorSummary(entry.getAdminId(), null, null);
        return new AdminAuditLogDto(
                entry.getId(),
                actor,
                entry.getActionType(),
                entry.getTargetEntity(),
                entry.getTargetId(),
                entry.getOldValues(),
                entry.getNewValues(),
                entry.getIpAddress(),
                entry.getCreatedAt()
        );
    }

    public static String resolveClientIpAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return truncate(forwarded.split(",")[0].trim(), 45);
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return truncate(realIp.trim(), 45);
            }
            if (request.getRemoteAddr() != null && !request.getRemoteAddr().isBlank()) {
                return truncate(request.getRemoteAddr().trim(), 45);
            }
        }
        return "UNKNOWN";
    }

    private static @Nullable Map<String, Object> copyMap(@Nullable Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
