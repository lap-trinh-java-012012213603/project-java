package com.analoghub.platform.labworkflow.repository;

import com.analoghub.platform.labworkflow.domain.FilmRoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FilmRollRepository extends JpaRepository<FilmRoll, UUID> {

    List<FilmRoll> findByOrderId(UUID orderId);

    List<FilmRoll> findByLabIdOrderByCheckedInAtAsc(UUID labId);

    Optional<FilmRoll> findByQrCheckinTokenHash(String qrCheckinTokenHash);

    Optional<FilmRoll> findByRollCode(String rollCode);

    @Query("""
        SELECT r FROM FilmRoll r
        WHERE r.slaBreachNotified = false
          AND r.slaDeadlineAt IS NOT NULL
          AND r.slaDeadlineAt < :now
          AND r.status NOT IN ('COMPLETED', 'NEGATIVE_RETURNED', 'CANCELLED', 'BLANK_ROLL_DETECTED')
    """)
    List<FilmRoll> findBreachedSlaRolls(@Param("now") OffsetDateTime now);
}