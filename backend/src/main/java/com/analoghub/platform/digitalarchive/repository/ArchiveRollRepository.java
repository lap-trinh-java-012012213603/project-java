package com.analoghub.platform.digitalarchive.repository;

import com.analoghub.platform.digitalarchive.domain.ArchiveRoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchiveRollRepository extends JpaRepository<ArchiveRoll, UUID> {
    Optional<ArchiveRoll> findBySourceFilmRollId(UUID sourceFilmRollId);
    Optional<ArchiveRoll> findByIdAndDeletedAtIsNull(UUID id);
    List<ArchiveRoll> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerUserId);
    List<ArchiveRoll> findByAlbumIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID albumId);
}