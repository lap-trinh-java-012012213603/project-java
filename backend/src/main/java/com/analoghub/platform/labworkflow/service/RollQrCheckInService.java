package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.booking.facade.BookingOrderFacade;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.QrCheckInRequest;
import com.analoghub.platform.labworkflow.event.FilmRollStatusChangedEvent;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RollQrCheckInService {

    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;
    private final BookingOrderFacade bookingOrderFacade;
    private final FilmLabFacade filmLabFacade;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FilmRollResponse checkInRollByQr(UUID labId, UUID operatorUserId, QrCheckInRequest request) {
        String tokenOrCode = request.qrTokenOrRollCode().trim();
        String hashedInput = sha256Hex(tokenOrCode);

        FilmRoll roll = filmRollRepository.findByQrCheckinTokenHash(tokenOrCode)
                .or(() -> filmRollRepository.findByQrCheckinTokenHash(hashedInput))
                .or(() -> filmRollRepository.findByRollCode(tokenOrCode))
                .orElseThrow(() -> ResourceNotFoundException.roll(tokenOrCode));

        if (!roll.getLabId().equals(labId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cuộn film này không thuộc Lab của bạn");
        }
        if (!"AWAITING_CHECKIN".equals(roll.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cuộn film đã được check-in hoặc đang ở trạng thái: " + roll.getStatus());
        }

        int turnaroundHours = filmLabFacade.getEstimatedTurnaroundHours(labId, roll.getProcessType());
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime slaDeadline = now.plusHours(turnaroundHours);

        String oldStatus = roll.getStatus();
        roll.setStatus("CHECKED_IN");
        roll.setCheckedInAt(now);
        roll.setSlaDeadlineAt(slaDeadline);
        FilmRoll saved = filmRollRepository.save(roll);

        FilmRollStatusLog statusLog = new FilmRollStatusLog();
        statusLog.setFilmRollId(saved.getId());
        statusLog.setLabId(labId);
        statusLog.setFromStatus(oldStatus);
        statusLog.setToStatus("CHECKED_IN");
        statusLog.setChangedByUserId(operatorUserId);
        statusLog.setNote(request.note() != null ? request.note() : "Quét mã QR Check-in thành công");
        statusLogRepository.save(statusLog);

        bookingOrderFacade.updateOrderStatus(saved.getOrderId(), "IN_LAB_PROCESSING");
        eventPublisher.publishEvent(new FilmRollStatusChangedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getLabId(),
                saved.getCustomerId(),
                saved.getRollCode(),
                oldStatus,
                "CHECKED_IN",
                saved.getSlaDeadlineAt(),
                now
        ));

        return new FilmRollResponse(
                saved.getId(),
                saved.getOrderId(),
                saved.getOrderItemId(),
                saved.getLabId(),
                saved.getCustomerId(),
                saved.getRollCode(),
                saved.getFilmFormat(),
                saved.getProcessType(),
                saved.getScanResolution(),
                saved.getPushPullStops(),
                saved.getStatus(),
                Boolean.TRUE.equals(saved.getIsBlankRoll()),
                saved.getCheckedInAt(),
                saved.getSlaDeadlineAt(),
                Boolean.TRUE.equals(saved.getSlaBreachNotified()),
                saved.getCompletedAt()
        );
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return input;
        }
    }
}
