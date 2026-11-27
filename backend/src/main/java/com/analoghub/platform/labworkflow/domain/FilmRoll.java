package com.analoghub.platform.labworkflow.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wfl_film_rolls")
@Getter
@Setter
@NoArgsConstructor
public class FilmRoll extends BaseAuditEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "roll_code", nullable = false, unique = true, length = 64)
    private String rollCode;

    @Column(name = "qr_checkin_token_hash", nullable = false, unique = true, length = 128)
    private String qrCheckinTokenHash;

    @Column(name = "film_format", nullable = false, length = 32)
    private String filmFormat;

    @Column(name = "process_type", nullable = false, length = 32)
    private String processType;

    @Column(name = "scan_resolution", nullable = false, length = 32)
    private String scanResolution = "NONE";

    @Column(name = "push_pull_stops", nullable = false)
    private Integer pushPullStops = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "AWAITING_CHECKIN";

    @Column(name = "is_blank_roll", nullable = false)
    private Boolean isBlankRoll = false;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "sla_deadline_at")
    private OffsetDateTime slaDeadlineAt;

    @Column(name = "sla_breach_notified", nullable = false)
    private Boolean slaBreachNotified = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}