package com.analoghub.platform.aiassistant.service.rag;

import com.analoghub.platform.aiassistant.dto.RagCitationDto;
import com.analoghub.platform.aiassistant.dto.RagStructuredOutput.RawCitationItem;
import com.analoghub.platform.aiassistant.service.rag.RagRetrievalService.ScoredChunk;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Post-Generation Citation Verifier (Section 7.3 of 02-rag-pipeline.md):
 * 1. Verifies each cited chunkId belongs to the Top-K retrieved chunks sent in the prompt.
 * 2. Verifies the cited quote exists in the original chunk content (exact substring or Levenshtein similarity >= 0.85).
 * 3. Strips phantom/hallucinated citations and penalizes confidenceScore.
 */
@Slf4j
@Service
public class CitationVerifier {

    public static final double MIN_QUOTE_SIMILARITY = 0.85;
    public static final double HALLUCINATION_CONFIDENCE_PENALTY = 0.25;

    @Builder
    public record VerificationResult(
            List<RagCitationDto> verifiedCitations,
            int totalRawCitations,
            int removedHallucinatedCount,
            double adjustedConfidenceScore
    ) {
    }

    public VerificationResult verifyCitations(
            List<RawCitationItem> rawCitations,
            List<ScoredChunk> contextChunks,
            Double initialConfidenceScore
    ) {
        double baseConfidence = initialConfidenceScore != null
                ? Math.max(0.0, Math.min(1.0, initialConfidenceScore))
                : 0.90;

        if (rawCitations == null || rawCitations.isEmpty() || contextChunks == null || contextChunks.isEmpty()) {
            return VerificationResult.builder()
                    .verifiedCitations(List.of())
                    .totalRawCitations(rawCitations != null ? rawCitations.size() : 0)
                    .removedHallucinatedCount(rawCitations != null ? rawCitations.size() : 0)
                    .adjustedConfidenceScore(baseConfidence)
                    .build();
        }

        Map<UUID, ScoredChunk> chunkMap = new LinkedHashMap<>();
        for (ScoredChunk chunk : contextChunks) {
            if (chunk != null && chunk.chunkId() != null) {
                chunkMap.put(chunk.chunkId(), chunk);
            }
        }

        List<RagCitationDto> verified = new ArrayList<>();
        int hallucinatedCount = 0;

        for (RawCitationItem raw : rawCitations) {
            if (raw == null || raw.chunkId() == null) {
                hallucinatedCount++;
                continue;
            }

            ScoredChunk matchedChunk = chunkMap.get(raw.chunkId());
            if (matchedChunk == null) {
                log.warn("CitationVerifier stripped phantom chunkId={} not present in Top-K context", raw.chunkId());
                hallucinatedCount++;
                continue;
            }

            String quote = raw.quote() != null ? raw.quote().trim() : "";
            if (!StringUtils.hasText(quote)) {
                log.warn("CitationVerifier stripped empty quote for chunkId={}", raw.chunkId());
                hallucinatedCount++;
                continue;
            }

            if (!isQuoteGroundedInChunk(quote, matchedChunk.chunkContent())) {
                log.warn("CitationVerifier stripped ungrounded quote for chunkId={}: '{}'", raw.chunkId(), quote);
                hallucinatedCount++;
                continue;
            }

            verified.add(RagCitationDto.builder()
                    .chunkId(matchedChunk.chunkId())
                    .sourceType(matchedChunk.sourceType())
                    .sourceRefId(matchedChunk.sourceRefId())
                    .articleSlug(StringUtils.hasText(raw.articleSlug()) ? raw.articleSlug() : matchedChunk.articleSlug())
                    .chunkTitle(matchedChunk.chunkTitle())
                    .quote(quote)
                    .similarityScore(matchedChunk.similarityScore())
                    .rerankScore(matchedChunk.rerankScore())
                    .build());
        }

        double adjustedConfidence = Math.max(
                0.10,
                baseConfidence - (hallucinatedCount * HALLUCINATION_CONFIDENCE_PENALTY)
        );
        adjustedConfidence = Math.round(adjustedConfidence * 1000.0) / 1000.0;

        return VerificationResult.builder()
                .verifiedCitations(List.copyOf(verified))
                .totalRawCitations(rawCitations.size())
                .removedHallucinatedCount(hallucinatedCount)
                .adjustedConfidenceScore(adjustedConfidence)
                .build();
    }

    public boolean isQuoteGroundedInChunk(String quote, String chunkContent) {
        if (!StringUtils.hasText(quote) || !StringUtils.hasText(chunkContent)) {
            return false;
        }
        String normQuote = normalizeForComparison(quote);
        String normContent = normalizeForComparison(chunkContent);

        if (normContent.contains(normQuote)) {
            return true;
        }

        double bestSim = computeBestSubstringLevenshteinSimilarity(normQuote, normContent);
        return bestSim >= MIN_QUOTE_SIMILARITY;
    }

    static double computeBestSubstringLevenshteinSimilarity(String needle, String haystack) {
        if (needle.isEmpty() || haystack.isEmpty()) {
            return 0.0;
        }
        if (haystack.length() <= needle.length()) {
            return levenshteinSimilarity(needle, haystack);
        }

        int windowLen = needle.length();
        int step = Math.max(1, windowLen / 10);
        double best = 0.0;

        for (int start = 0; start <= haystack.length() - windowLen; start += step) {
            String sub = haystack.substring(start, start + windowLen);
            double sim = levenshteinSimilarity(needle, sub);
            if (sim > best) {
                best = sim;
                if (best >= MIN_QUOTE_SIMILARITY) {
                    return best;
                }
            }
        }
        String tail = haystack.substring(haystack.length() - windowLen);
        return Math.max(best, levenshteinSimilarity(needle, tail));
    }

    public static double levenshteinSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int dist = levenshteinDistance(s1, s2);
        return 1.0 - ((double) dist / maxLen);
    }

    private static int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = (c1 == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }

    private static String normalizeForComparison(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
