package com.analoghub.platform.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String publicBaseUrl;
    private long presignPutExpirationMinutes;
    private long presignGetExpirationMinutes;
    private long maxAvatarBytes;
    private long maxPublicMediaBytes;
    private long maxScanThumbBytes;
    private long maxScanPreviewBytes;
    private long maxScanOriginalBytes;
}