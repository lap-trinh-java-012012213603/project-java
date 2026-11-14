package com.analoghub.platform.payment.gateway;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * VNPay v2.1.0 Payment Gateway Service (HMAC-SHA512 signing & IPN verification).
 */
@Service
@RequiredArgsConstructor
public class VnpayGatewayService {

    public static final String VNP_VERSION = "2.1.0";
    public static final String VNP_COMMAND = "pay";
    public static final String VNP_CURR_CODE = "VND";
    public static final String VNP_ORDER_TYPE = "other";
    public static final ZoneId VNP_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VNP_ZONE_ID);

    private final VnpayProperties vnpayProperties;
    private final PlatformConfigFacade platformConfigFacade;

    public String buildCheckoutUrl(
            String txnCode,
            PaymentPurpose paymentPurpose,
            String referenceCode,
            BigDecimal amountVnd,
            String bankCode,
            String returnUrlOverride,
            String locale,
            String clientIp,
            Instant createdAt,
            Instant expiresAt
    ) {
        String tmnCode = resolveTmnCode();
        String hashSecret = resolveHashSecret();
        String payUrl = resolvePayUrl();
        String returnUrl = (returnUrlOverride != null && !returnUrlOverride.isBlank())
                ? returnUrlOverride.trim()
                : resolveReturnUrl();

        long vnpAmount = amountVnd
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        TreeMap<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", VNP_VERSION);
        vnpParams.put("vnp_Command", VNP_COMMAND);
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", VNP_CURR_CODE);
        vnpParams.put("vnp_TxnRef", txnCode);
        vnpParams.put("vnp_OrderInfo", "Thanh toan " + paymentPurpose.name() + " " + referenceCode);
        vnpParams.put("vnp_OrderType", VNP_ORDER_TYPE);
        vnpParams.put("vnp_Locale", (locale != null && !locale.isBlank()) ? locale.trim() : "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", VNP_DATE_FORMATTER.format(createdAt));
        vnpParams.put("vnp_ExpireDate", VNP_DATE_FORMATTER.format(expiresAt));

        if (bankCode != null && !bankCode.isBlank()) {
            vnpParams.put("vnp_BankCode", bankCode.trim());
        }

        String queryData = buildUrlEncodedDataString(vnpParams);
        String secureHash = hmacSha512Hex(hashSecret, queryData);

        return payUrl + "?" + queryData + "&vnp_SecureHash=" + secureHash;
    }

    public String computeSecureHash(Map<String, String> params) {
        TreeMap<String, String> sorted = filterAndSortVnpParams(params);
        String hashData = buildUrlEncodedDataString(sorted);
        return hmacSha512Hex(resolveHashSecret(), hashData);
    }

    public boolean verifyIpnSignature(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        TreeMap<String, String> sorted = filterAndSortVnpParams(params);
        if (sorted.isEmpty()) {
            return false;
        }

        String secret = resolveHashSecret();
        String normalizedReceived = receivedHash.trim().toLowerCase();

        String encodedData = buildUrlEncodedDataString(sorted);
        String expectedEncodedHash = hmacSha512Hex(secret, encodedData).toLowerCase();
        if (MessageDigest.isEqual(
                expectedEncodedHash.getBytes(StandardCharsets.UTF_8),
                normalizedReceived.getBytes(StandardCharsets.UTF_8)
        )) {
            return true;
        }

        String rawData = buildRawDataString(sorted);
        String expectedRawHash = hmacSha512Hex(secret, rawData).toLowerCase();
        return MessageDigest.isEqual(
                expectedRawHash.getBytes(StandardCharsets.UTF_8),
                normalizedReceived.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Instant parseVnpPayDate(String vnpPayDate) {
        if (vnpPayDate == null || vnpPayDate.isBlank()) {
            return Instant.now();
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(
                    vnpPayDate.trim(),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            );
            return localDateTime.atZone(VNP_ZONE_ID).toInstant();
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    public String hmacSha512Hex(String secret, String data) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "VNPay hashSecret is not configured");
        }
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to compute VNPay HMAC-SHA512 signature", ex);
        }
    }

    public int resolveTimeoutMinutes() {
        int defaultTimeout = vnpayProperties.getTimeoutMinutes() > 0 ? vnpayProperties.getTimeoutMinutes() : 15;
        return platformConfigFacade != null
                ? platformConfigFacade.getIntOrDefault("payment.vnpay.timeout_minutes", defaultTimeout)
                : defaultTimeout;
    }

    private String resolveTmnCode() {
        if (platformConfigFacade != null) {
            return platformConfigFacade.getString("payment.vnpay.tmn_code")
                    .filter(s -> !s.isBlank())
                    .orElse(vnpayProperties.getTmnCode());
        }
        return vnpayProperties.getTmnCode();
    }

    private String resolveHashSecret() {
        if (platformConfigFacade != null) {
            return platformConfigFacade.getString("payment.vnpay.hash_secret")
                    .filter(s -> !s.isBlank())
                    .orElse(vnpayProperties.getHashSecret());
        }
        return vnpayProperties.getHashSecret();
    }

    private String resolvePayUrl() {
        if (platformConfigFacade != null) {
            return platformConfigFacade.getString("payment.vnpay.pay_url")
                    .filter(s -> !s.isBlank())
                    .orElse(vnpayProperties.getPayUrl());
        }
        return vnpayProperties.getPayUrl();
    }

    private String resolveReturnUrl() {
        if (platformConfigFacade != null) {
            return platformConfigFacade.getString("payment.vnpay.return_url")
                    .filter(s -> !s.isBlank())
                    .orElse(vnpayProperties.getReturnUrl());
        }
        return vnpayProperties.getReturnUrl();
    }

    private TreeMap<String, String> filterAndSortVnpParams(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null
                    && key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null
                    && !value.isBlank()) {
                sorted.put(key, value);
            }
        }
        return sorted;
    }

    private String buildUrlEncodedDataString(TreeMap<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private String buildRawDataString(TreeMap<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }
}
