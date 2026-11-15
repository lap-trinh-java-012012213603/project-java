package com.analoghub.platform.labworkflow.controller;

import com.analoghub.platform.labworkflow.dto.ConfirmScanBatchRequest;
import com.analoghub.platform.labworkflow.dto.InitiateScanBatchRequest;
import com.analoghub.platform.labworkflow.dto.InitiateScanBatchResponse;
import com.analoghub.platform.labworkflow.service.ScanBatchUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labs/{labId}/rolls/{rollId}/scan-batches")
@RequiredArgsConstructor
public class ScanBatchUploadController {

    private final ScanBatchUploadService scanBatchUploadService;

    @PostMapping("/presign")
    public ResponseEntity<InitiateScanBatchResponse> initiateBatch(
            @PathVariable UUID labId,
            @PathVariable UUID rollId,
            @Valid @RequestBody InitiateScanBatchRequest request
    ) {
        InitiateScanBatchResponse response = scanBatchUploadService.initiateBatchUpload(labId, rollId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{batchId}/confirm")
    public ResponseEntity<Void> confirmBatch(
            @PathVariable UUID labId,
            @PathVariable UUID rollId,
            @PathVariable UUID batchId,
            @RequestBody(required = false) ConfirmScanBatchRequest request
    ) {
        scanBatchUploadService.confirmBatchUpload(labId, rollId, batchId, request);
        return ResponseEntity.ok().build();
    }
}