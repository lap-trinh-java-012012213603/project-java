package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.WorkshopRegistration;
import com.analoghub.platform.community.domain.WorkshopRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkshopRegistrationRepository extends JpaRepository<WorkshopRegistration, UUID> {

    boolean existsByWorkshopIdAndUserIdAndStatusNot(
            UUID workshopId,
            UUID userId,
            WorkshopRegistrationStatus excludedStatus
    );

    Optional<WorkshopRegistration> findByWorkshopIdAndCheckinCode(UUID workshopId, String checkinCode);

    boolean existsByCheckinCode(String checkinCode);
}
