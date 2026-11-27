package com.analoghub.platform.admin.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sensitive Admin operation so {@link AdminAuditLogAspect} automatically appends
 * an immutable entry into {@code adm_audit_logs} upon successful completion.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminAudited {

    String actionType();

    String targetEntity();

    String targetIdParam() default "";
}
