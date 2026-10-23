package com.analoghub.platform.common.storage;

import com.analoghub.platform.common.dto.PresignedUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

@Component
public class PresignedUrlGenerator {

    private final S3Presigner s3Presigner;

    @Value("${analoghub.s3.bucket:analoghub-media}")
    private String defaultBucket;

    public PresignedUrlGenerator(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    public PresignedUrlResponse generatePresignedUploadUrl(String objectKey, String contentType, Duration duration) {
        PutObjectRequest.Builder objectRequestBuilder = PutObjectRequest.builder()
                .bucket(defaultBucket)
                .key(objectKey);

        if (contentType != null && !contentType.isBlank()) {
            objectRequestBuilder.contentType(contentType);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(objectRequestBuilder.build())
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        Instant expiresAt = Instant.now().plus(duration);

        return new PresignedUrlResponse(presigned.url().toString(), objectKey, "PUT", expiresAt);
    }

    public PresignedUrlResponse generatePresignedDownloadUrl(String objectKey, Duration duration) {
        return generatePresignedDownloadUrl(objectKey, duration, null);
    }

    public PresignedUrlResponse generatePresignedDownloadUrl(String objectKey, Duration duration, String downloadFileName) {
        GetObjectRequest.Builder getObjectBuilder = GetObjectRequest.builder()
                .bucket(defaultBucket)
                .key(objectKey);

        if (downloadFileName != null && !downloadFileName.isBlank()) {
            getObjectBuilder.responseContentDisposition("attachment; filename=\"" + downloadFileName + "\"");
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectBuilder.build())
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        Instant expiresAt = Instant.now().plus(duration);

        return new PresignedUrlResponse(presigned.url().toString(), objectKey, "GET", expiresAt);
    }
}
