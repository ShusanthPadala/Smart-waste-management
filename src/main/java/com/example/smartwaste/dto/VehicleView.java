package com.example.smartwaste.dto;

import com.example.smartwaste.entity.VehicleStatus;

public record VehicleView(
        Long id,
        String vehicleNumber,
        String driverName,
        double capacity,
        double fuelEfficiency,
        VehicleStatus status,
        Double latitude,
        Double longitude,
        Long activeRouteId
) {}
