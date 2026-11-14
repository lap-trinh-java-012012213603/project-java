package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Resolves and validates GPS coordinates ({@code latitude}, {@code longitude}) for user addresses
 * and Film Lab role applications, and provides Haversine distance calculation.
 */
@Component
public class AddressCoordinateResolver {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static final Map<String, GeoPoint> PROVINCE_CENTROIDS = Map.ofEntries(
            Map.entry("79", new GeoPoint(10.776889, 106.700806)), // TP. Hồ Chí Minh
            Map.entry("01", new GeoPoint(21.028511, 105.804817)), // Hà Nội
            Map.entry("1", new GeoPoint(21.028511, 105.804817)),
            Map.entry("48", new GeoPoint(16.054407, 108.202167)), // Đà Nẵng
            Map.entry("31", new GeoPoint(20.844912, 106.688084)), // Hải Phòng
            Map.entry("92", new GeoPoint(10.045162, 105.746854)), // Cần Thơ
            Map.entry("75", new GeoPoint(10.957413, 106.842690)), // Đồng Nai
            Map.entry("74", new GeoPoint(10.980461, 106.651856)), // Bình Dương
            Map.entry("77", new GeoPoint(10.345990, 107.084260)), // Bà Rịa - Vũng Tàu
            Map.entry("46", new GeoPoint(16.463713, 107.590866)), // Thừa Thiên Huế
            Map.entry("68", new GeoPoint(11.940419, 108.458313)), // Lâm Đồng (Đà Lạt)
            Map.entry("56", new GeoPoint(12.238791, 109.196749))  // Khánh Hòa (Nha Trang)
    );

    private static final Map<String, GeoPoint> DISTRICT_CENTROIDS = Map.ofEntries(
            // TP. Hồ Chí Minh districts
            Map.entry("760", new GeoPoint(10.775658, 106.700424)), // Quận 1
            Map.entry("770", new GeoPoint(10.784370, 106.684409)), // Quận 3
            Map.entry("773", new GeoPoint(10.757826, 106.701297)), // Quận 4
            Map.entry("774", new GeoPoint(10.754028, 106.663375)), // Quận 5
            Map.entry("771", new GeoPoint(10.772109, 106.667954)), // Quận 10
            Map.entry("765", new GeoPoint(10.810583, 106.709142)), // Quận Bình Thạnh
            Map.entry("768", new GeoPoint(10.799194, 106.680288)), // Quận Phú Nhuận
            Map.entry("766", new GeoPoint(10.801466, 106.652597)), // Quận Tân Bình
            Map.entry("778", new GeoPoint(10.734034, 106.721579)), // Quận 7
            Map.entry("769", new GeoPoint(10.823099, 106.758435)), // TP. Thủ Đức
            // Hà Nội districts
            Map.entry("001", new GeoPoint(21.034085, 105.819454)), // Quận Ba Đình
            Map.entry("002", new GeoPoint(21.028511, 105.854167)), // Quận Hoàn Kiếm
            Map.entry("003", new GeoPoint(21.066695, 105.814141)), // Quận Tây Hồ
            Map.entry("005", new GeoPoint(21.030653, 105.788551)), // Quận Cầu Giấy
            Map.entry("006", new GeoPoint(21.018072, 105.829949)), // Quận Đống Đa
            Map.entry("007", new GeoPoint(21.006382, 105.855132))  // Quận Hai Bà Trưng
    );

    public record GeoPoint(double latitude, double longitude) {
    }

    /**
     * Validates provided coordinates or computes deterministic coordinates based on administrative codes
     * and street address when {@code latitude} or {@code longitude} is {@code null}.
     */
    public GeoPoint resolveCoordinates(
            Double latitude,
            Double longitude,
            String provinceCode,
            String districtCode,
            String wardCode,
            String fullAddress
    ) {
        if (latitude != null && longitude != null) {
            validateRange(latitude, longitude);
            return new GeoPoint(round6(latitude), round6(longitude));
        }

        GeoPoint base = resolveBaseCentroid(provinceCode, districtCode);
        String seed = (wardCode != null ? wardCode.trim() : "") + "|" + (fullAddress != null ? fullAddress.trim() : "");
        double[] microOffset = computeDeterministicOffset(seed);

        double resolvedLat = latitude != null ? latitude : round6(base.latitude() + microOffset[0]);
        double resolvedLon = longitude != null ? longitude : round6(base.longitude() + microOffset[1]);
        validateRange(resolvedLat, resolvedLon);
        return new GeoPoint(resolvedLat, resolvedLon);
    }

    public static void validateRange(double latitude, double longitude) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vĩ độ (latitude) không hợp lệ, phải nằm trong khoảng [-90.0, 90.0]."
            );
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Kinh độ (longitude) không hợp lệ, phải nằm trong khoảng [-180.0, 180.0]."
            );
        }
    }

    /**
     * Calculates the Haversine distance in kilometers between two GPS coordinates.
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return round6(EARTH_RADIUS_KM * c);
    }

    private static GeoPoint resolveBaseCentroid(String provinceCode, String districtCode) {
        if (districtCode != null && !districtCode.isBlank()) {
            GeoPoint districtPoint = DISTRICT_CENTROIDS.get(districtCode.trim());
            if (districtPoint != null) {
                return districtPoint;
            }
        }
        if (provinceCode != null && !provinceCode.isBlank()) {
            GeoPoint provincePoint = PROVINCE_CENTROIDS.get(provinceCode.trim());
            if (provincePoint != null) {
                return provincePoint;
            }
        }
        // Default centroid: District 1, Ho Chi Minh City
        return new GeoPoint(10.776889, 106.700806);
    }

    private static double[] computeDeterministicOffset(String seed) {
        if (seed == null || seed.isBlank() || "|".equals(seed)) {
            return new double[]{0.0, 0.0};
        }
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        long hash = crc.getValue();
        // Offset within [-0.005, +0.005] degrees (~500m radius)
        double latOffset = (((hash & 0xFFFFL) / 65535.0) - 0.5) * 0.01;
        double lonOffset = ((((hash >> 16) & 0xFFFFL) / 65535.0) - 0.5) * 0.01;
        return new double[]{latOffset, lonOffset};
    }

    private static double round6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
