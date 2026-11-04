package com.analoghub.platform.search.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.dto.LabAutocompleteResponse;
import com.analoghub.platform.search.dto.LabSearchCriteria;
import com.analoghub.platform.search.dto.LabSearchHitDto;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.LabSortBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    private LabSearchService labSearchService;

    @BeforeEach
    void setUp() {
        labSearchService = new LabSearchService(elasticsearchOperations);
    }

    @Test
    void searchLabs_withGeoCoordinates_returnsLabsOrderedByDistanceKmAscending() {
        LabSearchDocument farLab = LabSearchDocument.builder()
                .labId("018f4105-5566-7000-8000-778899001122")
                .slug("croplab-phu-nhuan")
                .name("CropLab Phú Nhuận")
                .fullAddress("18 Hoa Hồng, Phường 2, Quận Phú Nhuận, TP.HCM")
                .districtCode("768")
                .provinceCode("79")
                .location(new GeoPoint(10.7992, 106.6881))
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2"))
                .scannerModels(List.of("NORITSU_HS1800"))
                .minPriceVnd(new BigDecimal("65000.00"))
                .maxPriceVnd(new BigDecimal("150000.00"))
                .estimatedTurnaroundHours(8)
                .isAcceptingOrders(true)
                .avgRating(4.85f)
                .reviewCount(215)
                .build();

        LabSearchDocument nearLab = LabSearchDocument.builder()
                .labId("018f4100-1122-7000-8000-112233445566")
                .slug("llab-saigon-analog-darkroom")
                .name("LLab Sài Gòn — Analog & Darkroom")
                .fullAddress("42 Tôn Thất Thiệp, Phường Bến Nghé, Quận 1, TP.HCM")
                .districtCode("760")
                .provinceCode("79")
                .location(new GeoPoint(10.7745, 106.7032))
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800", "FRONTIER_SP3000"))
                .minPriceVnd(new BigDecimal("70000.00"))
                .maxPriceVnd(new BigDecimal("180000.00"))
                .estimatedTurnaroundHours(12)
                .isAcceptingOrders(true)
                .avgRating(4.91f)
                .reviewCount(328)
                .build();

        SearchHit<LabSearchDocument> hitFar = new SearchHit<>(
                LabSearchDocument.INDEX_NAME, farLab.getLabId(), null, 1.0f,
                new Object[]{2.8421}, Map.of(), Map.of(), null, null, null, farLab
        );
        SearchHit<LabSearchDocument> hitNear = new SearchHit<>(
                LabSearchDocument.INDEX_NAME, nearLab.getLabId(), null, 1.0f,
                new Object[]{0.3642}, Map.of(), Map.of(), null, null, null, nearLab
        );

        SearchHitsImpl<LabSearchDocument> mockHits = new SearchHitsImpl<>(
                2L,
                TotalHitsRelation.EQUAL_TO,
                1.0f,
                Duration.ofMillis(7),
                null,
                null,
                List.of(hitFar, hitNear),
                null,
                null,
                null
        );

        when(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(LabSearchDocument.class),
                eq(IndexCoordinates.of(LabSearchDocument.INDEX_NAME))
        )).thenReturn(mockHits);

        LabSearchCriteria criteria = LabSearchCriteria.builder()
                .q("trang film noritsu")
                .lat(10.7769)
                .lon(106.7009)
                .radiusKm(10.0)
                .formats(List.of("FMT_135", "FMT_120"))
                .processes(List.of("C41", "ECN2"))
                .scannerModels(List.of("NORITSU_HS1800"))
                .maxPriceVnd(new BigDecimal("120000.00"))
                .maxTurnaroundHours(24)
                .onlyAccepting(true)
                .sortBy(LabSortBy.DISTANCE)
                .page(0)
                .size(10)
                .build();

        LabSearchResponse response = labSearchService.searchLabs(criteria);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).slug()).isEqualTo("llab-saigon-analog-darkroom");
        assertThat(response.items().get(0).distanceKm()).isEqualTo(0.36);
        assertThat(response.items().get(1).slug()).isEqualTo("croplab-phu-nhuan");
        assertThat(response.items().get(1).distanceKm()).isEqualTo(2.84);
        assertThat(response.items().get(0).distanceKm())
                .isLessThan(response.items().get(1).distanceKm());

        assertThat(response.facets().processCounts()).containsEntry("C41", 2L).containsEntry("BW", 1L);
        assertThat(response.facets().formatCounts()).containsEntry("FMT_135", 2L);
        assertThat(response.facets().scannerModelCounts()).containsEntry("NORITSU_HS1800", 2L);
        assertThat(response.tookMs()).isEqualTo(7L);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(
                queryCaptor.capture(),
                eq(LabSearchDocument.class),
                eq(IndexCoordinates.of(LabSearchDocument.INDEX_NAME))
        );
        NativeQuery capturedQuery = queryCaptor.getValue();
        assertThat(capturedQuery.getSortOptions()).isNotEmpty();
        assertThat(capturedQuery.getAggregations()).containsKeys(
                LabSearchService.AGG_PROCESS_COUNTS,
                LabSearchService.AGG_FORMAT_COUNTS,
                LabSearchService.AGG_SCANNER_MODEL_COUNTS
        );
    }

    @Test
    void searchLabs_whenLatProvidedWithoutLon_throwsValidationError() {
        LabSearchCriteria criteria = LabSearchCriteria.builder()
                .lat(10.7769)
                .lon(null)
                .build();

        assertThatThrownBy(() -> labSearchService.searchLabs(criteria))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    void searchLabs_whenSortByDistanceWithoutCoordinates_throwsValidationError() {
        LabSearchCriteria criteria = LabSearchCriteria.builder()
                .sortBy(LabSortBy.DISTANCE)
                .build();

        assertThatThrownBy(() -> labSearchService.searchLabs(criteria))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    void autocompleteLabs_returnsHighlightedSuggestionsAndShortAddress() {
        LabSearchDocument lab = LabSearchDocument.builder()
                .labId("018f4100-1122-7000-8000-112233445566")
                .slug("llab-saigon-analog-darkroom")
                .name("LLab Sài Gòn — Analog & Darkroom")
                .fullAddress("42 Tôn Thất Thiệp, Phường Bến Nghé, Quận 1, TP.HCM")
                .districtCode("760")
                .provinceCode("79")
                .avgRating(4.91f)
                .minPriceVnd(new BigDecimal("70000.00"))
                .isAcceptingOrders(true)
                .build();

        SearchHit<LabSearchDocument> hit = new SearchHit<>(
                LabSearchDocument.INDEX_NAME,
                lab.getLabId(),
                null,
                5.2f,
                new Object[0],
                Map.of("name.autocomplete", List.of("<em>LLab</em> <em>Sài</em> Gòn — Analog & Darkroom")),
                Map.of(),
                null,
                null,
                null,
                lab
        );

        SearchHitsImpl<LabSearchDocument> mockHits = new SearchHitsImpl<>(
                1L,
                TotalHitsRelation.EQUAL_TO,
                5.2f,
                Duration.ofMillis(3),
                null,
                null,
                List.of(hit),
                null,
                null,
                null
        );

        when(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(LabSearchDocument.class),
                eq(IndexCoordinates.of(LabSearchDocument.INDEX_NAME))
        )).thenReturn(mockHits);

        LabAutocompleteResponse response = labSearchService.autocompleteLabs("llab sai", "79", 5);

        assertThat(response.suggestions()).hasSize(1);
        assertThat(response.suggestions().getFirst().highlightedName())
                .isEqualTo("<em>LLab</em> <em>Sài</em> Gòn — Analog & Darkroom");
        assertThat(response.suggestions().getFirst().shortAddress())
                .isEqualTo("Quận 1, TP.HCM");
        assertThat(response.tookMs()).isEqualTo(3L);
    }
}

