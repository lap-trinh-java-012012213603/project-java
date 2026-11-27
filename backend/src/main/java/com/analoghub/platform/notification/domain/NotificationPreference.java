package com.analoghub.platform.notification.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ntf_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference extends BaseAuditEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "muted_categories", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> mutedCategories = new ArrayList<>();
}
