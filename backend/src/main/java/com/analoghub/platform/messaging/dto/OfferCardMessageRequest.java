package com.analoghub.platform.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferCardMessageRequest(
        UUID listingId,
        UUID offerId,
        UUID senderUserId,
        UUID recipientUserId,
        String offerType,
        BigDecimal offeredPriceVnd,
        String swapDescription,
        String status
) {
}
