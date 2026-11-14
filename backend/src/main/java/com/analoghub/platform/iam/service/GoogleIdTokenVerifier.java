package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Verifies Google OIDC {@code id_token} strings and extracts the authenticated Google profile claims.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );
    private static final String PLACEHOLDER_CLIENT_ID = "your-google-client-id";

    private final ObjectMapper objectMapper;
    private final String configuredClientId;

    public GoogleIdTokenVerifier(
            ObjectMapper objectMapper,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String configuredClientId
    ) {
        this.objectMapper = objectMapper;
        this.configuredClientId = configuredClientId != null ? configuredClientId.trim() : "";
    }

    public record GoogleUserPayload(
            String sub,
            String email,
            boolean emailVerified,
            String fullName,
            String pictureUrl
    ) {
    }

    /**
     * Parses and validates a Google OAuth2 / OIDC {@code id_token}.
     *
     * @throws AuthException with {@code OAUTH2_AUTHENTICATION_FAILED} (401) if invalid, expired, or unverified
     */
    public GoogleUserPayload verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw AuthException.oauth2Failed("Google", "Google ID Token không được để trống.");
        }

        String[] parts = idToken.trim().split("\\.");
        if (parts.length != 3 || parts[2].isBlank()) {
            throw AuthException.oauth2Failed("Google", "Cấu trúc Google ID Token không hợp lệ.");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));

            String iss = textOrNull(payload, "iss");
            if (iss == null || !ALLOWED_ISSUERS.contains(iss)) {
                throw AuthException.oauth2Failed("Google", "Issuer (iss) của Google ID Token không hợp lệ.");
            }

            if (!configuredClientId.isEmpty() && !PLACEHOLDER_CLIENT_ID.equalsIgnoreCase(configuredClientId)) {
                String aud = textOrNull(payload, "aud");
                if (!configuredClientId.equals(aud)) {
                    throw AuthException.oauth2Failed("Google", "Google ID Token không thuộc ứng dụng AnalogHub (aud mismatch).");
                }
            }

            long expEpochSeconds = payload.path("exp").asLong(0L);
            if (expEpochSeconds <= Instant.now().getEpochSecond()) {
                throw AuthException.oauth2Failed("Google", "Google ID Token đã hết hạn.");
            }

            String sub = textOrNull(payload, "sub");
            String email = textOrNull(payload, "email");
            boolean emailVerified = payload.path("email_verified").isBoolean()
                    ? payload.path("email_verified").asBoolean()
                    : "true".equalsIgnoreCase(textOrNull(payload, "email_verified"));

            if (sub == null || sub.isBlank() || email == null || email.isBlank() || !emailVerified) {
                throw AuthException.oauth2Failed("Google", "Tài khoản Google chưa xác thực email hợp lệ.");
            }

            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            String fullName = textOrNull(payload, "name");
            if (fullName == null || fullName.isBlank()) {
                int atIdx = normalizedEmail.indexOf('@');
                fullName = atIdx > 0 ? normalizedEmail.substring(0, atIdx) : "AnalogHub User";
            }
            String picture = textOrNull(payload, "picture");

            return new GoogleUserPayload(sub.trim(), normalizedEmail, true, fullName.trim(), picture);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("[GoogleOAuth2] Failed to parse/verify Google ID Token: {}", ex.getMessage());
            throw AuthException.oauth2Failed("Google", "Google ID Token không hợp lệ hoặc đã bị thay đổi.");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return (text == null || text.isBlank()) ? null : text;
    }
}
