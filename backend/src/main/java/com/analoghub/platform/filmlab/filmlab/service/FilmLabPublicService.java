package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.ServicePackage;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FilmLabPublicService {

    private final LabRepository labRepository;
    private final ServicePackageRepository servicePackageRepository;

    /**
     * Lấy thông tin chi tiết của Lab theo slug hoặc id, kèm theo danh sách gói dịch vụ đang hoạt động.
     */
    public LabDetailResponse getLabDetailBySlugOrId(String slugOrId) {
        Lab lab;
        try {
            // Thử parse xem có phải UUID không
            UUID labId = UUID.fromString(slugOrId);
            lab = labRepository.findByIdAndActiveTrue(labId)
                    .orElseThrow(() -> new RuntimeException("Lab không tồn tại hoặc đã bị khóa: " + slugOrId));
        } catch (IllegalArgumentException e) {
            // Nếu không phải UUID thì tìm theo slug
            lab = labRepository.findBySlug(slugOrId)
                    .filter(Lab::isActive)
                    .orElseThrow(() -> new RuntimeException("Lab không tồn tại với slug: " + slugOrId));
        }

        // Lấy danh sách các gói dịch vụ active của lab này
        List<ServicePackage> servicePackages = servicePackageRepository.findByLabIdAndActiveTrue(lab.getId());

        return new LabDetailResponse(lab, servicePackages);
    }

    // Response DTO nội bộ cho service
    public record LabDetailResponse(
            Lab lab,
            List<ServicePackage> servicePackages
    ) {}
}