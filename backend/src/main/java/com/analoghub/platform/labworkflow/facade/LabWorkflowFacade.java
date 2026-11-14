package com.analoghub.platform.labworkflow.facade;

import com.analoghub.platform.labworkflow.dto.FilmRollResponse;

import java.util.List;
import java.util.UUID;

public interface LabWorkflowFacade {

    record OrderItemRollSpec(
            UUID orderItemId,
            String filmFormat,
            String processType,
            String scanResolution,
            int pushPullStops,
            int rollQuantity
    ) {}

    List<FilmRollResponse> initializeRollsForOrder(
            UUID orderId,
            UUID labId,
            UUID customerId,
            List<OrderItemRollSpec> itemSpecs
    );

    List<FilmRollResponse> getRollsByOrderId(UUID orderId);
}