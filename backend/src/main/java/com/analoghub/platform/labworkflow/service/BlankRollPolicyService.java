package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.ReportBlankRollRequest;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import com.analoghub.platform.payment.facade.PaymentFacade;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
public class BlankRollPolicyService {
    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;
    private final FilmLabFacade filmLabFacade;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final @Nullable PaymentFacade paymentFacade;

    public BlankRollPolicyService(
            FilmRollRepository filmRollRepository,
            FilmRollStatusLogRepository statusLogRepository,
            FilmLabFacade filmLabFacade,
            SimpMessagingTemplate simpMessagingTemplate
    ) {
        this(filmRollRepository, statusLogRepository, filmLabFacade, simpMessagingTemplate, null);
    }

    @Autowired
    public BlankRollPolicyService(
            FilmRollRepository filmRollRepository,
            FilmRollStatusLogRepository statusLogRepository,
            FilmLabFacade filmLabFacade,
            SimpMessagingTemplate simpMessagingTemplate,
            @Nullable PaymentFacade paymentFacade
    ) {
        this.filmRollRepository = filmRollRepository;
        this.statusLogRepository = statusLogRepository;
        this.filmLabFacade = filmLabFacade;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.paymentFacade = paymentFacade;
    }

    @Transactional
    public FilmRollResponse reportBlankRoll(UUID labId, UUID rollId, UUID operatorId, ReportBlankRollRequest request) {
        FilmRoll roll = filmRollRepository.findById(rollId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy"));
        if (!roll.getLabId().equals(labId)) throw new IllegalArgumentException("Không thuộc Lab");
        if (Boolean.TRUE.equals(roll.getIsBlankRoll())) throw new IllegalArgumentException("Đã báo trắng");

        String oldStatus = roll.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        roll.setIsBlankRoll(true);
        roll.setStatus("BLANK_ROLL_DETECTED");
        roll.setCompletedAt(now);
        FilmRoll saved = filmRollRepository.save(roll);

        filmLabFacade.releaseCapacity(labId, saved.getProcessType(), 1);

        FilmRollStatusLog logMsg = new FilmRollStatusLog();
        logMsg.setFilmRollId(saved.getId());
        logMsg.setLabId(labId);
        logMsg.setFromStatus(oldStatus);
        logMsg.setToStatus("BLANK_ROLL_DETECTED");
        logMsg.setChangedByUserId(operatorId);
        logMsg.setNote("Film trắng: " + request.note());
        statusLogRepository.save(logMsg);

        if (paymentFacade != null) {
            try {
                paymentFacade.refundEscrow(saved.getOrderId(), "Hoàn phí cuộn trắng: " + saved.getRollCode(), "BLANK_ROLL");
            } catch (Exception e) {
                log.warn("Lỗi hoàn phí cuộn trắng {}: {}", saved.getRollCode(), e.getMessage());
            }
        }

        FilmRollResponse response = new FilmRollResponse(saved.getId(), saved.getOrderId(), saved.getOrderItemId(), saved.getLabId(), saved.getCustomerId(), saved.getRollCode(), saved.getFilmFormat(), saved.getProcessType(), saved.getScanResolution(), saved.getPushPullStops(), saved.getStatus(), Boolean.TRUE.equals(saved.getIsBlankRoll()), saved.getCheckedInAt(), saved.getSlaDeadlineAt(), Boolean.TRUE.equals(saved.getSlaBreachNotified()), saved.getCompletedAt());

        simpMessagingTemplate.convertAndSend("/topic/orders/" + saved.getOrderId() + "/rolls", response);
        simpMessagingTemplate.convertAndSend("/topic/labs/" + labId + "/kanban", response);

        return response;
    }
}