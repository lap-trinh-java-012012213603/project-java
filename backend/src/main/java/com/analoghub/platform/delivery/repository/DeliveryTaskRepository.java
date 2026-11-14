package com.analoghub.platform.delivery.repository;

import com.analoghub.platform.delivery.domain.DeliveryTask;
import com.analoghub.platform.delivery.domain.DeliveryTaskStatus;
import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DeliveryTask t WHERE t.id = :id")
    Optional<DeliveryTask> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DeliveryTask t WHERE t.externalTrackingId = :trackingId AND t.provider = :provider")
    Optional<DeliveryTask> findByExternalTrackingIdAndProviderForUpdate(
            @Param("trackingId") String trackingId,
            @Param("provider") LogisticsProviderType provider
    );

    long countByAssignedDriverUserIdAndStatusIn(UUID assignedDriverUserId, Collection<DeliveryTaskStatus> statuses);

    List<DeliveryTask> findByProviderAndStatusAndAssignedDriverUserIdIsNullOrderByPickupDeadlineAtAsc(
            LogisticsProviderType provider,
            DeliveryTaskStatus status
    );

    List<DeliveryTask> findByStatusInAndPickupDeadlineAtBefore(
            Collection<DeliveryTaskStatus> statuses,
            Instant deadline
    );

    List<DeliveryTask> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
