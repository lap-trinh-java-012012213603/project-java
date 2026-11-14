package com.analoghub.platform.delivery.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class HmacSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long MAX_TIMESTAMP_DRIFT_MS = 300_000L;

    public boolean verifyAhamoveSignature(String secret, String rawBody, String signatureHeader) {
        if (secret == null || secret.isBlank() || rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String expected = hmacSha256Hex(secret, rawBody);
        return constantTimeEquals(expected, signatureHeader.trim());
    }

    public boolean verifyLalamoveAuthorization(String secret, String uriPath, String rawBody, String authorizationHeader) {
        if (secret == null || secret.isBlank() || rawBody == null || authorizationHeader == null) {
            return false;
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.toLowerCase().startsWith("hmac ")) {
            return false;
        }
        String tokenPart = trimmed.substring(5).trim();
        String[] parts = tokenPart.split(":");
        if (parts.length != 3) {
            return false;
        }
        long timestampMs;
        try {
            long parsed = Long.parseLong(parts[1]);
            timestampMs = parsed < 10_000_000_000L ? parsed * 1000L : parsed;
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - timestampMs) > MAX_TIMESTAMP_DRIFT_MS) {
            return false;
        }
        String signatureInput = parts[1] + "\r\nPOST\r\n" + uriPath + "\r\n\r\n" + rawBody;
        String expected = hmacSha256Hex(secret, signatureInput);
        return constantTimeEquals(expected, parts[2]);
    }

    public String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
