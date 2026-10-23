package com.analoghub.platform.common.storage;

import com.analoghub.platform.common.dto.PresignedUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final PresignedUrlGenerator presignedUrlGenerator;

    @Value("${analoghub.s3.bucket:analoghub-media}")
    private String defaultBucket;

    public S3StorageService(S3Client s3Client, PresignedUrlGenerator presignedUrlGenerator) {
        this.s3Client = s3Client;
        this.presignedUrlGenerator = presignedUrlGenerator;
    }

    public void uploadFile(String objectKey, byte[] data, String contentType) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(defaultBucket)
                .key(objectKey);

        if (contentType != null && !contentType.isBlank()) {
            builder.contentType(contentType);
        }

        s3Client.putObject(builder.build(), RequestBody.fromBytes(data));
    }

    public byte[] downloadFile(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(defaultBucket)
                .key(objectKey)
                .build();

        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(getObjectRequest);
        return responseBytes.asByteArray();
    }

    public void deleteFile(String objectKey) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(defaultBucket)
                .key(objectKey)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }

    public boolean doesObjectExist(String objectKey) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(defaultBucket)
                    .key(objectKey)
                    .build();
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public PresignedUrlResponse generatePresignedUploadUrl(String objectKey, String contentType, Duration duration) {
        return presignedUrlGenerator.generatePresignedUploadUrl(objectKey, contentType, duration);
    }

    public PresignedUrlResponse generatePresignedDownloadUrl(String objectKey, Duration duration) {
        return presignedUrlGenerator.generatePresignedDownloadUrl(objectKey, duration);
    }

    public PresignedUrlResponse generatePresignedDownloadUrl(String objectKey, Duration duration, String downloadFileName) {
        return presignedUrlGenerator.generatePresignedDownloadUrl(objectKey, duration, downloadFileName);
    }
}
