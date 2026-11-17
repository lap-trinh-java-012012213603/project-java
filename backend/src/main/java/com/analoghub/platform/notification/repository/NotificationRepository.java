package com.analoghub.platform.notification.repository;

import com.analoghub.platform.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByRecipientUserIdAndSourceEventId(UUID recipientUserId, UUID sourceEventId);

    long countByRecipientUserIdAndIsReadFalse(UUID recipientUserId);

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(
            UUID recipientUserId,
            Pageable pageable
    );

    Page<Notification> findByRecipientUserIdAndNotificationTypeOrderByCreatedAtDesc(
            UUID recipientUserId,
            String notificationType,
            Pageable pageable
    );

    Page<Notification> findByRecipientUserIdAndIsReadFalseAndNotificationTypeOrderByCreatedAtDesc(
            UUID recipientUserId,
            String notificationType,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientUserId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
