package com.analoghub.platform.filmlab.dto;

import lombok.Data;

@Data
public class UpdateCapacityRuleRequest {
    private String processType;
    private int maxDailyRolls;
    private int baseTurnaroundHours;
    private int penaltyHoursPerBatch;
    private boolean isAcceptingOrders;
}