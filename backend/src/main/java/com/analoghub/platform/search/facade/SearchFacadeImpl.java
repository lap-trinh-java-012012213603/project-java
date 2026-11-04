package com.analoghub.platform.search.facade;

import com.analoghub.platform.search.document.LabSearchDocument;
import com.analoghub.platform.search.dto.LabSearchCriteria;
import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.LabSortBy;
import com.analoghub.platform.search.dto.ListingSearchCriteria;
import com.analoghub.platform.search.dto.ListingSearchResponse;
import com.analoghub.platform.search.dto.ListingSortBy;
import com.analoghub.platform.search.listener.ListingIndexSyncListener;
import com.analoghub.platform.search.repository.LabSearchRepository;
import com.analoghub.platform.search.repository.ListingSearchRepository;
import com.analoghub.platform.search.service.LabSearchService;
import com.analoghub.platform.search.service.ListingSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFacadeImpl implements SearchFacade {

    private final LabSearchRepository labSearchRepository;
    private final ListingSearchRepository listingSearchRepository;
    private final LabSearchService labSearchService;
    private final ListingSearchService listingSearchService;
    private final ListingIndexSyncListener listingIndexSyncListener;

    @Override
    public void indexFilmLab(UUID labId) {
        if (labId == null) {
            return;
        }
        String labIdStr = labId.toString();
        LabSearchDocument doc = labSearchRepository.findById(labIdStr)
                .orElseGet(() -> LabSearchDocument.builder()
                        .labId(labIdStr)
                        .status("ACTIVE")
                        .isAcceptingOrders(true)
                        .avgRating(5.0f)
                        .reviewCount(0)
                        .build());
        doc.setUpdatedAt(Instant.now());
        labSearchRepository.save(doc);
        log.info("Indexed FilmLab via SearchFacade: labId={}", labIdStr);
    }

    @Override
    public void indexMarketplaceListing(UUID listingId) {
        listingIndexSyncListener.syncByListingId(listingId);
    }

    @Override
    public void removeMarketplaceListing(UUID listingId) {
        if (listingId == null) {
            return;
        }
        listingSearchRepository.deleteById(listingId.toString());
        log.info("Removed MarketplaceListing from Elasticsearch via SearchFacade: listingId={}", listingId);
    }

    @Override
    public LabSearchResponse searchLabsNearby(double lat, double lon, double radiusKm) {
        LabSearchCriteria criteria = LabSearchCriteria.builder()
                .lat(lat)
                .lon(lon)
                .radiusKm(radiusKm)
                .sortBy(LabSortBy.DISTANCE)
                .onlyAccepting(true)
                .page(0)
                .size(20)
                .build();
        return labSearchService.searchLabs(criteria);
    }

    @Override
    public ListingSearchResponse searchListings(String keyword, String category) {
        ListingSearchCriteria criteria = ListingSearchCriteria.builder()
                .q(keyword)
                .category(category)
                .sortBy(keyword != null && !keyword.isBlank() ? ListingSortBy.RELEVANCE : ListingSortBy.NEWEST)
                .page(0)
                .size(20)
                .build();
        return listingSearchService.searchListings(criteria);
    }
}

