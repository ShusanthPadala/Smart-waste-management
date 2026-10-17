package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class CollectionRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime generatedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    private RouteStatus status = RouteStatus.PLANNED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    private double totalDistanceKm;
    private double estimatedTravelTimeMinutes;
    private double estimatedFuelLitres;
    private double estimatedCo2Kg;
    private double totalLoadLitres;

    /** Distance the same stops would cost without 2-opt improvement (nearest-neighbour only). */
    private double unoptimizedDistanceKm;

    @OneToMany(mappedBy = "collectionRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<RouteStop> stops = new ArrayList<>();
}
