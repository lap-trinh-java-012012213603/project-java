package com.analoghub.platform.common.exception;

public class MarketplaceException extends BusinessException {

    public MarketplaceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static MarketplaceException listingNotAvailable() {
        return new MarketplaceException(ErrorCode.LISTING_NOT_AVAILABLE);
    }

    public static MarketplaceException cannotOfferOwnListing() {
        return new MarketplaceException(ErrorCode.CANNOT_OFFER_OWN_LISTING);
    }
}