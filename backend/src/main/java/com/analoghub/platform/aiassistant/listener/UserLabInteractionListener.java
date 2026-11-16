package com.analoghub.platform.aiassistant.listener;

import com.analoghub.platform.aiassistant.service.recommendation.InteractionLogger;
import com.analoghub.platform.booking.event.LabOrderCreatedEvent;
import com.analoghub.platform.filmlab.event.LabReviewPostedEvent;
import com.analoghub.platform.filmlab.event.LabViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Listens to domain events across FilmLab and Booking modules to record implicit feedback signals
 * into ai_user_lab_interactions (Section 1.1 of 03-hybrid-recommender-and-semantic-search.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLabInteractionListener {

    private final InteractionLogger interactionLogger;

    @ApplicationModuleListener
    public void onLabViewed(LabViewedEvent event) {
        if (event == null || event.getLabId() == null || !StringUtils.hasText(event.getViewerIpOrId())) {
            return;
        }
        UUID viewerUserId = parseUuidSafely(event.getViewerIpOrId());
        if (viewerUserId == null) {
            // Anonymous guest view by IP — skip user interaction matrix logging
            return;
        }
        interactionLogger.logViewDetail(viewerUserId, event.getLabId());
    }

    @ApplicationModuleListener
    public void onLabOrderCreated(LabOrderCreatedEvent event) {
        if (event == null || event.customerId() == null || event.labId() == null) {
            return;
        }
        interactionLogger.logStartBooking(event.customerId(), event.labId());
        log.info("Logged START_BOOKING (+4.0) implicit interaction: userId={}, labId={}, orderId={}",
                event.customerId(), event.labId(), event.orderId());
    }

    @ApplicationModuleListener
    public void onLabReviewPosted(LabReviewPostedEvent event) {
        if (event == null || event.getLabId() == null) {
            return;
        }
        log.debug("Received LabReviewPostedEvent for labId={}, reviewId={}", event.getLabId(), event.getReviewId());
    }

    private static UUID parseUuidSafely(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
