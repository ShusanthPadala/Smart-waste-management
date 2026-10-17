package com.example.smartwaste.entity;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * How a bin's surroundings are used. Drives the simulated fill rate: a typical rate (% per hour)
 * shaped by time of day and weekday.
 */
public enum SiteProfile {
    RESIDENTIAL(1.2),
    MARKET(2.5),
    COMMERCIAL(1.6),
    PARK(0.9),
    TRANSIT(1.8);

    private final double defaultRatePerHour;

    SiteProfile(double defaultRatePerHour) {
        this.defaultRatePerHour = defaultRatePerHour;
    }

    public double defaultRatePerHour() {
        return defaultRatePerHour;
    }

    /** How busy the site is at {@code at} relative to its average (roughly averages to 1 over a week). */
    public double usageMultiplier(LocalDateTime at) {
        int h = at.getHour();
        boolean weekend = at.getDayOfWeek() == DayOfWeek.SATURDAY || at.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean night = h < 6;
        return switch (this) {
            case RESIDENTIAL -> night ? 0.25 : (h >= 7 && h <= 9) || (h >= 19 && h <= 22) ? 1.8 : 0.9;
            case MARKET -> night ? 0.1 : (h >= 8 && h <= 20 ? 1.6 : 0.5) * (weekend ? 1.4 : 1.0);
            case COMMERCIAL -> weekend ? 0.3 : (h >= 9 && h <= 18 ? 2.0 : 0.25);
            case PARK -> night ? 0.1 : ((h >= 6 && h <= 9) || (h >= 16 && h <= 20) ? 1.7 : 0.8) * (weekend ? 1.6 : 0.9);
            case TRANSIT -> night ? 0.2 : (h >= 8 && h <= 10) || (h >= 17 && h <= 20) ? 2.0 : 1.0;
        };
    }
}
