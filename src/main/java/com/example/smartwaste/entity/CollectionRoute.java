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
    private double totalDistanceKm;
    private double estimatedTravelTimeMinutes;
    private double estimatedFuelLitres;
    private double estimatedCo2Kg;
    
    @OneToMany(mappedBy = "collectionRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteStop> stops = new ArrayList<>();
}
