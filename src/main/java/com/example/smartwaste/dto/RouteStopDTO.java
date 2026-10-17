package com.example.smartwaste.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RouteStopDTO {
    private Long id;
    private Long binId;
    private int sequenceOrder;
    private String binCode;
    private String locationName;
    private double latitude;
    private double longitude;
    private double fillAtPlanning;
    private double loadLitres;
    private double arrivalOffsetMinutes;
    private boolean collected;
    private LocalDateTime collectedAt;
}
