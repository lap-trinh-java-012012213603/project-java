package com.analoghub.platform.aiassistant.controller;

import com.analoghub.platform.aiassistant.dto.HybridSearchResponse;
import com.analoghub.platform.aiassistant.dto.HybridSearchScope;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService;
import com.analoghub.platform.aiassistant.service.search.SemanticSearchService.HybridSearchQuery;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/search")
@RequiredArgsConstructor
public class AiSemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    /**
     * API 7: GET /api/v1/ai/search/hybrid
     * Tìm kiếm Ngữ nghĩa Lai — Hybrid Search RRF (Elasticsearch BM25 + pgvector HNSW, k = 60).
     */
    @GetMapping("/hybrid")
    @PreAuthorize("permitAll()")
    @RateLimit(keyPrefix = "ai:search:hybrid", capacity = 45, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<HybridSearchResponse>> hybridSearch(
            @RequestParam("q") @NotBlank @Size(min = 2, max = 300) String query,
            @RequestParam(defaultValue = "ALL") HybridSearchScope scope,
            @RequestParam(required = false) @Size(max = 20) String provinceCode,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "60") @Min(10) @Max(120) int rrfK,
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit
    ) {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId().orElse(null);

        HybridSearchResponse response = semanticSearchService.hybridSearch(HybridSearchQuery.builder()
                .userId(currentUserId)
                .query(query)
                .scope(scope)
                .provinceCode(provinceCode)
                .lat(lat)
                .lon(lon)
                .rrfK(rrfK)
                .limit(limit)
                .build());

        return ResponseEntity.ok(ApiResponse.success(
                "Thực thi tìm kiếm ngữ nghĩa lai (Elasticsearch BM25 + pgvector RRF) thành công.",
                response
        ));
    }
}
