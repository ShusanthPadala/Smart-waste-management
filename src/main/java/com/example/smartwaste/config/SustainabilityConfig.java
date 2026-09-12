package com.example.smartwaste.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class SustainabilityConfig {
    @Value("${app.sustainability.vehicle-fuel-efficiency-km-per-litre:4.5}")
    private double fuelEfficiency;

    @Value("${app.sustainability.co2-emission-factor-kg-per-litre:2.68}")
    private double co2Factor;

    @Value("${app.sustainability.vehicle-capacity:100.0}")
    private double vehicleCapacity;
}
