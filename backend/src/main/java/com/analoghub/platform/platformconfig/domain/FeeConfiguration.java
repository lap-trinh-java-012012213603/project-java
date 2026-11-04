package com.analoghub.platform.platformconfig.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import com.analoghub.platform.platformconfig.dto.FeeScope;
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

/**
 * Platform commission and fee rule stored in {@code cfg_fee_configurations}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cfg_fee_configurations")
public class FeeConfiguration extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_scope", length = 64, nullable = false, unique = true)
    private FeeScope feeScope;

    @Column(name = "commission_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal commissionRate;

    @Builder.Default
    @Column(name = "fixed_fee_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal fixedFeeVnd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "min_fee_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal minFeeVnd = BigDecimal.ZERO;

    @Column(name = "max_fee_vnd", precision = 15, scale = 2)
    private BigDecimal maxFeeVnd;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
