package com.analoghub.platform.common.idempotency;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private static final String REDIS_LOCK_PREFIX = "idemp:lock:";
    private static final String FALLBACK_HEADER = "X-Idempotency-Key";

    private final StringRedisTemplate stringRedisTemplate;
    private final IdempotencyStoreService storeService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object aroundIdempotentRequest(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String rawKey = extractHeaderKey(request, idempotent.headerName());
        if (rawKey == null || rawKey.isBlank()) {
            if (idempotent.required()) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
            }
            return joinPoint.proceed();
        }

        String idempotencyKey = rawKey.trim();
        UUID userId = resolveCurrentUserId();
        String userScope = userId != null ? userId.toString() : "anonymous";
        String method = request.getMethod();
        String path = request.getRequestURI();
        String rawBody = serializeArguments(joinPoint.getArgs());

        // 1. Tính request_hash = SHA256(userId + method + path + rawBody)
        String requestHash = sha256Hex(userScope + ":" + method + ":" + path + ":" + rawBody);

        // 2. Acquire Redis Distributed Lock (SET NX EX 60)
        String redisLockKey = REDIS_LOCK_PREFIX + idempotencyKey;
        Boolean lockAcquired = acquireRedisLock(redisLockKey, requestHash, idempotent.lockTtlSeconds());
        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        try {
            // 3. Kiểm tra bảng sys_idempotency_keys
            Optional<IdempotencyKeyEntity> existingOpt = storeService.findByKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                IdempotencyKeyEntity existing = existingOpt.get();
                if (existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(Instant.now())) {
                    if (existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                        throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
                    }
                    if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                        if (!existing.getRequestHash().equalsIgnoreCase(requestHash)) {
                            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
                        }
                        log.info("[Idempotency] Replaying cached response for key={} path={}", idempotencyKey, path);
                        return reconstructResponse(joinPoint, existing);
                    }
                }
            }

            // Chưa tồn tại -> Insert IN_PROGRESS
            storeService.markInProgress(
                    idempotencyKey,
                    userId,
                    path,
                    requestHash,
                    idempotent.expirationHours()
            );

            Object result;
            try {
                result = joinPoint.proceed();
            } catch (Throwable ex) {
                storeService.markFailedAndClear(idempotencyKey);
                throw ex;
            }

            // Lưu response_status & response_body -> COMPLETED
            int status = 200;
            Object bodyToStore = result;
            if (result instanceof ResponseEntity<?> responseEntity) {
                status = responseEntity.getStatusCode().value();
                bodyToStore = responseEntity.getBody();
            }
            String serializedResponse = bodyToStore != null ? objectMapper.writeValueAsString(bodyToStore) : null;
            storeService.markCompleted(idempotencyKey, status, serializedResponse);

            return result;
        } finally {
            releaseRedisLock(redisLockKey);
        }
    }

    private Boolean acquireRedisLock(String redisLockKey, String value, long ttlSeconds) {
        try {
            return stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisLockKey, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("[Idempotency] Redis lock unavailable for key={}, falling back to DB check: {}",
                    redisLockKey, ex.getMessage());
            return Boolean.TRUE;
        }
    }

    private void releaseRedisLock(String redisLockKey) {
        try {
            stringRedisTemplate.delete(redisLockKey);
        } catch (Exception ex) {
            log.debug("[Idempotency] Failed to release Redis lock {}: {}", redisLockKey, ex.getMessage());
        }
    }

    private Object reconstructResponse(ProceedingJoinPoint joinPoint, IdempotencyKeyEntity existing) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        Type genericReturnType = signature.getMethod().getGenericReturnType();
        int status = existing.getResponseStatus() != null ? existing.getResponseStatus() : 200;
        String json = existing.getResponseBody();

        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            if (json == null || json.isBlank()) {
                return ResponseEntity.status(status).build();
            }
            if (genericReturnType instanceof ParameterizedType paramType) {
                Type bodyType = paramType.getActualTypeArguments()[0];
                JavaType javaType = objectMapper.getTypeFactory().constructType(bodyType);
                Object body = objectMapper.readValue(json, javaType);
                return ResponseEntity.status(status).body(body);
            }
            Object body = objectMapper.readValue(json, Object.class);
            return ResponseEntity.status(status).body(body);
        }

        if (void.class.equals(returnType) || Void.class.equals(returnType) || json == null || json.isBlank()) {
            return null;
        }

        JavaType javaType = objectMapper.getTypeFactory().constructType(genericReturnType);
        return objectMapper.readValue(json, javaType);
    }

    private String serializeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> serializableArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null
                    || arg instanceof ServletRequest
                    || arg instanceof ServletResponse
                    || arg instanceof BindingResult
                    || arg instanceof Authentication
                    || arg instanceof MultipartFile) {
                continue;
            }
            serializableArgs.add(arg);
        }
        if (serializableArgs.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(serializableArgs);
        } catch (Exception ex) {
            return serializableArgs.toString();
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String extractHeaderKey(HttpServletRequest request, String configuredHeader) {
        String key = request.getHeader(configuredHeader);
        if (key == null || key.isBlank()) {
            key = request.getHeader(FALLBACK_HEADER);
        }
        return key;
    }

    private static UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(auth.getName().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
