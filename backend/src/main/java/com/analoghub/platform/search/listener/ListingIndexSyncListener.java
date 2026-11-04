package com.analoghub.platform.search.listener;

import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import com.analoghub.platform.search.document.ListingSearchDocument;
import com.analoghub.platform.search.repository.ListingSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListingIndexSyncListener {

    private final ListingSearchRepository listingSearchRepository;
    private final ObjectProvider<MarketplaceFacade> marketplaceFacadeProvider;

    @ApplicationModuleListener
    public void onListingPublished(ListingPublishedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        syncListingToIndex(
                event.listingId(),
                event.sellerId(),
                event.title(),
                event.category(),
                event.priceVnd(),
                event.status() != null ? event.status() : "PUBLISHED",
                event.occurredAt() != null ? event.occurredAt() : Instant.now()
        );
        log.info("Indexed published MarketplaceListing in Elasticsearch: listingId={}", event.listingId());
    }

    @ApplicationModuleListener
    public void onListingUpdated(ListingUpdatedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        syncListingToIndex(
                event.listingId(),
                event.sellerId(),
                event.title(),
                event.category(),
                event.priceVnd(),
                event.status() != null ? event.status() : "PUBLISHED",
                event.occurredAt() != null ? event.occurredAt() : Instant.now()
        );
        log.info("Updated MarketplaceListing in Elasticsearch: listingId={}, status={}", event.listingId(), event.status());
    }

    @ApplicationModuleListener
    public void onListingArchived(ListingArchivedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        String listingIdStr = event.listingId().toString();
        Optional<ListingSearchDocument> existing = listingSearchRepository.findById(listingIdStr);
        if (existing.isPresent()) {
            ListingSearchDocument doc = existing.get();
            doc.setStatus(event.status() != null ? event.status() : "ARCHIVED");
            listingSearchRepository.save(doc);
        } else {
            listingSearchRepository.deleteById(listingIdStr);
        }
        log.info("Archived MarketplaceListing in Elasticsearch: listingId={}, status={}", listingIdStr, event.status());
    }

    public void syncByListingId(UUID listingId) {
        if (listingId == null) {
            return;
        }
        MarketplaceFacade marketplaceFacade = marketplaceFacadeProvider.getIfAvailable();
        if (marketplaceFacade != null) {
            Optional<MarketplaceListingDto> dtoOpt = marketplaceFacade.getListingById(listingId);
            if (dtoOpt.isPresent()) {
                ListingSearchDocument doc = mapDtoToDocument(dtoOpt.get(), Instant.now());
                listingSearchRepository.save(doc);
                return;
            }
        }
        log.debug("MarketplaceFacade returned empty for listingId={}; skipping full sync.", listingId);
    }

    private void syncListingToIndex(
            UUID listingId,
            UUID sellerId,
            String fallbackTitle,
            String fallbackCategory,
            java.math.BigDecimal fallbackPriceVnd,
            String fallbackStatus,
            Instant occurredAt
    ) {
        String listingIdStr = listingId.toString();
        MarketplaceFacade marketplaceFacade = marketplaceFacadeProvider.getIfAvailable();
        if (marketplaceFacade != null) {
            Optional<MarketplaceListingDto> dtoOpt = marketplaceFacade.getListingById(listingId);
            if (dtoOpt.isPresent()) {
                ListingSearchDocument doc = mapDtoToDocument(dtoOpt.get(), occurredAt);
                listingSearchRepository.save(doc);
                return;
            }
        }

        ListingSearchDocument doc = listingSearchRepository.findById(listingIdStr)
                .orElseGet(() -> ListingSearchDocument.builder()
                        .listingId(listingIdStr)
                        .createdAt(occurredAt)
                        .build());

        if (sellerId != null) {
            doc.setSellerUserId(sellerId.toString());
        }
        if (fallbackTitle != null) {
            doc.setTitle(fallbackTitle);
            if (doc.getSlug() == null) {
                doc.setSlug(LabIndexSyncListener.toSlug(fallbackTitle));
            }
        }
        if (fallbackCategory != null) {
            doc.setCategory(fallbackCategory);
        }
        if (fallbackPriceVnd != null) {
            doc.setPriceVnd(fallbackPriceVnd);
        }
        if (fallbackStatus != null) {
            doc.setStatus(fallbackStatus);
        }
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(occurredAt);
        }

        listingSearchRepository.save(doc);
    }

    private ListingSearchDocument mapDtoToDocument(MarketplaceListingDto dto, Instant defaultCreatedAt) {
        return ListingSearchDocument.builder()
                .listingId(dto.id().toString())
                .sellerUserId(dto.sellerUserId() != null ? dto.sellerUserId().toString() : null)
                .slug(dto.slug() != null ? dto.slug() : LabIndexSyncListener.toSlug(dto.title()))
                .title(dto.title())
                .description(dto.description())
                .category(dto.category())
                .brand(dto.brand())
                .modelName(dto.modelName())
                .conditionGrade(dto.conditionGrade())
                .priceVnd(dto.priceVnd())
                .allowPriceOffer(dto.allowPriceOffer())
                .allowSwap(dto.allowSwap())
                .provinceCode(dto.provinceCode())
                .districtCode(dto.districtCode())
                .status(dto.status() != null ? dto.status() : "PUBLISHED")
                .createdAt(defaultCreatedAt != null ? defaultCreatedAt : Instant.now())
                .build();
    }
}

