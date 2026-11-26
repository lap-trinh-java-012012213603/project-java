package com.analoghub.platform.marketplace;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.delivery.dto.CreateDeliveryTaskRequest;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateDto;
import com.analoghub.platform.delivery.dto.ShippingFeeEstimateRequest;
import com.analoghub.platform.delivery.event.MarketParcelDeliveredEvent;
import com.analoghub.platform.delivery.facade.DeliveryFacade;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.marketplace.domain.ConditionGrade;
import com.analoghub.platform.marketplace.domain.ListingCategory;
import com.analoghub.platform.marketplace.domain.ListingStatus;
import com.analoghub.platform.marketplace.domain.MarketOrderStatus;
import com.analoghub.platform.marketplace.domain.MarketplaceListing;
import com.analoghub.platform.marketplace.domain.MarketplaceOffer;
import com.analoghub.platform.marketplace.domain.MarketplaceOrder;
import com.analoghub.platform.marketplace.domain.MarketplaceOrderItem;
import com.analoghub.platform.marketplace.domain.SellerReview;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedRequest;
import com.analoghub.platform.marketplace.dto.ConfirmOrderReceivedResponse;
import com.analoghub.platform.marketplace.dto.CreateMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.CreateOfferRequest;
import com.analoghub.platform.marketplace.dto.CreateSellerReviewRequest;
import com.analoghub.platform.marketplace.dto.MarketOrderCreatedResponse;
import com.analoghub.platform.marketplace.dto.OfferCreatedResponse;
import com.analoghub.platform.marketplace.dto.OfferUpdatedResponse;
import com.analoghub.platform.marketplace.dto.RespondOfferRequest;
import com.analoghub.platform.marketplace.dto.SellerReviewResponse;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderRequest;
import com.analoghub.platform.marketplace.dto.ShipMarketOrderResponse;
import com.analoghub.platform.marketplace.listener.MarketplacePaymentAndDeliveryListener;
import com.analoghub.platform.marketplace.repository.MarketplaceListingImageRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceListingRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOfferRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOrderItemRepository;
import com.analoghub.platform.marketplace.repository.MarketplaceOrderRepository;
import com.analoghub.platform.marketplace.repository.SellerReviewRepository;
import com.analoghub.platform.marketplace.service.ListingOfferService;
import com.analoghub.platform.marketplace.service.MarketplaceOrderService;
import com.analoghub.platform.messaging.dto.OfferCardMessageRequest;
import com.analoghub.platform.messaging.facade.MessagingFacade;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.facade.PaymentFacade;
import com.analoghub.platform.platformconfig.dto.FeeCalculationResult;
import com.analoghub.platform.platformconfig.dto.FeeScope;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferIntegrationTest {

    @Mock
    private MarketplaceListingRepository listingRepository;

    @Mock
    private MarketplaceListingImageRepository listingImageRepository;

    @Mock
    private MarketplaceOfferRepository offerRepository;

    @Mock
    private MarketplaceOrderRepository orderRepository;

    @Mock
    private MarketplaceOrderItemRepository orderItemRepository;

    @Mock
    private SellerReviewRepository sellerReviewRepository;

    @Mock
    private MessagingFacade messagingFacade;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private DeliveryFacade deliveryFacade;

    @Mock
    private PaymentFacade paymentFacade;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private StorageService storageService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Map<UUID, MarketplaceListing> listingStore = new ConcurrentHashMap<>();
    private final Map<UUID, MarketplaceOffer> offerStore = new ConcurrentHashMap<>();
    private final Map<UUID, MarketplaceOrder> orderStore = new ConcurrentHashMap<>();
    private final List<MarketplaceOrderItem> orderItemStore = new ArrayList<>();
    private final Map<UUID, SellerReview> reviewByOrderStore = new ConcurrentHashMap<>();
    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    private ListingOfferService offerService;
    private MarketplaceOrderService orderService;
    private MarketplacePaymentAndDeliveryListener paymentAndDeliveryListener;

    private UUID sellerId;
    private UUID buyerId;
    private UUID targetListingId;
    private UUID swapListingId;
    private UUID buyerAddressId;
    private UUID sellerAddressId;

    @BeforeEach
    void setUp() {
        listingStore.clear();
        offerStore.clear();
        orderStore.clear();
        orderItemStore.clear();
        reviewByOrderStore.clear();
        redisStore.clear();

        sellerId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        targetListingId = UUID.randomUUID();
        swapListingId = UUID.randomUUID();
        buyerAddressId = UUID.randomUUID();
        sellerAddressId = UUID.randomUUID();

        MarketplaceListing targetListing = MarketplaceListing.builder()
                .sellerUserId(sellerId)
                .slug("nikon-fm2n-black-chrome-4455")
                .title("Combo Nikon FM2n Black Chrome + Lens 50mm f/1.4 AI-S")
                .description("Máy đẹp sưu tầm, cơ tốc chuẩn toàn dải")
                .category(ListingCategory.CAMERA_BODY)
                .brand("Nikon")
                .modelName("FM2n")
                .conditionGrade(ConditionGrade.NEAR_MINT)
                .priceVnd(new BigDecimal("11500000.00"))
                .allowPriceOffer(true)
                .allowSwap(true)
                .provinceCode("79")
                .districtCode("760")
                .status(ListingStatus.PUBLISHED)
                .build();
        targetListing.setId(targetListingId);
        targetListing.setCreatedAt(Instant.now());
        listingStore.put(targetListingId, targetListing);

        MarketplaceListing buyerSwapListing = MarketplaceListing.builder()
                .sellerUserId(buyerId)
                .slug("olympus-om4-ti-black-9900")
                .title("Body Olympus OM-4 Ti Black")
                .description("Đo sáng Multi-Spot chuẩn xác")
                .category(ListingCategory.CAMERA_BODY)
                .brand("Olympus")
                .modelName("OM-4 Ti")
                .conditionGrade(ConditionGrade.EXCELLENT)
                .priceVnd(new BigDecimal("9000000.00"))
                .allowPriceOffer(true)
                .allowSwap(true)
                .provinceCode("79")
                .districtCode("760")
                .status(ListingStatus.PUBLISHED)
                .build();
        buyerSwapListing.setId(swapListingId);
        buyerSwapListing.setCreatedAt(Instant.now());
        listingStore.put(swapListingId, buyerSwapListing);

        lenient().when(listingRepository.findByIdAndDeletedAtIsNull(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(listingStore.get(inv.getArgument(0)))
                        .filter(l -> l.getDeletedAt() == null));
        lenient().when(listingRepository.save(any(MarketplaceListing.class)))
                .thenAnswer(inv -> {
                    MarketplaceListing l = inv.getArgument(0);
                    if (l.getId() == null) {
                        l.setId(UUID.randomUUID());
                    }
                    listingStore.put(l.getId(), l);
                    return l;
                });
        lenient().when(listingRepository.saveAndFlush(any(MarketplaceListing.class)))
                .thenAnswer(inv -> listingRepository.save(inv.getArgument(0)));

        lenient().when(offerRepository.save(any(MarketplaceOffer.class)))
                .thenAnswer(inv -> {
                    MarketplaceOffer o = inv.getArgument(0);
                    if (o.getId() == null) {
                        o.setId(UUID.randomUUID());
                    }
                    if (o.getCreatedAt() == null) {
                        o.setCreatedAt(Instant.now());
                    }
                    o.setUpdatedAt(Instant.now());
                    offerStore.put(o.getId(), o);
                    return o;
                });
        lenient().when(offerRepository.saveAndFlush(any(MarketplaceOffer.class)))
                .thenAnswer(inv -> offerRepository.save(inv.getArgument(0)));
        lenient().when(offerRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(offerStore.get(inv.getArgument(0))));
        lenient().when(offerRepository.existsByListingIdAndBuyerUserIdAndStatusIn(any(), any(), any()))
                .thenAnswer(inv -> {
                    UUID lId = inv.getArgument(0);
                    UUID bId = inv.getArgument(1);
                    List<?> statuses = inv.getArgument(2);
                    return offerStore.values().stream()
                            .anyMatch(o -> o.getListingId().equals(lId)
                                    && o.getBuyerUserId().equals(bId)
                                    && statuses.contains(o.getStatus()));
                });

        lenient().when(orderRepository.save(any(MarketplaceOrder.class)))
                .thenAnswer(inv -> {
                    MarketplaceOrder ord = inv.getArgument(0);
                    if (ord.getId() == null) {
                        ord.setId(UUID.randomUUID());
                    }
                    if (ord.getCreatedAt() == null) {
                        ord.setCreatedAt(Instant.now());
                    }
                    ord.setUpdatedAt(Instant.now());
                    orderStore.put(ord.getId(), ord);
                    return ord;
                });
        lenient().when(orderRepository.saveAndFlush(any(MarketplaceOrder.class)))
                .thenAnswer(inv -> orderRepository.save(inv.getArgument(0)));
        lenient().when(orderRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(orderStore.get(inv.getArgument(0))));

        lenient().when(orderItemRepository.save(any(MarketplaceOrderItem.class)))
                .thenAnswer(inv -> {
                    MarketplaceOrderItem item = inv.getArgument(0);
                    if (item.getId() == null) {
                        item.setId(UUID.randomUUID());
                    }
                    orderItemStore.add(item);
                    return item;
                });
        lenient().when(orderItemRepository.findByMarketOrderId(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID ordId = inv.getArgument(0);
                    return orderItemStore.stream().filter(i -> i.getMarketOrderId().equals(ordId)).toList();
                });

        lenient().when(sellerReviewRepository.existsByMarketOrderId(any(UUID.class)))
                .thenAnswer(inv -> reviewByOrderStore.containsKey(inv.getArgument(0)));
        lenient().when(sellerReviewRepository.saveAndFlush(any(SellerReview.class)))
                .thenAnswer(inv -> {
                    SellerReview r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(UUID.randomUUID());
                    }
                    r.setCreatedAt(Instant.now());
                    reviewByOrderStore.put(r.getMarketOrderId(), r);
                    return r;
                });

        lenient().when(messagingFacade.getOrCreateConversation(anyString(), any(UUID.class), anyList()))
                .thenReturn(UUID.randomUUID());
        lenient().when(messagingFacade.sendOfferCardMessage(any(OfferCardMessageRequest.class)))
                .thenReturn(UUID.randomUUID());

        UserAddressDto buyerAddr = new UserAddressDto(
                buyerAddressId, buyerId, "Lê Hoàng Nam", "0909123456",
                "12 Nguyễn Huệ, Quận 1, TP.HCM", "26734", "760", "79", 10.7740, 106.7030, true
        );
        UserAddressDto sellerAddr = new UserAddressDto(
                sellerAddressId, sellerId, "Trần Minh Quân", "0988776655",
                "120 Nguyễn Đình Chiểu, Quận 1, TP.HCM", "26737", "760", "79", 10.7810, 106.6950, true
        );
        lenient().when(iamFacade.findAddressById(eq(buyerId), eq(buyerAddressId))).thenReturn(Optional.of(buyerAddr));
        lenient().when(iamFacade.findAddressById(eq(sellerId), eq(sellerAddressId))).thenReturn(Optional.of(sellerAddr));
        lenient().when(iamFacade.findDefaultAddress(eq(sellerId))).thenReturn(Optional.of(sellerAddr));
        lenient().when(iamFacade.findDefaultAddress(eq(buyerId))).thenReturn(Optional.of(buyerAddr));

        lenient().when(deliveryFacade.estimateShippingFee(any(ShippingFeeEstimateRequest.class)))
                .thenReturn(new ShippingFeeEstimateDto(new BigDecimal("45000.00"), 4.2, "AHAMOVE"));
        lenient().when(deliveryFacade.createDeliveryTask(any(CreateDeliveryTaskRequest.class)))
                .thenReturn(UUID.randomUUID());

        lenient().when(platformConfigFacade.calculateFeeBreakdown(any(FeeScope.class), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    FeeScope scope = inv.getArgument(0);
                    BigDecimal gross = inv.getArgument(1);
                    BigDecimal fee = gross.multiply(new BigDecimal("0.05"));
                    return new FeeCalculationResult(scope, gross, new BigDecimal("0.05"), BigDecimal.ZERO, fee, gross.subtract(fee));
                });

        lenient().when(storageService.exists(anyString())).thenReturn(true);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        offerService = new ListingOfferService(
                listingRepository,
                offerRepository,
                messagingFacade,
                iamFacade,
                storageService,
                eventPublisher
        );
        orderService = new MarketplaceOrderService(
                listingRepository,
                listingImageRepository,
                offerRepository,
                orderRepository,
                orderItemRepository,
                sellerReviewRepository,
                iamFacade,
                deliveryFacade,
                paymentFacade,
                platformConfigFacade,
                messagingFacade,
                storageService,
                redisTemplate,
                eventPublisher
        );
        paymentAndDeliveryListener = new MarketplacePaymentAndDeliveryListener(orderService);
    }

    @Test
    @DisplayName("Full C2C SWAP_WITH_CASH Offer -> Counter -> Accept -> Escrow Order -> 72h Inspection -> Confirm Release & Review")
    void shouldCompleteFullSwapWithCashOfferToEscrowReleaseAndReviewWorkflow() {
        CreateOfferRequest createOfferReq = new CreateOfferRequest(
                "SWAP_WITH_CASH",
                null,
                swapListingId,
                "Body Olympus OM-4 Ti Black ngoại hình 95%, đo sáng Multi-Spot chuẩn xác, màn trập chuẩn mọi tốc độ",
                new BigDecimal("2500000.00"),
                "Em xin phép giao lưu OM-4 Ti và bù thêm bác 2.5 triệu ạ!"
        );

        OfferCreatedResponse offerCreated = offerService.submitOffer(buyerId, targetListingId, createOfferReq);
        assertThat(offerCreated.status()).isEqualTo("PENDING");
        assertThat(offerCreated.cashTopupVnd()).isEqualByComparingTo("2500000.00");

        RespondOfferRequest counterReq = new RespondOfferRequest(
                "COUNTER",
                new BigDecimal("3000000.00"),
                "Máy bác đẹp quá, bác bù giúp em tròn 3 triệu là anh em chốt giao lưu luôn nhé!"
        );
        OfferUpdatedResponse countered = offerService.respondToOffer(sellerId, offerCreated.offerId(), counterReq);
        assertThat(countered.status()).isEqualTo("COUNTERED");
        assertThat(countered.counterAmountVnd()).isEqualByComparingTo("3000000.00");

        assertThatThrownBy(() -> offerService.respondToOffer(
                sellerId,
                offerCreated.offerId(),
                new RespondOfferRequest("ACCEPT", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        OfferUpdatedResponse accepted = offerService.respondToOffer(
                buyerId,
                offerCreated.offerId(),
                new RespondOfferRequest("ACCEPT", null, "Nhất trí bác ơi, em chốt 3 triệu nhé!")
        );
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.agreedAmountVnd()).isEqualByComparingTo("3000000.00");

        ArgumentCaptor<OfferCardMessageRequest> offerCardCaptor = ArgumentCaptor.forClass(OfferCardMessageRequest.class);
        verify(messagingFacade, times(3)).sendOfferCardMessage(offerCardCaptor.capture());
        assertThat(offerCardCaptor.getAllValues())
                .extracting(OfferCardMessageRequest::status)
                .containsExactly("PENDING", "COUNTERED", "ACCEPTED");

        CreateMarketOrderRequest createOrderReq = new CreateMarketOrderRequest(
                targetListingId,
                offerCreated.offerId(),
                buyerAddressId,
                "AHAMOVE",
                "Nhờ bác bọc chống sốc 3 lớp giúp em"
        );
        MarketOrderCreatedResponse orderCreated = orderService.createOrder(buyerId, createOrderReq);
        assertThat(orderCreated.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(orderCreated.orderType()).isEqualTo("SWAP_EXCHANGE");
        assertThat(orderCreated.itemTotalVnd()).isEqualByComparingTo("3000000.00");
        assertThat(orderCreated.shippingFeeVnd()).isEqualByComparingTo("45000.00");
        assertThat(orderCreated.totalPayableVnd()).isEqualByComparingTo("3045000.00");

        assertThat(listingStore.get(targetListingId).getStatus()).isEqualTo(ListingStatus.RESERVED);
        assertThat(listingStore.get(swapListingId).getStatus()).isEqualTo(ListingStatus.RESERVED);

        paymentAndDeliveryListener.onPaymentSucceeded(new PaymentSucceededEvent(
                UUID.randomUUID(),
                "MARKETPLACE_ORDER",
                orderCreated.id(),
                buyerId,
                orderCreated.totalPayableVnd()
        ));
        assertThat(orderStore.get(orderCreated.id()).getStatus()).isEqualTo(MarketOrderStatus.ESCROW_HELD_AWAITING_SHIP);

        ShipMarketOrderResponse shipped = orderService.shipOrder(
                sellerId,
                orderCreated.id(),
                new ShipMarketOrderRequest(
                        sellerAddressId,
                        List.of("private/marketplace/orders/" + orderCreated.id() + "/seal-01.jpg"),
                        "Gọi trước 10 phút khi tới hẻm"
                )
        );
        assertThat(shipped.orderStatus()).isEqualTo("SHIPPED");
        assertThat(shipped.deliveryTask().taskId()).isNotNull();

        Instant deliveredAt = Instant.now();
        paymentAndDeliveryListener.onMarketParcelDelivered(new MarketParcelDeliveredEvent(
                shipped.deliveryTask().taskId(),
                orderCreated.id(),
                buyerId,
                sellerId,
                deliveredAt
        ));

        MarketplaceOrder inspectingOrder = orderStore.get(orderCreated.id());
        assertThat(inspectingOrder.getStatus()).isEqualTo(MarketOrderStatus.DELIVERED_INSPECTING);
        assertThat(inspectingOrder.getInspectionDeadlineAt())
                .isEqualTo(deliveredAt.plus(72, ChronoUnit.HOURS));

        ConfirmOrderReceivedResponse confirmed = orderService.confirmReceived(
                buyerId,
                orderCreated.id(),
                new ConfirmOrderReceivedRequest("Đã kiểm tra màn trập và đo sáng chuẩn xác như mô tả!")
        );
        assertThat(confirmed.orderStatus()).isEqualTo("COMPLETED");
        assertThat(confirmed.escrowSettlement().escrowStatus()).isEqualTo("RELEASED_TO_PAYEE");
        assertThat(confirmed.escrowSettlement().netPayoutToSellerVnd()).isEqualByComparingTo("2850000.00");
        verify(paymentFacade).releaseEscrow(orderCreated.id(), "MARKETPLACE_ORDER");

        assertThat(listingStore.get(targetListingId).getStatus()).isEqualTo(ListingStatus.SOLD);
        assertThat(listingStore.get(swapListingId).getStatus()).isEqualTo(ListingStatus.SOLD);

        SellerReviewResponse review = orderService.createSellerReview(
                buyerId,
                orderCreated.id(),
                new CreateSellerReviewRequest(
                        (short) 5,
                        true,
                        "Bác Quân rất nhiệt tình, chiếc FM2n đẹp xuất sắc đúng chuẩn Near Mint!"
                )
        );
        assertThat(review.rating()).isEqualTo((short) 5);
        assertThat(review.itemAsDescribed()).isTrue();
    }

    @Test
    @DisplayName("Should reject self-offer and PRICE offer below 50% threshold")
    void shouldRejectSelfOfferAndLowballPriceOffer() {
        assertThatThrownBy(() -> offerService.submitOffer(
                sellerId,
                targetListingId,
                new CreateOfferRequest("PRICE", new BigDecimal("10000000.00"), null, null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_OFFER_OWN_LISTING);

        assertThatThrownBy(() -> offerService.submitOffer(
                buyerId,
                targetListingId,
                new CreateOfferRequest("PRICE", new BigDecimal("4000000.00"), null, null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
