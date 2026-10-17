package com.example.smartwaste.dto;

import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SiteProfile;
import com.example.smartwaste.entity.WasteType;
import java.time.LocalDateTime;

/** A bin together with its latest prediction: everything the dashboard needs in one row. */
public record BinView(
        Long id,
        String binCode,
        String locationName,
        String zone,
        WasteType wasteType,
        SiteProfile siteProfile,
        double latitude,
        double longitude,
        double capacity,
        double currentFillPercentage,
        BinStatus status,
        LocalDateTime lastUpdated,
        LocalDateTime lastCollectedAt,
        double predictedFillPercentage,
        Double estimatedHoursToFull,
        LocalDateTime predictedOverflowTime,
        double confidence,
        double fillRatePerHour,
        boolean onActiveRoute
) {}
