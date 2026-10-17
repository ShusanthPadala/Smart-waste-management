package com.example.smartwaste.dto;

import java.time.LocalDateTime;

public record SimulationState(LocalDateTime simTime, double speed, boolean paused, boolean enabled) {}
