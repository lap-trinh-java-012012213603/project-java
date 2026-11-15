package com.analoghub.platform.digitalarchive.repository;

import com.analoghub.platform.digitalarchive.domain.ArchiveFrame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchiveFrameRepository extends JpaRepository<ArchiveFrame, UUID> {
    List<ArchiveFrame> findByArchiveRollIdOrderByFrameNumberAsc(UUID archiveRollId);
    Optional<ArchiveFrame> findByArchiveRollIdAndFrameNumber(UUID archiveRollId, Short frameNumber);
    List<ArchiveFrame> findByOwnerUserIdAndIsFavoriteTrueOrderByCreatedAtDesc(UUID ownerUserId);
}