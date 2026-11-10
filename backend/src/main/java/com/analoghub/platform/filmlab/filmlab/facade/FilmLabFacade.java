package com.analoghub.platform.filmlab.facade;

public interface FilmLabFacade {

    boolean reserveCapacity(Long labId, int rollCount);

    void releaseCapacity(Long labId, int rollCount);

    void recordOrderMetrics(Long labId, int rollCount);
}