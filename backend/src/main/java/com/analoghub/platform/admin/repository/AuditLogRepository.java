package com.analoghub.platform.admin.repository;

import com.analoghub.platform.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTargetEntityAndTargetIdOrderByCreatedAtDesc(String targetEntity, UUID targetId);

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    private static UnsupportedOperationException immutableAuditLogError() {
        return new UnsupportedOperationException(
                "adm_audit_logs là sổ nhật ký kiểm toán bất biến (Immutable Append-Only), nghiêm cấm xóa dữ liệu."
        );
    }

    @Override
    default void deleteById(UUID id) {
        throw immutableAuditLogError();
    }

    @Override
    default void delete(AuditLog entity) {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAllById(Iterable<? extends UUID> ids) {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAll(Iterable<? extends AuditLog> entities) {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAll() {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAllInBatch() {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAllInBatch(Iterable<AuditLog> entities) {
        throw immutableAuditLogError();
    }

    @Override
    default void deleteAllByIdInBatch(Iterable<UUID> ids) {
        throw immutableAuditLogError();
    }
}
