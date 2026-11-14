package com.analoghub.platform.payment.gateway;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.DuplicateResourceException;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.payment.controller.PaymentController;
import com.analoghub.platform.payment.controller.VnpayWebhookController;
import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.LedgerEntryDirection;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransaction;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;
import com.analoghub.platform.payment.domain.PaymentWebhookLog;
import com.analoghub.platform.payment.domain.Wallet;
import com.analoghub.platform.payment.domain.WalletLedgerEntry;
import com.analoghub.platform.payment.domain.WalletType;
import com.analoghub.platform.payment.dto.CheckoutResponse;
import com.analoghub.platform.payment.dto.CreateCheckoutRequest;
import com.analoghub.platform.payment.dto.TransactionStatusResponse;
import com.analoghub.platform.payment.dto.VnpayIpnResponse;
import com.analoghub.platform.payment.event.PaymentFailedEvent;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PaymentTransactionRepository;
import com.analoghub.platform.payment.repository.PaymentWebhookLogRepository;
import com.analoghub.platform.payment.repository.WalletLedgerEntryRepository;
import com.analoghub.platform.payment.repository.WalletRepository;
import com.analoghub.platform.payment.service.PaymentCheckoutService;
import com.analoghub.platform.payment.service.WalletLedgerService;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import com.analoghub.platform.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VnpayGatewayAndWebhookTest {

    private static final String TEST_TMN_CODE = "ANALOG01";
    private static final String TEST_HASH_SECRET = "VNPAY_SUPER_SECRET_HMAC_SHA512_KEY_2026_ANALOGHUB";
    private static final String TEST_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String TEST_RETURN_URL = "https://app.analoghub.vn/payment/result";

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private EscrowHoldRepository escrowHoldRepository;

    @Mock
    private PaymentWebhookLogRepository paymentWebhookLogRepository;

    @Mock
    private IamFacade iamFacade;

    @Mock
    private PlatformConfigFacade platformConfigFacade;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private VnpayProperties vnpayProperties;
    private VnpayGatewayService vnpayGatewayService;
    private WalletLedgerService walletLedgerService;
    private PaymentCheckoutService paymentCheckoutService;
    private PaymentController paymentController;
    private VnpayWebhookController vnpayWebhookController;

    private final Map<UUID, Wallet> walletStore = new HashMap<>();
    private final List<WalletLedgerEntry> ledgerStore = new ArrayList<>();
    private final Map<String, PaymentTransaction> txnByCodeStore = new HashMap<>();
    private final Map<UUID, EscrowHold> escrowByRefStore = new HashMap<>();
    private final Map<String, PaymentWebhookLog> webhookLogStore = new HashMap<>();

    private Wallet systemEscrowWallet;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        walletStore.clear();
        ledgerStore.clear();
        txnByCodeStore.clear();
        escrowByRefStore.clear();
        webhookLogStore.clear();

        vnpayProperties = new VnpayProperties();
        vnpayProperties.setTmnCode(TEST_TMN_CODE);
        vnpayProperties.setHashSecret(TEST_HASH_SECRET);
        vnpayProperties.setPayUrl(TEST_PAY_URL);
        vnpayProperties.setReturnUrl(TEST_RETURN_URL);
        vnpayProperties.setTimeoutMinutes(15);

        vnpayGatewayService = new VnpayGatewayService(vnpayProperties, platformConfigFacade);
        walletLedgerService = new WalletLedgerService(walletRepository, walletLedgerEntryRepository);
        paymentCheckoutService = new PaymentCheckoutService(
                paymentTransactionRepository,
                escrowHoldRepository,
                paymentWebhookLogRepository,
                walletLedgerService,
                vnpayGatewayService,
                iamFacade,
                platformConfigFacade,
                eventPublisher
        );
        paymentController = new PaymentController(paymentCheckoutService);
        vnpayWebhookController = new VnpayWebhookController(paymentCheckoutService);

        systemEscrowWallet = Wallet.builder()
                .ownerUserId(null)
                .walletType(WalletType.SYSTEM_ESCROW)
                .availableBalanceVnd(new BigDecimal("0.00"))
                .lockedBalanceVnd(new BigDecimal("0.00"))
                .currency("VND")
                .build();
        systemEscrowWallet.setId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        walletStore.put(systemEscrowWallet.getId(), systemEscrowWallet);

        setupRepositoryMocks();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupRepositoryMocks() {
        lenient().when(iamFacade.isActiveUser(any(UUID.class))).thenReturn(true);
        lenient().when(platformConfigFacade.getString(anyString())).thenReturn(Optional.empty());
        lenient().when(platformConfigFacade.getIntOrDefault(anyString(), any(Integer.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        lenient().when(walletRepository.findByWalletType(any(WalletType.class))).thenAnswer(inv -> {
            WalletType type = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> w.getWalletType() == type).findFirst();
        });

        lenient().when(walletRepository.findByOwnerUserId(any(UUID.class))).thenAnswer(inv -> {
            UUID ownerId = inv.getArgument(0);
            return walletStore.values().stream().filter(w -> ownerId.equals(w.getOwnerUserId())).findFirst();
        });

        lenient().when(walletRepository.findAllByIdInOrderByIdAscForUpdate(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.stream()
                    .map(walletStore::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(w -> w.getId().toString()))
                    .toList();
        });

        lenient().when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            walletStore.put(w.getId(), w);
            return w;
        });

        lenient().when(walletRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<Wallet> wallets = inv.getArgument(0);
            wallets.forEach(w -> {
                if (w.getId() == null) {
                    w.setId(UUID.randomUUID());
                }
                walletStore.put(w.getId(), w);
            });
            return new ArrayList<>(wallets);
        });

        lenient().when(walletLedgerEntryRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            Collection<WalletLedgerEntry> entries = inv.getArgument(0);
            List<WalletLedgerEntry> saved = new ArrayList<>();
            for (WalletLedgerEntry e : entries) {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
                ledgerStore.add(e);
                saved.add(e);
            }
            return saved;
        });

        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction txn = inv.getArgument(0);
            if (txn.getId() == null) {
                txn.setId(UUID.randomUUID());
            }
            if (txn.getCreatedAt() == null) {
                txn.setCreatedAt(Instant.now());
            }
            txnByCodeStore.put(txn.getTxnCode(), txn);
            return txn;
        });

        lenient().when(paymentTransactionRepository.findByTxnCode(anyString())).thenAnswer(inv ->
                Optional.ofNullable(txnByCodeStore.get(inv.getArgument(0))));

        lenient().when(paymentTransactionRepository.findByTxnCodeForUpdate(anyString())).thenAnswer(inv ->
                Optional.ofNullable(txnByCodeStore.get(inv.getArgument(0))));

        lenient().when(paymentTransactionRepository.existsByPaymentPurposeAndReferenceIdAndStatus(
                any(PaymentPurpose.class), any(UUID.class), any(PaymentTransactionStatus.class)
        )).thenAnswer(inv -> {
            PaymentPurpose purpose = inv.getArgument(0);
            UUID refId = inv.getArgument(1);
            PaymentTransactionStatus status = inv.getArgument(2);
            return txnByCodeStore.values().stream()
                    .anyMatch(t -> t.getPaymentPurpose() == purpose
                            && refId.equals(t.getReferenceId())
                            && t.getStatus() == status);
        });

        lenient().when(paymentTransactionRepository.findByPaymentPurposeAndReferenceIdAndStatus(
                any(PaymentPurpose.class), any(UUID.class), any(PaymentTransactionStatus.class)
        )).thenAnswer(inv -> {
            PaymentPurpose purpose = inv.getArgument(0);
            UUID refId = inv.getArgument(1);
            PaymentTransactionStatus status = inv.getArgument(2);
            return txnByCodeStore.values().stream()
                    .filter(t -> t.getPaymentPurpose() == purpose
                            && refId.equals(t.getReferenceId())
                            && t.getStatus() == status)
                    .toList();
        });

        lenient().when(escrowHoldRepository.save(any(EscrowHold.class))).thenAnswer(inv -> {
            EscrowHold hold = inv.getArgument(0);
            if (hold.getId() == null) {
                hold.setId(UUID.randomUUID());
            }
            escrowByRefStore.put(hold.getReferenceId(), hold);
            return hold;
        });

        lenient().when(escrowHoldRepository.findByReferenceId(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(escrowByRefStore.get(inv.getArgument(0))));

        lenient().when(escrowHoldRepository.findByPaymentTxnId(any(UUID.class))).thenAnswer(inv -> {
            UUID txnId = inv.getArgument(0);
            return escrowByRefStore.values().stream()
                    .filter(e -> txnId.equals(e.getPaymentTxnId()))
                    .findFirst();
        });

        lenient().when(paymentWebhookLogRepository.findByEventIdempotencyKey(anyString())).thenAnswer(inv ->
                Optional.ofNullable(webhookLogStore.get(inv.getArgument(0))));

        lenient().when(paymentWebhookLogRepository.save(any(PaymentWebhookLog.class))).thenAnswer(inv -> {
            PaymentWebhookLog log = inv.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            webhookLogStore.put(log.getEventIdempotencyKey(), log);
            return log;
        });
    }

    private void authenticateAs(UUID userId, Set<String> roles) {
        AuthenticatedUser principal = AuthenticatedUser.builder()
                .userId(userId)
                .jti(UUID.randomUUID().toString())
                .roles(roles)
                .labIds(Set.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    @DisplayName("POST /api/v1/payments/checkout-url + VNPay IPN Webhook should verify HMAC-SHA512, credit SYSTEM_ESCROW, create EscrowHold (HELD) and publish PaymentSucceededEvent")
    void shouldCreateVnpayCheckoutAndProcessIpnWebhookIntoEscrowAndOutbox() {
        UUID payerUserId = UUID.randomUUID();
        UUID payeeUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticateAs(payerUserId, Set.of("CUSTOMER"));

        CreateCheckoutRequest checkoutRequest = new CreateCheckoutRequest(
                PaymentPurpose.LAB_ORDER,
                orderId,
                "LAB-20261105-0452",
                payeeUserId,
                new BigDecimal("315000.00"),
                GatewayProvider.VNPAY,
                "VNBANK",
                "https://app.analoghub.vn/orders/" + orderId + "/payment-result",
                "vn"
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("113.161.72.15");

        ResponseEntity<ApiResponse<CheckoutResponse>> checkoutResp =
                paymentController.createCheckout(checkoutRequest, servletRequest);

        assertThat(checkoutResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkoutResp.getBody()).isNotNull();
        CheckoutResponse checkoutData = checkoutResp.getBody().getData();
        assertThat(checkoutData.status()).isEqualTo(PaymentTransactionStatus.INITIATED);
        assertThat(checkoutData.txnCode()).startsWith("PAY");
        assertThat(checkoutData.checkoutUrl())
                .startsWith(TEST_PAY_URL)
                .contains("vnp_Amount=31500000")
                .contains("vnp_TmnCode=" + TEST_TMN_CODE)
                .contains("vnp_TxnRef=" + checkoutData.txnCode())
                .contains("vnp_SecureHash=");
        assertThat(checkoutData.escrowHoldId()).isNull();

        // Simulate VNPay IPN callback with valid HMAC-SHA512 signature
        Map<String, String> ipnParams = new LinkedHashMap<>();
        ipnParams.put("vnp_Amount", "31500000");
        ipnParams.put("vnp_BankCode", "NCB");
        ipnParams.put("vnp_BankTranNo", "VNP14398512");
        ipnParams.put("vnp_CardType", "ATM");
        ipnParams.put("vnp_OrderInfo", "Thanh toan LAB_ORDER LAB-20261105-0452");
        ipnParams.put("vnp_PayDate", "20261105034800");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TmnCode", TEST_TMN_CODE);
        ipnParams.put("vnp_TransactionNo", "14398512");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TxnRef", checkoutData.txnCode());
        String validHash = vnpayGatewayService.computeSecureHash(ipnParams);
        ipnParams.put("vnp_SecureHash", validHash);

        ResponseEntity<VnpayIpnResponse> ipnResponse = vnpayWebhookController.handleIpn(ipnParams);
        assertThat(ipnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ipnResponse.getBody()).isNotNull();
        assertThat(ipnResponse.getBody().rspCode()).isEqualTo("00");
        assertThat(ipnResponse.getBody().message()).isEqualTo("Confirm Success");

        // 1. Verify SYSTEM_ESCROW wallet balance increased by 315,000.00 VND
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("315000.00");

        // 2. Verify Double-Entry Ledger Entries (1 DEBIT + 1 CREDIT with same journalGroupId)
        assertThat(ledgerStore).hasSize(2);
        BigDecimal totalDebit = ledgerStore.stream()
                .filter(e -> e.getEntryDirection() == LedgerEntryDirection.DEBIT)
                .map(WalletLedgerEntry::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = ledgerStore.stream()
                .filter(e -> e.getEntryDirection() == LedgerEntryDirection.CREDIT)
                .map(WalletLedgerEntry::getAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebit).isEqualByComparingTo("315000.00");
        assertThat(totalCredit).isEqualByComparingTo("315000.00");

        // 3. Verify pay_escrow_holds created with status HELD and correct payeeUserId
        EscrowHold createdEscrow = escrowByRefStore.get(orderId);
        assertThat(createdEscrow).isNotNull();
        assertThat(createdEscrow.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(createdEscrow.getPayerUserId()).isEqualTo(payerUserId);
        assertThat(createdEscrow.getPayeeUserId()).isEqualTo(payeeUserId);
        assertThat(createdEscrow.getGrossAmountVnd()).isEqualByComparingTo("315000.00");

        // 4. Verify PaymentSucceededEvent published to Outbox
        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentSucceededEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.txnId()).isEqualTo(checkoutData.transactionId());
        assertThat(publishedEvent.paymentPurpose()).isEqualTo("LAB_ORDER");
        assertThat(publishedEvent.referenceId()).isEqualTo(orderId);
        assertThat(publishedEvent.payerId()).isEqualTo(payerUserId);
        assertThat(publishedEvent.amountVnd()).isEqualByComparingTo("315000.00");

        // 5. Verify duplicate IPN replay returns RspCode 02 without double-crediting Escrow
        reset(eventPublisher);
        ResponseEntity<VnpayIpnResponse> duplicateResponse = vnpayWebhookController.handleIpn(ipnParams);
        assertThat(duplicateResponse.getBody()).isNotNull();
        assertThat(duplicateResponse.getBody().rspCode()).isEqualTo("02");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("315000.00");
        assertThat(ledgerStore).hasSize(2);
        verify(eventPublisher, never()).publishEvent(any());

        // 6. Verify GET /api/v1/payments/transactions/{txnCode} returns SUCCEEDED + escrowHold
        ResponseEntity<ApiResponse<TransactionStatusResponse>> statusResp =
                paymentController.getTransactionStatus(checkoutData.txnCode());
        assertThat(statusResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionStatusResponse statusData = statusResp.getBody().getData();
        assertThat(statusData.status()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
        assertThat(statusData.gatewayTxnNo()).isEqualTo("14398512");
        assertThat(statusData.escrowHold()).isNotNull();
        assertThat(statusData.escrowHold().escrowId()).isEqualTo(createdEscrow.getId());
        assertThat(statusData.escrowHold().status()).isEqualTo(EscrowStatus.HELD);
    }

    @Test
    @DisplayName("VNPay IPN Webhook should reject forged HMAC-SHA512 checksum (RspCode 97), invalid amount (RspCode 04), and unknown order (RspCode 01)")
    void shouldHandleInvalidSignatureAmountMismatchAndOrderNotFoundInWebhook() {
        UUID payerUserId = UUID.randomUUID();
        UUID payeeUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticateAs(payerUserId, Set.of("CUSTOMER"));

        CreateCheckoutRequest checkoutRequest = new CreateCheckoutRequest(
                PaymentPurpose.MARKETPLACE_ORDER,
                orderId,
                "MKT-20261105-9999",
                payeeUserId,
                new BigDecimal("1500000.00"),
                GatewayProvider.VNPAY,
                null,
                null,
                "vn"
        );
        CheckoutResponse checkoutData = paymentController
                .createCheckout(checkoutRequest, new MockHttpServletRequest())
                .getBody()
                .getData();

        // 1. Forged signature -> RspCode 97
        Map<String, String> forgedParams = new LinkedHashMap<>();
        forgedParams.put("vnp_Amount", "150000000");
        forgedParams.put("vnp_BankCode", "VCB");
        forgedParams.put("vnp_OrderInfo", "Thanh toan MARKETPLACE_ORDER MKT-20261105-9999");
        forgedParams.put("vnp_PayDate", "20261105035000");
        forgedParams.put("vnp_ResponseCode", "00");
        forgedParams.put("vnp_TmnCode", TEST_TMN_CODE);
        forgedParams.put("vnp_TransactionNo", "99887766");
        forgedParams.put("vnp_TransactionStatus", "00");
        forgedParams.put("vnp_TxnRef", checkoutData.txnCode());
        forgedParams.put("vnp_SecureHash", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");

        ResponseEntity<VnpayIpnResponse> forgedResp = vnpayWebhookController.handleIpn(forgedParams);
        assertThat(forgedResp.getBody().rspCode()).isEqualTo("97");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("0.00");

        // 2. Wrong amount (100,000 VND instead of 1,500,000 VND) -> RspCode 04
        Map<String, String> wrongAmountParams = new LinkedHashMap<>(forgedParams);
        wrongAmountParams.remove("vnp_SecureHash");
        wrongAmountParams.put("vnp_Amount", "10000000");
        wrongAmountParams.put("vnp_SecureHash", vnpayGatewayService.computeSecureHash(wrongAmountParams));

        ResponseEntity<VnpayIpnResponse> wrongAmountResp = vnpayWebhookController.handleIpn(wrongAmountParams);
        assertThat(wrongAmountResp.getBody().rspCode()).isEqualTo("04");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("0.00");

        // 3. Unknown vnp_TxnRef -> RspCode 01
        Map<String, String> unknownOrderParams = new LinkedHashMap<>(forgedParams);
        unknownOrderParams.remove("vnp_SecureHash");
        unknownOrderParams.put("vnp_TxnRef", "PAY202611059999999999");
        unknownOrderParams.put("vnp_SecureHash", vnpayGatewayService.computeSecureHash(unknownOrderParams));

        ResponseEntity<VnpayIpnResponse> unknownResp = vnpayWebhookController.handleIpn(unknownOrderParams);
        assertThat(unknownResp.getBody().rspCode()).isEqualTo("01");

        // 4. Failed payment on VNPay (vnp_ResponseCode = "24") -> RspCode 00 + PaymentFailedEvent
        Map<String, String> failedPaymentParams = new LinkedHashMap<>(forgedParams);
        failedPaymentParams.remove("vnp_SecureHash");
        failedPaymentParams.put("vnp_ResponseCode", "24");
        failedPaymentParams.put("vnp_TransactionStatus", "02");
        failedPaymentParams.put("vnp_SecureHash", vnpayGatewayService.computeSecureHash(failedPaymentParams));

        ResponseEntity<VnpayIpnResponse> failedResp = vnpayWebhookController.handleIpn(failedPaymentParams);
        assertThat(failedResp.getBody().rspCode()).isEqualTo("00");
        assertThat(txnByCodeStore.get(checkoutData.txnCode()).getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);

        ArgumentCaptor<PaymentFailedEvent> failedEventCaptor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishEvent(failedEventCaptor.capture());
        assertThat(failedEventCaptor.getValue().referenceId()).isEqualTo(orderId);
        assertThat(failedEventCaptor.getValue().reason()).isEqualTo("VNPAY_RESPONSE_24");
    }

    @Test
    @DisplayName("INTERNAL_WALLET checkout should debit user wallet, credit SYSTEM_ESCROW, create EscrowHold and publish PaymentSucceededEvent immediately")
    void shouldProcessInternalWalletCheckoutAndEnforceGuards() {
        UUID payerUserId = UUID.randomUUID();
        UUID payeeUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticateAs(payerUserId, Set.of("CUSTOMER"));

        Wallet payerWallet = Wallet.builder()
                .ownerUserId(payerUserId)
                .walletType(WalletType.USER_WALLET)
                .availableBalanceVnd(new BigDecimal("500000.00"))
                .lockedBalanceVnd(new BigDecimal("0.00"))
                .currency("VND")
                .build();
        payerWallet.setId(UUID.fromString("00000000-0000-7000-8000-000000000050"));
        walletStore.put(payerWallet.getId(), payerWallet);

        CreateCheckoutRequest request = new CreateCheckoutRequest(
                PaymentPurpose.LAB_ORDER,
                orderId,
                "LAB-20261105-7777",
                payeeUserId,
                new BigDecimal("200000.00"),
                GatewayProvider.INTERNAL_WALLET,
                null,
                null,
                "vn"
        );

        ResponseEntity<ApiResponse<CheckoutResponse>> response =
                paymentController.createCheckout(request, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CheckoutResponse data = response.getBody().getData();
        assertThat(data.status()).isEqualTo(PaymentTransactionStatus.SUCCEEDED);
        assertThat(data.checkoutUrl()).isNull();
        assertThat(data.escrowHoldId()).isNotNull();
        assertThat(data.paidAt()).isNotNull();

        assertThat(payerWallet.getAvailableBalanceVnd()).isEqualByComparingTo("300000.00");
        assertThat(systemEscrowWallet.getAvailableBalanceVnd()).isEqualByComparingTo("200000.00");
        assertThat(ledgerStore).hasSize(2);
        verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));

        // Duplicate payment attempt for the same order -> 409 Conflict
        assertThatThrownBy(() -> paymentController.createCheckout(request, new MockHttpServletRequest()))
                .isInstanceOf(DuplicateResourceException.class);

        // Self-payment attempt -> 403 Forbidden (BusinessException)
        CreateCheckoutRequest selfPayRequest = new CreateCheckoutRequest(
                PaymentPurpose.LAB_ORDER,
                UUID.randomUUID(),
                "LAB-SELF-01",
                payerUserId,
                new BigDecimal("100000.00"),
                GatewayProvider.VNPAY,
                null,
                null,
                "vn"
        );
        assertThatThrownBy(() -> paymentController.createCheckout(selfPayRequest, new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class);

        // Unauthorized user trying to view payer's transaction -> 403 Forbidden
        authenticateAs(UUID.randomUUID(), Set.of("CUSTOMER"));
        assertThatThrownBy(() -> paymentController.getTransactionStatus(data.txnCode()))
                .isInstanceOf(BusinessException.class);

        // Unknown txnCode -> 404 Not Found
        assertThatThrownBy(() -> paymentController.getTransactionStatus("PAY202611050000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
