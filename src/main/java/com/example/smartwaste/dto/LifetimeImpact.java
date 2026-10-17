package com.example.smartwaste.dto;

/**
 * Totals over every completed route. {@code averageFillAtPickup} is the key efficiency signal:
 * fixed schedules routinely empty half-full bins, data-driven routing waits until they are worth the trip.
 */
public record LifetimeImpact(
        int routesCompleted,
        int binsCollected,
        double distanceKm,
        double fuelLitres,
        double co2Kg,
        double averageFillAtPickup,
        double wasteCollectedLitres
) {}
