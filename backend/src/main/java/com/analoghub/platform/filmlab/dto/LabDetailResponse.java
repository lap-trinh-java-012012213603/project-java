package com.analoghub.platform.filmlab.dto;

import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.domain.ServicePackage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LabDetailResponse {
    private Lab lab;
    private List<ServicePackage> packages;
    private List<LabCapacityRule> capacities;
}