package com.analoghub.platform.aiassistant.listener;

import com.analoghub.platform.aiassistant.client.AiModelRouter;
import com.analoghub.platform.aiassistant.client.EmbeddingClient;
import com.analoghub.platform.aiassistant.client.EmbeddingTaskType;
import com.analoghub.platform.aiassistant.domain.LabEmbedding;
import com.analoghub.platform.aiassistant.domain.ListingEmbedding;
import com.analoghub.platform.aiassistant.repository.LabEmbeddingRepository;
import com.analoghub.platform.aiassistant.repository.ListingEmbeddingRepository;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.event.LabReviewPostedEvent;
import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.marketplace.facade.MarketplaceFacade;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Synchronizes dense vector embeddings (768 dimensions, L2-normalized) for Film Labs (ai_lab_embeddings)
 * and Marketplace Listings (ai_listing_embeddings) when domain events are published
 * (Section 2.2, 3.2 & 5 of 03-hybrid-recommender-and-semantic-search.md).
 */
@Slf4j
@Component
public class LabAndListingEmbeddingListener {

    @Builder
    public record LabProfileSyncPayload(
            UUID labId,
            String slug,
            String name,
            String fullAddress,
            String provinceCode,
            List<String> supportedFormats,
            List<String> supportedProcesses,
            List<String> scannerModels,
            String colorSignatureBio,
            Integer estimatedTurnaroundHours,
            BigDecimal minPriceVnd,
            Double avgRating
    ) {
    }

    @Builder
    public record ListingSyncPayload(
            UUID listingId,
            UUID sellerUserId,
            String slug,
            String title,
            String description,
            String category,
            String brand,
            String modelName,
            String conditionGrade,
            BigDecimal priceVnd,
            String provinceCode,
            String status
    ) {
    }

    private final LabEmbeddingRepository labEmbeddingRepository;
    private final ListingEmbeddingRepository listingEmbeddingRepository;
    private final AiModelRouter aiModelRouter;
    private final ObjectProvider<MarketplaceFacade> marketplaceFacadeProvider;

    private final Map<UUID, LabEmbedding> labEmbeddingCache = new ConcurrentHashMap<>();
    private final Map<UUID, ListingEmbedding> listingEmbeddingCache = new ConcurrentHashMap<>();

    @Autowired
    public LabAndListingEmbeddingListener(
            LabEmbeddingRepository labEmbeddingRepository,
            ListingEmbeddingRepository listingEmbeddingRepository,
            AiModelRouter aiModelRouter,
            ObjectProvider<MarketplaceFacade> marketplaceFacadeProvider
    ) {
        this.labEmbeddingRepository = labEmbeddingRepository;
        this.listingEmbeddingRepository = listingEmbeddingRepository;
        this.aiModelRouter = aiModelRouter;
        this.marketplaceFacadeProvider = marketplaceFacadeProvider;
    }

    public LabAndListingEmbeddingListener(
            LabEmbeddingRepository labEmbeddingRepository,
            ListingEmbeddingRepository listingEmbeddingRepository,
            AiModelRouter aiModelRouter
    ) {
        this(labEmbeddingRepository, listingEmbeddingRepository, aiModelRouter, null);
    }

    @ApplicationModuleListener
    public void onFilmLabCreated(FilmLabCreatedEvent event) {
        if (event == null || event.labId() == null) {
            return;
        }
        String labName = StringUtils.hasText(event.labName()) ? event.labName().trim() : "Film Lab AnalogHub";
        LabProfileSyncPayload payload = LabProfileSyncPayload.builder()
                .labId(event.labId())
                .slug(toSlug(labName))
                .name(labName)
                .fullAddress("Việt Nam")
                .supportedFormats(List.of("FMT_135", "FMT_120"))
                .supportedProcesses(List.of("C41", "ECN2", "BW"))
                .scannerModels(List.of("NORITSU_HS1800", "FRONTIER_SP3000"))
                .colorSignatureBio("Tráng scan film chuẩn nhiệt độ buồng tối, màu sắc trong trẻo.")
                .estimatedTurnaroundHours(24)
                .minPriceVnd(BigDecimal.valueOf(65000))
                .avgRating(4.8)
                .build();

        upsertLabEmbedding(payload);
        log.info("Synced FilmLab vector embedding on FilmLabCreatedEvent: labId={}, name={}", event.labId(), labName);
    }

