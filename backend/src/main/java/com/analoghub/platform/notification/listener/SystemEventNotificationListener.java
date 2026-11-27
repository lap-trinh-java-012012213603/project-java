package com.analoghub.platform.notification.listener;

import com.analoghub.platform.booking.event.BookingCreatedEvent;
import com.analoghub.platform.community.event.ArticleApprovedForRagEvent;
import com.analoghub.platform.community.event.ArticleRetractedEvent;
import com.analoghub.platform.community.event.ContentReportedEvent;
import com.analoghub.platform.community.event.WorkshopRegisteredEvent;
import com.analoghub.platform.delivery.event.DeliveryFailedEvent;
import com.analoghub.platform.delivery.event.FilmBagDeliveredToLabEvent;
import com.analoghub.platform.delivery.event.FilmBagPickedUpEvent;
import com.analoghub.platform.delivery.event.MarketParcelDeliveredEvent;
import com.analoghub.platform.delivery.event.NegativeReturnedToCustomerEvent;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.iam.event.RoleApplicationApprovedEvent;
import com.analoghub.platform.iam.event.RoleApplicationSubmittedEvent;
import com.analoghub.platform.iam.event.UserRegisteredEvent;
import com.analoghub.platform.labworkflow.event.LabWorkflowEvent;
import com.analoghub.platform.marketplace.event.ListingArchivedEvent;
import com.analoghub.platform.marketplace.event.ListingPublishedEvent;
import com.analoghub.platform.marketplace.event.ListingUpdatedEvent;
import com.analoghub.platform.marketplace.event.OfferAcceptedEvent;
import com.analoghub.platform.marketplace.event.OfferSubmittedEvent;
import com.analoghub.platform.messaging.event.MessageSentEvent;
import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.payment.event.BlankRollRefundProcessedEvent;
import com.analoghub.platform.payment.event.EscrowReleasedEvent;
import com.analoghub.platform.payment.event.PaymentFailedEvent;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.event.PayoutCompletedEvent;
import com.analoghub.platform.payment.event.RefundProcessedEvent;
import com.analoghub.platform.platformconfig.event.SystemConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SystemEventNotificationListener {

    private static final UUID SYSTEM_ADMIN_RECIPIENT_ID = new UUID(0L, 1L);

    private final NotificationFacade notificationFacade;
    private final Set<String> processedEventKeys = ConcurrentHashMap.newKeySet();

    @ApplicationModuleListener
    public void onSystemConfigChanged(SystemConfigChangedEvent event) {
        if (event == null) {
            return;
        }
        UUID recipientId = event.changedBy() != null ? event.changedBy() : SYSTEM_ADMIN_RECIPIENT_ID;
        dispatchIdempotent(new SendNotificationRequest(
                recipientId,
                "SYSTEM_ANNOUNCEMENT",
                "Cấu hình hệ thống đã được cập nhật",
                "Tham số '" + event.configKey() + "' đã được cập nhật sang giá trị mới: " + event.newValue(),
                Map.of(
                        "targetScreen", "ADMIN_PLATFORM_CONFIG",
                        "configKey", event.configKey() != null ? event.configKey() : ""
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.userId(),
                "SYSTEM_ANNOUNCEMENT",
                "Chào mừng bạn đến với cộng đồng nhiếp ảnh film AnalogHub!",
                "Xin chào " + event.fullName() + ", tài khoản của bạn đã sẵn sàng để đặt lịch tráng film, giao lưu máy ảnh C2C và trao đổi kiến thức buồng tối.",
                Map.of("targetScreen", "HOME"),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onRoleApplicationSubmitted(RoleApplicationSubmittedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.userId(),
                "SYSTEM_ANNOUNCEMENT",
                "Đã tiếp nhận hồ sơ đăng ký nâng cấp vai trò " + event.requestedRole(),
                "Hồ sơ của bạn đang được Ban quản trị AnalogHub xét duyệt. Kết quả sẽ được thông báo sớm nhất.",
                Map.of(
                        "targetScreen", "ROLE_APPLICATION_STATUS",
                        "applicationId", event.applicationId(),
                        "requestedRole", event.requestedRole()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onRoleApplicationApproved(RoleApplicationApprovedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.userId(),
                "SYSTEM_ANNOUNCEMENT",
                "Chúc mừng! Đơn nâng cấp vai trò " + event.approvedRole() + " đã được phê duyệt",
                "Tài khoản của bạn đã chính thức được cấp quyền " + event.approvedRole() + " trên hệ thống AnalogHub.",
                Map.of(
                        "targetScreen", "PROFILE_ROLES",
                        "applicationId", event.applicationId(),
                        "approvedRole", event.approvedRole()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.payerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.payerId(),
                "WALLET_ESCROW",
                "Thanh toán giao dịch thành công",
                "Khoản thanh toán " + event.amountVnd() + " VND (" + event.paymentPurpose() + ") đã được bảo vệ trong tài khoản tạm giữ Escrow.",
                Map.of(
                        "targetScreen", "TRANSACTION_DETAIL",
                        "txnId", event.txnId(),
                        "referenceId", event.referenceId(),
                        "paymentPurpose", event.paymentPurpose()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.referenceId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.referenceId(),
                "WALLET_ESCROW",
                "Giao dịch thanh toán không thành công",
                "Giao dịch thanh toán (" + event.paymentPurpose() + ") chưa hoàn tất: " + event.reason(),
                Map.of(
                        "targetScreen", "PAYMENT_RETRY",
                        "txnId", event.txnId(),
                        "referenceId", event.referenceId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onEscrowReleased(EscrowReleasedEvent event) {
        if (event == null || event.payeeUserId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.payeeUserId(),
                "WALLET_ESCROW",
                "Giải ngân tiền tạm giữ Escrow thành công!",
                "Số tiền doanh thu ròng " + event.netVnd() + " VND đã được cộng vào Ví AnalogHub của bạn.",
                Map.of(
                        "targetScreen", "WALLET_DETAIL",
                        "escrowId", event.escrowId(),
                        "referenceId", event.referenceId(),
                        "referenceType", event.referenceType()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onBlankRollRefundProcessed(BlankRollRefundProcessedEvent event) {
        if (event == null || event.customerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.customerId(),
                "BLANK_ROLL_REFUND",
                "Hoàn tự động 100% phí scan cho cuộn film trắng",
                "Cuộn film không lên hình sau khi tráng. Hệ thống đã hoàn trả tự động " + event.refundedScanFeeVnd() + " VND phí scan vào Ví AnalogHub của bạn.",
                Map.of(
                        "targetScreen", "LAB_ORDER_DETAIL",
                        "orderId", event.orderId(),
                        "rollId", event.rollId(),
                        "refundedAmountVnd", event.refundedScanFeeVnd()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onRefundProcessed(RefundProcessedEvent event) {
        if (event == null || event.referenceId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.referenceId(),
                "WALLET_ESCROW",
                "Hoàn tiền giao dịch Escrow thành công",
                "Khoản tiền " + event.refundAmountVnd() + " VND cho giao dịch " + event.referenceType() + " đã được hoàn về ví của bạn.",
                Map.of(
                        "targetScreen", "WALLET_DETAIL",
                        "refundId", event.refundId(),
                        "escrowId", event.escrowId(),
                        "referenceId", event.referenceId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onPayoutCompleted(PayoutCompletedEvent event) {
        if (event == null || event.requesterUserId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.requesterUserId(),
                "WALLET_ESCROW",
                "Yêu cầu rút tiền về tài khoản ngân hàng đã hoàn tất",
                "Khoản rút " + event.amountVnd() + " VND (Mã đối soát ngân hàng: " + event.bankRef() + ") đã được chuyển khoản thành công.",
                Map.of(
                        "targetScreen", "PAYOUT_HISTORY",
                        "payoutId", event.payoutId(),
                        "bankRef", event.bankRef() != null ? event.bankRef() : ""
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onFilmBagPickedUp(FilmBagPickedUpEvent event) {
        if (event == null) {
            return;
        }
        if (event.customerId() != null) {
            dispatchIdempotent(new SendNotificationRequest(
                    event.customerId(),
                    "LAB_ORDER_UPDATE",
                    "Tài xế đã nhận túi film của bạn",
                    "Cuộn film thuộc đơn hàng của bạn đang trên đường vận chuyển tới phòng Film Lab.",
                    Map.of(
                            "targetScreen", "LAB_ORDER_DETAIL",
                            "orderId", event.orderId(),
                            "deliveryTaskId", event.taskId()
                    ),
                    event.eventId()
            ));
        }
    }

    @ApplicationModuleListener
    public void onFilmBagDeliveredToLab(FilmBagDeliveredToLabEvent event) {
        if (event == null || event.labId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.labId(),
                "LAB_ORDER_UPDATE",
                "Túi film của khách hàng đã giao tới phòng Lab",
                "Kiện film của đơn hàng " + event.orderId() + " đã được bàn giao tại quầy. Vui lòng quét QR Check-in để bắt đầu quy trình tráng.",
                Map.of(
                        "targetScreen", "LAB_QR_CHECKIN",
                        "orderId", event.orderId(),
                        "labId", event.labId(),
                        "deliveryTaskId", event.taskId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onMarketParcelDelivered(MarketParcelDeliveredEvent event) {
        if (event == null) {
            return;
        }
        if (event.buyerId() != null) {
            dispatchIdempotent(new SendNotificationRequest(
                    event.buyerId(),
                    "MARKETPLACE_ORDER",
                    "Thiết bị C2C đã giao thành công — Bắt đầu 72h kiểm tra máy!",
                    "Bạn có 72 giờ để kiểm tra màn trập, đo sáng và thấu kính trước khi xác nhận giải ngân Escrow cho người bán.",
                    Map.of(
                            "targetScreen", "MARKETPLACE_ORDER_DETAIL",
                            "marketOrderId", event.marketOrderId(),
                            "deliveryTaskId", event.taskId()
                    ),
                    event.eventId()
            ));
        }
        if (event.sellerId() != null) {
            dispatchIdempotent(new SendNotificationRequest(
                    event.sellerId(),
                    "MARKETPLACE_ORDER",
                    "Đơn hàng C2C của bạn đã được giao tới người mua",
                    "Người mua có tối đa 72 giờ kiểm tra thiết bị. Tiền Escrow sẽ tự động giải ngân về ví của bạn sau khi hoàn tất.",
                    Map.of(
                            "targetScreen", "MARKETPLACE_ORDER_DETAIL",
                            "marketOrderId", event.marketOrderId(),
                            "deliveryTaskId", event.taskId()
                    ),
                    event.eventId()
            ));
        }
    }

    @ApplicationModuleListener
    public void onNegativeReturnedToCustomer(NegativeReturnedToCustomerEvent event) {
        if (event == null || event.customerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.customerId(),
                "LAB_ORDER_UPDATE",
                "Âm bản film gốc đã được giao trả thành công!",
                "Tài xế đã hoàn tất giao trả cuộn âm bản đã tráng cho đơn hàng của bạn. Cảm ơn bạn đã đồng hành cùng AnalogHub!",
                Map.of(
                        "targetScreen", "LAB_ORDER_DETAIL",
                        "orderId", event.orderId(),
                        "deliveryTaskId", event.taskId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onDeliveryFailed(DeliveryFailedEvent event) {
        if (event == null || event.referenceId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.referenceId(),
                "LAB_ORDER_UPDATE",
                "Cảnh báo sự cố vận chuyển đơn hàng",
                "Chuyến giao nhận cho đơn (" + event.referenceType() + ") gặp sự cố: " + event.failureReason(),
                Map.of(
                        "targetScreen", "DELIVERY_TRACKING",
                        "deliveryTaskId", event.taskId(),
                        "referenceId", event.referenceId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onFilmLabCreated(FilmLabCreatedEvent event) {
        if (event == null || event.labId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.labId(),
                "SYSTEM_ANNOUNCEMENT",
                "Hồ sơ phòng Lab '" + event.labName() + "' đã được khởi tạo",
                "Phòng Film Lab của bạn đã được ghi nhận trên hệ thống AnalogHub.",
                Map.of(
                        "targetScreen", "LAB_DASHBOARD",
                        "labId", event.labId()
                ),
                event.labId()
        ));
    }

    @ApplicationModuleListener
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event == null || event.labId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.labId(),
                "LAB_ORDER_UPDATE",
                "Phòng Lab nhận được đơn đặt lịch tráng/scan mới",
                "Đơn đặt lịch mới (" + event.bookingId() + ") vừa được khởi tạo trên hệ thống.",
                Map.of(
                        "targetScreen", "LAB_ORDER_DETAIL",
                        "orderId", event.bookingId(),
                        "labId", event.labId()
                ),
                event.bookingId()
        ));
    }

    @ApplicationModuleListener
    public void onLabWorkflowUpdated(LabWorkflowEvent event) {
        if (event == null || event.workflowId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.workflowId(),
                "ROLL_KANBAN_PROGRESS",
                "Cập nhật tiến độ xử lý cuộn film tại buồng tối",
                "Cuộn film của bạn vừa chuyển sang bước: " + event.status(),
                Map.of(
                        "targetScreen", "ROLL_PROGRESS_DETAIL",
                        "workflowId", event.workflowId(),
                        "status", event.status() != null ? event.status() : ""
                ),
                event.workflowId()
        ));
    }

    @ApplicationModuleListener
    public void onMessageSent(MessageSentEvent event) {
        if (event == null || event.recipientIds() == null) {
            return;
        }
        for (UUID recipientId : event.recipientIds()) {
            if (recipientId == null || recipientId.equals(event.senderId())) {
                continue;
            }
            dispatchIdempotent(new SendNotificationRequest(
                    recipientId,
                    "COMMUNITY_ACTIVITY",
                    "Bạn có tin nhắn mới trong hộp thư hội thoại",
                    event.previewText() != null ? event.previewText() : "Bạn vừa nhận được một tin nhắn mới.",
                    Map.of(
                            "targetScreen", "CHAT_CONVERSATION",
                            "conversationId", event.conversationId(),
                            "messageId", event.messageId()
                    ),
                    event.eventId()
            ));
        }
    }

    @ApplicationModuleListener
    public void onListingPublished(ListingPublishedEvent event) {
        if (event == null || event.sellerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.sellerId(),
                "MARKETPLACE_ORDER",
                "Tin đăng thiết bị của bạn đã được xuất bản công khai",
                "Tin đăng '" + event.title() + "' đã hiển thị trên Chợ C2C AnalogHub.",
                Map.of(
                        "targetScreen", "MARKETPLACE_LISTING_DETAIL",
                        "listingId", event.listingId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onListingUpdated(ListingUpdatedEvent event) {
        if (event == null || event.sellerId() == null) {
            return;
        }
        if ("RESERVED".equalsIgnoreCase(event.status())) {
            dispatchIdempotent(new SendNotificationRequest(
                    event.sellerId(),
                    "MARKETPLACE_ORDER",
                    "Thiết bị '" + event.title() + "' vừa được người mua đặt giữ chỗ!",
                    "Một người mua vừa tạo đơn hàng C2C cho thiết bị của bạn và đang tiến hành thanh toán Escrow.",
                    Map.of(
                            "targetScreen", "MARKETPLACE_SELLER_ORDERS",
                            "listingId", event.listingId()
                    ),
                    event.eventId()
            ));
        }
    }

    @ApplicationModuleListener
    public void onListingArchived(ListingArchivedEvent event) {
        if (event == null || event.sellerId() == null) {
            return;
        }
        if ("EXPIRED".equalsIgnoreCase(event.status()) || "HIDDEN_BY_ADMIN".equalsIgnoreCase(event.status())) {
            dispatchIdempotent(new SendNotificationRequest(
                    event.sellerId(),
                    "MARKETPLACE_ORDER",
                    "Tin đăng thiết bị trên Chợ C2C đã chuyển trạng thái " + event.status(),
                    "Tin đăng (" + event.listingId() + ") hiện không còn hiển thị công khai trên sàn.",
                    Map.of(
                            "targetScreen", "MARKETPLACE_MY_LISTINGS",
                            "listingId", event.listingId(),
                            "status", event.status()
                    ),
                    event.eventId()
            ));
        }
    }

    @ApplicationModuleListener
    public void onOfferSubmitted(OfferSubmittedEvent event) {
        if (event == null || event.sellerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.sellerId(),
                "MARKETPLACE_OFFER",
                "Bạn nhận được một đề nghị thương lượng mới trên Chợ C2C",
                "Người mua vừa gửi đề nghị (" + event.offerType() + ") cho thiết bị của bạn.",
                Map.of(
                        "targetScreen", "MARKETPLACE_LISTING_OFFERS",
                        "listingId", event.listingId(),
                        "offerId", event.offerId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onOfferAccepted(OfferAcceptedEvent event) {
        if (event == null || event.buyerId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.buyerId(),
                "MARKETPLACE_OFFER",
                "Đề nghị thương lượng của bạn đã được chấp nhận!",
                "Người bán đã đồng ý mức giá/giao lưu đề xuất. Bạn có thể tạo đơn hàng C2C ngay.",
                Map.of(
                        "targetScreen", "MARKETPLACE_CHECKOUT",
                        "listingId", event.listingId(),
                        "offerId", event.offerId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onArticleApprovedForRag(ArticleApprovedForRagEvent event) {
        if (event == null || event.articleId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                SYSTEM_ADMIN_RECIPIENT_ID,
                "COMMUNITY_ACTIVITY",
                "Bài viết chuyên gia đã được duyệt đồng bộ AI RAG",
                "Bài viết '" + event.title() + "' đã được đưa vào hàng đợi băm chunk và nhúng vector RAG.",
                Map.of(
                        "targetScreen", "COMMUNITY_ARTICLE_DETAIL",
                        "articleId", event.articleId(),
                        "slug", event.slug()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onArticleRetracted(ArticleRetractedEvent event) {
        if (event == null || event.articleId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                SYSTEM_ADMIN_RECIPIENT_ID,
                "COMMUNITY_ACTIVITY",
                "Đã thu hồi bài viết chuyên môn khỏi kho tri thức AI RAG",
                "Bài viết (" + event.slug() + ") đã được gỡ và xóa các đoạn tri thức khỏi bảng vector RAG.",
                Map.of(
                        "targetScreen", "COMMUNITY_ARTICLES",
                        "articleId", event.articleId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onContentReported(ContentReportedEvent event) {
        if (event == null || event.reporterId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.reporterId(),
                "COMMUNITY_ACTIVITY",
                "Đã ghi nhận báo cáo vi phạm tiêu chuẩn cộng đồng",
                "Báo cáo của bạn về nội dung (" + event.targetType() + ") với lý do '" + event.reason() + "' đã được chuyển tới Ban kiểm duyệt.",
                Map.of(
                        "targetScreen", "MODERATION_REPORT_STATUS",
                        "reportId", event.reportId(),
                        "targetId", event.targetId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onWorkshopRegistered(WorkshopRegisteredEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        dispatchIdempotent(new SendNotificationRequest(
                event.userId(),
                "COMMUNITY_ACTIVITY",
                "Vé điện tử QR tham dự Workshop/Photowalk đã sẵn sàng!",
                "Mã check-in QR của bạn là: " + event.checkinCode() + ". Vui lòng xuất trình mã này cho Ban tổ chức khi đến sự kiện.",
                Map.of(
                        "targetScreen", "WORKSHOP_TICKET",
                        "workshopId", event.workshopId(),
                        "registrationId", event.registrationId(),
                        "checkinCode", event.checkinCode()
                ),
                event.eventId()
        ));
    }

    public boolean isEventProcessed(UUID recipientUserId, UUID sourceEventId) {
        if (recipientUserId == null || sourceEventId == null) {
            return false;
        }
        return processedEventKeys.contains(recipientUserId + ":" + sourceEventId);
    }

    public void dispatchIdempotent(SendNotificationRequest request) {
        if (request == null || request.recipientUserId() == null) {
            return;
        }
        if (request.sourceEventId() != null) {
            String dedupKey = request.recipientUserId() + ":" + request.sourceEventId();
            if (!processedEventKeys.add(dedupKey)) {
                return;
            }
        }
        notificationFacade.sendNotification(request);
    }
}
