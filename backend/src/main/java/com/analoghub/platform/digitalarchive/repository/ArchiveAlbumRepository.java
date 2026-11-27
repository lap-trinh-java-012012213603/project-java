package com.analoghub.platform.digitalarchive.repository;

import com.analoghub.platform.digitalarchive.domain.ArchiveAlbum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArchiveAlbumRepository extends JpaRepository<ArchiveAlbum, UUID> {
    List<ArchiveAlbum> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<ArchiveAlbum> findByIdAndDeletedAtIsNull(UUID id);
}