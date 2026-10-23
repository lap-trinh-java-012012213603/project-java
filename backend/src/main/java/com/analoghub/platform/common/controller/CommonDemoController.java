package com.analoghub.platform.common.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.dto.ImageProcessResult;
import com.analoghub.platform.common.dto.PresignedUrlResponse;
import com.analoghub.platform.common.storage.ImageThumbnailProcessor;
import com.analoghub.platform.common.storage.S3StorageService;
import com.analoghub.platform.common.util.QrCodeGenerator;
import com.analoghub.platform.common.websocket.StompNotificationPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/common")
public class CommonDemoController {

    private final S3StorageService s3StorageService;
    private final StompNotificationPublisher stompPublisher;
    private final QrCodeGenerator qrCodeGenerator;
    private final ImageThumbnailProcessor imageProcessor;

    public CommonDemoController(
            S3StorageService s3StorageService,
            StompNotificationPublisher stompPublisher,
            QrCodeGenerator qrCodeGenerator,
            ImageThumbnailProcessor imageProcessor) {
        this.s3StorageService = s3StorageService;
        this.stompPublisher = stompPublisher;
        this.qrCodeGenerator = qrCodeGenerator;
        this.imageProcessor = imageProcessor;
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> getQrCodeImage(@RequestParam(defaultValue = "ROLL-89A2-01") String code) {
        byte[] qrBytes = qrCodeGenerator.generateQrCodePng(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(qrBytes);
    }

    @GetMapping("/barcode")
    public ResponseEntity<byte[]> getBarcodeImage(@RequestParam(defaultValue = "ROLL-89A2-01") String code) {
        byte[] barcodeBytes = qrCodeGenerator.generateBarcodePng(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .body(barcodeBytes);
    }

    @GetMapping("/qr/base64")
    public ApiResponse<Map<String, String>> getQrCodeBase64(@RequestParam(defaultValue = "ROLL-89A2-01") String code) {
        String base64 = qrCodeGenerator.generateQrCodeBase64(code);
        return ApiResponse.success(Map.of("code", code, "dataUrl", base64));
    }

    @GetMapping("/presigned-upload")
    public ApiResponse<PresignedUrlResponse> getPresignedUploadUrl(
            @RequestParam(defaultValue = "scans/ROLL-89A2-01.zip") String key,
            @RequestParam(defaultValue = "application/zip") String contentType) {
        PresignedUrlResponse response = s3StorageService.generatePresignedUploadUrl(
                key,
                contentType,
                Duration.ofMinutes(15)
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/presigned-download")
    public ApiResponse<PresignedUrlResponse> getPresignedDownloadUrl(
            @RequestParam(defaultValue = "scans/ROLL-89A2-01.zip") String key) {
        PresignedUrlResponse response = s3StorageService.generatePresignedDownloadUrl(
                key,
                Duration.ofMinutes(15)
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/notify-order")
    public ApiResponse<String> notifyOrderStatus(
            @RequestParam(defaultValue = "LAB-20261021-89A2") String orderCode,
            @RequestParam(defaultValue = "ROLL-89A2-01") String rollCode,
            @RequestParam(defaultValue = "SCANNING") String status,
            @RequestParam(required = false, defaultValue = "Dang quet anh Noritsu HS-1800") String note) {
        stompPublisher.publishRollStatus(orderCode, rollCode, status, note);
        return ApiResponse.success("Da gui thong bao STOMP toi kenh /topic/orders/" + orderCode, status);
    }

    @PostMapping("/process-image")
    public ApiResponse<Map<String, Object>> processImageFile(@RequestParam("file") MultipartFile file) throws IOException {
        ImageProcessResult result = imageProcessor.processImage(file.getBytes());
        return ApiResponse.success(Map.of(
                "originalWidth", result.originalWidth(),
                "originalHeight", result.originalHeight(),
                "format", result.format(),
                "previewSize", result.previewBytes().length,
                "thumbnailSize", result.thumbnailBytes().length
        ));
    }
}
