package com.analoghub.platform.labworkflow.service;

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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FilmRollResponse checkInRollByQr(UUID labId, UUID operatorUserId, QrCheckInRequest request) {
        String tokenOrCode = request.qrTokenOrRollCode().trim();
        String hashedInput = sha256Hex(tokenOrCode);

        FilmRoll roll = filmRollRepository.findByQrCheckinTokenHash(tokenOrCode)
                .or(() -> filmRollRepository.findByQrCheckinTokenHash(hashedInput))
                .or(() -> filmRollRepository.findByRollCode(tokenOrCode))
                .orElseThrow(() -> new IllegalArgumentException("Mã QR không hợp lệ"));

        if (!roll.getLabId().equals(labId)) throw new IllegalArgumentException("Không thuộc Lab");
        if (!"AWAITING_CHECKIN".equals(roll.getStatus())) throw new IllegalArgumentException("Lỗi trạng thái");

        OffsetDateTime now = OffsetDateTime.now();
        String oldStatus = roll.getStatus();
        roll.setStatus("CHECKED_IN");
        roll.setCheckedInAt(now);
        roll.setSlaDeadlineAt(now.plusHours(24)); // Mặc định 24h
        FilmRoll saved = filmRollRepository.save(roll);

        FilmRollStatusLog statusLog = new FilmRollStatusLog();
        statusLog.setFilmRollId(saved.getId());
        statusLog.setLabId(labId);
        statusLog.setFromStatus(oldStatus);
        statusLog.setToStatus("CHECKED_IN");
        statusLog.setChangedByUserId(operatorUserId);
        statusLog.setNote(request.note() != null ? request.note() : "Quét mã QR");
        statusLogRepository.save(statusLog);

        eventPublisher.publishEvent(new FilmRollStatusChangedEvent(saved.getId(), saved.getOrderId(), saved.getLabId(), saved.getCustomerId(), saved.getRollCode(), oldStatus, "CHECKED_IN", saved.getSlaDeadlineAt(), now));

        return new FilmRollResponse(saved.getId(), saved.getOrderId(), saved.getOrderItemId(), saved.getLabId(), saved.getCustomerId(), saved.getRollCode(), saved.getFilmFormat(), saved.getProcessType(), saved.getScanResolution(), saved.getPushPullStops(), saved.getStatus(), Boolean.TRUE.equals(saved.getIsBlankRoll()), saved.getCheckedInAt(), saved.getSlaDeadlineAt(), Boolean.TRUE.equals(saved.getSlaBreachNotified()), saved.getCompletedAt());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return input; }
    }
}