package com.analoghub.platform.aiassistant.service.governance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Component
public class AiCostEstimator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    public BigDecimal estimateCostUsd(String modelName, int inputTokens, int outputTokens) {
        String normalizedModel = modelName != null ? modelName.trim().toLowerCase(Locale.ROOT) : "";
        BigDecimal inputRatePerMillion;
        BigDecimal outputRatePerMillion;

        if (normalizedModel.contains("gemini-2.5-pro") || normalizedModel.contains("gemini-1.5-pro")) {
            inputRatePerMillion = new BigDecimal("1.250");
            outputRatePerMillion = new BigDecimal("5.000");
        } else if (normalizedModel.contains("text-embedding-004")) {
            inputRatePerMillion = new BigDecimal("0.025");
            outputRatePerMillion = BigDecimal.ZERO;
        } else if (normalizedModel.contains("text-embedding-3-small")) {
            inputRatePerMillion = new BigDecimal("0.020");
            outputRatePerMillion = BigDecimal.ZERO;
        } else if (normalizedModel.contains("gpt-4o-mini")) {
            inputRatePerMillion = new BigDecimal("0.150");
            outputRatePerMillion = new BigDecimal("0.600");
        } else if (normalizedModel.contains("gpt-4o")) {
            inputRatePerMillion = new BigDecimal("2.500");
            outputRatePerMillion = new BigDecimal("10.000");
        } else {
            // Default: gemini-2.5-flash ($0.075 / 1M input, $0.300 / 1M output)
            inputRatePerMillion = new BigDecimal("0.075");
            outputRatePerMillion = new BigDecimal("0.300");
        }

        BigDecimal inputCost = BigDecimal.valueOf(Math.max(0, inputTokens)).multiply(inputRatePerMillion);
        BigDecimal outputCost = BigDecimal.valueOf(Math.max(0, outputTokens)).multiply(outputRatePerMillion);

        return inputCost.add(outputCost)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }
}

