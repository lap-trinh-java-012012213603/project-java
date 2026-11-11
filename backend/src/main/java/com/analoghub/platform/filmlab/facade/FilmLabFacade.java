package com.analoghub.platform.filmlab.facade;

import java.math.BigDecimal;
import java.util.UUID;

public interface FilmLabFacade {
    boolean reserveCapacity(UUID labId, String processType, int requestedRolls);
    void releaseCapacity(UUID labId, String processType, int rollsToRelease);
    void recordOrderMetrics(UUID labId, BigDecimal orderAmount);
}