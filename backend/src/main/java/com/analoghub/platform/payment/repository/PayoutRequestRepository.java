package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.PayoutRequest;
import com.analoghub.platform.payment.domain.PayoutRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    long countByRequesterUserIdAndStatusIn(UUID requesterUserId, Collection<PayoutRequestStatus> statuses);

    Page<PayoutRequest> findByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId, Pageable pageable);

    Page<PayoutRequest> findByRequesterUserIdAndStatusOrderByCreatedAtDesc(
            UUID requesterUserId,
            PayoutRequestStatus status,
            Pageable pageable
    );

    Page<PayoutRequest> findByStatusOrderByCreatedAtAsc(PayoutRequestStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayoutRequest p WHERE p.id = :id")
    Optional<PayoutRequest> findByIdForUpdate(@Param("id") UUID id);
}
