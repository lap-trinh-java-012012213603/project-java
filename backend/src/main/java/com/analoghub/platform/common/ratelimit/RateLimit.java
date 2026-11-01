package com.analoghub.platform.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    String keyPrefix() default "default";

    long capacity() default -1L;

    long windowSeconds() default 60L;

    RateLimitScope scope() default RateLimitScope.USER;

    boolean enforceDailyQuota() default false;
}
