package com.example.smartwaste.dto;

import com.example.smartwaste.entity.BinStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PredictionResult {
    private Long binId;
    private double currentFillPercentage;
    private double predictedFillPercentage;
    private Double estimatedHoursToFull;
    private LocalDateTime predictedOverflowTime;
    private double confidence;
    private BinStatus recommendedStatus;
}
