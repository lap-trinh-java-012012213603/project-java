package com.analoghub.platform.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlv_tracking_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTrackingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "delivery_task_id", nullable = false, updatable = false)
    private UUID deliveryTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false, updatable = false)
    private DeliveryTaskStatus status;

    @Column(name = "latitude", updatable = false)
    private Double latitude;

    @Column(name = "longitude", updatable = false)
    private Double longitude;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private Instant loggedAt;

    @PrePersist
    void onCreate() {
        if (loggedAt == null) {
            loggedAt = Instant.now();
        }
    }
}
