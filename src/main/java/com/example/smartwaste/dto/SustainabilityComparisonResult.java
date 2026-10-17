package com.example.smartwaste.dto;

import lombok.Data;

@Data
public class SustainabilityComparisonResult {
    /** Fixed schedule: a truck visits every bin, every cycle, regardless of fill level. */
    private Metrics baseline;
    /** Data-driven: only bins that need collection are visited, on an optimised tour. */
    private Metrics optimized;

    private double distanceSavedPct;
    private double fuelSavedPct;
    private double co2SavedPct;

    /** Bins the fixed schedule would have visited needlessly this cycle. */
    private int binsSkipped;
    /** CO2 saved per year if one collection cycle runs per day. */
    private double annualCo2SavedKg;
    /** Mature trees needed to absorb that much CO2 in a year. */
    private double treesEquivalent;

    @Data
    public static class Metrics {
        private double totalDistanceKm;
        private double estimatedFuelLitres;
        private double estimatedCo2Kg;
        private int binsCollected;
        private int totalTrips;
        private double averageFillAtPickup;
    }
}
