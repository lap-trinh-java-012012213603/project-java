package com.analoghub.platform.search.facade;

import com.analoghub.platform.search.dto.LabSearchResponse;
import com.analoghub.platform.search.dto.ListingSearchResponse;

import java.util.List;
import java.util.UUID;

public interface SearchFacade {
    // Elasticsearch Sync Methods
    void indexFilmLab(UUID labId);
    void indexMarketplaceListing(UUID listingId);
    void removeMarketplaceListing(UUID listingId);

    // Search Methods
    Object searchLabsNearby(double lat, double lon, double radiusKm);
    Object searchListings(String keyword, String category);

    default LabSearchResponse searchLabsLexical(String query, String provinceCode, Double lat, Double lon, int limit) {
        return LabSearchResponse.builder()
                .items(List.of())
                .page(0)
                .size(limit)
                .totalElements(0)
                .totalPages(0)
                .tookMs(0)
                .build();
    }

    default ListingSearchResponse searchListingsLexical(String query, String provinceCode, String category, int limit) {
        Object res = searchListings(query, category);
        if (res instanceof ListingSearchResponse lsr) {
            return lsr;
        }
        return ListingSearchResponse.builder()
                .items(List.of())
                .page(0)
                .size(limit)
                .totalElements(0)
                .totalPages(0)
                .tookMs(0)
                .build();
    }
}
