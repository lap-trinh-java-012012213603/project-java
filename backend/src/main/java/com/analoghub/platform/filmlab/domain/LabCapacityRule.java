package com.analoghub.platform.filmlab.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_capacity_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lab_capacity_rules_lab_process", columnNames = {"lab_id", "process_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabCapacityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "process_type", nullable = false, length = 32)
    private String processType;

    @Column(name = "max_daily_rolls", nullable = false)
    private int maxDailyRolls;

    @Builder.Default
    @Column(name = "current_queued_rolls", nullable = false)
    private int currentQueuedRolls = 0;

    @Builder.Default
    @Column(name = "base_turnaround_hours", nullable = false)
    private int baseTurnaroundHours = 24;

    @Builder.Default
    @Column(name = "penalty_hours_per_batch", nullable = false)
    private int penaltyHoursPerBatch = 0;

    @Builder.Default
    @Column(name = "is_accepting_orders", nullable = false)
    private boolean isAcceptingOrders = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}