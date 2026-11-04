package com.analoghub.platform.payment.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.DuplicateResourceException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.iam.facade.IamFacade;
import com.analoghub.platform.payment.domain.EscrowHold;
import com.analoghub.platform.payment.domain.EscrowStatus;
import com.analoghub.platform.payment.domain.GatewayProvider;
import com.analoghub.platform.payment.domain.PaymentPurpose;
import com.analoghub.platform.payment.domain.PaymentTransaction;
import com.analoghub.platform.payment.domain.PaymentTransactionStatus;
import com.analoghub.platform.payment.domain.PaymentWebhookLog;
import com.analoghub.platform.payment.dto.CheckoutResponse;
import com.analoghub.platform.payment.dto.CreateCheckoutRequest;
import com.analoghub.platform.payment.dto.TransactionStatusResponse;
import com.analoghub.platform.payment.dto.VnpayIpnResponse;
import com.analoghub.platform.payment.event.PaymentFailedEvent;
import com.analoghub.platform.payment.event.PaymentSucceededEvent;
import com.analoghub.platform.payment.gateway.VnpayGatewayService;
import com.analoghub.platform.payment.repository.EscrowHoldRepository;
import com.analoghub.platform.payment.repository.PaymentTransactionRepository;
import com.analoghub.platform.payment.repository.PaymentWebhookLogRepository;
import com.analoghub.platform.platformconfig.facade.PlatformConfigFacade;
import com.analoghub.platform.security.AuthenticatedUser;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {

    public static final String PROVIDER_VNPAY = "VNPAY";
    public static final String WEBHOOK_STATUS_PROCESSED = "PROCESSED";
    public static final String WEBHOOK_STATUS_FAILED = "FAILED";
    public static final String WEBHOOK_STATUS_REJECTED_SIGNATURE = "REJECTED_SIGNATURE";

    private static final DateTimeFormatter TXN_CODE_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EscrowHoldRepository escrowHoldRepository;
    private final PaymentWebhookLogRepository paymentWebhookLogRepository;
    private final WalletLedgerService walletLedgerService;
    private final VnpayGatewayService vnpayGatewayService;
    private final IamFacade iamFacade;
    private final PlatformConfigFacade platformConfigFacade;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CheckoutResponse createCheckout(CreateCheckoutRequest request, String clientIp) {
        UUID payerUserId = SecurityContextUtils.getRequiredCurrentUserId();
        return createCheckoutForUser(payerUserId, request, clientIp);
    }

    @Transactional
    public CheckoutResponse createCheckoutForUser(UUID payerUserId, CreateCheckoutRequest request, String clientIp) {
        Objects.requireNonNull(payerUserId, "payerUserId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (payerUserId.equals(request.payeeUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Self-payment is forbidden (payerUserId == payeeUserId)");
        }

        if (iamFacade != null && !iamFacade.isActiveUser(request.payeeUserId())) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "Payee user not found or inactive");
        }

        validateReturnUrlOverride(request.returnUrlOverride());

        boolean alreadySucceeded = paymentTransactionRepository.existsByPaymentPurposeAndReferenceIdAndStatus(
                request.paymentPurpose(),
                request.referenceId(),
                PaymentTransactionStatus.SUCCEEDED
        );
        Optional<EscrowHold> existingEscrow = escrowHoldRepository.findByReferenceId(request.referenceId());
        if (alreadySucceeded || existingEscrow.isPresent()) {
            throw new DuplicateResourceException(
                    ErrorCode.ORDER_ALREADY_PAID,
                    "Order has already been paid or has an active escrow hold"
            );
        }

        expirePendingInitiatedTransactions(request.paymentPurpose(), request.referenceId());

        Instant now = Instant.now();
        int timeoutMinutes = vnpayGatewayService.resolveTimeoutMinutes();
        Instant expiresAt = now.plus(Duration.ofMinutes(timeoutMinutes));
        BigDecimal normalizedAmount = request.amountVnd().setScale(2, RoundingMode.HALF_UP);
        String txnCode = generateTxnCode(now);

        if (request.gatewayProvider() == GatewayProvider.VNPAY) {
            PaymentTransaction txn = PaymentTransaction.builder()
                    .txnCode(txnCode)
                    .payerUserId(payerUserId)
                    .payeeUserId(request.payeeUserId())
                    .paymentPurpose(request.paymentPurpose())
                    .referenceId(request.referenceId())
                    .gatewayProvider(GatewayProvider.VNPAY)
                    .gatewayTxnNo(PaymentTransaction.PENDING_PAYEE_PREFIX + request.payeeUserId())
                    .amountVnd(normalizedAmount)
                    .status(PaymentTransactionStatus.INITIATED)
                    .expiresAt(expiresAt)
                    .paidAt(null)
                    .build();
            txn = paymentTransactionRepository.save(txn);

            String checkoutUrl = vnpayGatewayService.buildCheckoutUrl(
                    txn.getTxnCode(),
                    request.paymentPurpose(),
                    request.referenceCode(),
                    normalizedAmount,
                    request.bankCode(),
                    request.returnUrlOverride(),
                    request.locale(),
                    clientIp,
                    now,
                    expiresAt
            );

            return new CheckoutResponse(
                    txn.getId(),
                    txn.getTxnCode(),
                    txn.getPaymentPurpose(),
                    txn.getReferenceId(),
                    txn.getGatewayProvider(),
                    txn.getAmountVnd(),
                    txn.getStatus(),
                    checkoutUrl,
                    null,
                    txn.getExpiresAt(),
                    null,
                    txn.getCreatedAt() != null ? txn.getCreatedAt() : now
            );
        }

        // Branch B: INTERNAL_WALLET
        PaymentTransaction txn = PaymentTransaction.builder()
                .txnCode(txnCode)
                .payerUserId(payerUserId)
                .payeeUserId(request.payeeUserId())
                .paymentPurpose(request.paymentPurpose())
                .referenceId(request.referenceId())
                .gatewayProvider(GatewayProvider.INTERNAL_WALLET)
                .gatewayTxnNo(null)
                .amountVnd(normalizedAmount)
                .status(PaymentTransactionStatus.SUCCEEDED)
                .expiresAt(expiresAt)
                .paidAt(now)
                .build();
        txn = paymentTransactionRepository.save(txn);

        walletLedgerService.recordInternalWalletEscrowIn(
                payerUserId,
                normalizedAmount,
                txn.getId(),
                request.paymentPurpose().name(),
                request.referenceId()
        );

        EscrowHold escrowHold = createAndSaveEscrowHold(
                txn.getId(),
                request.paymentPurpose(),
                request.referenceId(),
                payerUserId,
                request.payeeUserId(),
                normalizedAmount
        );

        eventPublisher.publishEvent(new PaymentSucceededEvent(
                txn.getId(),
                request.paymentPurpose().name(),
                request.referenceId(),
                payerUserId,
                normalizedAmount
        ));

        return new CheckoutResponse(
                txn.getId(),
                txn.getTxnCode(),
                txn.getPaymentPurpose(),
                txn.getReferenceId(),
                txn.getGatewayProvider(),
                txn.getAmountVnd(),
                txn.getStatus(),
                null,
                escrowHold.getId(),
                txn.getExpiresAt(),
                txn.getPaidAt(),
                txn.getCreatedAt() != null ? txn.getCreatedAt() : now
        );
    }

    @Transactional(readOnly = true)
    public TransactionStatusResponse getTransactionStatus(String txnCode) {
        AuthenticatedUser currentUser = SecurityContextUtils.getRequiredCurrentUser();
        PaymentTransaction txn = paymentTransactionRepository.findByTxnCode(txnCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Payment transaction not found for txnCode: " + txnCode
                ));

        if (!txn.getPayerUserId().equals(currentUser.getUserId()) && !currentUser.isAdmin()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "You do not have permission to view another user's payment transaction"
            );
        }

        PaymentTransactionStatus effectiveStatus = txn.getStatus();
        if (effectiveStatus == PaymentTransactionStatus.INITIATED
                && txn.getExpiresAt() != null
                && txn.getExpiresAt().isBefore(Instant.now())) {
            effectiveStatus = PaymentTransactionStatus.EXPIRED;
        }

        TransactionStatusResponse.EscrowHoldSummaryDto escrowSummary = escrowHoldRepository
                .findByPaymentTxnId(txn.getId())
                .map(hold -> new TransactionStatusResponse.EscrowHoldSummaryDto(
                        hold.getId(),
                        hold.getStatus(),
                        hold.getGrossAmountVnd(),
                        hold.getRefundedAmountVnd(),
                        hold.getAutoReleaseAt()
                ))
                .orElse(null);

        return new TransactionStatusResponse(
                txn.getId(),
                txn.getTxnCode(),
                txn.getPayerUserId(),
                txn.getPaymentPurpose(),
                txn.getReferenceId(),
                txn.getGatewayProvider(),
                txn.getPublicGatewayTxnNo(),
                txn.getAmountVnd(),
                effectiveStatus,
                escrowSummary,
                txn.getExpiresAt(),
                txn.getPaidAt(),
                txn.getCreatedAt()
        );
    }

    @Transactional
    public VnpayIpnResponse processVnpayIpn(Map<String, String> params) {
        if (params == null
                || isBlank(params.get("vnp_TxnRef"))
                || isBlank(params.get("vnp_Amount"))
                || isBlank(params.get("vnp_ResponseCode"))
                || isBlank(params.get("vnp_SecureHash"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required VNPay IPN parameters");
        }

        String vnpTxnRef = params.get("vnp_TxnRef").trim();
        String vnpTransactionNo = params.getOrDefault("vnp_TransactionNo", "NONE").trim();
        String vnpResponseCode = params.get("vnp_ResponseCode").trim();
        String vnpTransactionStatus = params.get("vnp_TransactionStatus");
        String vnpAmountRaw = params.get("vnp_Amount").trim();
        String vnpPayDate = params.get("vnp_PayDate");

        String eventKey = "VNPAY:" + vnpTxnRef + ":" + vnpTransactionNo + ":" + vnpResponseCode;

        Optional<PaymentWebhookLog> existingWebhookLog =
                paymentWebhookLogRepository.findByEventIdempotencyKey(eventKey);
        if (existingWebhookLog.isPresent()
                && WEBHOOK_STATUS_PROCESSED.equals(existingWebhookLog.get().getProcessingStatus())) {
            return VnpayIpnResponse.alreadyConfirmed();
        }

        boolean signatureValid = vnpayGatewayService.verifyIpnSignature(params);
        if (!signatureValid) {
            saveWebhookLog(
                    existingWebhookLog.orElse(null),
                    eventKey,
                    params,
                    false,
                    WEBHOOK_STATUS_REJECTED_SIGNATURE,
                    "Invalid Checksum"
            );
            return VnpayIpnResponse.invalidChecksum();
        }

        Optional<PaymentTransaction> txnOpt = paymentTransactionRepository.findByTxnCodeForUpdate(vnpTxnRef);
        if (txnOpt.isEmpty()) {
            saveWebhookLog(
                    existingWebhookLog.orElse(null),
                    eventKey,
                    params,
                    true,
                    WEBHOOK_STATUS_FAILED,
                    "Order not found"
            );
            return VnpayIpnResponse.orderNotFound();
        }

        PaymentTransaction txn = txnOpt.get();

        BigDecimal vnpAmountVnd;
        try {
            vnpAmountVnd = new BigDecimal(vnpAmountRaw)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            saveWebhookLog(
                    existingWebhookLog.orElse(null),
                    eventKey,
                    params,
                    true,
                    WEBHOOK_STATUS_FAILED,
                    "Malformed vnp_Amount"
            );
            return VnpayIpnResponse.invalidAmount();
        }

        if (vnpAmountVnd.compareTo(txn.getAmountVnd()) != 0) {
            saveWebhookLog(
                    existingWebhookLog.orElse(null),
                    eventKey,
                    params,
                    true,
                    WEBHOOK_STATUS_FAILED,
                    "Amount mismatch"
            );
            return VnpayIpnResponse.invalidAmount();
        }

        if (txn.getStatus() == PaymentTransactionStatus.SUCCEEDED
                || txn.getStatus() == PaymentTransactionStatus.FAILED) {
            saveWebhookLog(
                    existingWebhookLog.orElse(null),
                    eventKey,
                    params,
                    true,
                    WEBHOOK_STATUS_PROCESSED,
                    "Order already confirmed"
            );
            return VnpayIpnResponse.alreadyConfirmed();
        }

        boolean isSuccess = "00".equals(vnpResponseCode)
                && (vnpTransactionStatus == null || "00".equals(vnpTransactionStatus.trim()));

        if (isSuccess) {
            UUID payeeUserId = txn.getResolvedPayeeUserId();
            Instant paidAt = vnpayGatewayService.parseVnpPayDate(vnpPayDate);

            txn.setStatus(PaymentTransactionStatus.SUCCEEDED);
            txn.setGatewayTxnNo(vnpTransactionNo);
            txn.setPaidAt(paidAt);
            paymentTransactionRepository.save(txn);

            walletLedgerService.recordGatewayEscrowIn(
                    txn.getPayerUserId(),
                    txn.getAmountVnd(),
                    txn.getId(),
                    txn.getPaymentPurpose().name(),
                    txn.getReferenceId()
            );

            createAndSaveEscrowHold(
                    txn.getId(),
                    txn.getPaymentPurpose(),
                    txn.getReferenceId(),
                    txn.getPayerUserId(),
                    payeeUserId,
                    txn.getAmountVnd()
            );

            eventPublisher.publishEvent(new PaymentSucceededEvent(
                    txn.getId(),
                    txn.getPaymentPurpose().name(),
                    txn.getReferenceId(),
                    txn.getPayerUserId(),
                    txn.getAmountVnd()
            ));
        } else {
            txn.setStatus(PaymentTransactionStatus.FAILED);
            txn.setGatewayTxnNo(vnpTransactionNo);
            paymentTransactionRepository.save(txn);

            eventPublisher.publishEvent(new PaymentFailedEvent(
                    txn.getId(),
                    txn.getPaymentPurpose().name(),
                    txn.getReferenceId(),
                    "VNPAY_RESPONSE_" + vnpResponseCode
            ));
        }

        saveWebhookLog(
                existingWebhookLog.orElse(null),
                eventKey,
                params,
                true,
                WEBHOOK_STATUS_PROCESSED,
                null
        );
        return VnpayIpnResponse.confirmSuccess();
    }

    private void expirePendingInitiatedTransactions(PaymentPurpose purpose, UUID referenceId) {
        List<PaymentTransaction> initiatedList =
                paymentTransactionRepository.findByPaymentPurposeAndReferenceIdAndStatus(
                        purpose,
                        referenceId,
                        PaymentTransactionStatus.INITIATED
                );
        for (PaymentTransaction oldTxn : initiatedList) {
            oldTxn.setStatus(PaymentTransactionStatus.EXPIRED);
            paymentTransactionRepository.save(oldTxn);
        }
    }

    private EscrowHold createAndSaveEscrowHold(
            UUID paymentTxnId,
            PaymentPurpose paymentPurpose,
            UUID referenceId,
            UUID payerUserId,
            UUID payeeUserId,
            BigDecimal grossAmountVnd
    ) {
        String escrowReferenceType = resolveEscrowReferenceType(paymentPurpose);
        EscrowHold escrowHold = EscrowHold.builder()
                .paymentTxnId(paymentTxnId)
                .referenceType(escrowReferenceType)
                .referenceId(referenceId)
                .payerUserId(payerUserId)
                .payeeUserId(payeeUserId)
                .grossAmountVnd(grossAmountVnd)
                .refundedAmountVnd(new BigDecimal("0.00"))
                .commissionFeeVnd(new BigDecimal("0.00"))
                .netPayoutAmountVnd(new BigDecimal("0.00"))
                .status(EscrowStatus.HELD)
                .build();
        return escrowHoldRepository.save(escrowHold);
    }

    private String resolveEscrowReferenceType(PaymentPurpose purpose) {
        if (purpose == PaymentPurpose.MARKETPLACE_ORDER || purpose == PaymentPurpose.MARKETPLACE_SWAP_DEPOSIT) {
            return "MARKETPLACE_ORDER";
        }
        return "LAB_ORDER";
    }

    private void saveWebhookLog(
            PaymentWebhookLog existing,
            String eventKey,
            Map<String, String> params,
            boolean signatureValid,
            String processingStatus,
            String errorMessage
    ) {
        Map<String, Object> payloadCopy = new LinkedHashMap<>(params);
        PaymentWebhookLog log = existing != null
                ? existing
                : PaymentWebhookLog.builder()
                        .provider(PROVIDER_VNPAY)
                        .eventIdempotencyKey(eventKey)
                        .rawPayload(payloadCopy)
                        .signatureValid(signatureValid)
                        .receivedAt(Instant.now())
                        .build();
        log.setProcessingStatus(processingStatus);
        log.setErrorMessage(errorMessage);
        paymentWebhookLogRepository.save(log);
    }

    private void validateReturnUrlOverride(String returnUrlOverride) {
        if (returnUrlOverride == null || returnUrlOverride.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(returnUrlOverride.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid returnUrlOverride URI");
        }
        if (uri.getHost() == null || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "returnUrlOverride must be a valid HTTP/HTTPS URL");
        }
        if (platformConfigFacade != null) {
            Optional<String> allowedDomainsOpt = platformConfigFacade.getString("payment.vnpay.allowed_return_domains");
            if (allowedDomainsOpt.isPresent() && !allowedDomainsOpt.get().isBlank()) {
                String host = uri.getHost().toLowerCase();
                boolean matched = List.of(allowedDomainsOpt.get().split(",")).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
                if (!matched) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "returnUrlOverride host is not in payment.vnpay.allowed_return_domains"
                    );
                }
            }
        }
    }

    private String generateTxnCode(Instant now) {
        int random4 = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "PAY" + TXN_CODE_DATE_FMT.format(now) + random4;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
