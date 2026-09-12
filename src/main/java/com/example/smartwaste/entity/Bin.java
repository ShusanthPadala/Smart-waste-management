package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Bin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String binCode;
    private String locationName;
    private double latitude;
    private double longitude;
    private double capacity;
    private double currentFillPercentage;
    
    private LocalDateTime lastUpdated;
    
    @Enumerated(EnumType.STRING)
    private BinStatus status;
}
