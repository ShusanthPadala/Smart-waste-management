package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.SustainabilityService;
import com.example.smartwaste.dto.DailyImpact;
import com.example.smartwaste.dto.LifetimeImpact;
import com.example.smartwaste.dto.SustainabilityComparisonResult;
import com.example.smartwaste.dto.ZoneStat;
import com.example.smartwaste.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Sustainability & analytics")
public class MetricsController {
    private final SustainabilityService sustainabilityService;
    private final DashboardService dashboardService;

    @GetMapping({"/api/metrics/comparison", "/api/sustainability/compare"})
    @Operation(summary = "Fixed schedule vs optimised route for the current bin state")
    public SustainabilityComparisonResult getComparison() {
        return sustainabilityService.compareStrategies();
    }

    @GetMapping("/api/metrics/lifetime")
    @Operation(summary = "Totals over all completed routes")
    public LifetimeImpact lifetime() {
        return sustainabilityService.lifetimeImpact();
    }

    @GetMapping("/api/metrics/daily")
    @Operation(summary = "Completed-route totals per simulated day")
    public List<DailyImpact> daily(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("days must be between 1 and 90");
        return sustainabilityService.dailyImpact(days);
    }

    @GetMapping("/api/metrics/zones")
    @Operation(summary = "Average fill and pickup demand per zone")
    public List<ZoneStat> zones() {
        return dashboardService.zones();
    }
}
