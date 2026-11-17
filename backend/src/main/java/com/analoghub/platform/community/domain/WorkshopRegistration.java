package com.analoghub.platform.community.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "com_workshop_registrations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkshopRegistration extends BaseAuditEntity {

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private WorkshopRegistrationStatus status;

    @Column(name = "checkin_code", length = 64, nullable = false, unique = true)
    private String checkinCode;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;
}
