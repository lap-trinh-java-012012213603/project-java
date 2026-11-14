package com.analoghub.platform.delivery.provider;

import com.analoghub.platform.delivery.domain.LogisticsProviderType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class LogisticsProviderRegistry {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.35;

    private final Map<LogisticsProviderType, LogisticsProvider> providers = new EnumMap<>(LogisticsProviderType.class);
    private final InternalDriverProvider fallbackProvider;

    public LogisticsProviderRegistry(List<LogisticsProvider> providerList, InternalDriverProvider fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
        for (LogisticsProvider provider : providerList) {
            this.providers.put(provider.providerType(), provider);
        }
    }

    public LogisticsProvider getProvider(LogisticsProviderType type) {
        if (type == null) {
            return fallbackProvider;
        }
        return providers.getOrDefault(type, fallbackProvider);
    }

    public LogisticsProvider getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return fallbackProvider;
        }
        try {
            LogisticsProviderType type = LogisticsProviderType.valueOf(providerName.trim().toUpperCase());
            return getProvider(type);
        } catch (IllegalArgumentException ex) {
            return fallbackProvider;
        }
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return round2(EARTH_RADIUS_KM * c);
    }

    public static double estimateRoadDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 3.0;
        }
        double directKm = haversineKm(lat1, lon1, lat2, lon2);
        return round2(Math.max(0.5, directKm * ROAD_FACTOR));
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
