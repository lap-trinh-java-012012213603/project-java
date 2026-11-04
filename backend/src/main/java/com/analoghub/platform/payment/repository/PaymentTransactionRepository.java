package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransaction;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxnCode(String txnCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.txnCode = :txnCode")
    Optional<PaymentTransaction> findByTxnCodeForUpdate(@Param("txnCode") String txnCode);

    List<PaymentTransaction> findByPaymentPurposeAndReferenceId(PaymentPurpose paymentPurpose, UUID referenceId);

    List<PaymentTransaction> findByPaymentPurposeAndReferenceIdAndStatus(
            PaymentPurpose paymentPurpose,
            UUID referenceId,
            PaymentTransactionStatus status
    );

    boolean existsByPaymentPurposeAndReferenceIdAndStatus(
            PaymentPurpose paymentPurpose,
            UUID referenceId,
            PaymentTransactionStatus status
    );

    @Query("""
            SELECT t FROM PaymentTransaction t
            WHERE t.status = com.analoghub.platform.payment.domain.PaymentTransactionStatus.INITIATED
              AND t.expiresAt <= :now
            """)
    List<PaymentTransaction> findExpiredInitiatedTransactions(@Param("now") Instant now);
}
