package com.analoghub.platform.iam.repository;

import com.analoghub.platform.iam.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    Optional<UserAddress> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<UserAddress> findFirstByUserIdAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<UserAddress> findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.userId = :userId AND ua.deletedAt IS NULL AND ua.isDefault = true")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
