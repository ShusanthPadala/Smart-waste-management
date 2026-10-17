package com.example.smartwaste.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class SustainabilityConfig {
    @Value("${app.sustainability.vehicle-fuel-efficiency-km-per-litre:4.5}")
    private double fuelEfficiency = 4.5;

    @Value("${app.sustainability.co2-emission-factor-kg-per-litre:2.68}")
    private double co2Factor = 2.68;

    @Value("${app.sustainability.vehicle-capacity:100.0}")
    private double vehicleCapacity = 100.0;

    @Value("${app.sustainability.average-speed-kmh:25.0}")
    private double averageSpeedKmh = 25.0;

    @Value("${app.sustainability.service-minutes-per-stop:3.0}")
    private double serviceMinutesPerStop = 3.0;

    /** Rough annual CO2 uptake of one mature urban tree, used for the "trees equivalent" figure. */
    @Value("${app.sustainability.co2-absorbed-per-tree-kg-per-year:21.0}")
    private double co2PerTreeKgPerYear = 21.0;

    @Value("${app.depot.latitude:17.4401}")
    private double depotLatitude = 17.4401;

    @Value("${app.depot.longitude:78.3489}")
    private double depotLongitude = 78.3489;
}
