package com.analoghub.platform.community;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.community.domain.Workshop;
import com.analoghub.platform.community.domain.WorkshopRegistration;
import com.analoghub.platform.community.domain.WorkshopRegistrationStatus;
import com.analoghub.platform.community.domain.WorkshopStatus;
import com.analoghub.platform.community.dto.CreateWorkshopRequest;
import com.analoghub.platform.community.dto.WorkshopCheckInRequest;
import com.analoghub.platform.community.dto.WorkshopCheckInResponse;
import com.analoghub.platform.community.dto.WorkshopRegistrationResponse;
import com.analoghub.platform.community.dto.WorkshopResponse;
import com.analoghub.platform.community.event.WorkshopRegisteredEvent;
import com.analoghub.platform.community.listener.WorkshopTicketPaymentListener;
import com.analoghub.platform.community.repository.WorkshopRegistrationRepository;
import com.analoghub.platform.community.repository.WorkshopRepository;
import com.analoghub.platform.community.service.WorkshopService;
import com.analoghub.platform.iam.dto.AccountStatus;
import com.analoghub.platform.iam.dto.AuthProvider;
import com.analoghub.platform.iam.dto.RoleCode;
import com.analoghub.platform.iam.dto.UserSummaryDto;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.notification.dto.SendNotificationRequest;
import com.analoghub.platform.notification.facade.NotificationFacade;
import com.analoghub.platform.notification.listener.SystemEventNotificationListener;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkshopTicketingIntegrationTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopRegistrationRepository registrationRepository;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotificationFacade notificationFacade;

    private final Map<UUID, Workshop> workshopStore = new ConcurrentHashMap<>();
    private final Map<UUID, WorkshopRegistration> registrationStore = new ConcurrentHashMap<>();

    private WorkshopService workshopService;
    private WorkshopTicketPaymentListener paymentListener;
    private SystemEventNotificationListener notificationListener;

    private UUID organizerId;
    private UUID attendeeId;

    @BeforeEach
    void setUp() {
        workshopStore.clear();
        registrationStore.clear();

        organizerId = UUID.randomUUID();
        attendeeId = UUID.randomUUID();

        lenient().when(workshopRepository.saveAndFlush(any(Workshop.class)))
                .thenAnswer(inv -> {
                    Workshop w = inv.getArgument(0);
                    if (w.getId() == null) {
                        w.setId(UUID.randomUUID());
                    }
                    if (w.getCreatedAt() == null) {
                        w.setCreatedAt(Instant.now());
                    }
                    workshopStore.put(w.getId(), w);
                    return w;
                });
        lenient().when(workshopRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(workshopStore.get(inv.getArgument(0))));

        lenient().when(registrationRepository.saveAndFlush(any(WorkshopRegistration.class)))
                .thenAnswer(inv -> {
                    WorkshopRegistration r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(UUID.randomUUID());
                    }
                    if (r.getCreatedAt() == null) {
                        r.setCreatedAt(Instant.now());
                    }
                    registrationStore.put(r.getId(), r);
                    return r;
                });
        lenient().when(registrationRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(registrationStore.get(inv.getArgument(0))));
        lenient().when(registrationRepository.existsByCheckinCode(anyString()))
                .thenAnswer(inv -> {
                    String code = inv.getArgument(0);
                    return registrationStore.values().stream().anyMatch(r -> r.getCheckinCode().equals(code));
                });
        lenient().when(registrationRepository.existsByWorkshopIdAndUserIdAndStatusNot(any(), any(), any()))
                .thenAnswer(inv -> {
                    UUID wId = inv.getArgument(0);
                    UUID uId = inv.getArgument(1);
                    WorkshopRegistrationStatus excluded = inv.getArgument(2);
                    return registrationStore.values().stream()
                            .anyMatch(r -> r.getWorkshopId().equals(wId)
                                    && r.getUserId().equals(uId)
                                    && r.getStatus() != excluded);
                });
        lenient().when(registrationRepository.findByWorkshopIdAndCheckinCode(any(UUID.class), anyString()))
                .thenAnswer(inv -> {
                    UUID wId = inv.getArgument(0);
                    String code = inv.getArgument(1);
                    return registrationStore.values().stream()
                            .filter(r -> r.getWorkshopId().equals(wId) && r.getCheckinCode().equalsIgnoreCase(code))
                            .findFirst();
                });

        lenient().when(iamFacade.findUserById(any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    return Optional.of(new UserSummaryDto(
                            id, "attendee@analoghub.vn", "0909123456", "Lê Hoàng Nam",
                            null, AccountStatus.ACTIVE, AuthProvider.LOCAL,
                            Set.of(RoleCode.CUSTOMER), Map.of(), Instant.now()
                    ));
                });

        workshopService = new WorkshopService(
                workshopRepository,
                registrationRepository,
                iamFacade,
                storageService,
                eventPublisher
        );
        paymentListener = new WorkshopTicketPaymentListener(workshopService);
        notificationListener = new SystemEventNotificationListener(notificationFacade);
    }

    @Test
    @DisplayName("Paid Workshop registration -> PaymentSucceededEvent -> QR Check-in -> Outbox notification idempotency")
    void shouldCompletePaidWorkshopRegistrationPaymentAndQrCheckInWithIdempotentOutboxNotification() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);

        WorkshopResponse workshop = workshopService.createWorkshop(
                organizerId,
                new CreateWorkshopRequest(
                        "WORKSHOP",
                        null,
                        "Workshop Thực Hành Tráng & Phóng Ảnh Đen Trắng Thủ Công Trong Buồng Tối",
                        "Buổi thực hành trực tiếp 4 tiếng trong buồng tối ánh sáng đỏ: tự tay quấn film vào reel và lắc tank tráng Ilford HP5 Plus.",
                        "Analog Saigon Darkroom — 42 Tôn Thất Thiệp, Quận 1, TP.HCM",
                        start,
                        end,
                        2,
                        new BigDecimal("350000.00")
                )
        );
        assertThat(workshop.status()).isEqualTo(WorkshopStatus.OPEN.name());
        assertThat(workshop.currentParticipants()).isZero();

        WorkshopRegistrationResponse reg = workshopService.registerWorkshop(attendeeId, workshop.id());
        assertThat(reg.status()).isEqualTo(WorkshopRegistrationStatus.PENDING_PAYMENT.name());
        assertThat(reg.checkinCode()).startsWith("WS-");
        assertThat(workshopStore.get(workshop.id()).getCurrentParticipants()).isEqualTo(1);

        assertThatThrownBy(() -> workshopService.checkInParticipant(
                organizerId,
                false,
                workshop.id(),
                new WorkshopCheckInRequest(reg.checkinCode())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        paymentListener.onPaymentSucceeded(new PaymentSucceededEvent(
                UUID.randomUUID(),
                "WORKSHOP_TICKET",
                reg.registrationId(),
                attendeeId,
                new BigDecimal("350000.00")
        ));

        assertThat(registrationStore.get(reg.registrationId()).getStatus())
                .isEqualTo(WorkshopRegistrationStatus.CONFIRMED);

        ArgumentCaptor<WorkshopRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(WorkshopRegisteredEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        WorkshopRegisteredEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.checkinCode()).isEqualTo(reg.checkinCode());

        notificationListener.onWorkshopRegistered(publishedEvent);
        notificationListener.onWorkshopRegistered(publishedEvent);

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationFacade, times(1)).sendNotification(notifCaptor.capture());
        assertThat(notifCaptor.getValue().recipientUserId()).isEqualTo(attendeeId);
        assertThat(notifCaptor.getValue().sourceEventId()).isEqualTo(publishedEvent.eventId());

        WorkshopCheckInResponse checkedIn = workshopService.checkInParticipant(
                organizerId,
                false,
                workshop.id(),
                new WorkshopCheckInRequest(reg.checkinCode())
        );
        assertThat(checkedIn.status()).isEqualTo(WorkshopRegistrationStatus.ATTENDED.name());
        assertThat(checkedIn.checkedInAt()).isNotNull();

        assertThatThrownBy(() -> workshopService.checkInParticipant(
                organizerId,
                false,
                workshop.id(),
                new WorkshopCheckInRequest(reg.checkinCode())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("Should enforce workshop max capacity and block duplicate user registration")
    void shouldEnforceCapacityLimitAndBlockDuplicateRegistration() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        WorkshopResponse photowalk = workshopService.createWorkshop(
                organizerId,
                new CreateWorkshopRequest(
                        "PHOTOWALK",
                        null,
                        "Photowalk Bình Minh Sài Gòn Cùng Máy Cơ Film",
                        "Dạo phố chụp ảnh film buổi sáng sớm quanh Nhà thờ Đức Bà, Bưu điện Thành phố và Bến Bạch Đằng.",
                        "Bưu điện Trung tâm Sài Gòn, Quận 1",
                        start,
                        end,
                        2,
                        BigDecimal.ZERO
                )
        );

        WorkshopRegistrationResponse reg1 = workshopService.registerWorkshop(attendeeId, photowalk.id());
        assertThat(reg1.status()).isEqualTo(WorkshopRegistrationStatus.CONFIRMED.name());

        assertThatThrownBy(() -> workshopService.registerWorkshop(attendeeId, photowalk.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_JOINED_EVENT);

        UUID attendee2 = UUID.randomUUID();
        workshopService.registerWorkshop(attendee2, photowalk.id());
        assertThat(workshopStore.get(photowalk.id()).getStatus()).isEqualTo(WorkshopStatus.FULL);

        UUID attendee3 = UUID.randomUUID();
        assertThatThrownBy(() -> workshopService.registerWorkshop(attendee3, photowalk.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_CAPACITY_FULL);
    }
}
