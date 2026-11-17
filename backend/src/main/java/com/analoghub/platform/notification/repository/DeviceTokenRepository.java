package com.analoghub.platform.notification.repository;

import com.analoghub.platform.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByDeviceToken(String deviceToken);

    Optional<DeviceToken> findByDeviceTokenAndUserId(String deviceToken, UUID userId);

    List<DeviceToken> findByUserIdAndIsActiveTrueOrderByUpdatedAtAsc(UUID userId);
}
