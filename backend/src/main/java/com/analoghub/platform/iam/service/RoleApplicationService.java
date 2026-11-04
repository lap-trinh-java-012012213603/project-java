package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.domain.RoleApplication;
import com.analoghub.platform.iam.dto.RoleApplicationDto;
import com.analoghub.platform.iam.dto.RoleApplicationStatus;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.SubmitRoleApplicationRequest;
import com.analoghub.platform.iam.event.RoleApplicationSubmittedEvent;
import com.analoghub.platform.iam.repository.RoleApplicationRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.iam.repository.UserRoleRepository;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for submitting and querying Role Upgrade Applications (APIs 4.13 - 4.14 of {@code 04-iam.md}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleApplicationService {

    private final RoleApplicationRepository roleApplicationRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleApplicationPayloadValidator payloadValidator;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * API 4.13: Submits a role upgrade application ({@code LAB_OWNER}, {@code EXPERT}, {@code DELIVERY_PARTNER}),
     * enforcing duplicate-role and duplicate-pending guards, and publishes {@link RoleApplicationSubmittedEvent}.
     */
    @Transactional
    public RoleApplicationDto submitRoleApplication(SubmitRoleApplicationRequest request) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        RoleCode requestedRole = request.requestedRole();

        Map<String, Object> validatedPayload = payloadValidator.validateAndNormalize(
                currentUserId,
                requestedRole,
                request.portfolioPayload()
        );

        userRepository.findByIdForUpdate(currentUserId)
                .or(() -> userRepository.findByIdAndDeletedAtIsNull(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng."
                ));

        Set<RoleCode> existingRoles = userRoleRepository.findRoleCodesByUserId(currentUserId);
        if (existingRoles != null && existingRoles.contains(requestedRole)) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bạn đã sở hữu vai trò '" + requestedRole.name() + "' trên hệ thống."
            );
        }

        if (roleApplicationRepository.existsByUserIdAndRequestedRoleAndStatus(
                currentUserId,
                requestedRole,
                RoleApplicationStatus.PENDING
        )) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bạn đang có một hồ sơ xin nâng cấp vai trò '" + requestedRole.name()
                            + "' ở trạng thái chờ duyệt (PENDING). Vui lòng đợi kết quả xét duyệt từ Admin."
            );
        }

        RoleApplication application = RoleApplication.builder()
                .userId(currentUserId)
                .requestedRole(requestedRole)
                .portfolioPayload(validatedPayload)
                .status(RoleApplicationStatus.PENDING)
                .build();

        application = roleApplicationRepository.saveAndFlush(application);

        eventPublisher.publishEvent(new RoleApplicationSubmittedEvent(
                UUID.randomUUID(),
                application.getId(),
                currentUserId,
                requestedRole.name(),
                Instant.now()
        ));

        log.info("Submitted role application: id={}, userId={}, requestedRole={}",
                application.getId(), currentUserId, requestedRole);

        return toRoleApplicationDto(application, false);
    }

    /**
     * API 4.14: Lists the current user's role upgrade applications, optionally filtered by status,
     * and signs short-lived read URLs for evidence documents.
     */
    @Transactional(readOnly = true)
    public List<RoleApplicationDto> getMyRoleApplications(RoleApplicationStatus status) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        List<RoleApplication> applications = status == null
                ? roleApplicationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId)
                : roleApplicationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUserId, status);

        return applications.stream()
                .map(app -> toRoleApplicationDto(app, true))
                .toList();
    }

    private RoleApplicationDto toRoleApplicationDto(RoleApplication app, boolean resolveEvidenceUrls) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (app.getPortfolioPayload() != null) {
            payload.putAll(app.getPortfolioPayload());
        }

        if (resolveEvidenceUrls && storageService != null) {
            enrichEvidenceUrls(payload, "evidenceDocumentKeys", "evidenceDocumentUrls");
        }

        return new RoleApplicationDto(
                app.getId(),
                app.getUserId(),
                app.getRequestedRole(),
                Map.copyOf(payload),
                app.getStatus(),
                app.getReviewedByAdminId(),
                app.getReviewNote(),
                app.getReviewedAt(),
                app.getCreatedAt()
        );
    }

    private void enrichEvidenceUrls(Map<String, Object> payload, String keysField, String urlsField) {
        Object rawKeys = payload.get(keysField);
        if (!(rawKeys instanceof Collection<?> col) || col.isEmpty()) {
            return;
        }
        List<String> urls = new ArrayList<>();
        for (Object item : col) {
            if (item == null) {
                continue;
            }
            String key = item.toString().trim();
            try {
                String url = storageService.resolveReadUrl(key);
                urls.add(url != null ? url : key);
            } catch (Exception ex) {
                urls.add(key);
            }
        }
        payload.put(urlsField, List.copyOf(urls));
    }
}
