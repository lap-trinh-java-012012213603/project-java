package com.analoghub.platform.payment.repository;

import com.analoghub.platform.payment.domain.RefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    List<RefundRequest> findByEscrowIdOrderByCreatedAtDesc(UUID escrowId);
}
