package com.analoghub.platform.search.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.document.ListingSearchDocument;
import com.analoghub.platform.search.dto.ListingSearchCriteria;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.dto.ListingSortBy;
import com.analoghub.platform.search.listener.LabIndexSyncListener;
import com.analoghub.platform.search.listener.ListingIndexSyncListener;
import com.analoghub.platform.search.repository.LabSearchRepository;
import com.analoghub.platform.search.repository.ListingSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private LabSearchRepository labSearchRepository;

    @Mock
    private ListingSearchRepository listingSearchRepository;

    @Mock
    private ObjectProvider<MarketplaceFacade> marketplaceFacadeProvider;

    private ListingSearchService listingSearchService;

    @BeforeEach
    void setUp() {
        listingSearchService = new ListingSearchService(elasticsearchOperations);
    }

    @Test
    void searchListings_withVietnameseQuery_returnsMatchingItemsAndFacets() {
        ListingSearchDocument doc = ListingSearchDocument.builder()
                .listingId("018f4a80-1122-7abc-8000-001122334455")
                .sellerUserId("018f4001-2233-7000-8000-112233445566")
                .slug("combo-nikon-fm2n-black-chrome-lens-nikkor-50mm-f-1-4-ai-s-suu-tam-4455")
                .title("Combo Nikon FM2n Black Chrome + Lens Nikkor 50mm f/1.4 AI-S Sưu Tầm")
                .category("CAMERA_BODY")
                .brand("Nikon")
                .modelName("Nikon FM2n + Nikkor 50mm f/1.4 AI-S")
                .conditionGrade("NEAR_MINT")
                .priceVnd(new BigDecimal("11500000.00"))
                .allowPriceOffer(true)
                .allowSwap(true)
                .preferredSwapItems("Nhận giao lưu Olympus OM-4 Ti Black")
                .provinceCode("79")
                .districtCode("760")
                .status("PUBLISHED")
                .createdAt(Instant.parse("2026-11-01T16:40:00Z"))
                .build();

        SearchHit<ListingSearchDocument> hit = new SearchHit<>(
                ListingSearchDocument.INDEX_NAME,
                doc.getListingId(),
                null,
                14.823f,
                new Object[0],
                Map.of("title", List.of("Combo <em>Nikon</em> <em>FM2n</em> Black Chrome + Lens Nikkor <em>50mm</em> f/1.4 AI-S Sưu Tầm")),
                Map.of(),
                null,
                null,
                null,
                doc
        );

        SearchHitsImpl<ListingSearchDocument> mockHits = new SearchHitsImpl<>(
                1L,
                TotalHitsRelation.EQUAL_TO,
                14.823f,
                Duration.ofMillis(5),
                null,
                null,
                List.of(hit),
                null,
                null,
                null
        );

        when(elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(ListingSearchDocument.class),
                eq(IndexCoordinates.of(ListingSearchDocument.INDEX_NAME))
        )).thenReturn(mockHits);

        ListingSearchCriteria criteria = ListingSearchCriteria.builder()
                .q("nikon fm2n 50mm")
                .category("CAMERA_BODY")
                .brand("Nikon")
                .conditionGrades(List.of("MINT", "NEAR_MINT"))
                .minPriceVnd(new BigDecimal("5000000.00"))
                .maxPriceVnd(new BigDecimal("15000000.00"))
                .allowSwap(true)
                .provinceCode("79")
                .sortBy(ListingSortBy.RELEVANCE)
                .page(0)
                .size(20)
                .build();

        ListingSearchResponse response = listingSearchService.searchListings(criteria);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().listingId()).isEqualTo("018f4a80-1122-7abc-8000-001122334455");
        assertThat(response.items().getFirst().highlightedTitle())
                .contains("<em>Nikon</em>")
                .contains("<em>FM2n</em>");
        assertThat(response.items().getFirst().score()).isEqualTo(14.82f);
        assertThat(response.facets().categoryCounts()).containsEntry("CAMERA_BODY", 1L);
        assertThat(response.facets().conditionGradeCounts()).containsEntry("NEAR_MINT", 1L);
    }

    @Test
    void searchListings_whenMinPriceExceedsMaxPrice_throwsValidationError() {
        ListingSearchCriteria criteria = ListingSearchCriteria.builder()
                .minPriceVnd(new BigDecimal("20000000.00"))
                .maxPriceVnd(new BigDecimal("5000000.00"))
                .build();

        assertThatThrownBy(() -> listingSearchService.searchListings(criteria))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    void indexSyncListeners_whenDomainEventsPublished_syncDocumentsToElasticsearch() {
        LabIndexSyncListener labListener = new LabIndexSyncListener(labSearchRepository);
        UUID labId = UUID.randomUUID();
        when(labSearchRepository.findById(labId.toString())).thenReturn(Optional.empty());

        labListener.onFilmLabCreated(new FilmLabCreatedEvent(labId, "LLab Sài Gòn — Darkroom"));

        ArgumentCaptor<LabSearchDocument> labDocCaptor = ArgumentCaptor.forClass(LabSearchDocument.class);
        verify(labSearchRepository).save(labDocCaptor.capture());
        assertThat(labDocCaptor.getValue().getLabId()).isEqualTo(labId.toString());
        assertThat(labDocCaptor.getValue().getName()).isEqualTo("LLab Sài Gòn — Darkroom");
        assertThat(labDocCaptor.getValue().getSlug()).isEqualTo("llab-sai-gon-darkroom");

        ListingIndexSyncListener listingListener = new ListingIndexSyncListener(
                listingSearchRepository, marketplaceFacadeProvider);
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(marketplaceFacadeProvider.getIfAvailable()).thenReturn(null);
        when(listingSearchRepository.findById(listingId.toString())).thenReturn(Optional.empty());

        listingListener.onListingPublished(new ListingPublishedEvent(
                listingId, sellerId, "Máy ảnh Leica M6 TTL", "CAMERA_BODY", new BigDecimal("55000000"), "PUBLISHED"
        ));

        ArgumentCaptor<ListingSearchDocument> listingDocCaptor = ArgumentCaptor.forClass(ListingSearchDocument.class);
        verify(listingSearchRepository).save(listingDocCaptor.capture());
        assertThat(listingDocCaptor.getValue().getListingId()).isEqualTo(listingId.toString());
        assertThat(listingDocCaptor.getValue().getTitle()).isEqualTo("Máy ảnh Leica M6 TTL");
        assertThat(listingDocCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
    }
}

