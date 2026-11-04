package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :revokedAt, rt.updatedAt = :revokedAt
            WHERE rt.tokenFamilyId = :tokenFamilyId AND rt.revokedAt IS NULL
            """)
    int revokeAllByTokenFamilyId(@Param("tokenFamilyId") UUID tokenFamilyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :revokedAt, rt.updatedAt = :revokedAt
            WHERE rt.userId = :userId AND rt.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
