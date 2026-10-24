package com.analoghub.platform.common.exception;

import com.analoghub.platform.common.dto.ErrorResponse;
import com.analoghub.platform.common.dto.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Bắt riêng RateLimitExceededException để gắn thêm Header Retry-After chuẩn HTTP 429
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request
    ) {
        ErrorCode ec = ex.getErrorCode();
        return ResponseEntity
                .status(ec.getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(buildErrorResponse(ec, ex.getMessage(), request.getRequestURI(), null));
    }

    // 2. Bắt toàn bộ Custom Domain Exceptions (Kế thừa từ BusinessException)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("[BusinessException] code={} status={} path={} msg={}",
                ec.getCode(), ec.getHttpStatus().value(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ex.getMessage(), request.getRequestURI(), null));
    }

    // 3. Bắt lỗi @Valid trên DTO RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldError(
                        err.getField(),
                        err.getRejectedValue(),
                        err.getDefaultMessage()
                ))
                .toList();

        ErrorCode ec = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), fieldErrors));
    }

    // 4. Bắt lỗi Validation trên @RequestParam / @PathVariable
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return new FieldError(field, v.getInvalidValue(), v.getMessage());
                })
                .toList();

        ErrorCode ec = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), fieldErrors));
    }

    // 5. Bắt lỗi thiếu @RequestParam bắt buộc
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        ErrorCode ec = ErrorCode.MISSING_REQUIRED_PARAMETER;
        String msg = String.format("Thiếu tham số bắt buộc: '%s' (%s)", ex.getParameterName(), ex.getParameterType());
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, msg, request.getRequestURI(), null));
    }

    // 6. Bắt lỗi sai cú pháp JSON hoặc sai giá trị Enum/UUID/Date
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ErrorCode ec = ErrorCode.MALFORMED_JSON_REQUEST;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    // 7. Bắt các lỗi đăng nhập từ Spring Security
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
        ErrorCode ec = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ErrorResponse> handleAccountLocked(HttpServletRequest request) {
        ErrorCode ec = ErrorCode.ACCOUNT_LOCKED_OR_DISABLED;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(HttpServletRequest request) {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    // 8. Bắt lỗi không đủ quyền RBAC (@PreAuthorize)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {
        ErrorCode ec = ErrorCode.ACCESS_DENIED;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    // 9. Bắt lỗi vượt kích thước file upload
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(HttpServletRequest request) {
        ErrorCode ec = ErrorCode.FILE_SIZE_LIMIT_EXCEEDED;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    // 10. Chốt chặn cuối cùng cho mọi lỗi 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaughtException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("[UncaughtException] path={}", request.getRequestURI(), ex);
        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(ec.getHttpStatus())
                .body(buildErrorResponse(ec, ec.getMessage(), request.getRequestURI(), null));
    }

    // Helper dùng chung @Builder của ErrorResponse
    private ErrorResponse buildErrorResponse(
            ErrorCode ec,
            String message,
            String path,
            List<FieldError> fieldErrors
    ) {
        return ErrorResponse.builder()
                .success(false)
                .code(ec.getCode())
                .message(message)
                .path(path)
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }
}