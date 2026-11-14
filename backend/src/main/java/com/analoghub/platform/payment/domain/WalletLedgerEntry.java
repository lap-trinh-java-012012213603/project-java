package com.analoghub.platform.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Append-Only ledger entry entity mapped to {@code pay_wallet_ledger_entries}.
 * Any attempt to update or delete an existing ledger row is strictly forbidden (Principle 5).
 */
@Entity
@Table(name = "pay_wallet_ledger_entries")
@Immutable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "journal_group_id", nullable = false, updatable = false)
    private UUID journalGroupId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "payment_txn_id", updatable = false)
    private UUID paymentTxnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_direction", length = 8, nullable = false, updatable = false)
    private LedgerEntryDirection entryDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 32, nullable = false, updatable = false)
    private LedgerEntryType entryType;

    @Column(name = "amount_vnd", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal amountVnd;

    @Column(name = "balance_after_vnd", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal balanceAfterVnd;

    @Column(name = "reference_type", length = 32, nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    @PreRemove
    public void preventMutation() {
        throw new UnsupportedOperationException(
                "pay_wallet_ledger_entries is immutable append-only and cannot be updated or deleted"
        );
    }
}
