package com.analoghub.platform.common.storage;

import com.analoghub.platform.common.exception.ArchiveStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties props;

    public record PresignedUploadResult(
            String objectKey,
            String uploadPutUrl,
            long expiresInSeconds
    ) {}

    /**
     * 1. Dùng cho các file thông thường (Avatar, Ảnh Lab, Ảnh Marketplace, Ảnh bài viết Community)
     */
    public PresignedUploadResult generatePublicUploadUrl(
            StorageCategory category,
            UUID ownerOrEntityId,
            String originalFileName,
            String contentType,
            long fileSizeBytes
    ) {
        validateFile(category, originalFileName, contentType, fileSizeBytes);

        String ext = extractExtension(originalFileName, contentType);
        String objectKey = String.format("%s/%s/%s%s",
                category.getBasePrefix(),
                ownerOrEntityId,
                UUID.randomUUID(),
                ext
        );

        String putUrl = createPresignedPutUrl(objectKey, contentType);
        return new PresignedUploadResult(objectKey, putUrl, props.getPresignPutExpirationMinutes() * 60);
    }

    /**
     * 2. Dùng riêng cho Film Lab khi upload 36 kiểu ảnh scan của 1 cuộn phim (Multi-tenant isolation)
     * Kết quả key dạng: private/tenants/{labId}/rolls/{rollId}/frames/01_original.jpg
     */
    public PresignedUploadResult generateRollFrameUploadUrl(
            StorageCategory frameCategory, // ROLL_SCAN_THUMB, ROLL_SCAN_PREVIEW, hoặc ROLL_SCAN_ORIGINAL
            UUID labId,
            UUID rollId,
            int frameNumber,
            String originalFileName,
            String contentType,
            long fileSizeBytes
    ) {
        validateFile(frameCategory, originalFileName, contentType, fileSizeBytes);

        String variant = switch (frameCategory) {
            case ROLL_SCAN_THUMB -> "thumb";
            case ROLL_SCAN_PREVIEW -> "preview";
            default -> "original";
        };
        String ext = extractExtension(originalFileName, contentType);
        String objectKey = String.format("private/tenants/%s/rolls/%s/frames/%02d_%s%s",
                labId, rollId, frameNumber, variant, ext
        );

        String putUrl = createPresignedPutUrl(objectKey, contentType);
        return new PresignedUploadResult(objectKey, putUrl, props.getPresignPutExpirationMinutes() * 60);
    }

    /**
     * 3. Lấy URL để trả về cho Frontend hiển thị hoặc tải file:
     * - Nếu key bắt đầu bằng "public/" -> ghép thẳng link tĩnh (nhanh, cache được trên trình duyệt)
     * - Nếu key bắt đầu bằng "private/" -> tự động ký Presigned GET URL có thời hạn
     */
    public String resolveReadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("public/")) {
            return props.getPublicBaseUrl() + "/" + objectKey;
        }
        return createPresignedGetUrl(objectKey, null);
    }

    /**
     * 4. Tạo link tải file gốc có ép trình duyệt bật hộp thoại Save As (Content-Disposition: attachment)
     */
    public String generateDownloadOriginalUrl(String objectKey, String downloadFileName) {
        return createPresignedGetUrl(objectKey, "attachment; filename=\"" + downloadFileName + "\"");
    }

    /**
     * 5. Kiểm tra file đã thực sự được FE đẩy lên R2/MinIO chưa (Dùng trong API /confirm-upload)
     */
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * 6. Đọc mảng byte của ảnh preview từ Storage để đưa vào Tầng 1 Scan QC (OpenCV Laplacian/Histogram)
     */
    public byte[] downloadBytes(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(props.getBucketName())
                            .key(objectKey)
                            .build()
            );
            return objectBytes.asByteArray();
        } catch (NoSuchKeyException e) {
            throw ArchiveStorageException.objectNotFoundOnStorage(objectKey);
        } catch (Exception e) {
            throw ArchiveStorageException.presignFailed(e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================
    private void validateFile(StorageCategory category, String fileName, String contentType, long fileSizeBytes) {
        if (contentType == null || !category.getAllowedContentTypes().contains(contentType.toLowerCase())) {
            throw ArchiveStorageException.unsupportedFormat(contentType);
        }
        if (fileSizeBytes <= 0 || fileSizeBytes > category.getMaxSizeBytes()) {
            throw ArchiveStorageException.fileSizeExceeded(fileName, fileSizeBytes);
        }
    }

    private String createPresignedPutUrl(String objectKey, String contentType) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(props.getPresignPutExpirationMinutes()))
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Failed to generate Presigned PUT URL for key={}", objectKey, e);
            throw ArchiveStorageException.presignFailed(e);
        }
    }

    private String createPresignedGetUrl(String objectKey, String responseContentDisposition) {
        try {
            GetObjectRequest.Builder getBuilder = GetObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey);

            if (responseContentDisposition != null) {
                getBuilder.responseContentDisposition(responseContentDisposition);
            }

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(props.getPresignGetExpirationMinutes()))
                    .getObjectRequest(getBuilder.build())
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Failed to generate Presigned GET URL for key={}", objectKey, e);
            throw ArchiveStorageException.presignFailed(e);
        }
    }

    private String extractExtension(String fileName, String contentType) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        }
        return switch (contentType.toLowerCase()) {
            case "image/webp" -> ".webp";
            case "image/png" -> ".png";
            case "image/tiff" -> ".tiff";
            default -> ".jpg";
        };
    }
}