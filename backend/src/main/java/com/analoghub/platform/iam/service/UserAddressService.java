package com.analoghub.platform.iam.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.iam.domain.UserAddress;
import com.analoghub.platform.iam.dto.DeleteUserAddressResponse;
import com.analoghub.platform.iam.dto.UpsertUserAddressRequest;
import com.analoghub.platform.iam.dto.UserAddressDto;
import com.analoghub.platform.iam.repository.UserAddressRepository;
import com.analoghub.platform.iam.repository.UserRepository;
import com.analoghub.platform.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for User Address Book management (APIs 4.9 - 4.12 of {@code 04-iam.md}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    public static final int MAX_ADDRESSES_PER_USER = 10;

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;
    private final AddressCoordinateResolver coordinateResolver;

    /**
     * API 4.9: Lists all active addresses for the current user, ordered by {@code is_default DESC, created_at DESC}.
     */
    @Transactional(readOnly = true)
    public List<UserAddressDto> getMyAddresses() {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();
        return userAddressRepository
                .findByUserIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toUserAddressDto)
                .toList();
    }

    /**
     * API 4.10: Creates a new address in the user's address book (max 10 active addresses),
     * resolving GPS coordinates and ensuring single default address consistency.
     */
    @Transactional
    public UserAddressDto createAddress(UpsertUserAddressRequest request) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        userRepository.findByIdForUpdate(currentUserId)
                .or(() -> userRepository.findByIdAndDeletedAtIsNull(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy hồ sơ người dùng."
                ));

        long currentCount = userAddressRepository.countByUserIdAndDeletedAtIsNull(currentUserId);
        if (currentCount >= MAX_ADDRESSES_PER_USER) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Sổ địa chỉ đã đạt giới hạn tối đa 10 địa chỉ. Vui lòng xóa bớt địa chỉ cũ trước khi thêm mới."
            );
        }

        boolean isDefault = (currentCount == 0) || Boolean.TRUE.equals(request.isDefault());
        if (isDefault && currentCount > 0) {
            userAddressRepository.clearDefaultForUser(currentUserId);
        }

        AddressCoordinateResolver.GeoPoint coords = coordinateResolver.resolveCoordinates(
                request.latitude(),
                request.longitude(),
                request.provinceCode(),
                request.districtCode(),
                request.wardCode(),
                request.fullAddress()
        );

        UserAddress address = UserAddress.builder()
                .userId(currentUserId)
                .recipientName(request.recipientName().trim())
                .recipientPhone(AuthService.normalizeVnPhone(request.recipientPhone()))
                .fullAddress(request.fullAddress().trim())
                .wardCode(request.wardCode().trim())
                .districtCode(request.districtCode().trim())
                .provinceCode(request.provinceCode().trim())
                .latitude(coords.latitude())
                .longitude(coords.longitude())
                .isDefault(isDefault)
                .build();

        address = userAddressRepository.saveAndFlush(address);
        return toUserAddressDto(address);
    }

    /**
     * API 4.11: Updates an existing address owned by the current user or sets it as the default address.
     */
    @Transactional
    public UserAddressDto updateAddress(UUID addressId, UpsertUserAddressRequest request) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        UserAddress address = userAddressRepository.findByIdForUpdate(addressId)
                .or(() -> userAddressRepository.findByIdAndDeletedAtIsNull(addressId))
                .orElseThrow(() -> new ResourceNotFoundException("UserAddress", addressId));

        if (!address.getUserId().equals(currentUserId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không có quyền chỉnh sửa địa chỉ thuộc sở hữu của người dùng khác."
            );
        }

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            userAddressRepository.clearDefaultForUser(currentUserId);
            address.setDefault(true);
        }

        AddressCoordinateResolver.GeoPoint coords = coordinateResolver.resolveCoordinates(
                request.latitude(),
                request.longitude(),
                request.provinceCode(),
                request.districtCode(),
                request.wardCode(),
                request.fullAddress()
        );

        address.setRecipientName(request.recipientName().trim());
        address.setRecipientPhone(AuthService.normalizeVnPhone(request.recipientPhone()));
        address.setFullAddress(request.fullAddress().trim());
        address.setWardCode(request.wardCode().trim());
        address.setDistrictCode(request.districtCode().trim());
        address.setProvinceCode(request.provinceCode().trim());
        address.setLatitude(coords.latitude());
        address.setLongitude(coords.longitude());

        address = userAddressRepository.saveAndFlush(address);
        return toUserAddressDto(address);
    }

    /**
     * API 4.12: Soft-deletes an address from the current user's address book.
     * If the deleted address was the default, promotes the most recent remaining active address to default.
     */
    @Transactional
    public DeleteUserAddressResponse deleteAddress(UUID addressId) {
        UUID currentUserId = SecurityContextUtils.getRequiredCurrentUserId();

        UserAddress address = userAddressRepository.findByIdForUpdate(addressId)
                .or(() -> userAddressRepository.findByIdAndDeletedAtIsNull(addressId))
                .orElseThrow(() -> new ResourceNotFoundException("UserAddress", addressId));

        if (!address.getUserId().equals(currentUserId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Bạn không có quyền chỉnh sửa địa chỉ thuộc sở hữu của người dùng khác."
            );
        }

        boolean wasDefault = address.isDefault();
        Instant deletedAt = Instant.now();
        address.setDeletedAt(deletedAt);
        address.setDefault(false);
        userAddressRepository.saveAndFlush(address);

        UUID newDefaultAddressId = null;
        if (wasDefault) {
            Optional<UserAddress> nextCandidateOpt = userAddressRepository
                    .findFirstByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserId);
            if (nextCandidateOpt.isPresent()) {
                UserAddress nextDefault = nextCandidateOpt.get();
                nextDefault.setDefault(true);
                nextDefault = userAddressRepository.saveAndFlush(nextDefault);
                newDefaultAddressId = nextDefault.getId();
            }
        } else {
            newDefaultAddressId = userAddressRepository
                    .findFirstByUserIdAndIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserId)
                    .map(UserAddress::getId)
                    .orElse(null);
        }

        return new DeleteUserAddressResponse(addressId, newDefaultAddressId, deletedAt);
    }

    private UserAddressDto toUserAddressDto(UserAddress address) {
        return new UserAddressDto(
                address.getId(),
                address.getUserId(),
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getFullAddress(),
                address.getWardCode(),
                address.getDistrictCode(),
                address.getProvinceCode(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
