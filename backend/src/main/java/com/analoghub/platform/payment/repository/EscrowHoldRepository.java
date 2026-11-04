package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowHoldRepository extends JpaRepository<EscrowHold, UUID> {

    Optional<EscrowHold> findByReferenceId(UUID referenceId);

    Optional<EscrowHold> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    Optional<EscrowHold> findByPaymentTxnId(UUID paymentTxnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowHold e WHERE e.referenceType = :referenceType AND e.referenceId = :referenceId")
    Optional<EscrowHold> findByReferenceTypeAndReferenceIdForUpdate(
            @Param("referenceType") String referenceType,
            @Param("referenceId") UUID referenceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowHold e WHERE e.referenceId = :referenceId")
    Optional<EscrowHold> findByReferenceIdForUpdate(@Param("referenceId") UUID referenceId);

    @Query("""
            SELECT COALESCE(SUM(e.grossAmountVnd - e.refundedAmountVnd), 0)
            FROM EscrowHold e
            WHERE e.payeeUserId = :payeeUserId
              AND e.status IN :statuses
            """)
    BigDecimal sumPendingReceivableByPayeeUserId(
            @Param("payeeUserId") UUID payeeUserId,
            @Param("statuses") Collection<EscrowStatus> statuses
    );

    @Query("""
            SELECT e FROM EscrowHold e
            WHERE e.status IN :statuses
              AND e.autoReleaseAt IS NOT NULL
              AND e.autoReleaseAt <= :now
            """)
    List<EscrowHold> findDueForAutoRelease(
            @Param("statuses") Collection<EscrowStatus> statuses,
            @Param("now") Instant now
    );
}
