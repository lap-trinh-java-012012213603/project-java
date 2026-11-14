package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, UUID> {

    Optional<PaymentWebhookLog> findByEventIdempotencyKey(String eventIdempotencyKey);

    boolean existsByEventIdempotencyKey(String eventIdempotencyKey);
}
