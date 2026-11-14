package com.analoghub.platform.labworkflow.scheduler;

import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Scheduled(fixedDelayString = "300000") // 5 phút
    @SchedulerLock(name = "RollSlaMonitorScheduler_check", lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
    @Transactional
    public void monitorRollSlaBreaches() {
        OffsetDateTime now = OffsetDateTime.now();
        List<FilmRoll> breachedRolls = filmRollRepository.findBreachedSlaRolls(now);

        for (FilmRoll roll : breachedRolls) {
            roll.setSlaBreachNotified(true);
            filmRollRepository.save(roll);

            Map<String, String> payload = Map.of(
                    "type", "SLA_BREACH_ALERT",
                    "rollId", roll.getId().toString(),
                    "rollCode", roll.getRollCode(),
                    "status", roll.getStatus()
            );
            simpMessagingTemplate.convertAndSend("/topic/labs/" + roll.getLabId() + "/kanban", payload);

            log.warn("Phát hiện cuộn film {} tại Lab {} vi phạm SLA", roll.getRollCode(), roll.getLabId());
        }
    }
}