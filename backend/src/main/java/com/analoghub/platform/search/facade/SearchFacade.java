package com.analoghub.platform.search.facade;

import java.util.UUID;

public interface SearchFacade {
    // Elasticsearch Sync Methods
    void indexFilmLab(UUID labId);
    void indexMarketplaceListing(UUID listingId);
    void removeMarketplaceListing(UUID listingId);
    
    // Search Methods (Stubs)
    Object searchLabsNearby(double lat, double lon, double radiusKm);
    Object searchListings(String keyword, String category);
}
