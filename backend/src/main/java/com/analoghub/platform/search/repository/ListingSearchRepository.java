package com.analoghub.platform.search.repository;

import com.analoghub.platform.search.document.ListingSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ListingSearchRepository extends ElasticsearchRepository<ListingSearchDocument, String> {

    Optional<ListingSearchDocument> findBySlug(String slug);

    List<ListingSearchDocument> findByStatus(String status);

    List<ListingSearchDocument> findByStatusAndCategory(String status, String category);

    List<ListingSearchDocument> findBySellerUserId(String sellerUserId);
}

