package com.example.smartwaste.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CollectionRouteDTO {
    private Long id;
    private LocalDateTime generatedAt;
    private double totalDistanceKm;
    private double estimatedTravelTimeMinutes;
    private double estimatedFuelLitres;
    private double estimatedCo2Kg;
    private List<RouteStopDTO> stops;
}
