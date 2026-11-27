package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.ReportBlankRollRequest;
import com.analoghub.platform.labworkflow.event.BlankRollDetectedEvent;
import com.analoghub.platform.labworkflow.event.FilmRollStatusChangedEvent;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlankRollPolicyService {

    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;
    private final FilmLabFacade filmLabFacade;
    private final StompNotificationPublisher stompPublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FilmRollResponse reportBlankRoll(UUID labId, UUID rollId, UUID operatorId, ReportBlankRollRequest request) {
        FilmRoll roll = filmRollRepository.findById(rollId)
                .orElseThrow(() -> ResourceNotFoundException.roll(rollId));

        if (!roll.getLabId().equals(labId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cuộn film này không thuộc Lab của bạn");
        }
        if (Boolean.TRUE.equals(roll.getIsBlankRoll())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cuộn film này đã được ghi nhận film trắng trước đó");
        }

        String oldStatus = roll.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        roll.setIsBlankRoll(true);
        roll.setStatus("BLANK_ROLL_DETECTED");
        roll.setCompletedAt(now);
        FilmRoll saved = filmRollRepository.save(roll);

        filmLabFacade.releaseCapacity(labId, saved.getProcessType(), 1);

        String note = "Film trắng: " + (request.note() != null ? request.note() : "");
        FilmRollStatusLog logMsg = new FilmRollStatusLog();
        logMsg.setFilmRollId(saved.getId());
        logMsg.setLabId(labId);
        logMsg.setFromStatus(oldStatus);
        logMsg.setToStatus("BLANK_ROLL_DETECTED");
        logMsg.setChangedByUserId(operatorId);
        logMsg.setNote(note);
        statusLogRepository.save(logMsg);

        eventPublisher.publishEvent(new BlankRollDetectedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getOrderItemId(),
                saved.getLabId(),
                saved.getCustomerId(),
                saved.getRollCode(),
                note,
                now
        ));

        eventPublisher.publishEvent(new FilmRollStatusChangedEvent(
                saved.getId(),
                saved.getOrderId(),
                saved.getLabId(),
                saved.getCustomerId(),
                saved.getRollCode(),
                oldStatus,
                "BLANK_ROLL_DETECTED",
                saved.getSlaDeadlineAt(),
                now
        ));

        FilmRollResponse response = new FilmRollResponse(
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

        stompPublisher.publishToTopic("/topic/orders/" + saved.getOrderId() + "/rolls", response);
        stompPublisher.publishToTopic("/topic/labs/" + labId + "/kanban", response);

        return response;
    }
}