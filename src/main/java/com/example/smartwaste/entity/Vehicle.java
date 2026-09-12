package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String vehicleNumber;
    private double capacity;
    private double fuelEfficiency;
    
    @Enumerated(EnumType.STRING)
    private VehicleStatus status;
}
