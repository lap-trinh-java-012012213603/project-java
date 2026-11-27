package com.analoghub.platform.admin.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Optional contract for service return values of {@link AdminAudited} methods to provide
 * structured before/after state snapshots and receive the persisted {@code auditLogId}.
 */
public interface AuditableResult<T> {

    UUID auditTargetId();

    Map<String, Object> toAuditOldValues();

    Map<String, Object> toAuditNewValues();

    T withAuditLogId(UUID auditLogId);
}
