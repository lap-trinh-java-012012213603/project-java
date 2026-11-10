package com.analoghub.platform.filmlab.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_capacity_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabCapacityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lab_id", nullable = false, unique = true)
    private UUID labId;

    @Builder.Default
    @Column(name = "max_daily_rolls", nullable = false)
    private int maxDailyRolls = 20;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}