package com.analoghub.platform.labworkflow.controller;

import com.analoghub.platform.common.dto.ApiResponse;
import com.analoghub.platform.labworkflow.dto.FilmRollResponse;
import com.analoghub.platform.labworkflow.dto.ReportBlankRollRequest;
import com.analoghub.platform.labworkflow.dto.UpdateRollStatusRequest;
import com.analoghub.platform.labworkflow.service.BlankRollPolicyService;
import com.analoghub.platform.labworkflow.service.RollKanbanService;
import com.analoghub.platform.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my-labs/{labId}/rolls")
@RequiredArgsConstructor
public class LabKanbanController {

    private final RollKanbanService rollKanbanService;
    private final BlankRollPolicyService blankRollPolicyService;

    @GetMapping("/kanban")
    public ResponseEntity<ApiResponse<List<FilmRollResponse>>> getKanbanBoard(@PathVariable UUID labId) {
        return ResponseEntity.ok(ApiResponse.success(rollKanbanService.getLabKanbanRolls(labId)));
    }

    @PatchMapping("/{rollId}/status")
    public ResponseEntity<ApiResponse<FilmRollResponse>> updateRollStatus(
            @PathVariable UUID labId,
            @PathVariable UUID rollId,
            @Valid @RequestBody UpdateRollStatusRequest request
    ) {
        UUID operatorId = SecurityContextUtils.getRequiredCurrentUserId();
        FilmRollResponse response = rollKanbanService.transitionStatus(labId, rollId, operatorId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái Kanban thành công", response));
    }

    @PostMapping("/{rollId}/blank-roll")
    public ResponseEntity<ApiResponse<FilmRollResponse>> reportBlankRoll(
            @PathVariable UUID labId,
            @PathVariable UUID rollId,
            @Valid @RequestBody ReportBlankRollRequest request
    ) {
        UUID operatorId = SecurityContextUtils.getRequiredCurrentUserId();
        FilmRollResponse response = blankRollPolicyService.reportBlankRoll(labId, rollId, operatorId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận cuộn film trắng và hoàn phí scan cho khách", response));
    }
}
