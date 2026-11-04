package com.analoghub.platform.delivery.repository;

import com.analoghub.platform.delivery.domain.DeliveryTrackingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryTrackingLogRepository extends JpaRepository<DeliveryTrackingLog, UUID> {

    List<DeliveryTrackingLog> findTop100ByDeliveryTaskIdOrderByLoggedAtDesc(UUID deliveryTaskId);
}
