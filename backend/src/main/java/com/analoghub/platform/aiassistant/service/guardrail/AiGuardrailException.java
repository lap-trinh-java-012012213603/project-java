package com.analoghub.platform.aiassistant.service.guardrail;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class AiGuardrailException extends BusinessException {

    public static final String CODE_PROMPT_INJECTION = "AI_PROMPT_INJECTION_DETECTED";
    public static final String CODE_INPUT_GUARDRAIL_BLOCKED = "AI_INPUT_GUARDRAIL_BLOCKED";

    private final String guardrailRule;
    private final String guardrailCode;
    private final String rejectedInput;

    public AiGuardrailException(String guardrailRule, String guardrailCode, String message, String rejectedInput) {
        super(ErrorCode.AI_PROMPT_TOO_LONG, message);
        this.guardrailRule = guardrailRule;
        this.guardrailCode = guardrailCode;
        this.rejectedInput = rejectedInput;
    }

    public static AiGuardrailException promptInjection(String rejectedInput) {
        return new AiGuardrailException(
                "PROMPT_INJECTION",
                CODE_PROMPT_INJECTION,
                "Câu hỏi bị từ chối bởi bộ lọc an toàn Input Guardrail (AI_PROMPT_INJECTION_DETECTED): "
                        + "Phát hiện chỉ thị can thiệp hệ thống hoặc vượt rào (Prompt Injection).",
                rejectedInput
        );
    }

    public static AiGuardrailException invalidLength(String rejectedInput, int minLength, int maxLength) {
        return new AiGuardrailException(
                "INPUT_LENGTH",
                CODE_INPUT_GUARDRAIL_BLOCKED,
                String.format("Câu hỏi phải có độ dài từ %d đến %d ký tự.", minLength, maxLength),
                rejectedInput
        );
    }
}
