package com.analoghub.platform.booking.repository;

import com.analoghub.platform.booking.domain.LabOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {
    List<LabOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<LabOrder> findByLabIdOrderByCreatedAtDesc(UUID labId);
    List<LabOrder> findByStatusAndPaymentExpiresAtBefore(String status, OffsetDateTime paymentExpiresAt);
}