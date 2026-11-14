package com.analoghub.platform.marketplace.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.MarketplaceException;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.domain.ListingStatus;
import com.analoghub.platform.marketplace.domain.MarketplaceListing;
import com.analoghub.platform.marketplace.domain.MarketplaceOffer;
import com.analoghub.platform.marketplace.domain.OfferStatus;
import com.analoghub.platform.marketplace.domain.OfferType;
import com.analoghub.platform.marketplace.dto.CreateOfferRequest;
import com.analoghub.platform.marketplace.dto.OfferCreatedResponse;
import com.analoghub.platform.marketplace.dto.OfferSummaryResponse;
import com.analoghub.platform.marketplace.dto.OfferUpdatedResponse;
import com.analoghub.platform.marketplace.dto.RespondOfferRequest;
import com.analoghub.platform.marketplace.event.OfferAcceptedEvent;
import com.analoghub.platform.marketplace.event.OfferSubmittedEvent;
import com.analoghub.platform.marketplace.repository.MarketplaceListingRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOfferRepository;
import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import com.analoghub.platform.messaging.facade.MessagingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingOfferService {

    private static final BigDecimal MIN_PRICE_OFFER_VND = new BigDecimal("10000.00");
    private static final BigDecimal HALF_RATIO = new BigDecimal("0.50");
    private static final List<OfferStatus> OPEN_OFFER_STATUSES = List.of(OfferStatus.PENDING, OfferStatus.COUNTERED);

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceOfferRepository offerRepository;
    private final MessagingFacade messagingFacade;
    private final IamFacade iamFacade;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OfferCreatedResponse submitOffer(UUID buyerUserId, UUID listingId, CreateOfferRequest request) {
        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        if (listing.getSellerUserId().equals(buyerUserId)) {
            throw MarketplaceException.cannotOfferOwnListing();
        }

        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw MarketplaceException.listingNotAvailable();
        }

        OfferType offerType = parseOfferType(request.offerType());

        BigDecimal normalizedOfferedPrice = null;
        BigDecimal normalizedCashTopup = null;
        String normalizedSwapDesc = null;

        if (offerType == OfferType.PRICE) {
            if (!listing.isAllowPriceOffer()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Người bán không bật tùy chọn nhận đề nghị trả giá cho tin đăng này"
                );
            }
            BigDecimal minAllowed = listing.getPriceVnd().multiply(HALF_RATIO);
            if (request.offeredPriceVnd() == null
                    || request.offeredPriceVnd().compareTo(MIN_PRICE_OFFER_VND) < 0
                    || request.offeredPriceVnd().compareTo(minAllowed) < 0
                    || request.offeredPriceVnd().compareTo(listing.getPriceVnd()) >= 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Mức giá đề xuất phải từ 50% đến dưới 100% giá niêm yết của thiết bị"
                );
            }
            normalizedOfferedPrice = request.offeredPriceVnd();
        } else {
            if (!listing.isAllowSwap()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Người bán không bật tùy chọn giao lưu đổi thiết bị (allowSwap = false) cho tin đăng này"
                );
            }
            if (request.swapItemDescription() == null || request.swapItemDescription().trim().length() < 20) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Mô tả thiết bị đem ra giao lưu phải từ 20 đến 1000 ký tự"
                );
            }
            normalizedSwapDesc = request.swapItemDescription().trim();

            if (request.swapOfferedListingId() != null) {
                MarketplaceListing swapListing = listingRepository
                        .findByIdAndDeletedAtIsNull(request.swapOfferedListingId())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.LISTING_NOT_FOUND,
                                "Không tìm thấy tin đăng thiết bị dùng để giao lưu"
                        ));
                if (!swapListing.getSellerUserId().equals(buyerUserId)
                        || swapListing.getStatus() != ListingStatus.PUBLISHED) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Tin đăng thiết bị đem ra đổi phải thuộc quyền sở hữu của bạn và đang ở trạng thái PUBLISHED"
                    );
                }
            }

            if (offerType == OfferType.SWAP_WITH_CASH) {
                if (request.cashTopupVnd() == null || request.cashTopupVnd().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Số tiền bù thêm (cashTopupVnd) phải lớn hơn 0 đối với hình thức SWAP_WITH_CASH"
                    );
                }
                normalizedCashTopup = request.cashTopupVnd();
            } else {
                normalizedCashTopup = BigDecimal.ZERO;
            }
        }

        if (offerRepository.existsByListingIdAndBuyerUserIdAndStatusIn(listingId, buyerUserId, OPEN_OFFER_STATUSES)) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bạn đang có một đề nghị thương lượng chưa kết thúc trên tin đăng này"
            );
        }

        Instant expiresAt = Instant.now().plus(48, ChronoUnit.HOURS);
        MarketplaceOffer offer = MarketplaceOffer.builder()
                .listingId(listing.getId())
                .buyerUserId(buyerUserId)
                .sellerUserId(listing.getSellerUserId())
                .offerType(offerType)
                .offeredPriceVnd(normalizedOfferedPrice)
                .swapOfferedListingId(request.swapOfferedListingId())
                .swapItemDescription(normalizedSwapDesc)
                .cashTopupVnd(normalizedCashTopup)
                .status(OfferStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        offer = offerRepository.save(offer);

        UUID conversationId = messagingFacade.getOrCreateConversation(
                "MARKET_LISTING",
                listing.getId(),
                List.of(buyerUserId, listing.getSellerUserId())
        );

        BigDecimal cardAmount = offerType == OfferType.PRICE ? normalizedOfferedPrice : normalizedCashTopup;
        String cardDesc = buildCardDescription(normalizedSwapDesc, request.messageToSeller());
        UUID offerCardMessageId = messagingFacade.sendOfferCardMessage(new OfferCardMessageRequest(
                listing.getId(),
                offer.getId(),
                buyerUserId,
                listing.getSellerUserId(),
                offerType.name(),
                cardAmount,
                cardDesc,
                OfferStatus.PENDING.name()
        ));

        eventPublisher.publishEvent(new OfferSubmittedEvent(
                offer.getId(),
                listing.getId(),
                buyerUserId,
                listing.getSellerUserId(),
                offerType.name(),
                cardAmount,
                OfferStatus.PENDING.name()
        ));

        return new OfferCreatedResponse(
                offer.getId(),
                listing.getId(),
                buyerUserId,
                listing.getSellerUserId(),
                offerType.name(),
                offer.getOfferedPriceVnd(),
                offer.getSwapOfferedListingId(),
                offer.getSwapItemDescription(),
                offer.getCashTopupVnd(),
                null,
                offer.getStatus().name(),
                conversationId,
                offerCardMessageId,
                offer.getExpiresAt(),
                offer.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OfferSummaryResponse> getListingOffers(
            UUID currentUserId,
            boolean isAdmin,
            UUID listingId,
            String statusStr,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }

        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        OfferStatus statusFilter = null;
        if (statusStr != null && !statusStr.isBlank()) {
            statusFilter = parseOfferStatus(statusStr);
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<MarketplaceOffer> offerPage;

        if (isAdmin || listing.getSellerUserId().equals(currentUserId)) {
            offerPage = statusFilter == null
                    ? offerRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                    : offerRepository.findByListingIdAndStatusOrderByCreatedAtDesc(listingId, statusFilter, pageable);
        } else {
            offerPage = statusFilter == null
                    ? offerRepository.findByListingIdAndBuyerUserIdOrderByCreatedAtDesc(listingId, currentUserId, pageable)
                    : offerRepository.findByListingIdAndBuyerUserIdAndStatusOrderByCreatedAtDesc(
                            listingId, currentUserId, statusFilter, pageable);
        }

        List<MarketplaceOffer> offers = offerPage.getContent();
        Set<UUID> buyerIds = offers.stream().map(MarketplaceOffer::getBuyerUserId).collect(Collectors.toSet());
        Map<UUID, UserSummaryDto> userMap = buyerIds.isEmpty() ? Map.of() : iamFacade.findUsersByIds(buyerIds);

        List<OfferSummaryResponse> items = offers.stream()
                .map(o -> {
                    UserSummaryDto buyer = userMap.get(o.getBuyerUserId());
                    OfferSummaryResponse.BuyerSummary buyerSummary = new OfferSummaryResponse.BuyerSummary(
                            o.getBuyerUserId(),
                            buyer != null ? buyer.fullName() : "Người dùng AnalogHub",
                            buyer != null ? storageService.resolveReadUrl(buyer.avatarKey()) : null
                    );
                    BigDecimal counterAmt = o.getStatus() == OfferStatus.COUNTERED ? resolveEffectiveAmount(o) : null;
                    return new OfferSummaryResponse(
                            o.getId(),
                            o.getListingId(),
                            buyerSummary,
                            o.getOfferType().name(),
                            o.getOfferedPriceVnd(),
                            o.getSwapOfferedListingId(),
                            o.getSwapItemDescription(),
                            o.getCashTopupVnd(),
                            counterAmt,
                            o.getStatus().name(),
                            o.getExpiresAt(),
                            o.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                items,
                page,
                size,
                (int) offerPage.getTotalElements(),
                offerPage.getTotalPages()
        );
    }

    @Transactional
    public OfferUpdatedResponse respondToOffer(UUID actorUserId, UUID offerId, RespondOfferRequest request) {
        MarketplaceOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND, "Không tìm thấy đề nghị thương lượng"));

        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(offer.getListingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        boolean isBuyer = offer.getBuyerUserId().equals(actorUserId);
        boolean isSeller = offer.getSellerUserId().equals(actorUserId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đề nghị này");
        }

        if (offer.getStatus() != OfferStatus.PENDING && offer.getStatus() != OfferStatus.COUNTERED) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đề nghị đã ở trạng thái kết thúc (" + offer.getStatus() + "), không thể cập nhật"
            );
        }

        Instant now = Instant.now();
        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(now)) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đề nghị thương lượng đã hết hạn");
        }

        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw MarketplaceException.listingNotAvailable();
        }

        String action = request.action().trim().toUpperCase();
        OfferStatus currentStatus = offer.getStatus();
        OfferStatus nextStatus;
        BigDecimal counterAmount = null;

        if (currentStatus == OfferStatus.PENDING) {
            if (isSeller) {
                switch (action) {
                    case "ACCEPT" -> nextStatus = OfferStatus.ACCEPTED;
                    case "REJECT" -> nextStatus = OfferStatus.REJECTED;
                    case "COUNTER" -> {
                        if (request.counterAmountVnd() == null
                                || request.counterAmountVnd().compareTo(BigDecimal.ZERO) < 0) {
                            throw new BusinessException(
                                    ErrorCode.VALIDATION_ERROR,
                                    "Vui lòng nhập mức tiền trả giá ngược (counterAmountVnd >= 0)"
                            );
                        }
                        nextStatus = OfferStatus.COUNTERED;
                        counterAmount = request.counterAmountVnd();
                        if (offer.getOfferType() == OfferType.PRICE) {
                            offer.setOfferedPriceVnd(counterAmount);
                        } else {
                            offer.setCashTopupVnd(counterAmount);
                        }
                        offer.setExpiresAt(now.plus(48, ChronoUnit.HOURS));
                    }
                    default -> throw new BusinessException(
                            ErrorCode.ACCESS_DENIED,
                            "Người bán chỉ được phép thực hiện ACCEPT, REJECT hoặc COUNTER trên đề nghị đang chờ"
                    );
                }
            } else {
                if (!"CANCEL".equals(action)) {
                    throw new BusinessException(
                            ErrorCode.ACCESS_DENIED,
                            "Người mua chỉ được phép rút lại đề nghị (CANCEL) khi đang chờ người bán phản hồi"
                    );
                }
                nextStatus = OfferStatus.CANCELLED;
            }
        } else {
            if (isBuyer) {
                switch (action) {
                    case "ACCEPT" -> nextStatus = OfferStatus.ACCEPTED;
                    case "REJECT" -> nextStatus = OfferStatus.REJECTED;
                    default -> throw new BusinessException(
                            ErrorCode.ACCESS_DENIED,
                            "Người mua chỉ được phép ACCEPT hoặc REJECT đối với đề nghị trả giá ngược (COUNTERED)"
                    );
                }
            } else {
                if (!"CANCEL".equals(action)) {
                    throw new BusinessException(
                            ErrorCode.ACCESS_DENIED,
                            "Chỉ người mua mới có quyền chấp nhận hoặc từ chối mức giá trả ngược (COUNTERED)"
                    );
                }
                nextStatus = OfferStatus.CANCELLED;
            }
        }

        offer.setStatus(nextStatus);
        offer = offerRepository.saveAndFlush(offer);

        UUID recipientId = isSeller ? offer.getBuyerUserId() : offer.getSellerUserId();
        BigDecimal effectiveAmount = resolveEffectiveAmount(offer);
        String cardDesc = buildCardDescription(offer.getSwapItemDescription(), request.responseNote());

        messagingFacade.sendOfferCardMessage(new OfferCardMessageRequest(
                offer.getListingId(),
                offer.getId(),
                actorUserId,
                recipientId,
                offer.getOfferType().name(),
                effectiveAmount,
                cardDesc,
                nextStatus.name()
        ));

        BigDecimal agreedAmount = null;
        if (nextStatus == OfferStatus.ACCEPTED) {
            agreedAmount = effectiveAmount != null ? effectiveAmount : BigDecimal.ZERO;
            eventPublisher.publishEvent(new OfferAcceptedEvent(
                    offer.getId(),
                    offer.getListingId(),
                    offer.getBuyerUserId(),
                    offer.getSellerUserId(),
                    offer.getOfferType().name(),
                    agreedAmount,
                    nextStatus.name()
            ));
        }

        return new OfferUpdatedResponse(
                offer.getId(),
                offer.getListingId(),
                offer.getOfferType().name(),
                offer.getOfferedPriceVnd(),
                offer.getCashTopupVnd(),
                nextStatus == OfferStatus.COUNTERED ? effectiveAmount : counterAmount,
                agreedAmount,
                nextStatus.name(),
                offer.getExpiresAt(),
                offer.getUpdatedAt()
        );
    }

    private BigDecimal resolveEffectiveAmount(MarketplaceOffer offer) {
        return offer.getOfferType() == OfferType.PRICE ? offer.getOfferedPriceVnd() : offer.getCashTopupVnd();
    }

    private String buildCardDescription(String swapDesc, String note) {
        if (swapDesc != null && !swapDesc.isBlank() && note != null && !note.isBlank()) {
            return swapDesc + " — Ghi chú: " + note.trim();
        }
        if (swapDesc != null && !swapDesc.isBlank()) {
            return swapDesc;
        }
        if (note != null && !note.isBlank()) {
            return note.trim();
        }
        return null;
    }

    private OfferType parseOfferType(String raw) {
        try {
            return OfferType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "offerType chỉ chấp nhận PRICE, SWAP hoặc SWAP_WITH_CASH"
            );
        }
    }

    private OfferStatus parseOfferStatus(String raw) {
        try {
            return OfferStatus.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái đề nghị không hợp lệ: " + raw);
        }
    }
}
