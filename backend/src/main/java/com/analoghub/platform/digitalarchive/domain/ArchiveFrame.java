package com.analoghub.platform.digitalarchive.domain;

import com.analoghub.platform.common.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "arc_frames")
@Getter
@Setter
@NoArgsConstructor
public class ArchiveFrame extends BaseAuditEntity {

    @Column(name = "archive_roll_id", nullable = false)
    private UUID archiveRollId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "frame_number", nullable = false)
    private Short frameNumber;

    @Column(name = "s3_key_original", nullable = false, length = 512)
    private String s3KeyOriginal;

    @Column(name = "s3_key_web", nullable = false, length = 512)
    private String s3KeyWeb;

    @Column(name = "s3_key_thumb", nullable = false, length = 512)
    private String s3KeyThumb;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "qc_status", nullable = false, length = 32)
    private String qcStatus = "PENDING_AI";

    @Column(name = "qc_overall_score", precision = 5, scale = 2)
    private BigDecimal qcOverallScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exif_metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> exifMetadata = new HashMap<>();

    @Column(name = "is_favorite", nullable = false)
    private Boolean isFavorite = false;
}