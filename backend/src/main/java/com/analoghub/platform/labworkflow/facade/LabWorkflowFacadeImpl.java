package com.analoghub.platform.labworkflow.facade;

import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabWorkflowFacadeImpl implements LabWorkflowFacade {

    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;

    @Override
    @Transactional
    public List<FilmRollResponse> initializeRollsForOrder(
            UUID orderId,
            UUID labId,
            UUID customerId,
            List<OrderItemRollSpec> itemSpecs
    ) {
        List<FilmRoll> rollsToSave = new ArrayList<>();
        int seq = 1;

        for (OrderItemRollSpec spec : itemSpecs) {
            for (int i = 0; i < spec.rollQuantity(); i++) {
                String rollCode = "ROLL-" + orderId.toString().substring(0, 8).toUpperCase() + "-" + String.format("%02d", seq++);
                String rawToken = rollCode + ":" + UUID.randomUUID();
                String tokenHash = sha256Hex(rawToken);

                FilmRoll roll = new FilmRoll();
                roll.setOrderId(orderId);
                roll.setOrderItemId(spec.orderItemId());
                roll.setLabId(labId);
                roll.setCustomerId(customerId);
                roll.setRollCode(rollCode);
                roll.setQrCheckinTokenHash(tokenHash);
                roll.setFilmFormat(spec.filmFormat());
                roll.setProcessType(spec.processType());
                roll.setScanResolution(spec.scanResolution() != null ? spec.scanResolution() : "NONE");
                roll.setPushPullStops(spec.pushPullStops());
                roll.setStatus("AWAITING_CHECKIN");
                roll.setIsBlankRoll(false);
                roll.setSlaBreachNotified(false);

                rollsToSave.add(roll);
            }
        }

        List<FilmRoll> savedRolls = filmRollRepository.saveAll(rollsToSave);

        for (FilmRoll saved : savedRolls) {
            FilmRollStatusLog log = new FilmRollStatusLog();
            log.setFilmRollId(saved.getId());
            log.setLabId(labId);
            log.setFromStatus(null);
            log.setToStatus("AWAITING_CHECKIN");
            log.setNote("Khởi tạo cuộn film sau khi thanh toán đơn hàng");
            statusLogRepository.save(log);
        }

        return savedRolls.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilmRollResponse> getRollsByOrderId(UUID orderId) {
        return filmRollRepository.findByOrderId(orderId).stream().map(this::toDto).toList();
    }

    private FilmRollResponse toDto(FilmRoll r) {
        return new FilmRollResponse(
                r.getId(),
                r.getOrderId(),
                r.getOrderItemId(),
                r.getLabId(),
                r.getCustomerId(),
                r.getRollCode(),
                r.getFilmFormat(),
                r.getProcessType(),
                r.getScanResolution(),
                r.getPushPullStops(),
                r.getStatus(),
                Boolean.TRUE.equals(r.getIsBlankRoll()),
                r.getCheckedInAt(),
                r.getSlaDeadlineAt(),
                Boolean.TRUE.equals(r.getSlaBreachNotified()),
                r.getCompletedAt()
        );
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}