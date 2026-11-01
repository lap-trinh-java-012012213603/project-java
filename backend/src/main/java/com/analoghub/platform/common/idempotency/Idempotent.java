package com.analoghub.platform.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String headerName() default "Idempotency-Key";

    long lockTtlSeconds() default 60L;

    long expirationHours() default 24L;

    boolean required() default false;
}
