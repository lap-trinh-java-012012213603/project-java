package com.analoghub.platform.marketplace.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mkt_listings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListing extends BaseAuditEntity {

    @Column(name = "seller_user_id", nullable = false)
    private UUID sellerUserId;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32, nullable = false)
    private ListingCategory category;

    @Column(name = "brand", length = 64)
    private String brand;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", length = 32, nullable = false)
    private ConditionGrade conditionGrade;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_specs", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> technicalSpecs;

    @Column(name = "price_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal priceVnd;

    @Column(name = "allow_price_offer", nullable = false)
    private boolean allowPriceOffer;

    @Column(name = "allow_swap", nullable = false)
    private boolean allowSwap;

    @Column(name = "preferred_swap_items", columnDefinition = "text")
    private String preferredSwapItems;

    @Column(name = "province_code", length = 32)
    private String provinceCode;

    @Column(name = "district_code", length = 32)
    private String districtCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ListingStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onPrePersist() {
        if (technicalSpecs == null) {
            technicalSpecs = Map.of();
        }
    }
}
