package com.analoghub.platform.aiassistant.service.guardrail;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Stage-2 Output Guardrail (Section 8 of 02-rag-pipeline.md):
 * 1. System Prompt Leakage Check & Sanitization
 * 2. Darkroom Chemical Keyword Detection (developer, fixer, bleach, blix, D-76, PB-2, C-41...)
 * 3. Automatic Safety Warning Attachment ('Đeo găng tay Nitrile & thông gió tốt')
 */
@Slf4j
@Service
public class OutputGuardrail {

    public static final String DEFAULT_CHEMICAL_SAFETY_NOTICE_VI =
            "⚠️ **CẢNH BÁO AN TOÀN HÓA CHẤT BUỒNG TỐI (OUTPUT GUARDRAIL)**: "
            + "Khi pha chế hoặc thao tác với hóa chất buồng tối (Developer, Bleach, Fixer, Blix, PB-2...), "
            + "bắt buộc **đeo găng tay Nitrile & thông gió tốt**, sử dụng kính bảo hộ và tuyệt đối không xả trực tiếp "
            + "hóa chất đã qua sử dụng ra nguồn nước sinh hoạt.";

    private static final List<String> SYSTEM_PROMPT_LEAK_MARKERS = List.of(
            "QUY TẮC BẮT BUỘC (KHÔNG ĐƯỢC VI PHẠM)",
            "[TÀI LIỆU THAM KHẢO]:",
            "{CONTEXT_CHUNKS}",
            "{CHAT_HISTORY}",
            "{USER_QUESTION}"
    );

    private static final List<String> CHEMICAL_SAFETY_KEYWORDS = List.of(
            "developer", "fixer", "bleach", "blix", "stabilizer", "stop bath", "remjet",
            "pb-2", "pb2", "sd-49", "c-41", "c41", "ecn-2", "ecn2", "e-6", "e6",
            "d-76", "d76", "hc-110", "hc110", "rodinal", "xtol", "id-11",
            "metol", "hydroquinone", "borax", "ferricyanide", "dichromate",
            "acetic acid", "sulfuric", "formalin", "formaldehyde", "cd-3", "cd-4",
            "thuốc tráng", "thuốc hiện", "thuốc hãm", "thuốc tẩy", "thuốc định hình",
            "hóa chất", "pha thuốc", "pha chế", "nhiệt độ 37.8"
    );

    @Builder
    public record OutputGuardrailResult(
            String finalAnswer,
            boolean safetyWarningAttached,
            String safetyNoticeVi,
            boolean systemPromptLeakSanitized
    ) {
    }

    public OutputGuardrailResult inspectAndEnforce(String rawAnswer, String questionContext, boolean abstained) {
        if (!StringUtils.hasText(rawAnswer)) {
            return OutputGuardrailResult.builder()
                    .finalAnswer("")
                    .safetyWarningAttached(false)
                    .safetyNoticeVi(null)
                    .systemPromptLeakSanitized(false)
                    .build();
        }

        String sanitized = rawAnswer.trim();
        boolean leakDetected = false;
        for (String marker : SYSTEM_PROMPT_LEAK_MARKERS) {
            if (sanitized.contains(marker)) {
                leakDetected = true;
                sanitized = sanitized.replace(marker, "").trim();
            }
        }

        if (abstained) {
            return OutputGuardrailResult.builder()
                    .finalAnswer(sanitized)
                    .safetyWarningAttached(false)
                    .safetyNoticeVi(null)
                    .systemPromptLeakSanitized(leakDetected)
                    .build();
        }

        boolean mentionsChemicals = containsChemicalKeywords(sanitized) || containsChemicalKeywords(questionContext);
        if (!mentionsChemicals) {
            return OutputGuardrailResult.builder()
                    .finalAnswer(sanitized)
                    .safetyWarningAttached(false)
                    .safetyNoticeVi(null)
                    .systemPromptLeakSanitized(leakDetected)
                    .build();
        }

        String lowerAnswer = sanitized.toLowerCase(Locale.ROOT);
        boolean alreadyHasSafetyNotice = lowerAnswer.contains("cảnh báo an toàn hóa chất")
                && lowerAnswer.contains("nitrile");

        String finalAnswer = alreadyHasSafetyNotice
                ? sanitized
                : sanitized + "\n\n" + DEFAULT_CHEMICAL_SAFETY_NOTICE_VI;

        return OutputGuardrailResult.builder()
                .finalAnswer(finalAnswer)
                .safetyWarningAttached(true)
                .safetyNoticeVi(DEFAULT_CHEMICAL_SAFETY_NOTICE_VI)
                .systemPromptLeakSanitized(leakDetected)
                .build();
    }

    public boolean containsChemicalKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String folded = InputGuardrail.foldVietnamese(text);
        for (String keyword : CHEMICAL_SAFETY_KEYWORDS) {
            String foldedKw = InputGuardrail.foldVietnamese(keyword);
            if (lower.contains(keyword) || folded.contains(foldedKw)) {
                return true;
            }
        }
        return false;
    }
}