    @ApplicationModuleListener
    public void onLabReviewPosted(LabReviewPostedEvent event) {
        if (event == null || event.getLabId() == null) {
            return;
        }
        UUID labId = event.getLabId();
        LabEmbedding existing = labEmbeddingCache.get(labId);
        if (existing == null) {
            existing = labEmbeddingRepository.findByLabId(labId).orElse(null);
        }
        if (existing != null) {
            existing.setUpdatedAt(Instant.now());
            float[] vector = aiModelRouter.embed(null, existing.getDescription(), EmbeddingTaskType.RETRIEVAL_DOCUMENT);
            existing.setEmbedding(vector);
            LabEmbedding saved = labEmbeddingRepository.save(existing);
            saved.setEmbedding(vector);
            labEmbeddingCache.put(labId, saved);
            log.info("Refreshed FilmLab vector embedding on LabReviewPostedEvent: labId={}", labId);
        }
    }

    @ApplicationModuleListener
    public void onListingPublished(ListingPublishedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        syncListingFromEvent(
                event.listingId(),
                event.sellerId(),
                event.title(),
                event.category(),
                event.priceVnd(),
                event.status()
        );
        log.info("Synced MarketplaceListing vector embedding on ListingPublishedEvent: listingId={}", event.listingId());
    }

    @ApplicationModuleListener
    public void onListingUpdated(ListingUpdatedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        if (event.status() != null && !"PUBLISHED".equalsIgnoreCase(event.status())) {
            removeListingEmbedding(event.listingId());
            return;
        }
        syncListingFromEvent(
                event.listingId(),
                event.sellerId(),
                event.title(),
                event.category(),
                event.priceVnd(),
                event.status()
        );
        log.info("Updated MarketplaceListing vector embedding on ListingUpdatedEvent: listingId={}", event.listingId());
    }

    @ApplicationModuleListener
    public void onListingArchived(ListingArchivedEvent event) {
        if (event == null || event.listingId() == null) {
            return;
        }
        removeListingEmbedding(event.listingId());
        log.info("Removed MarketplaceListing vector embedding on ListingArchivedEvent: listingId={}", event.listingId());
    }

    @Transactional
    public LabEmbedding upsertLabEmbedding(LabProfileSyncPayload payload) {
        if (payload == null || payload.labId() == null) {
            throw new IllegalArgumentException("LabProfileSyncPayload and labId must not be null");
        }

        String synthesizedText = buildSynthesizedLabProfileText(payload);
        float[] vector = aiModelRouter.embed(null, synthesizedText, EmbeddingTaskType.RETRIEVAL_DOCUMENT);
        float[] normalized = EmbeddingClient.normalizeL2(vector, EmbeddingClient.REQUIRED_DIMENSIONS);

        LabEmbedding entity = labEmbeddingRepository.findByLabId(payload.labId())
                .orElseGet(() -> LabEmbedding.builder()
                        .id(UUID.randomUUID())
                        .labId(payload.labId())
                        .build());

        entity.setDescription(synthesizedText);
        entity.setUpdatedAt(Instant.now());
        entity.setEmbedding(normalized);
        entity.setSlug(StringUtils.hasText(payload.slug()) ? payload.slug() : toSlug(payload.name()));
        entity.setLabName(payload.name());
        entity.setFullAddress(payload.fullAddress());
        entity.setProvinceCode(payload.provinceCode());
        entity.setSupportedFormats(payload.supportedFormats() != null ? List.copyOf(payload.supportedFormats()) : List.of("FMT_135"));
        entity.setSupportedProcesses(payload.supportedProcesses() != null ? List.copyOf(payload.supportedProcesses()) : List.of("C41"));
        entity.setScannerModels(payload.scannerModels() != null ? List.copyOf(payload.scannerModels()) : List.of("NORITSU_HS1800"));
        entity.setMinPriceVnd(payload.minPriceVnd() != null ? payload.minPriceVnd() : BigDecimal.valueOf(65000));
        entity.setEstimatedTurnaroundHours(payload.estimatedTurnaroundHours() != null ? payload.estimatedTurnaroundHours() : 24);
        entity.setAvgRating(payload.avgRating() != null ? payload.avgRating() : 4.8);

        LabEmbedding saved = labEmbeddingRepository.save(entity);
        copyTransientLabMetadata(entity, saved);
        labEmbeddingCache.put(payload.labId(), saved);
        return saved;
    }

