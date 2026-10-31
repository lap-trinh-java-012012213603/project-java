package com.analoghub.platform.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Component
public class QrCodeGenerator {

    private static final int DEFAULT_QR_WIDTH = 300;
    private static final int DEFAULT_QR_HEIGHT = 300;
    private static final int DEFAULT_BARCODE_WIDTH = 400;
    private static final int DEFAULT_BARCODE_HEIGHT = 120;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public byte[] generateQrCodePng(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate QR code: " + e.getMessage(), e);
        }
    }

    public byte[] generateQrCodePng(String content) {
        return generateQrCodePng(content, DEFAULT_QR_WIDTH, DEFAULT_QR_HEIGHT);
    }

    public byte[] generateBarcodePng(String content, int width, int height) {
        try {
            Code128Writer barcodeWriter = new Code128Writer();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 10);

            BitMatrix bitMatrix = barcodeWriter.encode(content, BarcodeFormat.CODE_128, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate Barcode: " + e.getMessage(), e);
        }
    }

    public byte[] generateBarcodePng(String content) {
        return generateBarcodePng(content, DEFAULT_BARCODE_WIDTH, DEFAULT_BARCODE_HEIGHT);
    }

    public String generateQrCodeBase64(String content) {
        byte[] pngData = generateQrCodePng(content);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }

    public String generateBarcodeBase64(String content) {
        byte[] pngData = generateBarcodePng(content);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }

    public String generateRandomCheckinToken() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashTokenSha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
