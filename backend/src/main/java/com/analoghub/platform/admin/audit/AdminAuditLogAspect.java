package com.analoghub.platform.admin.audit;

import com.analoghub.platform.admin.domain.AuditLog;
import com.analoghub.platform.admin.service.AdminAuditLogService;
import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AOP aspect that intercepts {@link AdminAudited} methods and appends an immutable
 * {@link AuditLog} record on successful execution (never when validation/business exceptions throw).
 */
@Slf4j
@Aspect
@Component
public class AdminAuditLogAspect {

    private static final ThreadLocal<Boolean> ASPECT_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminAuditLogService adminAuditLogService;
    private final @Nullable ObjectMapper objectMapper;

    public AdminAuditLogAspect(AdminAuditLogService adminAuditLogService) {
        this(adminAuditLogService, null);
    }

    @Autowired
    public AdminAuditLogAspect(
            AdminAuditLogService adminAuditLogService,
            @Nullable ObjectMapper objectMapper
    ) {
        this.adminAuditLogService = adminAuditLogService;
        this.objectMapper = objectMapper;
    }

    public static boolean isAspectActive() {
        return Boolean.TRUE.equals(ASPECT_ACTIVE.get());
    }

    @Around("@annotation(adminAudited)")
    public Object aroundAuditedAction(ProceedingJoinPoint joinPoint, AdminAudited adminAudited) throws Throwable {
        boolean outer = !isAspectActive();
        if (outer) {
            ASPECT_ACTIVE.set(Boolean.TRUE);
        }
        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            if (outer) {
                ASPECT_ACTIVE.remove();
            }
        }

        UUID adminId = SecurityContextUtils.getCurrentUserId()
                .orElseGet(SecurityContextUtils::getRequiredCurrentUserId);

        UUID targetId = extractTargetId(joinPoint, adminAudited, result);
        Map<String, Object> oldValues = null;
        Map<String, Object> newValues;

        if (result instanceof AuditableResult<?> auditableResult) {
            oldValues = redactMap(auditableResult.toAuditOldValues());
            newValues = redactMap(auditableResult.toAuditNewValues());
        } else {
            newValues = buildGenericNewValues(joinPoint, result);
        }

        AuditLog savedLog = adminAuditLogService.recordAuditLog(
                adminId,
                adminAudited.actionType(),
                adminAudited.targetEntity(),
                targetId,
                oldValues,
                newValues,
                null
        );

        if (result instanceof AuditableResult<?> auditableResult && savedLog != null) {
            return auditableResult.withAuditLogId(savedLog.getId());
        }
        return result;
    }

    private UUID extractTargetId(
            ProceedingJoinPoint joinPoint,
            AdminAudited adminAudited,
            @Nullable Object result
    ) {
        if (result instanceof AuditableResult<?> auditableResult && auditableResult.auditTargetId() != null) {
            return auditableResult.auditTargetId();
        }

        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        String paramName = adminAudited.targetIdParam();
        if (paramName != null && !paramName.isBlank()
                && joinPoint.getSignature() instanceof MethodSignature signature) {
            String[] paramNames = signature.getParameterNames();
            if (paramNames != null) {
                for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                    if (paramName.equals(paramNames[i])) {
                        UUID matched = coerceUuid(args[i]);
                        if (matched != null) {
                            return matched;
                        }
                    }
                }
            }
        }

        for (Object arg : args) {
            UUID candidate = coerceUuid(arg);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> buildGenericNewValues(ProceedingJoinPoint joinPoint, @Nullable Object result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = joinPoint.getSignature() instanceof MethodSignature signature
                ? signature.getParameterNames()
                : null;

        if (args != null && args.length > 0) {
            Map<String, Object> argMap = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null
                        || arg instanceof ServletRequest
                        || arg instanceof ServletResponse
                        || arg instanceof BindingResult
                        || arg instanceof Authentication
                        || arg instanceof MultipartFile) {
                    continue;
                }
                String name = (paramNames != null && i < paramNames.length && paramNames[i] != null)
                        ? paramNames[i]
                        : "arg" + i;
                argMap.put(name, sanitizeValue(arg));
            }
            if (!argMap.isEmpty()) {
                snapshot.put("request", argMap);
            }
        }

        Object unwrapped = unwrapResult(result);
        if (unwrapped != null) {
            snapshot.put("outcome", sanitizeValue(unwrapped));
        }
        return snapshot;
    }

    private Object unwrapResult(@Nullable Object result) {
        Object current = result;
        if (current instanceof ResponseEntity<?> responseEntity) {
            current = responseEntity.getBody();
        }
        if (current instanceof ApiResponse<?> apiResponse) {
            current = apiResponse.getData();
        }
        return current;
    }

    private Object sanitizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof UUID
                || value instanceof Enum<?>) {
            return value instanceof Enum<?> e ? e.name() : value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    String key = k.toString();
                    sanitized.put(key, isSensitiveKey(key) ? "***REDACTED***" : sanitizeValue(v));
                }
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(sanitizeValue(item));
            }
            return items;
        }
        if (objectMapper != null) {
            try {
                Map<String, Object> converted = objectMapper.convertValue(value, MAP_TYPE);
                return redactMap(converted);
            } catch (Exception ex) {
                log.debug("[AuditAspect] Falling back to string representation for type {}: {}",
                        value.getClass().getSimpleName(), ex.getMessage());
            }
        }
        return value.toString();
    }

    private @Nullable Map<String, Object> redactMap(@Nullable Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        source.forEach((key, val) -> {
            if (key != null) {
                clean.put(key, isSensitiveKey(key) ? "***REDACTED***" : sanitizeValue(val));
            }
        });
        return clean;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("secret") || lower.contains("token");
    }

    private static @Nullable UUID coerceUuid(@Nullable Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
