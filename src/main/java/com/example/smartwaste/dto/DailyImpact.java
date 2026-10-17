package com.example.smartwaste.dto;

import java.time.LocalDate;

public record DailyImpact(LocalDate date, int routes, int binsCollected, double distanceKm, double co2Kg) {}
