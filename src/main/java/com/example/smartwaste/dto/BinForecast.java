package com.example.smartwaste.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BinForecast(
        Long binId,
        String binCode,
        List<Point> history,
        List<Point> forecast,
        double fillRatePerHour,
        double confidence,
        Double estimatedHoursToFull
) {
    public record Point(LocalDateTime timestamp, double fillPercentage) {}
}
