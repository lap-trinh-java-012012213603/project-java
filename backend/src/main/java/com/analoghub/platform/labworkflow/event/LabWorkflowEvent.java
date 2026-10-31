package com.analogohub.platform.labworkflow.event;

import java.util.UUID;

public record LabWorkflowEvent(
        UUID workflowId,
        String status
) {}