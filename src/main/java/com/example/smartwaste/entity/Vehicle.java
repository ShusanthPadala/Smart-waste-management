package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String vehicleNumber;
    private String driverName;

    /** Payload capacity in litres of compacted waste. */
    private double capacity;
    /** km per litre of diesel. */
    private double fuelEfficiency;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status;

    /** Live position (depot when idle). */
    private Double currentLatitude;
    private Double currentLongitude;
}
