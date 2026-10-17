package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Data
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    @JsonIgnore
    private CollectionRoute collectionRoute;

    private int sequenceOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id")
    private Bin bin;

    private String binCode;
    private String locationName;
    private double latitude;
    private double longitude;
    private double fillAtPlanning;
    private double loadLitres;

    /** Minutes after dispatch at which the truck reaches this stop. */
    private double arrivalOffsetMinutes;

    private boolean collected;
    private LocalDateTime collectedAt;
}
