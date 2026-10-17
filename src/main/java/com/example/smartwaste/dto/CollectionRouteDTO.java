package com.example.smartwaste.dto;

import com.example.smartwaste.entity.RouteStatus;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CollectionRouteDTO {
    private Long id;
    private RouteStatus status;
    private Long vehicleId;
    private String vehicleNumber;
    private LocalDateTime generatedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
    private double totalDistanceKm;
    /** Same stops in plain nearest-neighbour order, before 2-opt improvement. */
    private double unoptimizedDistanceKm;
    private double estimatedTravelTimeMinutes;
    private double estimatedFuelLitres;
    private double estimatedCo2Kg;
    private double totalLoadLitres;
    private double vehicleCapacityLitres;
    /** 0..1 share of elapsed route time; 1 once completed. */
    private double progress;
    private List<RouteStopDTO> stops;
}
