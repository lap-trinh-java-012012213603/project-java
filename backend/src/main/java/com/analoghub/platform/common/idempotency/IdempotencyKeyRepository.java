package com.analoghub.platform.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntity k WHERE k.expiresAt < :now")
    int deleteExpiredKeys(@Param("now") Instant now);
}
