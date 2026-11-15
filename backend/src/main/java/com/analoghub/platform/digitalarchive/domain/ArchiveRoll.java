package com.analoghub.platform.digitalarchive.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "arc_rolls")
@Getter
@Setter
@NoArgsConstructor
public class ArchiveRoll extends BaseAuditEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "album_id")
    private UUID albumId;

    @Column(name = "source_film_roll_id", nullable = false, unique = true)
    private UUID sourceFilmRollId;

    @Column(name = "source_order_id", nullable = false)
    private UUID sourceOrderId;

    @Column(name = "source_lab_id", nullable = false)
    private UUID sourceLabId;

    @Column(name = "film_stock_id")
    private UUID filmStockId;

    @Column(name = "roll_title", nullable = false, length = 150)
    private String rollTitle;

    @Column(name = "camera_model", length = 120)
    private String cameraModel;

    @Column(name = "lens_model", length = 120)
    private String lensModel;

    @Column(name = "shot_iso")
    private Integer shotIso;

    @Column(name = "shot_date")
    private LocalDate shotDate;

    @Column(name = "process_type", length = 32)
    private String processType;

    @Column(name = "scan_scanner_model", length = 80)
    private String scanScannerModel;

    @Column(name = "contact_sheet_s3_key", length = 512)
    private String contactSheetS3Key;

    @Column(name = "frame_count", nullable = false)
    private Integer frameCount = 0;

    @Column(name = "total_size_bytes", nullable = false)
    private Long totalSizeBytes = 0L;

    @Column(name = "raw_expires_at")
    private OffsetDateTime rawExpiresAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}