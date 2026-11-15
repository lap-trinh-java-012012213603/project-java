package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.booking.facade.BookingOrderFacade;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.ReportBlankRollRequest;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import com.analoghub.platform.payment.facade.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Autowired(required = false)
    private BookingOrderFacade bookingOrderFacade;

    @Autowired(required = false)
    private PaymentFacade paymentFacade;

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

        FilmRollStatusLog logMsg = new FilmRollStatusLog();
        logMsg.setFilmRollId(saved.getId());
        logMsg.setLabId(labId);
        logMsg.setFromStatus(oldStatus);
        logMsg.setToStatus("BLANK_ROLL_DETECTED");
        logMsg.setChangedByUserId(operatorId);
        logMsg.setNote("Film trắng: " + (request.note() != null ? request.note() : ""));
        statusLogRepository.save(logMsg);

        if (paymentFacade != null) {
            try {
                BigDecimal refundFee = BigDecimal.valueOf(30000);
                if (bookingOrderFacade != null && saved.getOrderItemId() != null) {
                    try {
                        refundFee = bookingOrderFacade.getUnitPriceForOrderItem(saved.getOrderItemId());
                    } catch (Exception ignored) {
                    }
                }
                paymentFacade.partialRefundBlankRoll(
                        saved.getOrderId(),
                        saved.getId(),
                        saved.getCustomerId(),
                        refundFee,
                        "Hoàn phí scan cuộn film trắng: " + saved.getRollCode()
                );
            } catch (Exception e) {
                log.warn("Lỗi hoàn phí cuộn trắng {}: {}", saved.getRollCode(), e.getMessage());
            }
        }

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
