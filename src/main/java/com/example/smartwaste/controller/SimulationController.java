package com.example.smartwaste.controller;

import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.dto.SimulationState;
import com.example.smartwaste.simulation.SimulationService;
import com.example.smartwaste.simulation.WorldLock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Control the simulated clock and sensor network")
public class SimulationController {
    private final SimulationService simulationService;
    private final WorldLock worldLock;
    private final SustainabilityConfig config;

    public record StateUpdate(Double speed, Boolean paused, Boolean autopilot) {}

    @GetMapping
    public Map<String, Object> state() {
        return view(simulationService.state());
    }

    @PostMapping
    @Operation(summary = "Change speed (1-3600x real time), pause/resume, or toggle autopilot")
    public Map<String, Object> update(@RequestBody StateUpdate update) {
        if (update.autopilot() != null) simulationService.setAutopilot(update.autopilot());
        return view(simulationService.update(update.speed(), update.paused()));
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        worldLock.run(simulationService::resetDemoData);
        return view(simulationService.state());
    }

    @PostMapping("/run")
    @Operation(summary = "Regenerate 7 days of history for the current bins")
    public ResponseEntity<String> runSimulation() {
        worldLock.run(simulationService::generateHistoricalData);
        return ResponseEntity.ok("Historical simulation data generated successfully.");
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Append sensor readings from a CSV upload (timestamp,bin,fillPercentage)")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty");
        int count = worldLock.call(() -> {
            try {
                return simulationService.importCsv(file.getInputStream());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read upload: " + e.getMessage());
            }
        });
        return Map.of("imported", count);
    }

    private Map<String, Object> view(SimulationState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("simTime", s.simTime());
        m.put("speed", s.speed());
        m.put("paused", s.paused());
        m.put("enabled", s.enabled());
        m.put("autopilot", simulationService.isAutopilot());
        m.put("depot", Map.of("lat", config.getDepotLatitude(), "lon", config.getDepotLongitude()));
        return m;
    }
}
