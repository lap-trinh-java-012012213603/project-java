package com.analoghub.platform.filmlab.repository;

import com.analoghub.platform.filmlab.domain.Lab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabRepository extends JpaRepository<Lab, UUID> {

    // Hỗ trợ tìm kiếm Lab theo slug hoặc id (Dùng cho API GET /api/v1/labs/{slugOrId})
    Optional<Lab> findBySlug(String slug);

    Optional<Lab> findByIdAndActiveTrue(UUID id);
}