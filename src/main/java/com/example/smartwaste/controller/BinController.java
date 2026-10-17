package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.dto.BinForecast;
import com.example.smartwaste.dto.BinRequest;
import com.example.smartwaste.dto.BinView;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.service.BinService;
import com.example.smartwaste.service.RouteService;
import com.example.smartwaste.simulation.SimulationService;
import com.example.smartwaste.simulation.WorldLock;
import com.example.smartwaste.web.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/bins")
@RequiredArgsConstructor
@Tag(name = "Bins", description = "Bin registry, sensor history and forecasts")
public class BinController {
    private final BinService binService;
    private final BinRepository binRepository;
    private final PredictionService predictionService;
    private final RouteService routeService;
    private final SimulationService simulationService;
    private final WorldLock worldLock;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "All bins with their latest prediction")
    public List<BinView> getAllBins() {
        return binService.list();
    }

    @GetMapping("/{id}")
    public BinView getBin(@PathVariable Long id) {
        return binService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BinView createBin(@Valid @RequestBody BinRequest request) {
        return worldLock.call(() -> binService.create(request));
    }

    @PutMapping("/{id}")
    public BinView updateBin(@PathVariable Long id, @Valid @RequestBody BinRequest request) {
        return worldLock.call(() -> binService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBin(@PathVariable Long id) {
        worldLock.run(() -> binService.delete(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/urgent")
    @Operation(summary = "Bins that need collection (URGENT or OVERFLOW_RISK)")
    public List<BinView> getUrgentBins() {
        return binService.list().stream()
                .filter(b -> RouteOptimizationService.NEEDS_PICKUP.contains(b.status()))
                .toList();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Up to 50 most recent sensor readings, oldest first")
    public List<SensorReading> getBinHistory(@PathVariable Long id) {
        return binService.history(id);
    }

    @GetMapping("/{id}/forecast")
    @Operation(summary = "Last 72h of readings plus a 24h projection along the fitted trend")
    public BinForecast getForecast(@PathVariable Long id) {
        return predictionService.forecast(id);
    }

    @PostMapping("/{id}/collect")
    @Operation(summary = "Mark a bin as emptied outside a planned route")
    public BinView collect(@PathVariable Long id) {
        return worldLock.call(() -> {
            routeService.collectBin(binRepository.findById(id).orElseThrow(() -> new NotFoundException("Bin " + id + " not found")),
                    LocalDateTime.now(clock));
            return binService.get(id);
        });
    }

    @PostMapping("/reset-demo")
    @Operation(summary = "Wipe all data and restore the demo district")
    public ResponseEntity<Void> resetDemoData() {
        worldLock.run(simulationService::resetDemoData);
        return ResponseEntity.ok().build();
    }
}
