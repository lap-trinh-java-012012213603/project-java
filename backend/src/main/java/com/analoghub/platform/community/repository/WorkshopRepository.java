package com.analoghub.platform.community.repository;

import com.analoghub.platform.community.domain.Workshop;
import com.analoghub.platform.community.domain.WorkshopStatus;
import com.analoghub.platform.community.domain.WorkshopType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, UUID> {

    @Query("""
            SELECT w FROM Workshop w
            WHERE (:workshopType IS NULL OR w.workshopType = :workshopType)
              AND (:status IS NULL OR w.status = :status)
              AND (:freeOnly = false OR w.ticketPriceVnd = :zeroPrice)
            ORDER BY w.startTime ASC
            """)
    Page<Workshop> searchWorkshops(
            @Param("workshopType") WorkshopType workshopType,
            @Param("status") WorkshopStatus status,
            @Param("freeOnly") boolean freeOnly,
            @Param("zeroPrice") BigDecimal zeroPrice,
            Pageable pageable
    );
}
