package com.analoghub.platform.notification.listener;

import com.analoghub.platform.community.event.WorkshopRegisteredEvent;
import com.analoghub.platform.iam.event.RoleApplicationApprovedEvent;
import com.analoghub.platform.iam.event.UserRegisteredEvent;
import com.analoghub.platform.marketplace.event.OfferAcceptedEvent;
import com.analoghub.platform.marketplace.event.OfferSubmittedEvent;
import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.service.NotificationDispatcherService;
import com.analoghub.platform.payment.event.BlankRollRefundProcessedEvent;
import com.analoghub.platform.payment.event.EscrowReleasedEvent;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.event.PayoutCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CoreNotificationEventListener {

    private final NotificationDispatcherService dispatcherService;

    @ApplicationModuleListener
    public void onUserRegistered(UserRegisteredEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.userId(),
                "SYSTEM_ANNOUNCEMENT",
                "Chào mừng bạn đến với cộng đồng nhiếp ảnh film AnalogHub!",
                "Xin chào " + event.fullName() + ", hãy bắt đầu khám phá các phòng Film Lab, chợ thiết bị C2C và kho tri thức buồng tối.",
                Map.of("targetScreen", "HOME"),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onRoleApplicationApproved(RoleApplicationApprovedEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.userId(),
                "SYSTEM_ANNOUNCEMENT",
                "Đơn đăng ký nâng cấp vai trò " + event.approvedRole() + " đã được phê duyệt!",
                "Tài khoản của bạn đã được cấp quyền " + event.approvedRole() + " trên hệ thống AnalogHub.",
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
        dispatcherService.dispatch(new SendNotificationRequest(
                event.payerId(),
                "WALLET_ESCROW",
                "Thanh toán giao dịch thành công",
                "Khoản thanh toán " + event.amountVnd() + " VND cho mục đích " + event.paymentPurpose() + " đã được ghi nhận vào hệ thống.",
                Map.of(
                        "targetScreen", "TRANSACTION_DETAIL",
                        "txnId", event.txnId(),
                        "referenceId", event.referenceId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onEscrowReleased(EscrowReleasedEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.payeeUserId(),
                "WALLET_ESCROW",
                "Giải ngân tiền tạm giữ Escrow thành công!",
                "Số tiền ròng " + event.netVnd() + " VND đã được cộng vào Ví AnalogHub của bạn.",
                Map.of(
                        "targetScreen", "WALLET_DETAIL",
                        "escrowId", event.escrowId(),
                        "referenceId", event.referenceId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onBlankRollRefundProcessed(BlankRollRefundProcessedEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.customerId(),
                "BLANK_ROLL_REFUND",
                "Hoàn tự động 100% phí scan cho cuộn film trắng",
                "Hệ thống đã hoàn trả " + event.refundedScanFeeVnd() + " VND phí scan vào Ví AnalogHub của bạn.",
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
    public void onPayoutCompleted(PayoutCompletedEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.requesterUserId(),
                "WALLET_ESCROW",
                "Yêu cầu rút tiền về ngân hàng đã hoàn tất",
                "Khoản rút " + event.amountVnd() + " VND (Mã tham chiếu: " + event.bankRef() + ") đã được chuyển khoản thành công.",
                Map.of(
                        "targetScreen", "PAYOUT_HISTORY",
                        "payoutId", event.payoutId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onOfferSubmitted(OfferSubmittedEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.sellerId(),
                "MARKETPLACE_OFFER",
                "Bạn nhận được một đề nghị thương lượng mới trên Chợ C2C",
                "Có người mua vừa gửi đề nghị (" + event.offerType() + ") cho thiết bị của bạn.",
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
        dispatcherService.dispatch(new SendNotificationRequest(
                event.buyerId(),
                "MARKETPLACE_OFFER",
                "Đề nghị thương lượng của bạn đã được chấp nhận!",
                "Người bán đã đồng ý mức thỏa thuận. Bạn có thể tiến hành tạo đơn hàng C2C ngay.",
                Map.of(
                        "targetScreen", "MARKETPLACE_CHECKOUT",
                        "listingId", event.listingId(),
                        "offerId", event.offerId()
                ),
                event.eventId()
        ));
    }

    @ApplicationModuleListener
    public void onWorkshopRegistered(WorkshopRegisteredEvent event) {
        dispatcherService.dispatch(new SendNotificationRequest(
                event.userId(),
                "COMMUNITY_ACTIVITY",
                "Vé tham dự sự kiện Workshop/Photowalk đã sẵn sàng!",
                "Mã check-in QR của bạn là: " + event.checkinCode() + ". Vui lòng xuất trình mã này khi đến sự kiện.",
                Map.of(
                        "targetScreen", "WORKSHOP_TICKET",
                        "workshopId", event.workshopId(),
                        "registrationId", event.registrationId(),
                        "checkinCode", event.checkinCode()
                ),
                event.eventId()
        ));
    }
}
