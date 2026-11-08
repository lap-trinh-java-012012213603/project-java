package com.analoghub.platform.marketplace.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageCategory;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.domain.ConditionGrade;
import com.analoghub.platform.marketplace.domain.ListingCategory;
import com.analoghub.platform.marketplace.domain.ListingStatus;
import com.analoghub.platform.marketplace.domain.MarketOrderStatus;
import com.analoghub.platform.marketplace.domain.MarketplaceListing;
import com.analoghub.platform.marketplace.domain.MarketplaceListingImage;
import com.analoghub.platform.marketplace.domain.MarketplaceOffer;
import com.analoghub.platform.marketplace.domain.OfferStatus;
import com.analoghub.platform.marketplace.domain.SellerReview;
import com.analoghub.platform.marketplace.dto.CreateListingRequest;
import com.analoghub.platform.marketplace.dto.DeleteListingResponse;
import com.analoghub.platform.marketplace.dto.ListingDetailResponse;
import com.analoghub.platform.marketplace.dto.ListingImageResponse;
import com.analoghub.platform.marketplace.dto.MarketplaceListingDto;
import com.analoghub.platform.marketplace.dto.PresignListingImageRequest;
import com.analoghub.platform.marketplace.dto.PresignedImageUploadResponse;
import com.analoghub.platform.marketplace.dto.TechnicalSpecsDto;
import com.analoghub.platform.marketplace.dto.UpdateListingRequest;
import com.analoghub.platform.marketplace.dto.UpdateListingResponse;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.marketplace.repository.MarketplaceListingImageRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceListingRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOfferRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOrderRepository;
import com.analoghub.platform.marketplace.repository.SellerReviewRepository;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceListingService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final List<OfferStatus> OPEN_OFFER_STATUSES = List.of(OfferStatus.PENDING, OfferStatus.COUNTERED);
    private static final List<OfferStatus> VIEWER_ACTIVE_STATUSES = List.of(
            OfferStatus.PENDING, OfferStatus.COUNTERED, OfferStatus.ACCEPTED
    );

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceListingImageRepository listingImageRepository;
    private final MarketplaceOfferRepository offerRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final SellerReviewRepository sellerReviewRepository;
    private final PlatformConfigFacade platformConfigFacade;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    public PresignedImageUploadResponse presignListingImage(UUID currentUserId, PresignListingImageRequest request) {
        StorageService.PresignedUploadResult result = storageService.generatePublicUploadUrl(
                StorageCategory.MARKETPLACE,
                currentUserId,
                request.fileName(),
                request.contentType(),
                request.fileSizeBytes()
        );
        return new PresignedImageUploadResponse(
                result.objectKey(),
                result.uploadPutUrl(),
                storageService.resolveReadUrl(result.objectKey()),
                result.expiresInSeconds()
        );
    }

    @Transactional
    public ListingDetailResponse createListing(UUID sellerUserId, CreateListingRequest request) {
        ListingCategory category = parseCategory(request.category());
        ConditionGrade conditionGrade = parseConditionGrade(request.conditionGrade());
        validatePriceStep(request.priceVnd());
        validateSwapFields(request.allowSwap(), request.preferredSwapItems());

        boolean publishImmediately = request.publishImmediately() == null || request.publishImmediately();
        if (publishImmediately) {
            enforceActiveListingLimit(sellerUserId);
        }

        verifyListingImages(request.imageS3Keys());

        int ttlDays = platformConfigFacade.getIntOrDefault("marketplace.listing.ttl_days", 30);
        Instant now = Instant.now();
        ListingStatus initialStatus = publishImmediately ? ListingStatus.PUBLISHED : ListingStatus.DRAFT;
        Instant expiresAt = publishImmediately ? now.plus(ttlDays, ChronoUnit.DAYS) : null;
        String uniqueSlug = generateUniqueSlug(request.title());

        MarketplaceListing listing = MarketplaceListing.builder()
                .sellerUserId(sellerUserId)
                .slug(uniqueSlug)
                .title(request.title().trim())
                .description(request.description().trim())
                .category(category)
                .brand(request.brand().trim())
                .modelName(request.modelName().trim())
                .conditionGrade(conditionGrade)
                .technicalSpecs(toSpecsMap(request.technicalSpecs()))
                .priceVnd(request.priceVnd())
                .allowPriceOffer(Boolean.TRUE.equals(request.allowPriceOffer()))
                .allowSwap(Boolean.TRUE.equals(request.allowSwap()))
                .preferredSwapItems(request.preferredSwapItems() != null ? request.preferredSwapItems().trim() : null)
                .provinceCode(request.provinceCode().trim())
                .districtCode(request.districtCode().trim())
                .status(initialStatus)
                .expiresAt(expiresAt)
                .build();
        listing = listingRepository.save(listing);

        List<MarketplaceListingImage> savedImages = saveListingImages(listing.getId(), request.imageS3Keys());

        if (initialStatus == ListingStatus.PUBLISHED) {
            eventPublisher.publishEvent(new ListingPublishedEvent(
                    listing.getId(),
                    sellerUserId,
                    listing.getTitle(),
                    listing.getCategory().name(),
                    listing.getPriceVnd(),
                    listing.getStatus().name()
            ));
        }

        return buildListingDetailResponse(listing, savedImages, sellerUserId);
    }

    @Transactional
    public UpdateListingResponse updateListing(UUID currentUserId, UUID listingId, UpdateListingRequest request) {
        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        if (!listing.getSellerUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không phải là chủ sở hữu của tin đăng này");
        }

        if (listing.getStatus() == ListingStatus.RESERVED
                || listing.getStatus() == ListingStatus.SOLD
                || listing.getStatus() == ListingStatus.HIDDEN_BY_ADMIN) {
            throw new BusinessException(
                    ErrorCode.LISTING_NOT_AVAILABLE,
                    "Không thể chỉnh sửa tin đăng đang được giữ chỗ giao dịch (RESERVED), đã bán (SOLD) hoặc bị khóa"
            );
        }

        if (!listing.getVersion().equals(request.version())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Phiên bản tin đăng không khớp do có giao dịch cập nhật đồng thời"
            );
        }

        ListingCategory category = parseCategory(request.category());
        ConditionGrade conditionGrade = parseConditionGrade(request.conditionGrade());
        ListingStatus targetStatus = parseEditableStatus(request.status());
        validatePriceStep(request.priceVnd());
        validateSwapFields(request.allowSwap(), request.preferredSwapItems());

        if (targetStatus == ListingStatus.PUBLISHED && listing.getStatus() != ListingStatus.PUBLISHED) {
            enforceActiveListingLimit(currentUserId);
        }

        verifyListingImages(request.imageS3Keys());

        Instant now = Instant.now();
        int ttlDays = platformConfigFacade.getIntOrDefault("marketplace.listing.ttl_days", 30);
        if (targetStatus == ListingStatus.PUBLISHED
                && (listing.getExpiresAt() == null || listing.getExpiresAt().isBefore(now))) {
            listing.setExpiresAt(now.plus(ttlDays, ChronoUnit.DAYS));
        }

        listing.setTitle(request.title().trim());
        listing.setDescription(request.description().trim());
        listing.setCategory(category);
        listing.setBrand(request.brand().trim());
        listing.setModelName(request.modelName().trim());
        listing.setConditionGrade(conditionGrade);
        listing.setTechnicalSpecs(toSpecsMap(request.technicalSpecs()));
        listing.setPriceVnd(request.priceVnd());
        listing.setAllowPriceOffer(Boolean.TRUE.equals(request.allowPriceOffer()));
        listing.setAllowSwap(Boolean.TRUE.equals(request.allowSwap()));
        listing.setPreferredSwapItems(request.preferredSwapItems() != null ? request.preferredSwapItems().trim() : null);
        listing.setProvinceCode(request.provinceCode().trim());
        listing.setDistrictCode(request.districtCode().trim());
        listing.setStatus(targetStatus);

        listing = listingRepository.saveAndFlush(listing);

        listingImageRepository.deleteByListingId(listing.getId());
        saveListingImages(listing.getId(), request.imageS3Keys());

        if (targetStatus == ListingStatus.PUBLISHED) {
            eventPublisher.publishEvent(new ListingUpdatedEvent(
                    listing.getId(),
                    currentUserId,
                    listing.getTitle(),
                    listing.getCategory().name(),
                    listing.getPriceVnd(),
                    listing.getStatus().name()
            ));
        } else {
            eventPublisher.publishEvent(new ListingArchivedEvent(
                    listing.getId(),
                    currentUserId,
                    targetStatus.name()
            ));
        }

        return new UpdateListingResponse(
                listing.getId(),
                listing.getSlug(),
                listing.getTitle(),
                listing.getPriceVnd(),
                listing.getConditionGrade().name(),
                listing.getStatus().name(),
                listing.getVersion(),
                listing.getExpiresAt(),
                listing.getUpdatedAt()
        );
    }

    @Transactional
    public DeleteListingResponse deleteListing(UUID currentUserId, boolean isAdmin, UUID listingId) {
        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        if (!isAdmin && !listing.getSellerUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xóa tin đăng này");
        }

        if (listing.getStatus() == ListingStatus.RESERVED || listing.getStatus() == ListingStatus.SOLD) {
            throw new BusinessException(
                    ErrorCode.LISTING_NOT_AVAILABLE,
                    "Không thể xóa tin đăng đang trong quá trình giao dịch đơn hàng C2C"
            );
        }

        Instant deletedAt = Instant.now();
        listing.setDeletedAt(deletedAt);
        listingRepository.save(listing);

        List<MarketplaceOffer> openOffers = offerRepository.findByListingIdAndStatusIn(listingId, OPEN_OFFER_STATUSES);
        for (MarketplaceOffer offer : openOffers) {
            offer.setStatus(OfferStatus.CANCELLED);
        }
        if (!openOffers.isEmpty()) {
            offerRepository.saveAll(openOffers);
        }

        eventPublisher.publishEvent(new ListingArchivedEvent(
                listing.getId(),
                listing.getSellerUserId(),
                "DELETED"
        ));

        return new DeleteListingResponse(listing.getId(), deletedAt, openOffers.size());
    }

    @Transactional(readOnly = true)
    public ListingDetailResponse getListingDetail(UUID viewerUserId, boolean isAdmin, String slugOrId) {
        Optional<MarketplaceListing> listingOpt = tryParseUuid(slugOrId)
                .flatMap(listingRepository::findByIdAndDeletedAtIsNull);

        if (listingOpt.isEmpty()) {
            listingOpt = listingRepository.findBySlugAndDeletedAtIsNull(slugOrId.trim());
        }

        MarketplaceListing listing = listingOpt.orElseThrow(() -> new BusinessException(
                ErrorCode.LISTING_NOT_FOUND,
                "Không tìm thấy tin đăng thiết bị với định danh '" + slugOrId + "'"
        ));

        boolean isOwner = viewerUserId != null && viewerUserId.equals(listing.getSellerUserId());
        if ((listing.getStatus() == ListingStatus.DRAFT
                || listing.getStatus() == ListingStatus.EXPIRED
                || listing.getStatus() == ListingStatus.HIDDEN_BY_ADMIN)
                && !isOwner && !isAdmin) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Tin đăng hiện không ở trạng thái công khai"
            );
        }

        List<MarketplaceListingImage> images = listingImageRepository.findByListingIdOrderBySortOrderAsc(listing.getId());
        return buildListingDetailResponse(listing, images, viewerUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MarketplaceListingDto> getMyListings(UUID sellerUserId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }
        Page<MarketplaceListing> listingPage = listingRepository.findBySellerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                sellerUserId, PageRequest.of(page, size)
        );
        List<MarketplaceListingDto> items = listingPage.getContent().stream()
                .map(this::toListingDto)
                .toList();
        return PageResponse.from(
                items,
                page,
                size,
                (int) listingPage.getTotalElements(),
                listingPage.getTotalPages()
        );
    }

    @Transactional
    public int expireOverdueListings() {
        Instant now = Instant.now();
        List<MarketplaceListing> expiredListings = listingRepository
                .findByStatusAndExpiresAtBeforeAndDeletedAtIsNull(ListingStatus.PUBLISHED, now);

        for (MarketplaceListing listing : expiredListings) {
            listing.setStatus(ListingStatus.EXPIRED);
            listingRepository.save(listing);

            List<MarketplaceOffer> openOffers = offerRepository.findByListingIdAndStatusIn(
                    listing.getId(), OPEN_OFFER_STATUSES
            );
            for (MarketplaceOffer offer : openOffers) {
                offer.setStatus(OfferStatus.EXPIRED);
            }
            if (!openOffers.isEmpty()) {
                offerRepository.saveAll(openOffers);
            }

            eventPublisher.publishEvent(new ListingArchivedEvent(
                    listing.getId(),
                    listing.getSellerUserId(),
                    ListingStatus.EXPIRED.name()
            ));
        }
        return expiredListings.size();
    }

    @Transactional(readOnly = true)
    public Optional<MarketplaceListingDto> findListingDtoById(UUID listingId) {
        return listingRepository.findByIdAndDeletedAtIsNull(listingId).map(this::toListingDto);
    }

    @Transactional
    public void hideListingByAdmin(UUID listingId, String reason) {
        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng"));

        listing.setStatus(ListingStatus.HIDDEN_BY_ADMIN);
        listingRepository.save(listing);

        List<MarketplaceOffer> openOffers = offerRepository.findByListingIdAndStatusIn(listingId, OPEN_OFFER_STATUSES);
        for (MarketplaceOffer offer : openOffers) {
            offer.setStatus(OfferStatus.CANCELLED);
        }
        if (!openOffers.isEmpty()) {
            offerRepository.saveAll(openOffers);
        }

        eventPublisher.publishEvent(new ListingArchivedEvent(
                listing.getId(),
                listing.getSellerUserId(),
                ListingStatus.HIDDEN_BY_ADMIN.name()
        ));
    }

    private ListingDetailResponse buildListingDetailResponse(
            MarketplaceListing listing,
            List<MarketplaceListingImage> images,
            UUID viewerUserId
    ) {
        List<ListingImageResponse> imageResponses = images.stream()
                .map(img -> new ListingImageResponse(
                        img.getId(),
                        img.getS3Key(),
                        storageService.resolveReadUrl(img.getS3Key()),
                        img.getSortOrder()
                ))
                .toList();

        UserSummaryDto sellerUser = iamFacade.findUserById(listing.getSellerUserId()).orElse(null);
        List<SellerReview> reviews = sellerReviewRepository.findBySellerUserId(listing.getSellerUserId());
        long completedSales = orderRepository.countBySellerUserIdAndStatus(
                listing.getSellerUserId(), MarketOrderStatus.COMPLETED
        );

        double avgRating = reviews.isEmpty()
                ? 0.0
                : Math.round(reviews.stream().mapToInt(SellerReview::getRating).average().orElse(0.0) * 100.0) / 100.0;
        double asDescribedRate = reviews.isEmpty()
                ? 1.0
                : Math.round(reviews.stream().filter(SellerReview::isItemAsDescribed).count() * 100.0 / reviews.size()) / 100.0;

        ListingDetailResponse.SellerProfileSummary sellerProfile = new ListingDetailResponse.SellerProfileSummary(
                listing.getSellerUserId(),
                sellerUser != null ? sellerUser.fullName() : "Thành viên AnalogHub",
                sellerUser != null ? storageService.resolveReadUrl(sellerUser.avatarKey()) : null,
                sellerUser != null ? sellerUser.createdAt() : null,
                avgRating,
                completedSales,
                asDescribedRate
        );

        long activeOfferCount = offerRepository.countByListingIdAndStatusIn(listing.getId(), OPEN_OFFER_STATUSES);

        ListingDetailResponse.ViewerOfferSummary viewerOffer = null;
        if (viewerUserId != null && !viewerUserId.equals(listing.getSellerUserId())) {
            viewerOffer = offerRepository
                    .findFirstByListingIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(
                            listing.getId(), viewerUserId, VIEWER_ACTIVE_STATUSES
                    )
                    .map(o -> new ListingDetailResponse.ViewerOfferSummary(
                            o.getId(),
                            o.getOfferType().name(),
                            o.getOfferedPriceVnd(),
                            o.getCashTopupVnd(),
                            o.getStatus().name(),
                            o.getExpiresAt()
                    ))
                    .orElse(null);
        }

        return new ListingDetailResponse(
                listing.getId(),
                listing.getSlug(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory().name(),
                listing.getBrand(),
                listing.getModelName(),
                listing.getConditionGrade().name(),
                listing.getTechnicalSpecs(),
                listing.getPriceVnd(),
                listing.isAllowPriceOffer(),
                listing.isAllowSwap(),
                listing.getPreferredSwapItems(),
                listing.getProvinceCode(),
                listing.getDistrictCode(),
                listing.getStatus().name(),
                imageResponses,
                sellerProfile,
                activeOfferCount,
                viewerOffer,
                listing.getVersion(),
                listing.getExpiresAt(),
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }

    private List<MarketplaceListingImage> saveListingImages(UUID listingId, List<String> s3Keys) {
        List<MarketplaceListingImage> entities = new ArrayList<>();
        for (int i = 0; i < s3Keys.size(); i++) {
            entities.add(MarketplaceListingImage.builder()
                    .listingId(listingId)
                    .s3Key(s3Keys.get(i).trim())
                    .sortOrder((short) i)
                    .build());
        }
        return listingImageRepository.saveAll(entities);
    }

    private void verifyListingImages(List<String> imageS3Keys) {
        for (String key : imageS3Keys) {
            if (key == null || !key.trim().startsWith("public/marketplace/")) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Đường dẫn ảnh phải nằm trong thư mục public/marketplace/"
                );
            }
            if (!storageService.exists(key.trim())) {
                throw new BusinessException(
                        ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                        "Ảnh đính kèm '" + key + "' chưa được tải lên kho lưu trữ"
                );
            }
        }
    }

    private void enforceActiveListingLimit(UUID sellerUserId) {
        int maxActive = platformConfigFacade.getIntOrDefault("marketplace.max_active_listings_per_user", 25);
        long currentActive = listingRepository.countBySellerUserIdAndStatusAndDeletedAtIsNull(
                sellerUserId, ListingStatus.PUBLISHED
        );
        if (currentActive >= maxActive) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Bạn đã đạt giới hạn tối đa " + maxActive + " tin đăng đang hiển thị cùng lúc"
            );
        }
    }

    private void validatePriceStep(BigDecimal priceVnd) {
        if (priceVnd == null || priceVnd.remainder(THOUSAND).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá bán niêm yết phải chia hết cho 1.000đ");
        }
    }

    private void validateSwapFields(Boolean allowSwap, String preferredSwapItems) {
        if (Boolean.TRUE.equals(allowSwap) && (preferredSwapItems == null || preferredSwapItems.isBlank())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vui lòng mô tả thiết bị mong muốn giao lưu khi bật tùy chọn allowSwap"
            );
        }
    }

    private ListingCategory parseCategory(String raw) {
        try {
            return ListingCategory.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Danh mục thiết bị không hợp lệ: " + raw);
        }
    }

    private ConditionGrade parseConditionGrade(String raw) {
        try {
            return ConditionGrade.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phân hạng ngoại hình không hợp lệ: " + raw);
        }
    }

    private ListingStatus parseEditableStatus(String raw) {
        try {
            ListingStatus parsed = ListingStatus.valueOf(raw.trim().toUpperCase());
            if (parsed != ListingStatus.DRAFT && parsed != ListingStatus.PUBLISHED) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Trạng thái cập nhật chỉ chấp nhận DRAFT hoặc PUBLISHED"
            );
        }
    }

    private Map<String, Object> toSpecsMap(TechnicalSpecsDto specs) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (specs == null) {
            return map;
        }
        if (specs.shutterSpeedAccuracy() != null) {
            map.put("shutterSpeedAccuracy", specs.shutterSpeedAccuracy());
        }
        if (specs.lightMeterStatus() != null) {
            map.put("lightMeterStatus", specs.lightMeterStatus());
        }
        if (specs.opticsCondition() != null) {
            map.put("opticsCondition", specs.opticsCondition());
        }
        if (specs.lightSealCondition() != null) {
            map.put("lightSealCondition", specs.lightSealCondition());
        }
        if (specs.mountType() != null) {
            map.put("mountType", specs.mountType());
        }
        if (specs.serialNumber() != null) {
            map.put("serialNumber", specs.serialNumber());
        }
        return map;
    }

    private String generateUniqueSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180).replaceAll("-$", "");
        }
        if (normalized.isEmpty()) {
            normalized = "analog-gear";
        }

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String candidate = normalized + "-" + suffix;
        while (listingRepository.existsBySlug(candidate)) {
            candidate = normalized + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private Optional<UUID> tryParseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private MarketplaceListingDto toListingDto(MarketplaceListing listing) {
        return new MarketplaceListingDto(
                listing.getId(),
                listing.getSellerUserId(),
                listing.getSlug(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory().name(),
                listing.getBrand(),
                listing.getModelName(),
                listing.getConditionGrade().name(),
                listing.getPriceVnd(),
                listing.isAllowPriceOffer(),
                listing.isAllowSwap(),
                listing.getProvinceCode(),
                listing.getDistrictCode(),
                listing.getStatus().name()
        );
    }
}
