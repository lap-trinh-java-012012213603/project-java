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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "com_workshops")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workshop extends BaseAuditEntity {

    @Column(name = "organizer_user_id", nullable = false)
    private UUID organizerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workshop_type", length = 32, nullable = false)
    private WorkshopType workshopType;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "location_address", length = 512)
    private String locationAddress;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants;

    @Column(name = "ticket_price_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal ticketPriceVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private WorkshopStatus status;
}
