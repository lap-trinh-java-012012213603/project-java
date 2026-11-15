package com.analoghub.platform.digitalarchive.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.digitalarchive.domain.ArchiveAlbum;
import com.analoghub.platform.digitalarchive.domain.ArchiveFrame;
import com.analoghub.platform.digitalarchive.domain.ArchiveRoll;
import com.analoghub.platform.digitalarchive.dto.ArchiveAlbumDto;
import com.analoghub.platform.digitalarchive.dto.ArchiveRollDetailResponse;
import com.analoghub.platform.digitalarchive.dto.UpdateRollMetadataRequest;
import com.analoghub.platform.digitalarchive.repository.ArchiveAlbumRepository;
import com.analoghub.platform.digitalarchive.repository.ArchiveFrameRepository;
import com.analoghub.platform.digitalarchive.repository.ArchiveRollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DigitalArchiveService {

    private final ArchiveAlbumRepository albumRepository;
    private final ArchiveRollRepository rollRepository;
    private final ArchiveFrameRepository frameRepository;
    private final StorageService storageService;

    public record FrameDownloadLinkDto(
            int frameNumber,
            String fileName,
            String downloadUrl
    ) {}

    @Transactional
    public ArchiveAlbumDto createAlbum(UUID userId, ArchiveAlbumDto.SaveAlbumRequest req) {
        ArchiveAlbum album = new ArchiveAlbum();
        album.setOwnerUserId(userId);
        album.setTitle(req.title());
        album.setDescription(req.description());
        album.setVisibility(req.visibility() != null ? req.visibility() : "PRIVATE");
        return toAlbumDto(albumRepository.save(album));
    }

    @Transactional(readOnly = true)
    public List<ArchiveAlbumDto> listUserAlbums(UUID userId) {
        return albumRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toAlbumDto)
                .toList();
    }

    @Transactional
    public void deleteAlbum(UUID userId, UUID albumId) {
        ArchiveAlbum album = albumRepository.findByIdAndDeletedAtIsNull(albumId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy album"));
        if (!album.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xóa album này");
        }
        album.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        albumRepository.save(album);
    }

    @Transactional(readOnly = true)
    public List<ArchiveRollDetailResponse> listUserRolls(UUID userId) {
        return rollRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(r -> toRollResponse(r, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArchiveRollDetailResponse getRollDetail(UUID userId, UUID rollId) {
        ArchiveRoll roll = getOwnedRoll(userId, rollId);
        List<ArchiveFrame> frames = frameRepository.findByArchiveRollIdOrderByFrameNumberAsc(rollId);
        return toRollResponse(roll, frames);
    }

    @Transactional
    public ArchiveRollDetailResponse updateRollMetadata(UUID userId, UUID rollId, UpdateRollMetadataRequest req) {
        ArchiveRoll roll = getOwnedRoll(userId, rollId);
        if (req.albumId() != null) {
            roll.setAlbumId(req.albumId());
        }
        if (req.rollTitle() != null && !req.rollTitle().isBlank()) {
            roll.setRollTitle(req.rollTitle());
        }
        if (req.cameraModel() != null) {
            roll.setCameraModel(req.cameraModel());
        }
        if (req.lensModel() != null) {
            roll.setLensModel(req.lensModel());
        }
        if (req.shotIso() != null) {
            roll.setShotIso(req.shotIso());
        }
        if (req.shotDate() != null) {
            roll.setShotDate(req.shotDate());
        }
        if (req.scanScannerModel() != null) {
            roll.setScanScannerModel(req.scanScannerModel());
        }
        ArchiveRoll saved = rollRepository.save(roll);
        List<ArchiveFrame> frames = frameRepository.findByArchiveRollIdOrderByFrameNumberAsc(rollId);
        return toRollResponse(saved, frames);
    }

    @Transactional
    public ArchiveRollDetailResponse.FrameDto updateFrameExifAndFavorite(
            UUID userId,
            UUID frameId,
            Boolean isFavorite,
            Map<String, Object> exifMetadata
    ) {
        ArchiveFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy frame"));
        if (!frame.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền chỉnh sửa frame này");
        }
        if (isFavorite != null) {
            frame.setIsFavorite(isFavorite);
        }
        if (exifMetadata != null) {
            Map<String, Object> merged = new HashMap<>(frame.getExifMetadata() != null ? frame.getExifMetadata() : Map.of());
            merged.putAll(exifMetadata);
            frame.setExifMetadata(merged);
        }
        ArchiveFrame saved = frameRepository.save(frame);
        return toFrameDto(saved);
    }

    @Transactional(readOnly = true)
    public String getFrameOriginalDownloadUrl(UUID userId, UUID frameId) {
        ArchiveFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy frame"));
        if (!frame.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền tải xuống frame này");
        }
        String fileName = String.format("frame_%02d_original.jpg", frame.getFrameNumber());
        return storageService.generateDownloadOriginalUrl(frame.getS3KeyOriginal(), fileName);
    }

    @Transactional(readOnly = true)
    public List<FrameDownloadLinkDto> getRollBatchDownloadLinks(UUID userId, UUID rollId) {
        ArchiveRoll roll = getOwnedRoll(userId, rollId);
        List<ArchiveFrame> frames = frameRepository.findByArchiveRollIdOrderByFrameNumberAsc(roll.getId());
        return frames.stream()
                .map(f -> {
                    String fileName = String.format("%s_frame_%02d.jpg",
                            roll.getRollTitle().replaceAll("\\s+", "_"), f.getFrameNumber());
                    String url = storageService.generateDownloadOriginalUrl(f.getS3KeyOriginal(), fileName);
                    return new FrameDownloadLinkDto(f.getFrameNumber(), fileName, url);
                })
                .toList();
    }

    private ArchiveRoll getOwnedRoll(UUID userId, UUID rollId) {
        ArchiveRoll roll = rollRepository.findByIdAndDeletedAtIsNull(rollId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cuộn film lưu trữ"));
        if (!roll.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập cuộn film này");
        }
        return roll;
    }

    private ArchiveAlbumDto toAlbumDto(ArchiveAlbum a) {
        return new ArchiveAlbumDto(
                a.getId(),
                a.getOwnerUserId(),
                a.getTitle(),
                a.getDescription(),
                a.getVisibility(),
                a.getCreatedAt() != null ? a.getCreatedAt().atOffset(ZoneOffset.UTC) : null
        );
    }

    private ArchiveRollDetailResponse toRollResponse(ArchiveRoll r, List<ArchiveFrame> frames) {
        return new ArchiveRollDetailResponse(
                r.getId(),
                r.getOwnerUserId(),
                r.getAlbumId(),
                r.getSourceFilmRollId(),
                r.getSourceOrderId(),
                r.getSourceLabId(),
                r.getRollTitle(),
                r.getCameraModel(),
                r.getLensModel(),
                r.getShotIso(),
                r.getShotDate(),
                r.getProcessType(),
                r.getScanScannerModel(),
                storageService.resolveReadUrl(r.getContactSheetS3Key()),
                r.getFrameCount(),
                r.getTotalSizeBytes(),
                r.getRawExpiresAt(),
                r.getCreatedAt() != null ? r.getCreatedAt().atOffset(ZoneOffset.UTC) : null,
                frames.stream().map(this::toFrameDto).toList()
        );
    }

    private ArchiveRollDetailResponse.FrameDto toFrameDto(ArchiveFrame f) {
        return new ArchiveRollDetailResponse.FrameDto(
                f.getId(),
                f.getFrameNumber(),
                storageService.resolveReadUrl(f.getS3KeyWeb()),
                storageService.resolveReadUrl(f.getS3KeyThumb()),
                f.getWidthPx(),
                f.getHeightPx(),
                f.getSizeBytes(),
                f.getQcStatus(),
                f.getQcOverallScore(),
                f.getExifMetadata(),
                Boolean.TRUE.equals(f.getIsFavorite())
        );
    }
}