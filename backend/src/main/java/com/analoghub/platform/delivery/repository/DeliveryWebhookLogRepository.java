package com.analoghub.platform.delivery.repository;

import com.analoghub.platform.delivery.domain.DeliveryWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryWebhookLogRepository extends JpaRepository<DeliveryWebhookLog, UUID> {

    Optional<DeliveryWebhookLog> findByWebhookEventId(String webhookEventId);
}
