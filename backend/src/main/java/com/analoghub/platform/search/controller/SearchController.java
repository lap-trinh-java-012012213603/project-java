package com.analoghub.platform.search.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.ratelimit.RateLimit;
import com.analoghub.platform.common.ratelimit.RateLimitScope;
import com.analoghub.platform.search.dto.LabAutocompleteResponse;
import com.analoghub.platform.search.dto.LabSearchCriteria;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.ListingSearchCriteria;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.service.LabSearchService;
import com.analoghub.platform.search.service.ListingSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "14. Search (Elasticsearch 8)", description = "Tìm kiếm địa lý Film Lab, lọc đa chiều, gợi ý tức thời & tìm kiếm thiết bị C2C")
public class SearchController {

    private final LabSearchService labSearchService;
    private final ListingSearchService listingSearchService;

    @GetMapping("/labs")
    @Operation(summary = "API 14.1: Tìm kiếm, lọc đa chiều & so sánh Film Lab theo tọa độ địa lý")
    @RateLimit(keyPrefix = "srch:search-labs", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<LabSearchResponse>> searchLabs(
            @Valid @ModelAttribute LabSearchCriteria criteria
    ) {
        LabSearchResponse response = labSearchService.searchLabs(criteria);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm và so sánh Film Lab thành công.", response));
    }

    @GetMapping("/labs/autocomplete")
    @Operation(summary = "API 14.2: Gợi ý nhanh tên Film Lab & khu vực (edge_ngram + icu_folding)")
    @RateLimit(keyPrefix = "srch:autocomplete-labs", capacity = 240, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<LabAutocompleteResponse>> autocompleteLabs(
            @RequestParam("prefix")
            @NotBlank(message = "Từ khóa gợi ý (prefix) không được để trống")
            @Size(min = 2, max = 60, message = "Từ khóa gợi ý (prefix) phải có độ dài tối thiểu 2 ký tự")
            String prefix,

            @RequestParam(value = "provinceCode", required = false)
            @Pattern(regexp = "^[0-9]{2}$", message = "Mã tỉnh/thành phố (provinceCode) phải gồm 2 chữ số")
            String provinceCode,

            @RequestParam(value = "limit", required = false, defaultValue = "8")
            @Min(value = 1, message = "Số lượng gợi ý tối thiểu là 1")
            @Max(value = 15, message = "Số lượng gợi ý tối đa là 15")
            Integer limit
    ) {
        LabAutocompleteResponse response = labSearchService.autocompleteLabs(prefix, provinceCode, limit);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gợi ý Film Lab thành công.", response));
    }

    @GetMapping("/listings")
    @Operation(summary = "API 14.3: Tìm kiếm & lọc thiết bị Chợ C2C tiếng Việt có dấu / không dấu (icu_folding)")
    @RateLimit(keyPrefix = "srch:search-listings", capacity = 120, windowSeconds = 60, scope = RateLimitScope.IP)
    public ResponseEntity<ApiResponse<ListingSearchResponse>> searchListings(
            @Valid @ModelAttribute ListingSearchCriteria criteria
    ) {
        ListingSearchResponse response = listingSearchService.searchListings(criteria);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm thiết bị trên chợ C2C thành công.", response));
    }
}
