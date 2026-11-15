package com.analoghub.platform.labworkflow.scheduler;

import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollSlaMonitorScheduler {

    private final FilmRollRepository filmRollRepository;
    private final StompNotificationPublisher stompPublisher;

    @Autowired(required = false)
    private NotificationFacade notificationFacade;

    @Scheduled(fixedDelayString = "300000") // 5 phút
    @SchedulerLock(name = "RollSlaMonitorScheduler_checkSlaBreaches", lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
    @Transactional
    public void monitorRollSlaBreaches() {
        OffsetDateTime now = OffsetDateTime.now();
        List<FilmRoll> breachedRolls = filmRollRepository.findBreachedSlaRolls(now);

        for (FilmRoll roll : breachedRolls) {
            roll.setSlaBreachNotified(true);
            filmRollRepository.save(roll);

            stompPublisher.publish("/topic/labs/" + roll.getLabId() + "/kanban", Map.of(
                    "type", "SLA_BREACH_ALERT",
                    "rollId", roll.getId().toString(),
                    "rollCode", roll.getRollCode(),
                    "status", roll.getStatus(),
                    "slaDeadlineAt", roll.getSlaDeadlineAt() != null ? roll.getSlaDeadlineAt().toString() : ""
            ));

            if (notificationFacade != null && roll.getCustomerId() != null) {
                try {
                    notificationFacade.sendNotification(new SendNotificationRequest(
                            roll.getCustomerId(),
                            "ROLL_SLA_DELAYED",
                            "Thông báo trễ tiến độ xử lý cuộn film",
                            "Cuộn film " + roll.getRollCode() + " đang cần thêm thời gian xử lý tại Lab.",
                            Map.of("orderId", roll.getOrderId().toString(), "rollId", roll.getId().toString()),
                            roll.getId()
                    ));
                } catch (Exception e) {
                    log.warn("Không thể gửi thông báo SLA cho cuộn {}: {}", roll.getRollCode(), e.getMessage());
                }
            }

            log.warn("Phát hiện cuộn film {} tại Lab {} vi phạm SLA (Hạn: {})",
                    roll.getRollCode(), roll.getLabId(), roll.getSlaDeadlineAt());
        }
    }
}
