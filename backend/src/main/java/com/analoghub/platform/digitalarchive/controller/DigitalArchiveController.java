package com.analoghub.platform.digitalarchive.controller;

import com.analoghub.platform.digitalarchive.dto.ArchiveAlbumDto;
import com.analoghub.platform.digitalarchive.dto.ArchiveRollDetailResponse;
import com.analoghub.platform.digitalarchive.dto.UpdateRollMetadataRequest;
import com.analoghub.platform.digitalarchive.service.ContactSheetGeneratorService;
import com.analoghub.platform.digitalarchive.service.DigitalArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
public class DigitalArchiveController {

    private final DigitalArchiveService archiveService;
    private final ContactSheetGeneratorService contactSheetGeneratorService;

    public record ContactSheetKeyResponse(String contactSheetS3Key) {}
    public record DownloadUrlResponse(String downloadUrl) {}

    @PostMapping("/albums")
    public ResponseEntity<ArchiveAlbumDto> createAlbum(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ArchiveAlbumDto.SaveAlbumRequest request
    ) {
        return ResponseEntity.ok(archiveService.createAlbum(userId, request));
    }

    @GetMapping("/albums")
    public ResponseEntity<List<ArchiveAlbumDto>> listAlbums(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(archiveService.listUserAlbums(userId));
    }

    @DeleteMapping("/albums/{albumId}")
    public ResponseEntity<Void> deleteAlbum(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID albumId
    ) {
        archiveService.deleteAlbum(userId, albumId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rolls")
    public ResponseEntity<List<ArchiveRollDetailResponse>> listRolls(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(archiveService.listUserRolls(userId));
    }

    @GetMapping("/rolls/{rollId}")
    public ResponseEntity<ArchiveRollDetailResponse> getRollDetail(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID rollId
    ) {
        return ResponseEntity.ok(archiveService.getRollDetail(userId, rollId));
    }

    @PatchMapping("/rolls/{rollId}/metadata")
    public ResponseEntity<ArchiveRollDetailResponse> updateRollMetadata(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID rollId,
            @Valid @RequestBody UpdateRollMetadataRequest request
    ) {
        return ResponseEntity.ok(archiveService.updateRollMetadata(userId, rollId, request));
    }

    @PostMapping("/rolls/{rollId}/contact-sheet")
    public ResponseEntity<ContactSheetKeyResponse> regenerateContactSheet(
            @PathVariable UUID rollId
    ) {
        String s3Key = contactSheetGeneratorService.generateContactSheetForRoll(rollId);
        return ResponseEntity.ok(new ContactSheetKeyResponse(s3Key != null ? s3Key : ""));
    }

    @PatchMapping("/frames/{frameId}")
    public ResponseEntity<ArchiveRollDetailResponse.FrameDto> updateFrame(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID frameId,
            @RequestParam(required = false) Boolean isFavorite,
            @RequestBody(required = false) Map<String, Object> exifMetadata
    ) {
        return ResponseEntity.ok(
                archiveService.updateFrameExifAndFavorite(userId, frameId, isFavorite, exifMetadata)
        );
    }

    @GetMapping("/frames/{frameId}/download-url")
    public ResponseEntity<DownloadUrlResponse> getFrameDownloadUrl(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID frameId
    ) {
        String url = archiveService.getFrameOriginalDownloadUrl(userId, frameId);
        return ResponseEntity.ok(new DownloadUrlResponse(url));
    }

    @GetMapping("/rolls/{rollId}/download-links")
    public ResponseEntity<List<DigitalArchiveService.FrameDownloadLinkDto>> getRollBatchDownloadLinks(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID rollId
    ) {
        return ResponseEntity.ok(archiveService.getRollBatchDownloadLinks(userId, rollId));
    }
}