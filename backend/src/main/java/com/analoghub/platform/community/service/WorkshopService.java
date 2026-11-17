package com.analoghub.platform.community.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.CommunityException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.community.domain.Workshop;
import com.analoghub.platform.community.domain.WorkshopRegistration;
import com.analoghub.platform.community.domain.WorkshopRegistrationStatus;
import com.analoghub.platform.community.domain.WorkshopStatus;
import com.analoghub.platform.community.domain.WorkshopType;
import com.analoghub.platform.community.dto.CreateWorkshopRequest;
import com.analoghub.platform.community.dto.ExpertArticleDetailResponse;
import com.analoghub.platform.community.dto.WorkshopCheckInRequest;
import com.analoghub.platform.community.dto.WorkshopCheckInResponse;
import com.analoghub.platform.community.dto.WorkshopRegistrationResponse;
import com.analoghub.platform.community.dto.WorkshopResponse;
import com.analoghub.platform.community.event.WorkshopRegisteredEvent;
import com.analoghub.platform.community.repository.WorkshopRegistrationRepository;
import com.analoghub.platform.community.repository.WorkshopRepository;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationRepository registrationRepository;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WorkshopResponse createWorkshop(UUID organizerUserId, CreateWorkshopRequest request) {
        WorkshopType workshopType = parseWorkshopType(request.workshopType());

        Instant minStartTime = Instant.now().plus(6, ChronoUnit.HOURS);
        if (request.startTime().isBefore(minStartTime)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian bắt đầu sự kiện phải cách hiện tại ít nhất 6 giờ"
            );
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian kết thúc sự kiện phải sau thời gian bắt đầu"
            );
        }

        Workshop workshop = Workshop.builder()
                .organizerUserId(organizerUserId)
                .workshopType(workshopType)
                .title(request.title().trim())
                .description(request.descriptionMarkdown().trim())
                .locationAddress(request.locationAddress().trim())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .maxParticipants(request.maxParticipants())
                .currentParticipants(0)
                .ticketPriceVnd(request.ticketPriceVnd())
                .status(WorkshopStatus.OPEN)
                .build();
        workshop = workshopRepository.saveAndFlush(workshop);

        UserSummaryDto organizer = iamFacade.findUserById(organizerUserId).orElse(null);
        return toWorkshopResponse(workshop, request.hostLabId(), organizer);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkshopResponse> listWorkshops(
            String workshopTypeStr,
            String statusStr,
            boolean freeOnly,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }

        WorkshopType typeFilter = null;
        if (workshopTypeStr != null && !workshopTypeStr.isBlank()) {
            typeFilter = parseWorkshopType(workshopTypeStr);
        }

        WorkshopStatus statusFilter = WorkshopStatus.OPEN;
        if (statusStr != null && !statusStr.isBlank()) {
            statusFilter = parseWorkshopStatus(statusStr);
        }

        Page<Workshop> workshopPage = workshopRepository.searchWorkshops(
                typeFilter,
                statusFilter,
                freeOnly,
                BigDecimal.ZERO,
                PageRequest.of(page, size)
        );

        List<Workshop> workshops = workshopPage.getContent();
        if (workshops.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        Set<UUID> organizerIds = workshops.stream().map(Workshop::getOrganizerUserId).collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> organizerMap = organizerIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(organizerIds);

        List<WorkshopResponse> items = workshops.stream()
                .map(w -> toWorkshopResponse(w, null, organizerMap.get(w.getOrganizerUserId())))
                .toList();

        return PageResponse.from(
                items,
                page,
                size,
                (int) workshopPage.getTotalElements(),
                workshopPage.getTotalPages()
        );
    }

    @Transactional
    public WorkshopRegistrationResponse registerWorkshop(UUID userId, UUID workshopId) {
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện Workshop"));

        if (workshop.getStatus() == WorkshopStatus.FULL || workshop.getCurrentParticipants() >= workshop.getMaxParticipants()) {
            throw CommunityException.eventCapacityFull();
        }
        if (workshop.getStatus() != WorkshopStatus.OPEN) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Sự kiện đã diễn ra, đã kết thúc hoặc đã bị hủy"
            );
        }

        if (registrationRepository.existsByWorkshopIdAndUserIdAndStatusNot(
                workshopId, userId, WorkshopRegistrationStatus.CANCELLED
        )) {
            throw CommunityException.alreadyJoinedEvent();
        }

        int updatedCount = workshop.getCurrentParticipants() + 1;
        workshop.setCurrentParticipants(updatedCount);
        if (updatedCount >= workshop.getMaxParticipants()) {
            workshop.setStatus(WorkshopStatus.FULL);
        }
        workshopRepository.saveAndFlush(workshop);

        boolean isFree = workshop.getTicketPriceVnd().compareTo(BigDecimal.ZERO) == 0;
        WorkshopRegistrationStatus regStatus = isFree
                ? WorkshopRegistrationStatus.CONFIRMED
                : WorkshopRegistrationStatus.PENDING_PAYMENT;

        String checkinCode = generateUniqueCheckinCode();
        WorkshopRegistration registration = WorkshopRegistration.builder()
                .workshopId(workshop.getId())
                .userId(userId)
                .status(regStatus)
                .checkinCode(checkinCode)
                .build();
        registration = registrationRepository.saveAndFlush(registration);

        if (isFree) {
            eventPublisher.publishEvent(new WorkshopRegisteredEvent(
                    registration.getId(),
                    workshop.getId(),
                    userId,
                    checkinCode
            ));
        }

        return new WorkshopRegistrationResponse(
                registration.getId(),
                workshop.getId(),
                userId,
                registration.getStatus().name(),
                workshop.getTicketPriceVnd(),
                "WORKSHOP_TICKET",
                registration.getCheckinCode(),
                registration.getCreatedAt()
        );
    }

    @Transactional
    public void handleTicketPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.referenceId() == null || event.paymentPurpose() == null) {
            return;
        }
        if (!"WORKSHOP_TICKET".equalsIgnoreCase(event.paymentPurpose().trim())) {
            return;
        }

        Optional<WorkshopRegistration> regOpt = registrationRepository.findById(event.referenceId());
        if (regOpt.isEmpty()) {
            return;
        }

        WorkshopRegistration registration = regOpt.get();
        if (registration.getStatus() != WorkshopRegistrationStatus.PENDING_PAYMENT) {
            return;
        }

        registration.setStatus(WorkshopRegistrationStatus.CONFIRMED);
        registrationRepository.saveAndFlush(registration);

        eventPublisher.publishEvent(new WorkshopRegisteredEvent(
                registration.getId(),
                registration.getWorkshopId(),
                registration.getUserId(),
                registration.getCheckinCode()
        ));
    }

    @Transactional
    public WorkshopCheckInResponse checkInParticipant(
            UUID actorUserId,
            boolean isAdmin,
            UUID workshopId,
            WorkshopCheckInRequest request
    ) {
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện Workshop"));

        if (!isAdmin && !workshop.getOrganizerUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ người tổ chức sự kiện mới có quyền quét mã check-in");
        }

        String normalizedCode = request.checkinCode().trim().toUpperCase();
        WorkshopRegistration registration = registrationRepository
                .findByWorkshopIdAndCheckinCode(workshopId, normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Mã vé check-in không tồn tại hoặc không thuộc sự kiện này"
                ));

        if (registration.getStatus() == WorkshopRegistrationStatus.ATTENDED) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Mã vé '" + normalizedCode + "' đã được check-in trước đó"
            );
        }

        if (registration.getStatus() != WorkshopRegistrationStatus.CONFIRMED) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vé đang ở trạng thái chưa thanh toán hoặc đã bị hủy, chưa đủ điều kiện vào cửa"
            );
        }

        Instant checkedInAt = Instant.now();
        registration.setStatus(WorkshopRegistrationStatus.ATTENDED);
        registration.setCheckedInAt(checkedInAt);
        registration = registrationRepository.saveAndFlush(registration);

        UserSummaryDto participant = iamFacade.findUserById(registration.getUserId()).orElse(null);
        WorkshopCheckInResponse.ParticipantSummary participantSummary = new WorkshopCheckInResponse.ParticipantSummary(
                registration.getUserId(),
                participant != null ? participant.fullName() : "Học viên AnalogHub",
                participant != null ? participant.phoneNumber() : null
        );

        return new WorkshopCheckInResponse(
                registration.getId(),
                workshop.getId(),
                participantSummary,
                registration.getCheckinCode(),
                registration.getStatus().name(),
                checkedInAt
        );
    }

    private WorkshopResponse toWorkshopResponse(Workshop w, UUID hostLabId, UserSummaryDto organizer) {
        ExpertArticleDetailResponse.AuthorSummary organizerSummary = new ExpertArticleDetailResponse.AuthorSummary(
                w.getOrganizerUserId(),
                organizer != null ? organizer.fullName() : "Ban tổ chức AnalogHub",
                organizer != null ? storageService.resolveReadUrl(organizer.avatarKey()) : null
        );
        int remaining = Math.max(0, w.getMaxParticipants() - w.getCurrentParticipants());
        return new WorkshopResponse(
                w.getId(),
                w.getOrganizerUserId(),
                hostLabId,
                w.getWorkshopType().name(),
                w.getTitle(),
                w.getDescription(),
                organizerSummary,
                w.getLocationAddress(),
                w.getStartTime(),
                w.getEndTime(),
                w.getMaxParticipants(),
                w.getCurrentParticipants(),
                remaining,
                w.getTicketPriceVnd(),
                w.getStatus().name(),
                w.getCreatedAt()
        );
    }

    private WorkshopType parseWorkshopType(String raw) {
        try {
            return WorkshopType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "workshopType chỉ hỗ trợ WORKSHOP hoặc PHOTOWALK");
        }
    }

    private WorkshopStatus parseWorkshopStatus(String raw) {
        String normalized = raw.trim().toUpperCase();
        if ("UPCOMING".equals(normalized) || "ONGOING".equals(normalized)) {
            return WorkshopStatus.OPEN;
        }
        try {
            return WorkshopStatus.valueOf(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số status sự kiện không hợp lệ: " + raw);
        }
    }

    private String generateUniqueCheckinCode() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        String code;
        do {
            String hex = String.format("%06X", ThreadLocalRandom.current().nextInt(0x100000, 0xFFFFFF));
            code = "WS-" + datePart + "-" + hex;
        } while (registrationRepository.existsByCheckinCode(code));
        return code;
    }
}
