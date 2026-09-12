package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.SustainabilityService;
import com.example.smartwaste.dto.SustainabilityComparisonResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {
    private final SustainabilityService sustainabilityService;

    @GetMapping("/comparison")
    public SustainabilityComparisonResult getComparison() {
        return sustainabilityService.compareStrategies();
    }
}
