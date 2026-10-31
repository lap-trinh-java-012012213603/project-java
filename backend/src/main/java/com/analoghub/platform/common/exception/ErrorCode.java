package com.analoghub.platform.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // =========================================================================
    // 1. SYSTEM & COMMON VALIDATION (SYS / VAL / RES)
    // =========================================================================
    INTERNAL_SERVER_ERROR("SYS_500", "An unexpected internal server error occurred. Please try again later", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("SYS_503", "Service is temporarily unavailable. Please try again later", HttpStatus.SERVICE_UNAVAILABLE),
    VALIDATION_ERROR("VAL_400", "Invalid request payload or parameters", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON_REQUEST("VAL_401", "Malformed JSON request or invalid data type (Enum/Date/UUID)", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_PARAMETER("VAL_402", "Missing required request parameter", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RES_404", "Requested resource not found", HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE("RES_409", "Resource already exists", HttpStatus.CONFLICT),

    // =========================================================================
    // 2. IAM, AUTHENTICATION, RBAC & MULTI-TENANT (AUTH / SEC / TENANT)
    // =========================================================================
    UNAUTHORIZED("AUTH_401", "Unauthorized access or invalid authentication session", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("AUTH_402", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_EXPIRED("AUTH_403", "Access token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_404", "Refresh token has expired or been revoked. Please sign in again", HttpStatus.UNAUTHORIZED),
    OAUTH2_AUTHENTICATION_FAILED("AUTH_405", "Social OAuth2 authentication failed", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED_OR_DISABLED("AUTH_406", "User account is locked or disabled", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS("AUTH_409", "Email address is already registered", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS("AUTH_410", "Phone number is already in use", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USER_404", "User not found", HttpStatus.NOT_FOUND),
    ACCESS_DENIED("SEC_403", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    TENANT_ACCESS_DENIED("TENANT_403", "Tenant isolation violation: You are not authorized to access this Film Lab's resources", HttpStatus.FORBIDDEN),
    TENANT_CONTEXT_MISSING("TENANT_400", "Missing Film Lab tenant identifier (Tenant ID)", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // 3. FILM LAB & FILM STOCK CATALOG (LAB / FILM)
    // =========================================================================
    LAB_NOT_FOUND("LAB_404", "Film Lab not found", HttpStatus.NOT_FOUND),
    LAB_SLUG_ALREADY_EXISTS("LAB_409", "Film Lab slug already exists", HttpStatus.CONFLICT),
    LAB_TEMPORARILY_CLOSED("LAB_400", "Film Lab is temporarily closed and not accepting new orders", HttpStatus.BAD_REQUEST),
    LAB_SERVICE_NOT_FOUND("LAB_405", "Film Lab service item not found", HttpStatus.NOT_FOUND),
    LAB_PROCESS_NOT_SUPPORTED("LAB_406", "Film Lab does not support the requested chemical process (C-41/ECN-2/BW) or film format", HttpStatus.BAD_REQUEST),
    FILM_STOCK_NOT_FOUND("FILM_404", "Film stock not found", HttpStatus.NOT_FOUND),
    ALREADY_REVIEWED_LAB("LAB_409_REV", "You have already submitted a review for this order", HttpStatus.CONFLICT),

    // =========================================================================
    // 4. ORDER, ROLL LIFECYCLE, PAYMENT & LOGISTICS (ORD / ROLL / PAY / SHIP)
    // =========================================================================
    ORDER_NOT_FOUND("ORD_404", "Order not found", HttpStatus.NOT_FOUND),
    EMPTY_ORDER_ROLLS("ORD_400", "An order must contain at least one film roll", HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_BE_CANCELLED("ORD_401", "Order cannot be cancelled because it is already being processed by the Film Lab", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_PAID("ORD_409", "Order has already been paid", HttpStatus.CONFLICT),
    ROLL_NOT_FOUND("ROLL_404", "Film roll not found in order", HttpStatus.NOT_FOUND),
    INVALID_ROLL_STATE_TRANSITION("ROLL_400", "Invalid film roll status transition in processing workflow", HttpStatus.BAD_REQUEST),
    ROLL_CODE_ALREADY_EXISTS("ROLL_409", "Film roll code already exists in this Lab", HttpStatus.CONFLICT),
    PAYMENT_SIGNATURE_INVALID("PAY_400", "Invalid payment gateway signature (VNPay/MoMo)", HttpStatus.BAD_REQUEST),
    PAYMENT_FAILED("PAY_402", "Payment transaction failed or was cancelled", HttpStatus.BAD_REQUEST),
    DELIVERY_ADDRESS_REQUIRED("SHIP_400", "Pickup/delivery address is required for courier fulfillment", HttpStatus.BAD_REQUEST),
    INVALID_LOGISTICS_WEBHOOK("SHIP_401", "Invalid logistics partner webhook payload", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // 5. DIGITAL FILM ARCHIVE & CLOUD STORAGE (ARC / STORAGE)
    // =========================================================================
    SCAN_PHOTO_NOT_FOUND("ARC_404", "Scanned photo frame not found in archive", HttpStatus.NOT_FOUND),
    ARCHIVE_NOT_UNLOCKED("ARC_403", "Digital film archive is locked until quality control (QC) and payment are completed", HttpStatus.FORBIDDEN),
    ROLL_NOT_IN_SCANNING_STATE("ARC_400", "Film roll must be in SCANNING or RESCAN_REQUIRED status before uploading scans", HttpStatus.BAD_REQUEST),
    BATCH_UPLOAD_LIMIT_EXCEEDED("ARC_401", "Batch upload cannot exceed 45 frames per film roll", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_IMAGE_FORMAT("STORAGE_400", "Unsupported image format. Only JPEG, PNG, WEBP, and TIFF are allowed", HttpStatus.BAD_REQUEST),
    FILE_SIZE_LIMIT_EXCEEDED("STORAGE_413", "File size exceeds the maximum allowed limit", HttpStatus.PAYLOAD_TOO_LARGE),
    STORAGE_PRESIGN_FAILED("STORAGE_500", "Failed to generate presigned storage URL", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_OBJECT_NOT_FOUND("STORAGE_404", "Uploaded file not found on cloud storage (R2/MinIO). Please upload again", HttpStatus.BAD_REQUEST),

    // =========================================================================
    // 6. MARKETPLACE (MKT)
    // =========================================================================
    LISTING_NOT_FOUND("MKT_404", "Marketplace listing not found", HttpStatus.NOT_FOUND),
    LISTING_NOT_AVAILABLE("MKT_409", "Marketplace item is already reserved or sold", HttpStatus.CONFLICT),
    CANNOT_OFFER_OWN_LISTING("MKT_400", "You cannot submit a purchase offer on your own listing", HttpStatus.BAD_REQUEST),
    OFFER_NOT_FOUND("MKT_405", "Purchase offer not found", HttpStatus.NOT_FOUND),

    // =========================================================================
    // 7. COMMUNITY & KNOWLEDGE SHARING (COM)
    // =========================================================================
    POST_NOT_FOUND("COM_404", "Community post not found", HttpStatus.NOT_FOUND),
    COMMENT_NOT_FOUND("COM_405", "Comment not found", HttpStatus.NOT_FOUND),
    EVENT_CAPACITY_FULL("COM_409", "Workshop or Photowalk event has reached maximum capacity", HttpStatus.CONFLICT),
    ALREADY_JOINED_EVENT("COM_410", "You have already registered for this event", HttpStatus.CONFLICT),

    // =========================================================================
    // 8. AI SERVICES: GEMINI RECSYS, RAG, HYBRID SEARCH & SCAN QC (AI)
    // =========================================================================
    AI_RATE_LIMIT_EXCEEDED("AI_429", "AI request rate limit exceeded. Please try again shortly", HttpStatus.TOO_MANY_REQUESTS),
    AI_PROMPT_TOO_LONG("AI_400", "Prompt length exceeds maximum allowed token limit", HttpStatus.BAD_REQUEST),
    AI_PROVIDER_ERROR("AI_502", "Google Gemini AI service is temporarily unavailable. Fallback mode activated", HttpStatus.BAD_GATEWAY),
    IMAGE_QC_ANALYSIS_FAILED("AI_500", "Failed to read pixel data for scan quality control (QC) analysis", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}