package com.analoghub.platform.aiassistant.service.governance;

import com.analoghub.platform.aiassistant.client.ModelProvider;
import com.analoghub.platform.aiassistant.client.TokenUsage;
import com.analoghub.platform.aiassistant.domain.entity.AiUsageLog;
import com.analoghub.platform.aiassistant.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageLogger {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiCostEstimator aiCostEstimator;

    public AiUsageLog logUsage(
            UUID userId,
            String featureType,
            ModelProvider provider,
            String modelName,
            TokenUsage tokenUsage,
            long latencyMs
    ) {
        int promptTokens = tokenUsage != null ? Math.max(0, tokenUsage.promptTokens()) : 0;
        int completionTokens = tokenUsage != null ? Math.max(0, tokenUsage.completionTokens()) : 0;
        int totalTokens = tokenUsage != null
                ? Math.max(0, tokenUsage.totalTokens())
                : promptTokens + completionTokens;

        BigDecimal estimatedCostUsd = aiCostEstimator.estimateCostUsd(modelName, promptTokens, completionTokens);
        String formattedAction = buildActionLabel(featureType, provider, modelName);

        AiUsageLog entry = AiUsageLog.builder()
                .userId(userId)
                .action(formattedAction)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .createdAt(Instant.now())
                .provider(provider)
                .modelName(modelName)
                .estimatedCostUsd(estimatedCostUsd)
                .latencyMs(latencyMs)
                .build();

        try {
            AiUsageLog saved = aiUsageLogRepository.save(entry);
            saved.setProvider(provider);
            saved.setModelName(modelName);
            saved.setEstimatedCostUsd(estimatedCostUsd);
            saved.setLatencyMs(latencyMs);
            return saved;
        } catch (Exception ex) {
            log.warn("Failed to persist AiUsageLog (action={}, tokens={}): {}",
                    formattedAction, totalTokens, ex.getMessage());
            return entry;
        }
    }

    static String buildActionLabel(String featureType, ModelProvider provider, String modelName) {
        String feature = featureType != null && !featureType.isBlank() ? featureType.trim() : "GENERAL";
        String prov = provider != null ? provider.name() : "UNKNOWN";
        String model = modelName != null && !modelName.isBlank() ? modelName.trim() : "default";
        String combined = feature + ":" + prov + ":" + model;
        return combined.length() > 100 ? combined.substring(0, 100) : combined;
    }
}

