package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.iam.dto.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and normalizes {@code portfolioPayload} according to {@code requestedRole}
 * ({@code LAB_OWNER}, {@code EXPERT}, {@code DELIVERY_PARTNER}) per API 4.13 of {@code 04-iam.md}.
 */
@Component
@RequiredArgsConstructor
public class RoleApplicationPayloadValidator {

    private static final Set<RoleCode> ALLOWED_UPGRADE_ROLES = Set.of(
            RoleCode.LAB_OWNER,
            RoleCode.EXPERT,
            RoleCode.DELIVERY_PARTNER
    );

    private static final String PROTECTED_ROLE_APP_PREFIX = "protected/role-applications/";

    private final AddressCoordinateResolver coordinateResolver;

    public Map<String, Object> validateAndNormalize(
            UUID applicantUserId,
            RoleCode requestedRole,
            Map<String, Object> portfolioPayload
    ) {
        if (requestedRole == null || !ALLOWED_UPGRADE_ROLES.contains(requestedRole)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vai trò xin nâng cấp không hợp lệ. Chỉ cho phép LAB_OWNER, EXPERT hoặc DELIVERY_PARTNER."
            );
        }
        if (portfolioPayload == null || portfolioPayload.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Hồ sơ năng lực (portfolioPayload) không được để trống."
            );
        }

        Map<String, Object> normalized = new LinkedHashMap<>(portfolioPayload);

        switch (requestedRole) {
            case LAB_OWNER -> validateLabOwnerPayload(normalized);
            case EXPERT -> validateExpertPayload(normalized);
            case DELIVERY_PARTNER -> validateDeliveryPartnerPayload(normalized);
            default -> throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Vai trò xin nâng cấp không được hỗ trợ: " + requestedRole
            );
        }

        validateEvidenceKeysOwnership(applicantUserId, normalized.get("evidenceDocumentKeys"));
        validateEvidenceKeysOwnership(applicantUserId, normalized.get("idCardImageKeys"));
        validateEvidenceKeysOwnership(applicantUserId, normalized.get("labPhotoS3Keys"));

        return normalized;
    }

    private void validateLabOwnerPayload(Map<String, Object> payload) {
        requireNonBlankString(payload, "proposedLabName", "Hồ sơ LAB_OWNER bắt buộc có tên phòng Lab (proposedLabName).");

        String labAddress = asTrimmedString(payload.get("labAddress"));
        if (labAddress == null) {
            labAddress = asTrimmedString(payload.get("fullAddress"));
        }
        if (labAddress == null || labAddress.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Hồ sơ LAB_OWNER bắt buộc có địa chỉ phòng Lab (labAddress)."
            );
        }

        requireNonEmptyCollection(payload, "equipmentList", "Hồ sơ LAB_OWNER bắt buộc liệt kê danh sách thiết bị (equipmentList).");
        requireNonEmptyCollection(payload, "supportedProcesses", "Hồ sơ LAB_OWNER bắt buộc liệt kê các quy trình hỗ trợ (supportedProcesses).");

        Double lat = asDouble(payload.get("latitude"));
        Double lon = asDouble(payload.get("longitude"));
        String provinceCode = asTrimmedString(payload.get("provinceCode"));
        String districtCode = asTrimmedString(payload.get("districtCode"));
        String wardCode = asTrimmedString(payload.get("wardCode"));

        if (lat != null || lon != null || provinceCode != null || districtCode != null) {
            AddressCoordinateResolver.GeoPoint coords = coordinateResolver.resolveCoordinates(
                    lat, lon, provinceCode, districtCode, wardCode, labAddress
            );
            payload.put("latitude", coords.latitude());
            payload.put("longitude", coords.longitude());
        }
    }

    private void validateExpertPayload(Map<String, Object> payload) {
        requireNonEmptyCollection(payload, "specialtyAreas", "Hồ sơ EXPERT bắt buộc có lĩnh vực chuyên môn (specialtyAreas).");
        if (payload.get("yearsOfExperience") == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Hồ sơ EXPERT bắt buộc có số năm kinh nghiệm (yearsOfExperience)."
            );
        }
    }

    private void validateDeliveryPartnerPayload(Map<String, Object> payload) {
        requireNonBlankString(payload, "vehicleType", "Hồ sơ DELIVERY_PARTNER bắt buộc có loại phương tiện (vehicleType).");
        requireNonBlankString(payload, "licensePlate", "Hồ sơ DELIVERY_PARTNER bắt buộc có biển kiểm soát (licensePlate).");
    }

    private void validateEvidenceKeysOwnership(UUID applicantUserId, Object rawKeys) {
        if (!(rawKeys instanceof Collection<?> keys)) {
            return;
        }
        String expectedPrefix = PROTECTED_ROLE_APP_PREFIX + applicantUserId + "/";
        for (Object item : keys) {
            if (item == null) {
                continue;
            }
            String key = item.toString().trim();
            if (key.startsWith(PROTECTED_ROLE_APP_PREFIX) && !key.startsWith(expectedPrefix)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Tài liệu minh chứng '" + key + "' không thuộc thư mục hồ sơ của người dùng hiện tại."
                );
            }
        }
    }

    private static void requireNonBlankString(Map<String, Object> payload, String key, String errorMessage) {
        String val = asTrimmedString(payload.get(key));
        if (val == null || val.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
    }

    private static void requireNonEmptyCollection(Map<String, Object> payload, String key, String errorMessage) {
        Object raw = payload.get(key);
        if (!(raw instanceof Collection<?> col) || col.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
    }

    private static String asTrimmedString(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Double asDouble(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tọa độ GPS không đúng định dạng số thực.");
        }
    }
}
