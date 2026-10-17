package com.example.smartwaste.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record DashboardSummary(
        LocalDateTime simTime,
        int totalBins,
        Map<String, Long> statusCounts,
        long needsPickup,
        double averageFill,
        double totalLoadLitres,
        int vehiclesAvailable,
        int vehiclesOnRoute,
        int activeRoutes,
        long openAlerts,
        LifetimeImpact lifetime
) {}
