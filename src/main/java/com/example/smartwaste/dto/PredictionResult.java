package com.example.smartwaste.dto;

import com.example.smartwaste.entity.BinStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PredictionResult {
    private Long binId;
    private String binCode;
    private double currentFillPercentage;
    /** Projected fill 24 hours from now (capped at 100). */
    private double predictedFillPercentage;
    private Double estimatedHoursToFull;
    private LocalDateTime predictedOverflowTime;
    /** R-squared of the trend line, penalised when few points are available. */
    private double confidence;
    /** Slope of the trend line in % per hour (0 when there is no rising trend). */
    private double fillRatePerHour;
    private BinStatus recommendedStatus;
}
