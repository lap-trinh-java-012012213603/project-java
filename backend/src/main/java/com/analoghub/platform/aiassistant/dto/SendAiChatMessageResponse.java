package com.analoghub.platform.aiassistant.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record SendAiChatMessageResponse(
        UUID sessionId,
        UUID userMessageId,
        UUID assistantMessageId,
        String providerUsed,
        String modelUsed,
        String answer,
        boolean abstained,
        String abstentionReason,
        boolean safetyWarningAttached,
        String safetyNoticeVi,
        Double confidenceScore,
        List<RagCitationDto> citations,
        RetrievalTelemetryDto retrievalTelemetry,
        UsageTelemetryDto usage,
        Instant createdAt
) {

    @Builder
    public record RetrievalTelemetryDto(
            String embeddingModel,
            int embeddingDimensions,
            int initialCandidatesCount,
            int rerankedPassedCount,
            double topRerankScore,
            double thresholdApplied,
            int knowledgeGraphExpandedNodes
    ) {
    }

    @Builder
    public record UsageTelemetryDto(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            BigDecimal estimatedCostUsd,
            long latencyMs
    ) {
    }
}
