package com.example.smartwaste.controller;

import com.example.smartwaste.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {
    private final SimulationService simulationService;

    @PostMapping("/run")
    public ResponseEntity<String> runSimulation() {
        simulationService.generateHistoricalData();
        return ResponseEntity.ok("Historical simulation data generated successfully.");
    }
    
    @PostMapping("/load-csv")
    public ResponseEntity<String> loadCsv(@RequestParam String path) {
        simulationService.loadDataFromCsv(path);
        return ResponseEntity.ok("Data loaded successfully from " + path);
    }
}