    @Transactional
    public ListingEmbedding upsertListingEmbedding(ListingSyncPayload payload) {
        if (payload == null || payload.listingId() == null) {
            throw new IllegalArgumentException("ListingSyncPayload and listingId must not be null");
        }

        String synthesizedFeatures = buildSynthesizedListingFeaturesText(payload);
        float[] vector = aiModelRouter.embed(payload.sellerUserId(), synthesizedFeatures, EmbeddingTaskType.RETRIEVAL_DOCUMENT);
        float[] normalized = EmbeddingClient.normalizeL2(vector, EmbeddingClient.REQUIRED_DIMENSIONS);

        ListingEmbedding entity = listingEmbeddingRepository.findByListingId(payload.listingId())
                .orElseGet(() -> ListingEmbedding.builder()
                        .id(UUID.randomUUID())
                        .listingId(payload.listingId())
                        .build());

        entity.setFeatures(synthesizedFeatures);
        entity.setUpdatedAt(Instant.now());
        entity.setEmbedding(normalized);
        entity.setSellerUserId(payload.sellerUserId());
        entity.setSlug(StringUtils.hasText(payload.slug()) ? payload.slug() : toSlug(payload.title()));
        entity.setTitle(payload.title());
        entity.setCategory(payload.category());
        entity.setBrand(payload.brand());
        entity.setModelName(payload.modelName());
        entity.setConditionGrade(payload.conditionGrade());
        entity.setPriceVnd(payload.priceVnd());
        entity.setProvinceCode(payload.provinceCode());
        entity.setStatus(StringUtils.hasText(payload.status()) ? payload.status() : "PUBLISHED");

        ListingEmbedding saved = listingEmbeddingRepository.save(entity);
        copyTransientListingMetadata(entity, saved);
        listingEmbeddingCache.put(payload.listingId(), saved);
        return saved;
    }

    @Transactional
    public void removeListingEmbedding(UUID listingId) {
        if (listingId == null) {
            return;
        }
        listingEmbeddingCache.remove(listingId);
        listingEmbeddingRepository.deleteByListingId(listingId);
    }

    public Optional<LabEmbedding> getCachedLabEmbedding(UUID labId) {
        return labId != null ? Optional.ofNullable(labEmbeddingCache.get(labId)) : Optional.empty();
    }

    public Collection<LabEmbedding> getAllCachedLabEmbeddings() {
        return Collections.unmodifiableCollection(labEmbeddingCache.values());
    }

    public Optional<ListingEmbedding> getCachedListingEmbedding(UUID listingId) {
        return listingId != null ? Optional.ofNullable(listingEmbeddingCache.get(listingId)) : Optional.empty();
    }

    public Collection<ListingEmbedding> getAllCachedListingEmbeddings() {
        return Collections.unmodifiableCollection(listingEmbeddingCache.values());
    }

    /**
     * Synthesizes Lab Profile text following Section 2.2 of 03-hybrid-recommender-and-semantic-search.md.
     */
    public static String buildSynthesizedLabProfileText(LabProfileSyncPayload p) {
        String name = StringUtils.hasText(p.name()) ? p.name().trim() : "Film Lab";
        String address = StringUtils.hasText(p.fullAddress()) ? p.fullAddress().trim() : "Việt Nam";
        String formats = p.supportedFormats() != null && !p.supportedFormats().isEmpty()
                ? String.join(", ", p.supportedFormats())
                : "135, 120";
        String processes = p.supportedProcesses() != null && !p.supportedProcesses().isEmpty()
                ? String.join(", ", p.supportedProcesses())
                : "C-41, ECN-2, Black & White";
        String scanners = p.scannerModels() != null && !p.scannerModels().isEmpty()
                ? String.join(", ", p.scannerModels())
                : "Noritsu HS-1800, Frontier SP-3000";
        String bio = StringUtils.hasText(p.colorSignatureBio())
                ? p.colorSignatureBio().trim()
                : "Tone màu trong trẻo, xử lý film chuẩn nhiệt độ buồng tối.";
        int turnaround = p.estimatedTurnaroundHours() != null ? p.estimatedTurnaroundHours() : 24;
        BigDecimal minPrice = p.minPriceVnd() != null ? p.minPriceVnd() : BigDecimal.valueOf(65000);

        return String.format(
                "[Lab Profile] Tên: %s | Địa chỉ: %s\n"
                        + "Định dạng film hỗ trợ: %s | Quy trình tráng: %s\n"
                        + "Dòng máy Scan: %s\n"
                        + "Đặc trưng màu sắc: %s\n"
                        + "Thời gian trả ảnh trung bình: %d giờ | Đơn giá từ: %s VND.",
                name, address, formats, processes, scanners, bio, turnaround, minPrice.toPlainString()
        );
    }

