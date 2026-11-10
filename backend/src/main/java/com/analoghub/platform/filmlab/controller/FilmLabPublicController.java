package com.analoghub.platform.filmlab.controller;

import com.analoghub.platform.filmlab.service.FilmLabPublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/labs")
@RequiredArgsConstructor
public class FilmLabPublicController {

    private final FilmLabPublicService filmLabPublicService;

    /**
     * API công khai: Lấy thông tin chi tiết Lab kèm theo bảng giá công suất hiển thị.
     * Endpoint: GET /api/v1/labs/{slugOrId}
     */
    @GetMapping("/{slugOrId}")
    public ResponseEntity<FilmLabPublicService.LabDetailResponse> getLabDetail(@PathVariable String slugOrId) {
        FilmLabPublicService.LabDetailResponse response = filmLabPublicService.getLabDetailBySlugOrId(slugOrId);
        return ResponseEntity.ok(response);
    }
}