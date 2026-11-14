package com.analoghub.platform.labworkflow.domain;

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
@Table(name = "wfl_scan_upload_batches")
@Getter
@Setter
@NoArgsConstructor
public class ScanUploadBatch extends BaseAuditEntity {

    @Column(name = "film_roll_id", nullable = false)
    private UUID filmRollId;

    @Column(name = "lab_id", nullable = false)
    private UUID labId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PRESIGNED_ISSUED";

    @Column(name = "expected_frame_count", nullable = false)
    private Integer expectedFrameCount;

    @Column(name = "uploaded_frame_count", nullable = false)
    private Integer uploadedFrameCount = 0;

    @Column(name = "s3_prefix", nullable = false, length = 512)
    private String s3Prefix;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}