package com.analoghub.platform.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyStoreService {

    private final IdempotencyKeyRepository repository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyEntity> findByKey(String idempotencyKey) {
        return repository.findById(idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKeyEntity markInProgress(
            String idempotencyKey,
            UUID userId,
            String requestPath,
            String requestHash,
            long expirationHours
    ) {
        Instant now = Instant.now();
        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .requestPath(requestPath)
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(expirationHours)))
                .build();
        return repository.saveAndFlush(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String idempotencyKey, int responseStatus, String responseBodyJson) {
        repository.findById(idempotencyKey).ifPresent(entity -> {
            entity.setStatus(IdempotencyStatus.COMPLETED);
            entity.setResponseStatus(responseStatus);
            entity.setResponseBody(responseBodyJson);
            repository.saveAndFlush(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndClear(String idempotencyKey) {
        repository.findById(idempotencyKey).ifPresent(entity -> {
            if (entity.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                repository.delete(entity);
                repository.flush();
            }
        });
    }

    @Transactional
    public int cleanupExpiredKeys(Instant now) {
        return repository.deleteExpiredKeys(now);
    }
}
