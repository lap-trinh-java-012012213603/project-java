package com.analoghub.platform.payment.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
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
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "pay_wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends BaseAuditEntity {

    @Column(name = "owner_user_id", unique = true)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", length = 32, nullable = false)
    private WalletType walletType;

    @Builder.Default
    @Column(name = "available_balance_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal availableBalanceVnd = new BigDecimal("0.00");

    @Builder.Default
    @Column(name = "locked_balance_vnd", precision = 15, scale = 2, nullable = false)
    private BigDecimal lockedBalanceVnd = new BigDecimal("0.00");

    @Builder.Default
    @Column(name = "currency", length = 8, nullable = false)
    private String currency = "VND";

    public BigDecimal getTotalBalanceVnd() {
        BigDecimal avail = availableBalanceVnd != null ? availableBalanceVnd : BigDecimal.ZERO;
        BigDecimal locked = lockedBalanceVnd != null ? lockedBalanceVnd : BigDecimal.ZERO;
        return avail.add(locked).setScale(2, RoundingMode.HALF_UP);
    }

    public void creditAvailable(BigDecimal amountVnd) {
        BigDecimal normalized = normalizePositiveAmount(amountVnd);
        BigDecimal current = availableBalanceVnd != null ? availableBalanceVnd : BigDecimal.ZERO;
        this.availableBalanceVnd = current.add(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    public void debitAvailable(BigDecimal amountVnd) {
        BigDecimal normalized = normalizePositiveAmount(amountVnd);
        BigDecimal current = availableBalanceVnd != null ? availableBalanceVnd : BigDecimal.ZERO;
        if (current.compareTo(normalized) < 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    String.format("Insufficient available wallet balance (required: %s VND, available: %s VND)",
                            normalized.toPlainString(), current.toPlainString())
            );
        }
        this.availableBalanceVnd = current.subtract(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    public void lockBalance(BigDecimal amountVnd) {
        BigDecimal normalized = normalizePositiveAmount(amountVnd);
        debitAvailable(normalized);
        BigDecimal currentLocked = lockedBalanceVnd != null ? lockedBalanceVnd : BigDecimal.ZERO;
        this.lockedBalanceVnd = currentLocked.add(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    public void unlockBalance(BigDecimal amountVnd) {
        BigDecimal normalized = normalizePositiveAmount(amountVnd);
        BigDecimal currentLocked = lockedBalanceVnd != null ? lockedBalanceVnd : BigDecimal.ZERO;
        if (currentLocked.compareTo(normalized) < 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    String.format("Insufficient locked wallet balance to unlock (required: %s VND, locked: %s VND)",
                            normalized.toPlainString(), currentLocked.toPlainString())
            );
        }
        this.lockedBalanceVnd = currentLocked.subtract(normalized).setScale(2, RoundingMode.HALF_UP);
        creditAvailable(normalized);
    }

    public void settleLockedBalance(BigDecimal amountVnd) {
        BigDecimal normalized = normalizePositiveAmount(amountVnd);
        BigDecimal currentLocked = lockedBalanceVnd != null ? lockedBalanceVnd : BigDecimal.ZERO;
        if (currentLocked.compareTo(normalized) < 0) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_FAILED,
                    String.format("Insufficient locked wallet balance to settle (required: %s VND, locked: %s VND)",
                            normalized.toPlainString(), currentLocked.toPlainString())
            );
        }
        this.lockedBalanceVnd = currentLocked.subtract(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Wallet mutation amount must be strictly positive (> 0)");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
