package com.analoghub.platform.common.storage;

import com.analoghub.platform.common.exception.ArchiveStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties props;
    private final AtomicBoolean publicPolicyConfigured = new AtomicBoolean(false);

    public record PresignedUploadResult(
            String objectKey,
            String uploadPutUrl,
            long expiresInSeconds
    ) {}

    public PresignedUploadResult generatePublicUploadUrl(
            StorageCategory category,
            UUID ownerOrEntityId,
            String originalFileName,
            String contentType,
            long fileSizeBytes
    ) {
        validateFile(category, originalFileName, contentType, fileSizeBytes);
        ensurePublicReadPolicyQuietly();

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
     * Directly uploads a public media file (e.g., User Avatar) to S3/MinIO in a single step and returns its objectKey.
     */
    public String uploadPublicFile(
            StorageCategory category,
            UUID ownerOrEntityId,
            String originalFileName,
            String contentType,
            byte[] fileBytes
    ) {
        long size = fileBytes != null ? fileBytes.length : 0L;
        validateFile(category, originalFileName, contentType, size);
        ensurePublicReadPolicyQuietly();

        String ext = extractExtension(originalFileName, contentType);
        String objectKey = String.format("%s/%s/%s%s",
                category.getBasePrefix(),
                ownerOrEntityId,
                UUID.randomUUID(),
                ext
        );

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes));
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to upload public file directly to S3/MinIO for key={}", objectKey, e);
            throw ArchiveStorageException.presignFailed(e);
        }
    }

    public PresignedUploadResult generateRollFrameUploadUrl(
            StorageCategory frameCategory,
            UUID labId,
            UUID orderId,
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

        String objectKey = String.format("private/tenants/%s/orders/%s/rolls/%s/frames/%02d_%s%s",
                labId, orderId, rollId, frameNumber, variant, ext
        );

        String putUrl = createPresignedPutUrl(objectKey, contentType);
        return new PresignedUploadResult(objectKey, putUrl, props.getPresignPutExpirationMinutes() * 60);
    }

    public String resolveReadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        if (objectKey.startsWith("public/")) {
            ensurePublicReadPolicyQuietly();
            return props.getPublicBaseUrl() + "/" + objectKey;
        }
        return createPresignedGetUrl(objectKey, null);
    }

    public String generateDownloadOriginalUrl(String objectKey, String downloadFileName) {
        return createPresignedGetUrl(objectKey, "attachment; filename=\"" + downloadFileName + "\"");
    }

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

    private void ensurePublicReadPolicyQuietly() {
        if (publicPolicyConfigured.get() || s3Client == null || props == null || props.getBucketName() == null) {
            return;
        }
        try {
            String bucket = props.getBucketName();
            String policyJson = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/public/*"]}]}
                    """.formatted(bucket).trim();
            s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket)
                    .policy(policyJson)
                    .build());
            publicPolicyConfigured.set(true);
        } catch (Exception ex) {
            log.debug("[StorageService] Could not auto-apply public bucket policy: {}", ex.getMessage());
        }
    }

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
