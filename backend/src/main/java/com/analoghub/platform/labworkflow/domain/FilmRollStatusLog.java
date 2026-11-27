package com.analoghub.platform.labworkflow.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wfl_film_roll_status_logs")
@Getter
@Setter
@NoArgsConstructor
public class FilmRollStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "film_roll_id", nullable = false)
    private UUID filmRollId;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}