    public static String buildSynthesizedListingFeaturesText(ListingSyncPayload p) {
        String title = StringUtils.hasText(p.title()) ? p.title().trim() : "Thiết bị nhiếp ảnh phim";
        String category = StringUtils.hasText(p.category()) ? p.category().trim() : "CAMERA_BODY";
        String brand = StringUtils.hasText(p.brand()) ? p.brand().trim() : "";
        String model = StringUtils.hasText(p.modelName()) ? p.modelName().trim() : "";
        String condition = StringUtils.hasText(p.conditionGrade()) ? p.conditionGrade().trim() : "EXCELLENT";
        String price = p.priceVnd() != null ? p.priceVnd().toPlainString() : "0";
        String desc = StringUtils.hasText(p.description()) ? p.description().trim() : "";

        return String.format(
                "[Marketplace Listing] Tiêu đề: %s | Danh mục: %s | Thương hiệu: %s | Dòng máy: %s | Tình trạng: %s | Giá: %s VND | Mô tả: %s",
                title, category, brand, model, condition, price, desc
        ).trim();
    }

    private void syncListingFromEvent(
            UUID listingId,
            UUID sellerId,
            String fallbackTitle,
            String fallbackCategory,
            BigDecimal fallbackPriceVnd,
            String fallbackStatus
    ) {
        MarketplaceFacade marketplaceFacade = marketplaceFacadeProvider != null
                ? marketplaceFacadeProvider.getIfAvailable()
                : null;
        if (marketplaceFacade != null) {
            try {
                Optional<MarketplaceListingDto> dtoOpt = marketplaceFacade.getListingById(listingId);
                if (dtoOpt.isPresent()) {
                    MarketplaceListingDto dto = dtoOpt.get();
                    upsertListingEmbedding(ListingSyncPayload.builder()
                            .listingId(dto.id())
                            .sellerUserId(dto.sellerUserId())
                            .slug(dto.slug())
                            .title(dto.title())
                            .description(dto.description())
                            .category(dto.category())
                            .brand(dto.brand())
                            .modelName(dto.modelName())
                            .conditionGrade(dto.conditionGrade())
                            .priceVnd(dto.priceVnd())
                            .provinceCode(dto.provinceCode())
                            .status(dto.status())
                            .build());
                    return;
                }
            } catch (Exception ex) {
                log.debug("Could not fetch MarketplaceListingDto from MarketplaceFacade for listingId={}: {}",
                        listingId, ex.getMessage());
            }
        }

        upsertListingEmbedding(ListingSyncPayload.builder()
                .listingId(listingId)
                .sellerUserId(sellerId)
                .slug(toSlug(fallbackTitle))
                .title(fallbackTitle)
                .category(fallbackCategory)
                .priceVnd(fallbackPriceVnd)
                .status(fallbackStatus != null ? fallbackStatus : "PUBLISHED")
                .build());
    }

    private static void copyTransientLabMetadata(LabEmbedding src, LabEmbedding dst) {
        if (src == dst || dst == null) {
            return;
        }
        dst.setEmbedding(src.getEmbedding());
        dst.setSlug(src.getSlug());
        dst.setLabName(src.getLabName());
        dst.setFullAddress(src.getFullAddress());
        dst.setProvinceCode(src.getProvinceCode());
        dst.setSupportedFormats(src.getSupportedFormats());
        dst.setSupportedProcesses(src.getSupportedProcesses());
        dst.setScannerModels(src.getScannerModels());
        dst.setMinPriceVnd(src.getMinPriceVnd());
        dst.setEstimatedTurnaroundHours(src.getEstimatedTurnaroundHours());
        dst.setAvgRating(src.getAvgRating());
    }

    private static void copyTransientListingMetadata(ListingEmbedding src, ListingEmbedding dst) {
        if (src == dst || dst == null) {
            return;
        }
        dst.setEmbedding(src.getEmbedding());
        dst.setSellerUserId(src.getSellerUserId());
        dst.setSlug(src.getSlug());
        dst.setTitle(src.getTitle());
        dst.setCategory(src.getCategory());
        dst.setBrand(src.getBrand());
        dst.setModelName(src.getModelName());
        dst.setConditionGrade(src.getConditionGrade());
        dst.setPriceVnd(src.getPriceVnd());
        dst.setProvinceCode(src.getProvinceCode());
        dst.setStatus(src.getStatus());
    }

    static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "item";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}