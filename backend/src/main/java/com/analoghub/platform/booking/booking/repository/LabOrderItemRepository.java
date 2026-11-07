package com.analoghub.platform.booking.repository;

import com.analoghub.platform.booking.domain.LabOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface LabOrderItemRepository extends JpaRepository<LabOrderItem, UUID> {
}