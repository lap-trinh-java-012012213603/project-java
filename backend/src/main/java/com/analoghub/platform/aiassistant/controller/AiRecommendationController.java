package com.analoghub.platform.aiassistant.controller;

import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorRequest;
import com.analoghub.platform.aiassistant.dto.FilmStockAdvisorResponse;
import com.analoghub.platform.aiassistant.dto.LabRecommendationResponse;
import com.analoghub.platform.aiassistant.service.recommendation.FilmStockAdvisor;
import com.analoghub.platform.aiassistant.service.recommendation.HybridLabRecommender;
import com.analoghub.platform.aiassistant.service.recommendation.HybridLabRecommender.LabRecommendationQuery;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ai/recommendations")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final HybridLabRecommender hybridLabRecommender;
    private final FilmStockAdvisor filmStockAdvisor;

    /**
     * API 5: GET /api/v1/ai/recommendations/labs
     * Gợi ý Film Lab Cá nhân hóa bằng Hybrid Recommender (4 thành phần: CF + CB + Geo + SLA).
     */
    @GetMapping("/labs")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ai:rec:labs", capacity = 30, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<LabRecommendationResponse>> recommendLabs(
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String filmFormat,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit
    ) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        LabRecommendationResponse response = hybridLabRecommender.recommendLabs(LabRecommendationQuery.builder()
                .userId(currentUserId)
                .lat(lat)
                .lon(lon)
                .processType(processType)
                .filmFormat(filmFormat)
                .limit(limit)
                .build());

        return ResponseEntity.ok(ApiResponse.success(
                "Tạo danh sách gợi ý Film Lab cá nhân hóa thành công.",
                response
        ));
    }

    /**
     * API 6: POST /api/v1/ai/recommendations/film-stocks
     * Tư vấn Chọn Cuộn Film Theo Bối cảnh Chụp.
     */
    @PostMapping("/film-stocks")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(keyPrefix = "ai:rec:film-stocks", capacity = 15, windowSeconds = 60, scope = RateLimitScope.USER)
    public ResponseEntity<ApiResponse<FilmStockAdvisorResponse>> adviseFilmStocks(
            @Valid @RequestBody FilmStockAdvisorRequest request
    ) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        FilmStockAdvisorResponse response = filmStockAdvisor.adviseFilmStocks(currentUserId, request);

        return ResponseEntity.ok(ApiResponse.success(
                "Tư vấn danh sách cuộn film phù hợp thành công.",
                response
        ));
    }
}
