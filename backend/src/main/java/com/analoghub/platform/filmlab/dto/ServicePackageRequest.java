package com.analoghub.platform.filmlab.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ServicePackageRequest {
    private String categoryCode;
    private String name;
    private String filmFormat;
    private String processType;
    private String scanResolution;
    private BigDecimal devFeeVnd;
    private BigDecimal scanFeeVnd;
    private int estimatedHours;
}