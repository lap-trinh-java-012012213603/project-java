package com.analoghub.platform.aiassistant.service.guardrail;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stage-1 Input Guardrail (Section 8 of 02-rag-pipeline.md):
 * 1. Length Validation (3 - 2000 chars)
 * 2. Prompt Injection & Jailbreak Filter (throws 400 AI_PROMPT_INJECTION_DETECTED)
 * 3. Out-of-Domain Detector (flags non-photography questions for safe Abstention)
 * 4. PII Masking (redacts Vietnamese phone numbers, emails, and house addresses)
 */
@Slf4j
@Service
public class InputGuardrail {

    public static final int MIN_QUESTION_LENGTH = 3;
    public static final int MAX_QUESTION_LENGTH = 2000;

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)\\bignore\\s+(?:all\\s+)?(?:previous|prior|above)\\s+(?:instructions|rules|prompts)\\b"),
            Pattern.compile("(?i)\\bdisregard\\s+(?:all\\s+)?(?:previous|prior|system)\\s+(?:instructions|rules|prompts)\\b"),
            Pattern.compile("(?i)\\b(?:override|reveal|print|show|dump|leak)\\s+(?:the\\s+)?system\\s+prompt\\b"),
            Pattern.compile("(?i)\\bignore\\s+system\\s+prompt\\b"),
            Pattern.compile("(?i)\\bsystem\\s+prompt\\s+override\\b"),
            Pattern.compile("(?i)\\b(?:you\\s+are\\s+now\\s+dan|act\\s+as\\s+dan|do\\s+anything\\s+now|jailbreak|developer\\s+mode\\s+enabled)\\b")
    );

    private static final List<String> FOLDED_INJECTION_PHRASES = List.of(
            "ignore previous instructions",
            "ignore all previous instructions",
            "ignore system prompt",
            "system prompt override",
            "ban la dan",
            "dong vai dan",
            "tiet lo system prompt",
            "hien thi system prompt",
            "in ra system prompt",
            "bo qua cac huong dan truoc",
            "bo qua huong dan truoc",
            "bo qua moi quy tac",
            "bo qua quy tac bat buoc"
    );

    private static final Set<String> PHOTOGRAPHY_DOMAIN_KEYWORDS = Set.of(
            "film", "phim", "anh", "chup", "may", "camera", "lens", "ong kinh", "trang", "scan",
            "buong toi", "darkroom", "lab", "c-41", "c41", "ecn-2", "ecn2", "e-6", "e6", "bw", "b&w",
            "den trang", "am ban", "negative", "slide", "duong ban", "reversal", "iso", "asa", "ei",
            "push", "pull", "stop", "stops", "khau do", "toc do", "man trap", "shutter", "exposure",
            "phoi sang", "chay sang", "thieu sang", "ho sang", "light leak", "bui", "xuoc", "scratch",
            "dust", "ice", "digital ice", "noritsu", "frontier", "hs-1800", "sp-3000", "scanner",
            "developer", "bleach", "fixer", "blix", "stabilizer", "stop bath", "remjet", "borax",
            "pb-2", "pb2", "sd-49", "d-76", "d76", "hc-110", "hc110", "rodinal", "xtol", "id-11",
            "metol", "hydroquinone", "ferricyanide", "formalin", "nitrile", "thuoc trang", "thuoc hien",
            "thuoc ham", "thuoc tay", "thuoc dinh hinh", "hoa chat", "kodak", "fujifilm", "fuji",
            "ilford", "cinestill", "portra", "tri-x", "trix", "hp5", "vision3", "velvia", "provia",
            "ektachrome", "gold", "ultramax", "colorplus", "agfa", "rollei", "fomapan", "kentmere",
            "leica", "canon", "nikon", "olympus", "pentax", "minolta", "contax", "hasselblad", "mamiya",
            "zenit", "yashica", "rangefinder", "slr", "tlr", "point and shoot", "medium format",
            "135", "120", "35mm", "canister", "cuon", "frame", "contact sheet", "archive", "luu tru",
            "sleeves", "print file", "vinegar syndrome", "am mau", "color cast", "grain", "hat", "halation"
    );

    private static final List<String> EXPLICIT_OUT_OF_DOMAIN_PHRASES = List.of(
            "bong da", "ngoai hang anh", "world cup", "champions league", "ty so tran",
            "chung khoan", "gia vang", "bitcoin", "crypto", "co phieu", "forex",
            "viet code", "lap trinh python", "scrape password", "hack wifi", "sql injection",
            "giai phuong trinh", "tich phan", "dao ham", "bai tap toan",
            "du bao thoi tiet", "ket qua xo so", "chinh tri", "bau cu"
    );

    @Builder
    public record InputGuardrailResult(
            String originalQuestion,
            String sanitizedQuestion,
            boolean piiMasked,
            boolean outOfDomain,
            String outOfDomainReason
    ) {
    }

    public InputGuardrailResult validateAndSanitize(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            throw AiGuardrailException.invalidLength(rawQuestion, MIN_QUESTION_LENGTH, MAX_QUESTION_LENGTH);
        }

        String trimmed = rawQuestion.trim();
        if (trimmed.length() < MIN_QUESTION_LENGTH || trimmed.length() > MAX_QUESTION_LENGTH) {
            throw AiGuardrailException.invalidLength(trimmed, MIN_QUESTION_LENGTH, MAX_QUESTION_LENGTH);
        }

        if (isPromptInjection(trimmed)) {
            log.warn("InputGuardrail blocked prompt injection attempt: '{}'", trimmed);
            throw AiGuardrailException.promptInjection(trimmed);
        }

        boolean piiDetected = PiiMaskingUtils.containsPii(trimmed);
        String sanitized = piiDetected ? PiiMaskingUtils.maskPii(trimmed) : trimmed;

        boolean outOfDomain = isOutOfDomain(sanitized);
        String outOfDomainReason = outOfDomain ? "OUT_OF_DOMAIN_QUESTION" : null;

        return InputGuardrailResult.builder()
                .originalQuestion(trimmed)
                .sanitizedQuestion(sanitized)
                .piiMasked(piiDetected)
                .outOfDomain(outOfDomain)
                .outOfDomainReason(outOfDomainReason)
                .build();
    }

    public boolean isPromptInjection(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        String folded = foldVietnamese(text);
        for (String phrase : FOLDED_INJECTION_PHRASES) {
            if (folded.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    public boolean isOutOfDomain(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String folded = foldVietnamese(text);

        boolean hasExplicitOffTopic = false;
        for (String phrase : EXPLICIT_OUT_OF_DOMAIN_PHRASES) {
            if (folded.contains(phrase)) {
                hasExplicitOffTopic = true;
                break;
            }
        }

        boolean hasDomainKeyword = false;
        for (String keyword : PHOTOGRAPHY_DOMAIN_KEYWORDS) {
            if (containsTerm(folded, keyword)) {
                // Guard against ambiguous Vietnamese words like "anh" in "ngoai hang anh" or "buong toi" vs "toi hom qua"
                if ("anh".equals(keyword) && folded.contains("ngoai hang anh")) {
                    continue;
                }
                hasDomainKeyword = true;
                break;
            }
        }

        if (hasExplicitOffTopic && !hasDomainKeyword) {
            return true;
        }
        return !hasDomainKeyword;
    }

    private static boolean containsTerm(String foldedText, String term) {
        int idx = foldedText.indexOf(term);
        while (idx >= 0) {
            boolean leftBoundary = idx == 0 || !Character.isLetterOrDigit(foldedText.charAt(idx - 1));
            int endIdx = idx + term.length();
            boolean rightBoundary = endIdx >= foldedText.length() || !Character.isLetterOrDigit(foldedText.charAt(endIdx));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            idx = foldedText.indexOf(term, idx + 1);
        }
        return false;
    }

    static String foldVietnamese(String text) {
        if (text == null) {
            return "";
        }
        String replaced = text.replace('đ', 'd').replace('Đ', 'D');
        String decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
