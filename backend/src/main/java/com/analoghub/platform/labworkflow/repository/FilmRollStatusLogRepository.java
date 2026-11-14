package com.analoghub.platform.labworkflow.repository;

import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FilmRollStatusLogRepository extends JpaRepository<FilmRollStatusLog, UUID> {
    List<FilmRollStatusLog> findByFilmRollIdOrderByCreatedAtAsc(UUID filmRollId);
}