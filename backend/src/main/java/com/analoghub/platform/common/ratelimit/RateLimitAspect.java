package com.analoghub.platform.common.ratelimit;

import com.analoghub.platform.common.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucketBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.ai.rate-limit.requests-per-minute:10}")
    private long defaultRequestsPerMinute;

    @Value("${app.ai.rate-limit.requests-per-day:50}")
    private long defaultRequestsPerDay;

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        long capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : defaultRequestsPerMinute;
        long windowSeconds = Math.max(1L, rateLimit.windowSeconds());
        String subjectKey = resolveSubjectKey(rateLimit.scope());
        String bucketKey = String.format("rl:%s:%s:%d:%d:%b",
                rateLimit.keyPrefix(), subjectKey, capacity, windowSeconds, rateLimit.enforceDailyQuota());

        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> createBucket(capacity, windowSeconds, rateLimit.enforceDailyQuota()));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            log.warn("[RateLimit] Limit exceeded for key={} retryAfter={}s", bucketKey, retryAfterSeconds);
            throw new RateLimitExceededException(retryAfterSeconds);
        }

        return joinPoint.proceed();
    }

    private Bucket createBucket(long capacity, long windowSeconds, boolean enforceDailyQuota) {
        LocalBucketBuilder builder = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofSeconds(windowSeconds))
                        .build());

        if (enforceDailyQuota && defaultRequestsPerDay > 0) {
            builder.addLimit(Bandwidth.builder()
                    .capacity(defaultRequestsPerDay)
                    .refillGreedy(defaultRequestsPerDay, Duration.ofDays(1))
                    .build());
        }

        return builder.build();
    }

    private String resolveSubjectKey(RateLimitScope scope) {
        if (scope == RateLimitScope.GLOBAL) {
            return "global";
        }
        if (scope == RateLimitScope.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                return "user:" + auth.getName();
            }
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded.split(",")[0].trim();
            }
            return "ip:" + request.getRemoteAddr();
        }
        return "anonymous";
    }
}
