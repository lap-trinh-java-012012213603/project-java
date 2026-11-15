package com.analoghub.platform.digitalarchive.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "arc_albums")
@Getter
@Setter
@NoArgsConstructor
public class ArchiveAlbum extends BaseAuditEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}