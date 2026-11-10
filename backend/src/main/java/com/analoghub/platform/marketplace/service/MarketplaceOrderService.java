package com.analoghub.platform.marketplace.service;

import com.analoghub.platform.common.dto.PageResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.MarketplaceException;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.delivery.dto.ContactAddressDto;
import com.analoghub.platform.delivery.dto.CreateDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateDto;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateRequest;
import com.analoghub.platform.delivery.facade.DeliveryFacade;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.domain.ListingStatus;
import com.analoghub.platform.marketplace.domain.MarketOrderStatus;
import com.analoghub.platform.marketplace.domain.MarketOrderType;
import com.analoghub.platform.marketplace.domain.MarketplaceListing;
import com.analoghub.platform.marketplace.domain.MarketplaceListingImage;
import com.analoghub.platform.marketplace.domain.MarketplaceOffer;
import com.analoghub.platform.marketplace.domain.MarketplaceOrder;
import com.analoghub.platform.marketplace.domain.MarketplaceOrderItem;
import com.analoghub.platform.marketplace.domain.OfferStatus;
import com.analoghub.platform.marketplace.domain.OfferType;
import com.analoghub.platform.marketplace.domain.SellerReview;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedRequest;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedResponse;
import com.analoghub.platform.marketplace.dto.CreateMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.CreateSellerReviewRequest;
import com.analoghub.platform.marketplace.dto.MarketOrderCreatedResponse;
import com.analoghub.platform.marketplace.dto.MarketOrderDetailResponse;
import com.analoghub.platform.marketplace.dto.MarketOrderSummaryResponse;
import com.analoghub.platform.marketplace.dto.MarketplaceOrderDto;
import com.analoghub.platform.marketplace.dto.SellerReviewResponse;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderResponse;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.marketplace.repository.MarketplaceListingImageRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceListingRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOfferRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOrderItemRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOrderRepository;
import com.analoghub.platform.marketplace.repository.SellerReviewRepository;
import com.analoghub.platform.messaging.facade.MessagingFacade;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MarketplaceOrderService {

    private static final Set<String> SUPPORTED_DELIVERY_PROVIDERS = Set.of(
            "AHAMOVE", "LALAMOVE", "INTERNAL_DRIVER", "MOCK"
    );
    private static final BigDecimal DEFAULT_SWAP_DEPOSIT_VND = new BigDecimal("200000.00");
    private static final BigDecimal DEFAULT_SHIPPING_FEE_VND = new BigDecimal("35000.00");
    private static final double DEFAULT_LAT = 10.7769;
    private static final double DEFAULT_LON = 106.7009;
    private static final String SHIP_META_KEY_PREFIX = "mkt:order:shipmeta:";

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceListingImageRepository listingImageRepository;
    private final MarketplaceOfferRepository offerRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceOrderItemRepository orderItemRepository;
    private final SellerReviewRepository sellerReviewRepository;
    private final IamFacade iamFacade;
    private final DeliveryFacade deliveryFacade;
    private final PaymentFacade paymentFacade;
    private final PlatformConfigFacade platformConfigFacade;
    private final MessagingFacade messagingFacade;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MarketOrderCreatedResponse createOrder(UUID buyerUserId, CreateMarketOrderRequest request) {
        String deliveryProvider = normalizeDeliveryProvider(request.deliveryProvider());

        MarketplaceListing listing = listingRepository.findByIdAndDeletedAtIsNull(request.listingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND, "Không tìm thấy tin đăng thiết bị"));

        if (listing.getSellerUserId().equals(buyerUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không thể tự đặt mua tin đăng của chính mình");
        }

        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw MarketplaceException.listingNotAvailable();
        }

        UserAddressDto buyerAddress = iamFacade.findAddressById(buyerUserId, request.shippingAddressId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy địa chỉ nhận hàng của người mua"
                ));

        MarketOrderType orderType;
        BigDecimal agreedPriceVnd;
        String paymentPurpose;
        MarketplaceListing swapListing = null;

        if (request.acceptedOfferId() == null) {
            orderType = MarketOrderType.STANDARD_PURCHASE;
            agreedPriceVnd = listing.getPriceVnd();
            paymentPurpose = "MARKETPLACE_ORDER";
        } else {
            MarketplaceOffer offer = offerRepository.findById(request.acceptedOfferId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND, "Không tìm thấy đề nghị thương lượng"));

            if (offer.getStatus() != OfferStatus.ACCEPTED
                    || !offer.getBuyerUserId().equals(buyerUserId)
                    || !offer.getListingId().equals(listing.getId())) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Đề nghị thương lượng chưa ở trạng thái ACCEPTED hoặc không thuộc quyền mua của bạn"
                );
            }

            if (offer.getOfferType() == OfferType.PRICE) {
                orderType = MarketOrderType.STANDARD_PURCHASE;
                agreedPriceVnd = offer.getOfferedPriceVnd() != null ? offer.getOfferedPriceVnd() : listing.getPriceVnd();
                paymentPurpose = "MARKETPLACE_ORDER";
            } else {
                orderType = MarketOrderType.SWAP_EXCHANGE;
                BigDecimal cashTopup = offer.getCashTopupVnd() != null ? offer.getCashTopupVnd() : BigDecimal.ZERO;
                if (cashTopup.compareTo(BigDecimal.ZERO) > 0) {
                    agreedPriceVnd = cashTopup;
                    paymentPurpose = "MARKETPLACE_ORDER";
                } else {
                    agreedPriceVnd = platformConfigFacade.getDecimalOrDefault(
                            "mkt.swap.min_security_deposit_vnd", DEFAULT_SWAP_DEPOSIT_VND
                    );
                    paymentPurpose = "MARKETPLACE_SWAP_DEPOSIT";
                }

                if (offer.getSwapOfferedListingId() != null) {
                    swapListing = listingRepository.findByIdAndDeletedAtIsNull(offer.getSwapOfferedListingId()).orElse(null);
                    if (swapListing != null && swapListing.getStatus() == ListingStatus.PUBLISHED) {
                        swapListing.setStatus(ListingStatus.RESERVED);
                        listingRepository.saveAndFlush(swapListing);
                    }
                }
            }
        }

        UserAddressDto sellerDefaultAddr = iamFacade.findDefaultAddress(listing.getSellerUserId()).orElse(null);
        double pickupLat = sellerDefaultAddr != null && sellerDefaultAddr.latitude() != null
                ? sellerDefaultAddr.latitude() : DEFAULT_LAT;
        double pickupLon = sellerDefaultAddr != null && sellerDefaultAddr.longitude() != null
                ? sellerDefaultAddr.longitude() : DEFAULT_LON;
        double dropoffLat = buyerAddress.latitude() != null ? buyerAddress.latitude() : DEFAULT_LAT;
        double dropoffLon = buyerAddress.longitude() != null ? buyerAddress.longitude() : DEFAULT_LON;

        BigDecimal shippingFeeVnd = estimateShippingFeeSafe(pickupLat, pickupLon, dropoffLat, dropoffLon);
        BigDecimal itemTotalVnd = agreedPriceVnd;
        BigDecimal totalPayableVnd = itemTotalVnd.add(shippingFeeVnd);

        listing.setStatus(ListingStatus.RESERVED);
        listing = listingRepository.saveAndFlush(listing);

        String orderCode = generateOrderCode();
        MarketplaceOrder order = MarketplaceOrder.builder()
                .orderCode(orderCode)
                .buyerUserId(buyerUserId)
                .sellerUserId(listing.getSellerUserId())
                .acceptedOfferId(request.acceptedOfferId())
                .orderType(orderType)
                .itemTotalVnd(itemTotalVnd)
                .shippingFeeVnd(shippingFeeVnd)
                .totalPayableVnd(totalPayableVnd)
                .status(MarketOrderStatus.PENDING_PAYMENT)
                .build();
        order = orderRepository.saveAndFlush(order);

        MarketplaceOrderItem orderItem = MarketplaceOrderItem.builder()
                .marketOrderId(order.getId())
                .listingId(listing.getId())
                .titleSnapshot(listing.getTitle())
                .agreedPriceVnd(agreedPriceVnd)
                .build();
        orderItem = orderItemRepository.save(orderItem);

        saveShippingMetaToRedis(order.getId(), buyerAddress.id(), deliveryProvider);

        messagingFacade.getOrCreateConversation(
                "MARKET_ORDER",
                order.getId(),
                List.of(buyerUserId, listing.getSellerUserId())
        );

        eventPublisher.publishEvent(new ListingUpdatedEvent(
                listing.getId(),
                listing.getSellerUserId(),
                listing.getTitle(),
                listing.getCategory().name(),
                listing.getPriceVnd(),
                ListingStatus.RESERVED.name()
        ));

        if (swapListing != null && swapListing.getStatus() == ListingStatus.RESERVED) {
            eventPublisher.publishEvent(new ListingUpdatedEvent(
                    swapListing.getId(),
                    swapListing.getSellerUserId(),
                    swapListing.getTitle(),
                    swapListing.getCategory().name(),
                    swapListing.getPriceVnd(),
                    ListingStatus.RESERVED.name()
            ));
        }

        Instant createdAt = order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now();
        Instant paymentExpiresAt = createdAt.plus(15, ChronoUnit.MINUTES);

        return new MarketOrderCreatedResponse(
                order.getId(),
                order.getOrderCode(),
                order.getBuyerUserId(),
                order.getSellerUserId(),
                order.getAcceptedOfferId(),
                order.getOrderType().name(),
                List.of(new MarketOrderCreatedResponse.OrderItemSummary(
                        orderItem.getId(),
                        orderItem.getListingId(),
                        orderItem.getTitleSnapshot(),
                        orderItem.getAgreedPriceVnd()
                )),
                order.getItemTotalVnd(),
                order.getShippingFeeVnd(),
                order.getTotalPayableVnd(),
                order.getStatus().name(),
                paymentPurpose,
                paymentExpiresAt,
                createdAt
        );
    }

    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.referenceId() == null || event.paymentPurpose() == null) {
            return;
        }
        String purpose = event.paymentPurpose().trim().toUpperCase();
        if (!"MARKETPLACE_ORDER".equals(purpose) && !"MARKETPLACE_SWAP_DEPOSIT".equals(purpose)) {
            return;
        }

        Optional<MarketplaceOrder> orderOpt = orderRepository.findById(event.referenceId());
        if (orderOpt.isEmpty()) {
            return;
        }

        MarketplaceOrder order = orderOpt.get();
        if (order.getStatus() != MarketOrderStatus.PENDING_PAYMENT) {
            return;
        }

        order.setStatus(MarketOrderStatus.ESCROW_HELD_AWAITING_SHIP);
        orderRepository.save(order);

        UUID conversationId = messagingFacade.getOrCreateConversation(
                "MARKET_ORDER",
                order.getId(),
                List.of(order.getBuyerUserId(), order.getSellerUserId())
        );
        messagingFacade.sendSystemNotice(
                conversationId,
                "Người mua đã hoàn tất thanh toán vào tài khoản tạm giữ Escrow cho đơn hàng "
                        + order.getOrderCode() + ". Người bán vui lòng đóng gói và bàn giao vận chuyển."
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MarketOrderSummaryResponse> getMyOrders(
            UUID currentUserId,
            String role,
            String statusStr,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tham số phân trang không hợp lệ");
        }
        if (role == null || role.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số truy vấn 'role' là bắt buộc và phải nhận giá trị BUYER hoặc SELLER"
            );
        }

        String normalizedRole = role.trim().toUpperCase();
        boolean isBuyerView;
        if ("BUYER".equals(normalizedRole)) {
            isBuyerView = true;
        } else if ("SELLER".equals(normalizedRole)) {
            isBuyerView = false;
        } else {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số truy vấn 'role' phải nhận giá trị BUYER hoặc SELLER"
            );
        }

        MarketOrderStatus statusFilter = null;
        if (statusStr != null && !statusStr.isBlank()) {
            statusFilter = parseOrderStatus(statusStr);
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<MarketplaceOrder> orderPage;
        if (isBuyerView) {
            orderPage = statusFilter == null
                    ? orderRepository.findByBuyerUserIdOrderByCreatedAtDesc(currentUserId, pageable)
                    : orderRepository.findByBuyerUserIdAndStatusOrderByCreatedAtDesc(currentUserId, statusFilter, pageable);
        } else {
            orderPage = statusFilter == null
                    ? orderRepository.findBySellerUserIdOrderByCreatedAtDesc(currentUserId, pageable)
                    : orderRepository.findBySellerUserIdAndStatusOrderByCreatedAtDesc(currentUserId, statusFilter, pageable);
        }

        List<MarketplaceOrder> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return PageResponse.from(List.of(), page, size, 0, 0);
        }

        List<UUID> orderIds = orders.stream().map(MarketplaceOrder::getId).toList();
        List<MarketplaceOrderItem> allItems = orderItemRepository.findByMarketOrderIdIn(orderIds);
        Map<UUID, MarketplaceOrderItem> primaryItemByOrderId = new HashMap<>();
        Set<UUID> listingIds = new HashSet<>();
        for (MarketplaceOrderItem item : allItems) {
            primaryItemByOrderId.putIfAbsent(item.getMarketOrderId(), item);
            listingIds.add(item.getListingId());
        }

        Map<UUID, String> primaryThumbByListingId = new HashMap<>();
        if (!listingIds.isEmpty()) {
            List<MarketplaceListingImage> images = listingImageRepository.findByListingIdInOrderBySortOrderAsc(listingIds);
            for (MarketplaceListingImage img : images) {
                primaryThumbByListingId.putIfAbsent(img.getListingId(), storageService.resolveReadUrl(img.getS3Key()));
            }
        }

        Set<UUID> counterpartyIds = new HashSet<>();
        for (MarketplaceOrder order : orders) {
            counterpartyIds.add(isBuyerView ? order.getSellerUserId() : order.getBuyerUserId());
        }
        Map<UUID, UserSummaryDto> userMap = counterpartyIds.isEmpty()
                ? Map.of()
                : iamFacade.findUsersByIds(counterpartyIds);

        List<MarketOrderSummaryResponse> summaries = orders.stream()
                .map(order -> {
                    UUID counterpartyId = isBuyerView ? order.getSellerUserId() : order.getBuyerUserId();
                    UserSummaryDto cpUser = userMap.get(counterpartyId);
                    MarketOrderSummaryResponse.CounterpartySummary counterparty = new MarketOrderSummaryResponse.CounterpartySummary(
                            counterpartyId,
                            cpUser != null ? cpUser.fullName() : "Thành viên AnalogHub",
                            cpUser != null ? storageService.resolveReadUrl(cpUser.avatarKey()) : null
                    );

                    MarketplaceOrderItem primaryItem = primaryItemByOrderId.get(order.getId());
                    String title = primaryItem != null ? primaryItem.getTitleSnapshot() : null;
                    String thumbUrl = primaryItem != null ? primaryThumbByListingId.get(primaryItem.getListingId()) : null;

                    return new MarketOrderSummaryResponse(
                            order.getId(),
                            order.getOrderCode(),
                            order.getOrderType().name(),
                            counterparty,
                            title,
                            thumbUrl,
                            order.getTotalPayableVnd(),
                            order.getStatus().name(),
                            order.getInspectionDeadlineAt(),
                            order.getCreatedAt()
                    );
                })
                .toList();

        return PageResponse.from(
                summaries,
                page,
                size,
                (int) orderPage.getTotalElements(),
                orderPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public MarketOrderDetailResponse getOrderDetail(UUID currentUserId, boolean isAdmin, UUID orderId) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));

        if (!isAdmin
                && !order.getBuyerUserId().equals(currentUserId)
                && !order.getSellerUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập thông tin đơn hàng C2C này");
        }

        Map<UUID, UserSummaryDto> userMap = iamFacade.findUsersByIds(
                List.of(order.getBuyerUserId(), order.getSellerUserId())
        );
        UserSummaryDto buyerUser = userMap.get(order.getBuyerUserId());
        UserSummaryDto sellerUser = userMap.get(order.getSellerUserId());

        MarketOrderDetailResponse.PartyContact buyerContact = new MarketOrderDetailResponse.PartyContact(
                order.getBuyerUserId(),
                buyerUser != null ? buyerUser.fullName() : "Người mua",
                buyerUser != null ? buyerUser.phoneNumber() : null
        );
        MarketOrderDetailResponse.PartyContact sellerContact = new MarketOrderDetailResponse.PartyContact(
                order.getSellerUserId(),
                sellerUser != null ? sellerUser.fullName() : "Người bán",
                sellerUser != null ? sellerUser.phoneNumber() : null
        );

        List<MarketOrderCreatedResponse.OrderItemSummary> items = orderItemRepository.findByMarketOrderId(order.getId())
                .stream()
                .map(i -> new MarketOrderCreatedResponse.OrderItemSummary(
                        i.getId(),
                        i.getListingId(),
                        i.getTitleSnapshot(),
                        i.getAgreedPriceVnd()
                ))
                .toList();

        FeeScope feeScope = resolveFeeScope(order.getOrderType());
        FeeCalculationResult feeCalc = calculateFeeBreakdownSafe(feeScope, order.getItemTotalVnd());

        MarketOrderDetailResponse.FinancialBreakdown financials = new MarketOrderDetailResponse.FinancialBreakdown(
                order.getItemTotalVnd(),
                order.getShippingFeeVnd(),
                order.getTotalPayableVnd(),
                resolveEscrowStatusLabel(order.getStatus()),
                feeCalc.commissionFeeVnd(),
                feeCalc.netPayoutAmountVnd()
        );

        UUID conversationId = messagingFacade.getOrCreateConversation(
                "MARKET_ORDER",
                order.getId(),
                List.of(order.getBuyerUserId(), order.getSellerUserId())
        );

        boolean hasReviewed = sellerReviewRepository.existsByMarketOrderId(order.getId());

        return new MarketOrderDetailResponse(
                order.getId(),
                order.getOrderCode(),
                order.getOrderType().name(),
                order.getStatus().name(),
                buyerContact,
                sellerContact,
                items,
                financials,
                conversationId,
                order.getInspectionDeadlineAt(),
                hasReviewed,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    @Transactional
    public ShipMarketOrderResponse shipOrder(UUID sellerUserId, UUID orderId, ShipMarketOrderRequest request) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));

        if (!order.getSellerUserId().equals(sellerUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ người bán của đơn hàng mới được xác nhận gửi hàng");
        }

        if (order.getStatus() != MarketOrderStatus.ESCROW_HELD_AWAITING_SHIP) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Chỉ có thể điều phối giao hàng khi đơn hàng đang ở trạng thái đã thanh toán tạm giữ Escrow"
            );
        }

        for (String key : request.packagingVideoOrPhotoKeys()) {
            if (key == null || key.isBlank() || !storageService.exists(key.trim())) {
                throw new BusinessException(
                        ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                        "Ảnh/video niêm phong đóng gói '" + key + "' chưa được tải lên kho lưu trữ"
                );
            }
        }

        UserAddressDto pickupAddr = iamFacade.findAddressById(sellerUserId, request.pickupAddressId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy địa chỉ lấy hàng của người bán"
                ));

        ShippingMeta shippingMeta = loadShippingMetaFromRedis(order.getId());
        UserAddressDto dropoffAddr = null;
        if (shippingMeta.shippingAddressId() != null) {
            dropoffAddr = iamFacade.findAddressById(order.getBuyerUserId(), shippingMeta.shippingAddressId()).orElse(null);
        }
        if (dropoffAddr == null) {
            dropoffAddr = iamFacade.findDefaultAddress(order.getBuyerUserId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.DELIVERY_ADDRESS_REQUIRED,
                            "Không tìm thấy địa chỉ nhận hàng của người mua"
                    ));
        }

        ContactAddressDto pickupContact = toContactAddress(pickupAddr);
        ContactAddressDto dropoffContact = toContactAddress(dropoffAddr);
        Instant pickupDeadlineAt = Instant.now().plus(2, ChronoUnit.HOURS);

        UUID deliveryTaskId = deliveryFacade.createDeliveryTask(new CreateDeliveryTaskRequest(
                "MARKETPLACE_ORDER",
                order.getId(),
                "C2C_SHIPPING",
                shippingMeta.deliveryProvider(),
                pickupContact,
                dropoffContact,
                pickupDeadlineAt
        ));

        order.setStatus(MarketOrderStatus.SHIPPED);
        order = orderRepository.saveAndFlush(order);

        UUID conversationId = messagingFacade.getOrCreateConversation(
                "MARKET_ORDER",
                order.getId(),
                List.of(order.getBuyerUserId(), order.getSellerUserId())
        );
        messagingFacade.sendSystemNotice(
                conversationId,
                "Người bán đã đóng gói niêm phong và tạo vận đơn giao hàng cho đơn " + order.getOrderCode() + "."
        );

        return new ShipMarketOrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus().name(),
                new ShipMarketOrderResponse.DeliveryDispatchSummary(
                        deliveryTaskId,
                        "C2C_SHIPPING",
                        shippingMeta.deliveryProvider(),
                        "ASSIGNING_DRIVER",
                        pickupDeadlineAt
                ),
                order.getUpdatedAt()
        );
    }

    @Transactional
    public ConfirmOrderReceivedResponse confirmReceived(
            UUID buyerUserId,
            UUID orderId,
            ConfirmOrderReceivedRequest request
    ) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));

        if (!order.getBuyerUserId().equals(buyerUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ người mua của đơn hàng mới có quyền xác nhận nhận hàng");
        }

        if (order.getStatus() == MarketOrderStatus.DISPUTED
                || order.getStatus() == MarketOrderStatus.COMPLETED
                || order.getStatus() == MarketOrderStatus.REFUNDED) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Khoản tạm giữ Escrow đã được tất toán trước đó hoặc đang bị đóng băng do tranh chấp"
            );
        }

        if (order.getStatus() != MarketOrderStatus.DELIVERED_INSPECTING
                && order.getStatus() != MarketOrderStatus.SHIPPED) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đơn hàng chưa ở trạng thái đang giao hoặc kiểm tra thiết bị"
            );
        }

        paymentFacade.releaseEscrow(order.getId(), "MARKETPLACE_ORDER");

        Instant completedAt = Instant.now();
        order.setStatus(MarketOrderStatus.COMPLETED);
        order = orderRepository.saveAndFlush(order);

        markOrderListingsAsSold(order);

        FeeScope feeScope = resolveFeeScope(order.getOrderType());
        FeeCalculationResult feeCalc = calculateFeeBreakdownSafe(feeScope, order.getItemTotalVnd());

        UUID conversationId = messagingFacade.getOrCreateConversation(
                "MARKET_ORDER",
                order.getId(),
                List.of(order.getBuyerUserId(), order.getSellerUserId())
        );
        messagingFacade.sendSystemNotice(
                conversationId,
                "Người mua đã xác nhận kiểm tra hàng thành công. Hệ thống đã giải ngân tiền bán hàng vào ví người bán."
        );

        return new ConfirmOrderReceivedResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus().name(),
                new ConfirmOrderReceivedResponse.EscrowSettlementSummary(
                        "RELEASED_TO_PAYEE",
                        order.getItemTotalVnd(),
                        feeCalc.commissionFeeVnd(),
                        feeCalc.netPayoutAmountVnd(),
                        completedAt
                ),
                completedAt
        );
    }

    @Transactional
    public SellerReviewResponse createSellerReview(
            UUID buyerUserId,
            UUID orderId,
            CreateSellerReviewRequest request
    ) {
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));

        if (!order.getBuyerUserId().equals(buyerUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Chỉ người mua của đơn hàng mới được gửi đánh giá");
        }

        if (order.getStatus() != MarketOrderStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Chỉ có thể gửi đánh giá sau khi đơn hàng đã hoàn tất (COMPLETED)"
            );
        }

        if (sellerReviewRepository.existsByMarketOrderId(orderId)) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Bạn đã gửi đánh giá cho đơn hàng C2C này trước đó"
            );
        }

        SellerReview review = SellerReview.builder()
                .marketOrderId(order.getId())
                .reviewerUserId(buyerUserId)
                .sellerUserId(order.getSellerUserId())
                .rating(request.rating())
                .itemAsDescribed(Boolean.TRUE.equals(request.itemAsDescribed()))
                .comment(request.comment().trim())
                .build();
        review = sellerReviewRepository.saveAndFlush(review);

        return new SellerReviewResponse(
                review.getId(),
                review.getMarketOrderId(),
                review.getReviewerUserId(),
                review.getSellerUserId(),
                review.getRating(),
                review.isItemAsDescribed(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<MarketplaceOrderDto> findOrderDtoById(UUID marketOrderId) {
        return orderRepository.findById(marketOrderId)
                .map(o -> new MarketplaceOrderDto(
                        o.getId(),
                        o.getOrderCode(),
                        o.getBuyerUserId(),
                        o.getSellerUserId(),
                        o.getOrderType().name(),
                        o.getTotalPayableVnd(),
                        o.getStatus().name()
                ));
    }

    @Transactional
    public void markOrderDisputed(UUID marketOrderId, UUID disputeId) {
        MarketplaceOrder order = orderRepository.findById(marketOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));
        order.setStatus(MarketOrderStatus.DISPUTED);
        orderRepository.save(order);
    }

    @Transactional
    public void resolveOrderDispute(UUID marketOrderId, String resolutionOutcome) {
        MarketplaceOrder order = orderRepository.findById(marketOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng C2C"));

        String normalized = resolutionOutcome != null ? resolutionOutcome.trim().toUpperCase() : "";
        if ("REFUND_BUYER".equals(normalized)) {
            order.setStatus(MarketOrderStatus.REFUNDED);
            orderRepository.save(order);
            releaseReservedListingsToPublished(order);
        } else {
            order.setStatus(MarketOrderStatus.COMPLETED);
            orderRepository.save(order);
            markOrderListingsAsSold(order);
        }
    }

    private void markOrderListingsAsSold(MarketplaceOrder order) {
        List<MarketplaceOrderItem> items = orderItemRepository.findByMarketOrderId(order.getId());
        Set<UUID> targetListingIds = new HashSet<>();
        for (MarketplaceOrderItem item : items) {
            targetListingIds.add(item.getListingId());
        }
        if (order.getAcceptedOfferId() != null) {
            offerRepository.findById(order.getAcceptedOfferId())
                    .map(MarketplaceOffer::getSwapOfferedListingId)
                    .ifPresent(targetListingIds::add);
        }

        for (UUID listingId : targetListingIds) {
            listingRepository.findByIdAndDeletedAtIsNull(listingId).ifPresent(listing -> {
                if (listing.getStatus() != ListingStatus.SOLD) {
                    listing.setStatus(ListingStatus.SOLD);
                    listingRepository.save(listing);
                    eventPublisher.publishEvent(new ListingArchivedEvent(
                            listing.getId(),
                            listing.getSellerUserId(),
                            ListingStatus.SOLD.name()
                    ));
                }
            });
        }
    }

    private void releaseReservedListingsToPublished(MarketplaceOrder order) {
        List<MarketplaceOrderItem> items = orderItemRepository.findByMarketOrderId(order.getId());
        for (MarketplaceOrderItem item : items) {
            listingRepository.findByIdAndDeletedAtIsNull(item.getListingId()).ifPresent(listing -> {
                if (listing.getStatus() == ListingStatus.RESERVED) {
                    listing.setStatus(ListingStatus.PUBLISHED);
                    listingRepository.save(listing);
                    eventPublisher.publishEvent(new ListingUpdatedEvent(
                            listing.getId(),
                            listing.getSellerUserId(),
                            listing.getTitle(),
                            listing.getCategory().name(),
                            listing.getPriceVnd(),
                            ListingStatus.PUBLISHED.name()
                    ));
                }
            });
        }
    }

    private BigDecimal estimateShippingFeeSafe(double pickupLat, double pickupLon, double dropoffLat, double dropoffLon) {
        try {
            ShippingFeeEstimateDto dto = deliveryFacade.estimateShippingFee(
                    new ShippingFeeEstimateRequest(pickupLat, pickupLon, dropoffLat, dropoffLon, "C2C_SHIPPING", false)
            );
            if (dto != null && dto.estimatedFeeVnd() != null) {
                return dto.estimatedFeeVnd();
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_SHIPPING_FEE_VND;
    }

    private FeeCalculationResult calculateFeeBreakdownSafe(FeeScope feeScope, BigDecimal grossAmountVnd) {
        try {
            FeeCalculationResult result = platformConfigFacade.calculateFeeBreakdown(feeScope, grossAmountVnd);
            if (result != null) {
                return result;
            }
        } catch (Exception ignored) {
        }
        BigDecimal rate = new BigDecimal("0.05");
        BigDecimal commission = grossAmountVnd.multiply(rate);
        BigDecimal net = grossAmountVnd.subtract(commission).max(BigDecimal.ZERO);
        return new FeeCalculationResult(feeScope, grossAmountVnd, rate, BigDecimal.ZERO, commission, net);
    }

    private FeeScope resolveFeeScope(MarketOrderType orderType) {
        return orderType == MarketOrderType.SWAP_EXCHANGE ? FeeScope.MARKETPLACE_SWAP : FeeScope.MARKETPLACE_SALE;
    }

    private String resolveEscrowStatusLabel(MarketOrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> "UNPAID";
            case ESCROW_HELD_AWAITING_SHIP, SHIPPED, DELIVERED_INSPECTING -> "HELD";
            case DISPUTED -> "FROZEN_BY_DISPUTE";
            case COMPLETED -> "RELEASED_TO_PAYEE";
            case REFUNDED -> "REFUNDED_TO_PAYER";
            case CANCELLED -> "CANCELLED";
        };
    }

    private MarketOrderStatus parseOrderStatus(String raw) {
        String normalized = raw.trim().toUpperCase();
        if ("PAID_ESCROW_HELD".equals(normalized)) {
            return MarketOrderStatus.ESCROW_HELD_AWAITING_SHIP;
        }
        if ("SHIPPING".equals(normalized)) {
            return MarketOrderStatus.SHIPPED;
        }
        try {
            return MarketOrderStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái đơn hàng không hợp lệ: " + raw);
        }
    }

    private String normalizeDeliveryProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn đơn vị vận chuyển");
        }
        String normalized = raw.trim().toUpperCase();
        if (!SUPPORTED_DELIVERY_PROVIDERS.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đơn vị vận chuyển chỉ hỗ trợ AHAMOVE, LALAMOVE, INTERNAL_DRIVER hoặc MOCK"
            );
        }
        return normalized;
    }

    private ContactAddressDto toContactAddress(UserAddressDto addr) {
        return new ContactAddressDto(
                addr.recipientName(),
                addr.recipientPhone(),
                addr.fullAddress(),
                addr.latitude() != null ? addr.latitude() : DEFAULT_LAT,
                addr.longitude() != null ? addr.longitude() : DEFAULT_LON
        );
    }

    private String generateOrderCode() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "MKT-" + datePart + "-" + randomSuffix;
    }

    private void saveShippingMetaToRedis(UUID orderId, UUID shippingAddressId, String provider) {
        try {
            String value = shippingAddressId + "|" + provider;
            redisTemplate.opsForValue().set(SHIP_META_KEY_PREFIX + orderId, value, Duration.ofDays(7));
        } catch (Exception ignored) {
        }
    }

    private ShippingMeta loadShippingMetaFromRedis(UUID orderId) {
        try {
            String raw = redisTemplate.opsForValue().get(SHIP_META_KEY_PREFIX + orderId);
            if (raw != null && raw.contains("|")) {
                String[] parts = raw.split("\\|", 2);
                return new ShippingMeta(UUID.fromString(parts[0]), parts[1]);
            }
        } catch (Exception ignored) {
        }
        return new ShippingMeta(null, "AHAMOVE");
    }

    private record ShippingMeta(UUID shippingAddressId, String deliveryProvider) {
    }
}
