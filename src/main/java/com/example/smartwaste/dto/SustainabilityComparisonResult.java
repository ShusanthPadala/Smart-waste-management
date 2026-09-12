package com.example.smartwaste.dto;
import lombok.Data;

@Data
public class SustainabilityComparisonResult {
    private Metrics baseline;
    private Metrics optimized;
    
    private double distanceSavedPct;
    private double fuelSavedPct;
    private double co2SavedPct;

    @Data
    public static class Metrics {
        private double totalDistanceKm;
        private double estimatedFuelLitres;
        private double estimatedCo2Kg;
        private int binsCollected;
        private int totalTrips;
    }
}
