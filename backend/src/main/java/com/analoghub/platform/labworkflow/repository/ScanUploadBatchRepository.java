package com.analoghub.platform.labworkflow.repository;

import com.analoghub.platform.labworkflow.domain.ScanUploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScanUploadBatchRepository extends JpaRepository<ScanUploadBatch, UUID> {
    List<ScanUploadBatch> findByFilmRollIdOrderByCreatedAtDesc(UUID filmRollId);
}