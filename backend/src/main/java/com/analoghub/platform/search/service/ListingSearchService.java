package com.analoghub.platform.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.search.document.ListingSearchDocument;
import com.analoghub.platform.search.dto.ListingSearchCriteria;
import com.analoghub.platform.search.dto.ListingSearchFacetsDto;
import com.analoghub.platform.search.dto.ListingSearchHitDto;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.dto.ListingSortBy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSearchService {

    public static final String AGG_CATEGORY_COUNTS = "category_counts";
    public static final String AGG_CONDITION_GRADE_COUNTS = "condition_grade_counts";

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "CAMERA_BODY", "LENS", "FILM_ROLL", "FLASH_ACCESSORY", "DARKROOM_GEAR"
    );

    private static final Set<String> ALLOWED_CONDITION_GRADES = Set.of(
            "MINT", "NEAR_MINT", "EXCELLENT", "USER_GOOD", "FOR_PARTS"
    );

    private final ElasticsearchOperations elasticsearchOperations;

    public ListingSearchResponse searchListings(ListingSearchCriteria criteria) {
        long startNanos = System.nanoTime();
        validateCriteria(criteria);

        boolean hasQuery = StringUtils.hasText(criteria.getQ());
        ListingSortBy effectiveSort = criteria.getSortBy() != null
                ? criteria.getSortBy()
                : (hasQuery ? ListingSortBy.RELEVANCE : ListingSortBy.NEWEST);

        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? criteria.getSize() : 20;

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if (hasQuery) {
            String keyword = criteria.getQ().trim();
            boolBuilder.must(MultiMatchQuery.of(m -> m
                    .query(keyword)
                    .fields(
                            "title^3.0",
                            "modelName^2.5",
                            "brand^2.0",
                            "description^1.0",
                            "preferredSwapItems^1.2"
                    )
                    .type(TextQueryType.BestFields)
                    .analyzer("vi_folded_analyzer")
                    .fuzziness("AUTO")
            )._toQuery());
        }

        boolBuilder.filter(TermQuery.of(t -> t.field("status").value("PUBLISHED"))._toQuery());

        if (StringUtils.hasText(criteria.getCategory())) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("category")
                    .value(criteria.getCategory().trim().toUpperCase(Locale.ROOT))
            )._toQuery());
        }

        if (StringUtils.hasText(criteria.getBrand())) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("brand.normalized")
                    .value(criteria.getBrand().trim().toLowerCase(Locale.ROOT))
            )._toQuery());
        }

        if (criteria.getConditionGrades() != null && !criteria.getConditionGrades().isEmpty()) {
            List<FieldValue> gradeValues = criteria.getConditionGrades().stream()
                    .map(g -> FieldValue.of(g.trim().toUpperCase(Locale.ROOT)))
                    .toList();
            boolBuilder.filter(TermsQuery.of(t -> t
                    .field("conditionGrade")
                    .terms(ts -> ts.value(gradeValues))
            )._toQuery());
        }

        if (criteria.getMinPriceVnd() != null || criteria.getMaxPriceVnd() != null) {
            boolBuilder.filter(RangeQuery.of(r -> r.number(n -> {
                n.field("priceVnd");
                if (criteria.getMinPriceVnd() != null) {
                    n.gte(criteria.getMinPriceVnd().doubleValue());
                }
                if (criteria.getMaxPriceVnd() != null) {
                    n.lte(criteria.getMaxPriceVnd().doubleValue());
                }
                return n;
            }))._toQuery());
        }

        if (criteria.getAllowSwap() != null) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("allowSwap")
                    .value(criteria.getAllowSwap())
            )._toQuery());
        }

        if (criteria.getAllowPriceOffer() != null) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("allowPriceOffer")
                    .value(criteria.getAllowPriceOffer())
            )._toQuery());
        }

        if (StringUtils.hasText(criteria.getProvinceCode())) {
            boolBuilder.filter(TermQuery.of(t -> t
                    .field("provinceCode")
                    .value(criteria.getProvinceCode().trim())
            )._toQuery());
        }

        Highlight highlight = new Highlight(
                HighlightParameters.builder()
                        .withPreTags("<em>")
                        .withPostTags("</em>")
                        .build(),
                List.of(new HighlightField("title"))
        );

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(PageRequest.of(page, size))
                .withHighlightQuery(new HighlightQuery(highlight, ListingSearchDocument.class))
                .withSort(buildSortOptions(effectiveSort))
                .withAggregation(AGG_CATEGORY_COUNTS, Aggregation.of(a -> a
                        .terms(t -> t.field("category").size(10))))
                .withAggregation(AGG_CONDITION_GRADE_COUNTS, Aggregation.of(a -> a
                        .terms(t -> t.field("conditionGrade").size(10))));

        SearchHits<ListingSearchDocument> searchHits = executeSearch(queryBuilder.build());

        String rawQuery = hasQuery ? criteria.getQ().trim() : null;
        List<ListingSearchHitDto> items = searchHits.getSearchHits().stream()
                .map(hit -> mapToListingSearchHitDto(hit, rawQuery))
                .toList();

        ListingSearchFacetsDto facets = extractListingFacets(searchHits.getAggregations(), items);
        long totalElements = searchHits.getTotalHits();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        long tookMs = resolveTookMs(searchHits.getExecutionDuration(), startNanos);

        return ListingSearchResponse.builder()
                .items(items)
                .facets(facets)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .tookMs(tookMs)
                .build();
    }

    private void validateCriteria(ListingSearchCriteria criteria) {
        if (criteria == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số tìm kiếm không được rỗng.");
        }

        if (criteria.getMinPriceVnd() != null && criteria.getMaxPriceVnd() != null
                && criteria.getMinPriceVnd().compareTo(criteria.getMaxPriceVnd()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Giá tối thiểu (minPriceVnd) không được lớn hơn giá tối đa (maxPriceVnd).");
        }

        if (StringUtils.hasText(criteria.getCategory())
                && !ALLOWED_CATEGORIES.contains(criteria.getCategory().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Danh mục thiết bị (category) không hợp lệ: " + criteria.getCategory());
        }

        if (criteria.getConditionGrades() != null) {
            for (String grade : criteria.getConditionGrades()) {
                if (!StringUtils.hasText(grade)
                        || !ALLOWED_CONDITION_GRADES.contains(grade.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Phân hạng ngoại hình (conditionGrade) không hợp lệ: " + grade);
                }
            }
        }
    }

    private List<SortOptions> buildSortOptions(ListingSortBy sortBy) {
        return switch (sortBy) {
            case RELEVANCE -> List.of(
                    SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );
            case NEWEST -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );
            case PRICE_ASC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("priceVnd").order(SortOrder.Asc))),
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );
            case PRICE_DESC -> List.of(
                    SortOptions.of(s -> s.field(f -> f.field("priceVnd").order(SortOrder.Desc))),
                    SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );
        };
    }

    private SearchHits<ListingSearchDocument> executeSearch(NativeQuery query) {
        try {
            return elasticsearchOperations.search(
                    query,
                    ListingSearchDocument.class,
                    IndexCoordinates.of(ListingSearchDocument.INDEX_NAME)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Elasticsearch query on '{}' failed: {}", ListingSearchDocument.INDEX_NAME, ex.getMessage(), ex);
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Cụm tìm kiếm Elasticsearch tạm thời không phản hồi. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private ListingSearchHitDto mapToListingSearchHitDto(SearchHit<ListingSearchDocument> hit, String rawQuery) {
        ListingSearchDocument doc = hit.getContent();
        List<String> titleHighlights = hit.getHighlightField("title");
        String highlightedTitle = (titleHighlights != null && !titleHighlights.isEmpty())
                ? titleHighlights.getFirst()
                : (rawQuery != null ? LabSearchService.buildFallbackHighlight(doc.getTitle(), rawQuery) : doc.getTitle());

        Float roundedScore = !Float.isNaN(hit.getScore())
                ? BigDecimal.valueOf(hit.getScore()).setScale(2, RoundingMode.HALF_UP).floatValue()
                : null;

        return ListingSearchHitDto.builder()
                .listingId(doc.getListingId())
                .sellerUserId(doc.getSellerUserId())
                .slug(doc.getSlug())
                .title(doc.getTitle())
                .highlightedTitle(highlightedTitle)
                .category(doc.getCategory())
                .brand(doc.getBrand())
                .modelName(doc.getModelName())
                .conditionGrade(doc.getConditionGrade())
                .priceVnd(doc.getPriceVnd())
                .allowPriceOffer(doc.getAllowPriceOffer())
                .allowSwap(doc.getAllowSwap())
                .preferredSwapItems(doc.getPreferredSwapItems())
                .thumbnailUrl(doc.getThumbnailUrl())
                .provinceCode(doc.getProvinceCode())
                .districtCode(doc.getDistrictCode())
                .status(doc.getStatus())
                .score(roundedScore)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private ListingSearchFacetsDto extractListingFacets(
            AggregationsContainer<?> aggregationsContainer,
            List<ListingSearchHitDto> fallbackItems
    ) {
        if (aggregationsContainer instanceof ElasticsearchAggregations esAggs) {
            return ListingSearchFacetsDto.builder()
                    .categoryCounts(extractStringTermsBuckets(esAggs, AGG_CATEGORY_COUNTS))
                    .conditionGradeCounts(extractStringTermsBuckets(esAggs, AGG_CONDITION_GRADE_COUNTS))
                    .build();
        }

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        Map<String, Long> conditionGradeCounts = new LinkedHashMap<>();

        for (ListingSearchHitDto item : fallbackItems) {
            if (item.category() != null) {
                categoryCounts.merge(item.category(), 1L, Long::sum);
            }
            if (item.conditionGrade() != null) {
                conditionGradeCounts.merge(item.conditionGrade(), 1L, Long::sum);
            }
        }

        return ListingSearchFacetsDto.builder()
                .categoryCounts(categoryCounts)
                .conditionGradeCounts(conditionGradeCounts)
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

    private static long resolveTookMs(Duration executionDuration, long startNanos) {
        if (executionDuration != null && !executionDuration.isZero() && !executionDuration.isNegative()) {
            return executionDuration.toMillis();
        }
        return Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}

