package com.analoghub.platform.labworkflow.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageCategory;
import com.analoghub.platform.common.storage.StorageProperties;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.labworkflow.domain.FilmRoll;
import com.analoghub.platform.labworkflow.domain.FilmRollStatusLog;
import com.analoghub.platform.labworkflow.domain.ScanUploadBatch;
import com.analoghub.platform.labworkflow.dto.ConfirmScanBatchRequest;
import com.analoghub.platform.labworkflow.dto.InitiateScanBatchRequest;
import com.analoghub.platform.labworkflow.dto.InitiateScanBatchResponse;
import com.analoghub.platform.labworkflow.event.RollScanUploadedEvent;
import com.analoghub.platform.labworkflow.repository.FilmRollRepository;
import com.analoghub.platform.labworkflow.repository.FilmRollStatusLogRepository;
import com.analoghub.platform.labworkflow.repository.ScanUploadBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanBatchUploadService {

    private final FilmRollRepository filmRollRepository;
    private final FilmRollStatusLogRepository statusLogRepository;
    private final ScanUploadBatchRepository batchRepository;
    private final StorageService storageService;
    private final S3Client s3Client;
    private final StorageProperties storageProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InitiateScanBatchResponse initiateBatchUpload(UUID labId, UUID rollId, InitiateScanBatchRequest request) {
        if (request.frames() == null || request.frames().isEmpty() || request.frames().size() > 45) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng frame tải lên phải từ 1 đến tối đa 45 frames");
        }

        FilmRoll roll = filmRollRepository.findById(rollId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cuộn film: " + rollId));

        if (!roll.getLabId().equals(labId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cuộn film không thuộc quyền quản lý của Lab này");
        }

        String s3Prefix = String.format("private/tenants/%s/orders/%s/rolls/%s/frames",
                labId, roll.getOrderId(), roll.getId());

        ScanUploadBatch batch = new ScanUploadBatch();
        batch.setFilmRollId(roll.getId());
        batch.setLabId(labId);
        batch.setStatus("PRESIGNED_ISSUED");
        batch.setExpectedFrameCount(request.frames().size());
        batch.setUploadedFrameCount(0);
        batch.setS3Prefix(s3Prefix);
        ScanUploadBatch savedBatch = batchRepository.save(batch);

        List<InitiateScanBatchResponse.FramePresignedUrlDto> urlDtos = new ArrayList<>();
        for (InitiateScanBatchRequest.FrameFileSpec spec : request.frames()) {
            StorageService.PresignedUploadResult presigned = storageService.generateRollFrameUploadUrl(
                    StorageCategory.ROLL_SCAN_ORIGINAL,
                    labId,
                    roll.getOrderId(),
                    roll.getId(),
                    spec.frameNumber(),
                    spec.fileName(),
                    spec.contentType(),
                    spec.fileSizeBytes()
            );
            urlDtos.add(new InitiateScanBatchResponse.FramePresignedUrlDto(
                    spec.frameNumber(),
                    presigned.objectKey(),
                    presigned.uploadPutUrl(),
                    presigned.expiresInSeconds()
            ));
        }

        return new InitiateScanBatchResponse(
                savedBatch.getId(),
                roll.getId(),
                savedBatch.getStatus(),
                savedBatch.getExpectedFrameCount(),
                savedBatch.getS3Prefix(),
                urlDtos
        );
    }

    @Transactional
    public void confirmBatchUpload(UUID labId, UUID rollId, UUID batchId, ConfirmScanBatchRequest request) {
        ScanUploadBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy batch upload: " + batchId));

        if (!batch.getFilmRollId().equals(rollId) || !batch.getLabId().equals(labId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Thông tin batch upload không khớp với cuộn film của Lab");
        }

        FilmRoll roll = filmRollRepository.findById(rollId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cuộn film: " + rollId));

        List<RollScanUploadedEvent.UploadedFrameInfo> processedFrames = new ArrayList<>();

        List<ConfirmScanBatchRequest.ConfirmedFrameMetadata> incomingFrames =
                request != null && request.frames() != null ? request.frames() : List.of();

        if (!incomingFrames.isEmpty()) {
            List<ConfirmScanBatchRequest.ConfirmedFrameMetadata> sorted = incomingFrames.stream()
                    .sorted(Comparator.comparingInt(ConfirmScanBatchRequest.ConfirmedFrameMetadata::frameNumber))
                    .toList();

            for (ConfirmScanBatchRequest.ConfirmedFrameMetadata item : sorted) {
                processedFrames.add(processSingleFrame(roll, batch.getS3Prefix(), item));
            }
        } else {
            for (int frameNum = 1; frameNum <= batch.getExpectedFrameCount(); frameNum++) {
                String origKey = String.format("%s/%02d_original.jpg", batch.getS3Prefix(), frameNum);
                ConfirmScanBatchRequest.ConfirmedFrameMetadata synthetic =
                        new ConfirmScanBatchRequest.ConfirmedFrameMetadata(frameNum, origKey, null, null, null, null);
                processedFrames.add(processSingleFrame(roll, batch.getS3Prefix(), synthetic));
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        batch.setUploadedFrameCount(processedFrames.size());
        batch.setStatus("COMPLETED");
        batch.setConfirmedAt(now);
        batchRepository.save(batch);

        String oldStatus = roll.getStatus();
        roll.setStatus("POST_PROCESSING_QC");
        filmRollRepository.save(roll);

        FilmRollStatusLog statusLog = new FilmRollStatusLog();
        statusLog.setFilmRollId(roll.getId());
        statusLog.setLabId(labId);
        statusLog.setFromStatus(oldStatus);
        statusLog.setToStatus("POST_PROCESSING_QC");
        statusLog.setNote("Hoàn tất tải lên " + processedFrames.size() + " frames scan");
        statusLogRepository.save(statusLog);

        eventPublisher.publishEvent(new RollScanUploadedEvent(
                batch.getId(),
                roll.getId(),
                roll.getOrderId(),
                roll.getLabId(),
                roll.getCustomerId(),
                roll.getRollCode(),
                roll.getProcessType(),
                processedFrames,
                now
        ));
    }

    private RollScanUploadedEvent.UploadedFrameInfo processSingleFrame(
            FilmRoll roll,
            String s3Prefix,
            ConfirmScanBatchRequest.ConfirmedFrameMetadata item
    ) {
        int frameNum = item.frameNumber();
        String origKey = item.s3KeyOriginal() != null && !item.s3KeyOriginal().isBlank()
                ? item.s3KeyOriginal()
                : String.format("%s/%02d_original.jpg", s3Prefix, frameNum);
        String webKey = String.format("%s/%02d_preview.jpg", s3Prefix, frameNum);
        String thumbKey = String.format("%s/%02d_thumb.jpg", s3Prefix, frameNum);

        Integer width = item.widthPx();
        Integer height = item.heightPx();
        long sizeBytes = item.sizeBytes() != null && item.sizeBytes() > 0 ? item.sizeBytes() : 0L;
        String sha256 = item.sha256Checksum();

        try {
            if (storageService.exists(origKey)) {
                byte[] origBytes = storageService.downloadBytes(origKey);
                sizeBytes = origBytes.length;
                if (sha256 == null || sha256.isBlank()) {
                    sha256 = computeSha256(origBytes);
                }
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(origBytes));
                if (img != null) {
                    width = img.getWidth();
                    height = img.getHeight();
                    byte[] previewBytes = resizeJpeg(img, 1600);
                    byte[] thumbBytes = resizeJpeg(img, 400);
                    uploadBytesToS3(webKey, previewBytes, "image/jpeg");
                    uploadBytesToS3(thumbKey, thumbBytes, "image/jpeg");
                }
            }
        } catch (Exception e) {
            log.warn("Không thể nén ảnh tự động trên S3 cho key={}, sử dụng fallback metadata: {}", origKey, e.getMessage());
        }

        return new RollScanUploadedEvent.UploadedFrameInfo(
                frameNum,
                origKey,
                webKey,
                thumbKey,
                width != null ? width : 3000,
                height != null ? height : 2000,
                sizeBytes,
                sha256
        );
    }

    private byte[] resizeJpeg(BufferedImage source, int maxDimension) throws Exception {
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(w, h));
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(source, 0, 0, targetW, targetH, Color.BLACK, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "jpg", baos);
        return baos.toByteArray();
    }

    private void uploadBytesToS3(String objectKey, byte[] bytes, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(storageProperties.getBucketName())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes)
        );
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return null;
        }
    }
}