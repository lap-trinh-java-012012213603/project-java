package com.analoghub.platform.search.service;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoDistanceQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.dto.GeoCoordinateDto;
import com.analoghub.platform.search.dto.LabAutocompleteResponse;
import com.analoghub.platform.search.dto.LabAutocompleteSuggestionDto;
import com.analoghub.platform.search.dto.LabSearchCriteria;
import com.analoghub.platform.search.dto.LabSearchFacetsDto;
import com.analoghub.platform.search.dto.LabSearchHitDto;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.LabSortBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabSearchService {

    public static final String AGG_PROCESS_COUNTS = "process_counts";
    public static final String AGG_FORMAT_COUNTS = "format_counts";
    public static final String AGG_SCANNER_MODEL_COUNTS = "scanner_model_counts";

    private static final double DEFAULT_RADIUS_KM = 15.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static final Set<String> ALLOWED_FORMATS = Set.of("FMT_135", "FMT_120", "FMT_SHEET");
    private static final Set<String> ALLOWED_PROCESSES = Set.of("C41", "ECN2", "BW", "E6");
    private static final Set<String> ALLOWED_SCANNER_MODELS = Set.of("NORITSU_HS1800", "FRONTIER_SP3000", "DSLR");

    private final ElasticsearchOperations elasticsearchOperations;

    public LabSearchResponse searchLabs(LabSearchCriteria criteria) {
        long startNanos = System.nanoTime();
        validateSearchCriteria(criteria);

        boolean hasGeo = criteria.getLat() != null && criteria.getLon() != null;
        LabSortBy effectiveSort = criteria.getSortBy() != null
                ? criteria.getSortBy()
                : (hasGeo ? LabSortBy.DISTANCE : LabSortBy.RATING);

        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? criteria.getSize() : 20;
        boolean onlyAccepting = criteria.getOnlyAccepting() == null || criteria.getOnlyAccepting();

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (StringUtils.hasText(criteria.getQ())) {
            String keyword = criteria.getQ().trim();
            boolBuilder.must(MultiMatchQuery.of(m -> m
                    .query(keyword)
                    .fields("name^3.0", "bio^1.2", "fullAddress^1.5")
                    .analyzer("vi_folded_analyzer")
                    .fuzziness("AUTO")
            )._toQuery());
        }

        boolBuilder.filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

        if (onlyAccepting) {
            boolBuilder.filter(TermQuery.of(t -> t.field("isAcceptingOrders").value(true))._toQuery());
        }

        if (hasGeo) {
            double radiusKm = criteria.getRadiusKm() != null ? criteria.getRadiusKm() : DEFAULT_RADIUS_KM;
            boolBuilder.filter(GeoDistanceQuery.of(g -> g
                    .field("location")
                    .distance(radiusKm + "km")
                    .location(loc -> loc.latlon(ll -> ll.lat(criteria.getLat()).lon(criteria.getLon())))
            )._toQuery());
        }

        if (StringUtils.hasText(criteria.getProvinceCode())) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("provinceCode")
                    .value(criteria.getProvinceCode().trim())
            )._toQuery());
        }

        if (StringUtils.hasText(criteria.getDistrictCode())) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("districtCode")
                    .value(criteria.getDistrictCode().trim())
            )._toQuery());
        }

        if (criteria.getFormats() != null && !criteria.getFormats().isEmpty()) {
            List<FieldValue> values = criteria.getFormats().stream()
                    .map(f -> FieldValue.of(f.trim().toUpperCase(Locale.ROOT)))
                    .toList();
            boolBuilder.filter(TermsQuery.of(t -> t
                    .field("supportedFormats")
                    .terms(ts -> ts.value(values))
            )._toQuery());
        }

        if (criteria.getProcesses() != null && !criteria.getProcesses().isEmpty()) {
            List<FieldValue> values = criteria.getProcesses().stream()
                    .map(p -> FieldValue.of(p.trim().toUpperCase(Locale.ROOT)))
                    .toList();
            boolBuilder.filter(TermsQuery.of(t -> t
                    .field("supportedProcesses")
                    .terms(ts -> ts.value(values))
            )._toQuery());
        }

        if (criteria.getScannerModels() != null && !criteria.getScannerModels().isEmpty()) {
            List<FieldValue> values = criteria.getScannerModels().stream()
                    .map(s -> FieldValue.of(s.trim().toUpperCase(Locale.ROOT)))
                    .toList();
            boolBuilder.filter(TermsQuery.of(t -> t
                    .field("scannerModels")
                    .terms(ts -> ts.value(values))
            )._toQuery());
        }

        if (criteria.getMaxPriceVnd() != null) {
            double maxPrice = criteria.getMaxPriceVnd().doubleValue();
            boolBuilder.filter(RangeQuery.of(r -> r
                    .number(n -> n.field("minPriceVnd").lte(maxPrice))
            )._toQuery());
        }

        if (criteria.getMaxTurnaroundHours() != null) {
            double maxHours = criteria.getMaxTurnaroundHours().doubleValue();
            boolBuilder.filter(RangeQuery.of(r -> r
                    .number(n -> n.field("estimatedTurnaroundHours").lte(maxHours))
            )._toQuery());
        }

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(page, size))
                .withSort(buildSortOptions(effectiveSort, criteria.getLat(), criteria.getLon()))
                .withAggregation(AGG_PROCESS_COUNTS, Aggregation.of(a -> a
                        .terms(t -> t.field("supportedProcesses").size(10))))
                .withAggregation(AGG_FORMAT_COUNTS, Aggregation.of(a -> a
                        .terms(t -> t.field("supportedFormats").size(10))))
                .withAggregation(AGG_SCANNER_MODEL_COUNTS, Aggregation.of(a -> a
                        .terms(t -> t.field("scannerModels").size(10))));

        SearchHits<LabSearchDocument> searchHits = executeSearch(queryBuilder.build());

        List<LabSearchHitDto> items = new ArrayList<>(searchHits.getSearchHits().stream()
                .map(hit -> mapToLabSearchHitDto(hit, criteria.getLat(), criteria.getLon(), effectiveSort))
                .toList());

        if (effectiveSort == LabSortBy.DISTANCE && items.size() > 1) {
            items.sort(Comparator.comparing(
                    LabSearchHitDto::distanceKm,
                    Comparator.nullsLast(Double::compareTo)
            ));
        }

        LabSearchFacetsDto facets = extractLabFacets(searchHits.getAggregations(), items);
        long totalElements = searchHits.getTotalHits();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        long tookMs = resolveTookMs(searchHits.getExecutionDuration(), startNanos);

        return LabSearchResponse.builder()
                .items(items)
                .facets(facets)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .tookMs(tookMs)
                .build();
    }

    public LabAutocompleteResponse autocompleteLabs(String prefix, String provinceCode, Integer limit) {
        long startNanos = System.nanoTime();
        if (!StringUtils.hasText(prefix) || prefix.trim().length() < 2 || prefix.trim().length() > 60) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Từ khóa gợi ý (prefix) phải có độ dài từ 2 đến 60 ký tự.");
        }
        int effectiveLimit = limit != null ? limit : 8;
        if (effectiveLimit < 1 || effectiveLimit > 15) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Số lượng gợi ý (limit) phải nằm trong khoảng từ 1 đến 15.");
        }
        if (StringUtils.hasText(provinceCode) && !provinceCode.trim().matches("^[0-9]{2}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Mã tỉnh/thành phố (provinceCode) phải gồm 2 chữ số.");
        }

        String normalizedPrefix = prefix.trim();
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                .must(MatchQuery.of(m -> m
                        .field("name.autocomplete")
                        .query(normalizedPrefix)
                        .analyzer("vi_folded_analyzer")
                )._toQuery())
                .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

        if (StringUtils.hasText(provinceCode)) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("provinceCode")
                    .value(provinceCode.trim())
            )._toQuery());
        }

        Highlight highlight = new Highlight(
                HighlightParameters.builder()
                        .withPreTags("<em>")
                        .withPostTags("</em>")
                        .build(),
                List.of(new HighlightField("name.autocomplete"))
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(0, effectiveLimit))
                .withHighlightQuery(new HighlightQuery(highlight, LabSearchDocument.class))
                .withSourceFilter(new FetchSourceFilter(
                        true,
                        new String[]{
                                "labId", "slug", "name", "fullAddress",
                                "districtCode", "provinceCode", "avgRating",
                                "minPriceVnd", "isAcceptingOrders"
                        },
                        null
                ))
                .withSort(List.of(
                        SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                        SortOptions.of(s -> s.field(f -> f.field("avgRating").order(SortOrder.Desc)))
                ))
                .build();

        SearchHits<LabSearchDocument> searchHits = executeSearch(nativeQuery);

        List<LabAutocompleteSuggestionDto> suggestions = searchHits.getSearchHits().stream()
                .map(hit -> mapToAutocompleteSuggestion(hit, normalizedPrefix))
                .toList();

        long tookMs = resolveTookMs(searchHits.getExecutionDuration(), startNanos);

        return LabAutocompleteResponse.builder()
                .suggestions(suggestions)
                .tookMs(tookMs)
                .build();
    }

    private void validateSearchCriteria(LabSearchCriteria criteria) {
        if (criteria == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số tìm kiếm không được rỗng.");
        }

        boolean hasLat = criteria.getLat() != null;
        boolean hasLon = criteria.getLon() != null;
        if (hasLat != hasLon) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Để tìm kiếm theo vị trí địa lý, bạn bắt buộc phải cung cấp đầy đủ tọa độ 'lat' và 'lon'.");
        }

        if (criteria.getSortBy() == LabSortBy.DISTANCE && !hasLat) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Để sắp xếp theo khoảng cách địa lý (sortBy=DISTANCE), bạn bắt buộc phải cung cấp đầy đủ tọa độ 'lat' và 'lon'.");
        }

        if (hasLat && (criteria.getLat() < -90.0 || criteria.getLat() > 90.0
                || criteria.getLon() < -180.0 || criteria.getLon() > 180.0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tọa độ GPS (lat, lon) nằm ngoài phạm vi hợp lệ.");
        }

        if (criteria.getRadiusKm() != null && (criteria.getRadiusKm() < 0.5 || criteria.getRadiusKm() > 200.0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Bán kính tìm kiếm (radiusKm) phải nằm trong khoảng từ 0.5 đến 200.0 km.");
        }

        if (criteria.getFormats() != null) {
            for (String format : criteria.getFormats()) {
                if (!StringUtils.hasText(format)
                        || !ALLOWED_FORMATS.contains(format.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Định dạng film không hợp lệ: " + format);
                }
            }
        }

        if (criteria.getProcesses() != null) {
            for (String process : criteria.getProcesses()) {
                if (!StringUtils.hasText(process)
                        || !ALLOWED_PROCESSES.contains(process.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Quy trình tráng film không hợp lệ: " + process);
                }
            }
        }

        if (criteria.getScannerModels() != null) {
            for (String scannerModel : criteria.getScannerModels()) {
                if (!StringUtils.hasText(scannerModel)
                        || !ALLOWED_SCANNER_MODELS.contains(scannerModel.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Dòng máy scan không hợp lệ: " + scannerModel);
                }
            }
        }
    }

    private List<SortOptions> buildSortOptions(LabSortBy sortBy, Double lat, Double lon) {
        return switch (sortBy) {
            case DISTANCE -> List.of(
                    SortOptions.of(s -> s.geoDistance(g -> g
                            .field("location")
                            .location(loc -> loc.latlon(ll -> ll.lat(lat).lon(lon)))
                            .unit(DistanceUnit.Kilometers)
                            .order(SortOrder.Asc)
                    )),
                    SortOptions.of(s -> s.field(f -> f.field("avgRating").order(SortOrder.Desc)))
            );
            case RATING -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("avgRating").order(SortOrder.Desc))),
                    SortOptions.of(s -> s.field(f -> f.field("reviewCount").order(SortOrder.Desc)))
            );
            case PRICE_ASC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("minPriceVnd").order(SortOrder.Asc))),
                    SortOptions.of(s -> s.field(f -> f.field("avgRating").order(SortOrder.Desc)))
            );
            case TURNAROUND_ASC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("estimatedTurnaroundHours").order(SortOrder.Asc))),
                    SortOptions.of(s -> s.field(f -> f.field("avgRating").order(SortOrder.Desc)))
            );
        };
    }

    private SearchHits<LabSearchDocument> executeSearch(NativeQuery query) {
        try {
            return elasticsearchOperations.search(
                    query,
                    LabSearchDocument.class,
                    IndexCoordinates.of(LabSearchDocument.INDEX_NAME)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Elasticsearch query on '{}' failed: {}", LabSearchDocument.INDEX_NAME, ex.getMessage(), ex);
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Cụm tìm kiếm Elasticsearch tạm thời không phản hồi. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private LabSearchHitDto mapToLabSearchHitDto(
            SearchHit<LabSearchDocument> hit,
            Double userLat,
            Double userLon,
            LabSortBy effectiveSort
    ) {
        LabSearchDocument doc = hit.getContent();
        GeoPoint geoPoint = doc.getLocation();
        GeoCoordinateDto locationDto = geoPoint != null
                ? new GeoCoordinateDto(geoPoint.getLat(), geoPoint.getLon())
                : null;

        Double distanceKm = resolveDistanceKm(hit, geoPoint, userLat, userLon, effectiveSort);

        return LabSearchHitDto.builder()
                .labId(doc.getLabId())
                .slug(doc.getSlug())
                .name(doc.getName())
                .bio(doc.getBio())
                .fullAddress(doc.getFullAddress())
                .districtCode(doc.getDistrictCode())
                .provinceCode(doc.getProvinceCode())
                .location(locationDto)
                .distanceKm(distanceKm)
                .coverImageUrl(doc.getCoverImageUrl())
                .supportedFormats(doc.getSupportedFormats() != null ? doc.getSupportedFormats() : List.of())
                .supportedProcesses(doc.getSupportedProcesses() != null ? doc.getSupportedProcesses() : List.of())
                .scannerModels(doc.getScannerModels() != null ? doc.getScannerModels() : List.of())
                .minPriceVnd(doc.getMinPriceVnd())
                .maxPriceVnd(doc.getMaxPriceVnd())
                .estimatedTurnaroundHours(doc.getEstimatedTurnaroundHours())
                .isAcceptingOrders(doc.getIsAcceptingOrders())
                .avgRating(doc.getAvgRating())
                .reviewCount(doc.getReviewCount())
                .build();
    }

    private Double resolveDistanceKm(
            SearchHit<LabSearchDocument> hit,
            GeoPoint labLocation,
            Double userLat,
            Double userLon,
            LabSortBy effectiveSort
    ) {
        if (userLat == null || userLon == null) {
            return null;
        }

        if (effectiveSort == LabSortBy.DISTANCE && hit.getSortValues() != null && !hit.getSortValues().isEmpty()) {
            Object firstSortVal = hit.getSortValues().getFirst();
            if (firstSortVal instanceof Number number && Double.isFinite(number.doubleValue())) {
                return roundTwoDecimals(number.doubleValue());
            }
        }

        if (labLocation != null) {
            double haversineKm = calculateHaversineKm(userLat, userLon, labLocation.getLat(), labLocation.getLon());
            return roundTwoDecimals(haversineKm);
        }

        return null;
    }

    public static double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private LabSearchFacetsDto extractLabFacets(
            AggregationsContainer<?> aggregationsContainer,
            List<LabSearchHitDto> fallbackItems
    ) {
        if (aggregationsContainer instanceof ElasticsearchAggregations esAggs) {
            Map<String, Long> processCounts = extractStringTermsBuckets(esAggs, AGG_PROCESS_COUNTS);
            Map<String, Long> formatCounts = extractStringTermsBuckets(esAggs, AGG_FORMAT_COUNTS);
            Map<String, Long> scannerModelCounts = extractStringTermsBuckets(esAggs, AGG_SCANNER_MODEL_COUNTS);
            return LabSearchFacetsDto.builder()
                    .processCounts(processCounts)
                    .formatCounts(formatCounts)
                    .scannerModelCounts(scannerModelCounts)
                    .build();
        }

        Map<String, Long> processCounts = new LinkedHashMap<>();
        Map<String, Long> formatCounts = new LinkedHashMap<>();
        Map<String, Long> scannerModelCounts = new LinkedHashMap<>();

        for (LabSearchHitDto item : fallbackItems) {
            if (item.supportedProcesses() != null) {
                for (String p : item.supportedProcesses()) {
                    processCounts.merge(p, 1L, Long::sum);
                }
            }
            if (item.supportedFormats() != null) {
                for (String f : item.supportedFormats()) {
                    formatCounts.merge(f, 1L, Long::sum);
                }
            }
            if (item.scannerModels() != null) {
                for (String s : item.scannerModels()) {
                    scannerModelCounts.merge(s, 1L, Long::sum);
                }
            }
        }

        return LabSearchFacetsDto.builder()
                .processCounts(processCounts)
                .formatCounts(formatCounts)
                .scannerModelCounts(scannerModelCounts)
                .build();
    }

    private Map<String, Long> extractStringTermsBuckets(ElasticsearchAggregations esAggs, String aggregationName) {
        Map<String, Long> result = new LinkedHashMap<>();
        ElasticsearchAggregation agg = esAggs.get(aggregationName);
        if (agg != null && agg.aggregation() != null && agg.aggregation().getAggregate().isSterms()) {
            List<StringTermsBucket> buckets = agg.aggregation().getAggregate().sterms().buckets().array();
            for (StringTermsBucket bucket : buckets) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
        }
        return result;
    }

    private LabAutocompleteSuggestionDto mapToAutocompleteSuggestion(
            SearchHit<LabSearchDocument> hit,
            String prefix
    ) {
        LabSearchDocument doc = hit.getContent();
        List<String> highlights = hit.getHighlightField("name.autocomplete");
        String highlightedName = (highlights != null && !highlights.isEmpty())
                ? highlights.getFirst()
                : buildFallbackHighlight(doc.getName(), prefix);

        return LabAutocompleteSuggestionDto.builder()
                .labId(doc.getLabId())
                .slug(doc.getSlug())
                .name(doc.getName())
                .highlightedName(highlightedName)
                .shortAddress(extractShortAddress(doc.getFullAddress()))
                .avgRating(doc.getAvgRating())
                .minPriceVnd(doc.getMinPriceVnd())
                .isAcceptingOrders(doc.getIsAcceptingOrders())
                .build();
    }

    static String buildFallbackHighlight(String name, String prefix) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(prefix)) {
            return name;
        }
        String result = name;
        String[] tokens = prefix.trim().split("\\s+");
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            String foldedToken = foldVietnamese(token);
            String[] words = result.split("(?<=\\s)|(?=\\s)");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.startsWith("<em>") && foldVietnamese(word).startsWith(foldedToken)) {
                    sb.append("<em>").append(word).append("</em>");
                } else {
                    sb.append(word);
                }
            }
            result = sb.toString();
        }
        return result;
    }

    private static String foldVietnamese(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    static String extractShortAddress(String fullAddress) {
        if (!StringUtils.hasText(fullAddress)) {
            return "";
        }
        String[] parts = fullAddress.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
        }
        return fullAddress.trim();
    }

    private static long resolveTookMs(Duration executionDuration, long startNanos) {
        if (executionDuration != null && !executionDuration.isZero() && !executionDuration.isNegative()) {
            return executionDuration.toMillis();
        }
        return Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}

