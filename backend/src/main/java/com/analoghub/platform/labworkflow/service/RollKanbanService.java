package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.booking.facade.BookingOrderFacade;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.UpdateRollStatusRequest;
import com.analoghub.platform.labworkflow.event.FilmRollStatusChangedEvent;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RollKanbanService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "CHECKED_IN", Set.of("DEVELOPING", "CANCELLED"),
            "DEVELOPING", Set.of("WASHING_DRYING", "BLANK_ROLL_DETECTED"),
            "WASHING_DRYING", Set.of("SCANNING", "BLANK_ROLL_DETECTED", "COMPLETED"),
            "SCANNING", Set.of("POST_PROCESSING_QC", "BLANK_ROLL_DETECTED"),
            "POST_PROCESSING_QC", Set.of("COMPLETED"),
            "COMPLETED", Set.of("NEGATIVE_RETURNED"),
            "BLANK_ROLL_DETECTED", Set.of("NEGATIVE_RETURNED", "COMPLETED")
    );

    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;
    private final FilmLabFacade filmLabFacade;
    private final BookingOrderFacade bookingOrderFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Transactional
    public FilmRollResponse transitionStatus(UUID labId, UUID rollId, UUID operatorId, UpdateRollStatusRequest request) {
        FilmRoll roll = filmRollRepository.findById(rollId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộn film"));
        if (!roll.getLabId().equals(labId)) throw new IllegalArgumentException("Không thuộc Lab");

        String currentStatus = roll.getStatus();
        String targetStatus = request.targetStatus().trim().toUpperCase();

        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }

        OffsetDateTime now = OffsetDateTime.now();
        roll.setStatus(targetStatus);
        if ("COMPLETED".equals(targetStatus)) {
            roll.setCompletedAt(now);
            filmLabFacade.releaseCapacity(labId, roll.getProcessType(), 1);
        }

        FilmRoll saved = filmRollRepository.save(roll);
        FilmRollStatusLog log = new FilmRollStatusLog();
        log.setFilmRollId(saved.getId());
        log.setLabId(labId);
        log.setFromStatus(currentStatus);
        log.setToStatus(targetStatus);
        log.setChangedByUserId(operatorId);
        log.setNote(request.note());
        statusLogRepository.save(log);

        checkAndSyncOrderCompletion(saved.getOrderId());

        FilmRollResponse response = toDto(saved);

        // Gửi qua WebSocket bằng SimpMessagingTemplate chuẩn
        simpMessagingTemplate.convertAndSend("/topic/orders/" + saved.getOrderId() + "/rolls", response);
        simpMessagingTemplate.convertAndSend("/topic/labs/" + labId + "/kanban", response);

        eventPublisher.publishEvent(new FilmRollStatusChangedEvent(saved.getId(), saved.getOrderId(), saved.getLabId(), saved.getCustomerId(), saved.getRollCode(), currentStatus, targetStatus, saved.getSlaDeadlineAt(), now));
        return response;
    }

    @Transactional(readOnly = true)
    public List<FilmRollResponse> getLabKanbanRolls(UUID labId) {
        return filmRollRepository.findByLabIdOrderByCheckedInAtAsc(labId).stream().map(this::toDto).toList();
    }

    private void checkAndSyncOrderCompletion(UUID orderId) {
        List<FilmRoll> rolls = filmRollRepository.findByOrderId(orderId);
        if (!rolls.isEmpty() && rolls.stream().allMatch(r -> Set.of("COMPLETED", "BLANK_ROLL_DETECTED", "NEGATIVE_RETURNED").contains(r.getStatus()))) {
            bookingOrderFacade.updateOrderStatus(orderId, "SCAN_DELIVERED");
        }
    }

    private FilmRollResponse toDto(FilmRoll r) {
        return new FilmRollResponse(r.getId(), r.getOrderId(), r.getOrderItemId(), r.getLabId(), r.getCustomerId(), r.getRollCode(), r.getFilmFormat(), r.getProcessType(), r.getScanResolution(), r.getPushPullStops(), r.getStatus(), Boolean.TRUE.equals(r.getIsBlankRoll()), r.getCheckedInAt(), r.getSlaDeadlineAt(), Boolean.TRUE.equals(r.getSlaBreachNotified()), r.getCompletedAt());
    }
}