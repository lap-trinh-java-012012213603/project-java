package com.analoghub.platform.filmlab.controller;

import com.analoghub.platform.filmlab.dto.LabDetailResponse;
import com.analoghub.platform.filmlab.service.FilmLabPublicService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/labs")
@RequiredArgsConstructor
public class FilmLabPublicController {

    private final FilmLabPublicService filmLabPublicService;

    @GetMapping("/{slugOrId}")
    public ResponseEntity<LabDetailResponse> getLabDetails(
            @PathVariable String slugOrId,
            HttpServletRequest request) {

        String clientIp = request.getRemoteAddr();
        LabDetailResponse response = filmLabPublicService.getLabDetails(slugOrId, clientIp);

        return ResponseEntity.ok(response);
    }
}