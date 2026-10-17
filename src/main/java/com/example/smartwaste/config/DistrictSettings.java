package com.example.smartwaste.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The district being operated: its display name and the depot every route starts and ends at.
 * Starts from application properties and can be changed at runtime from the Settings page.
 */
@Component
public class DistrictSettings {

    private volatile String cityName;
    private volatile double depotLatitude;
    private volatile double depotLongitude;

    public DistrictSettings(@Value("${app.district.name:Visakhapatnam}") String cityName,
                            @Value("${app.depot.latitude:17.7345}") double depotLatitude,
                            @Value("${app.depot.longitude:83.2985}") double depotLongitude) {
        this.cityName = cityName;
        this.depotLatitude = depotLatitude;
        this.depotLongitude = depotLongitude;
    }

    /** Defaults for tests and other non-Spring use. */
    public DistrictSettings() {
        this("Visakhapatnam", 17.7345, 83.2985);
    }

    public String getCityName() { return cityName; }
    public double getDepotLatitude() { return depotLatitude; }
    public double getDepotLongitude() { return depotLongitude; }

    public synchronized void update(String cityName, double depotLatitude, double depotLongitude) {
        this.cityName = cityName;
        this.depotLatitude = depotLatitude;
        this.depotLongitude = depotLongitude;
    }
}
