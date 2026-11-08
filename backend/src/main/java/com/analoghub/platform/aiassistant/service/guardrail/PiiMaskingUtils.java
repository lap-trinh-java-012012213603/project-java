package com.analoghub.platform.aiassistant.service.guardrail;

import java.util.regex.Pattern;

/**
 * Utility for masking Personally Identifiable Information (PII) before sending text to external LLMs
 * (Section 8.2 of 01-ai-architecture-and-model-gateway.md & Section 8 of 02-rag-pipeline.md).
 */
public final class PiiMaskingUtils {

    public static final String PHONE_REDACTED = "[PHONE_REDACTED]";
    public static final String EMAIL_REDACTED = "[EMAIL_REDACTED]";
    public static final String ADDRESS_REDACTED = "[ADDRESS_REDACTED]";

    // Matches Vietnamese mobile/landline numbers: +84901234567, 0901 234 567, 091-234-5678, 84987654321
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?84|0)(?:[\\s.\\-]?(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9]|2[0-9]))(?:[\\s.\\-]?\\d){7}(?!\\d)"
    );

    // Matches standard email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b"
    );

    // Matches specific Vietnamese street/house numbers while preserving Ward/District/Province
    private static final Pattern STREET_ADDRESS_PATTERN = Pattern.compile(
            "(?i)\\b(?:số\\s+nhà|số|ngõ|hẻm)\\s+\\d+[A-Za-z0-9/\\-]*(?:\\s+(?:đường|phố)\\s+[\\p{L}0-9\\s]{2,25})?"
    );

    private PiiMaskingUtils() {
    }

    public static String maskPii(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String masked = EMAIL_PATTERN.matcher(input).replaceAll(EMAIL_REDACTED);
        masked = VN_PHONE_PATTERN.matcher(masked).replaceAll(PHONE_REDACTED);
        masked = STREET_ADDRESS_PATTERN.matcher(masked).replaceAll(ADDRESS_REDACTED);
        return masked;
    }

    public static boolean containsPii(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(input).find()
                || VN_PHONE_PATTERN.matcher(input).find()
                || STREET_ADDRESS_PATTERN.matcher(input).find();
    }
}
