package com.example.smartwaste.controller;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import com.example.smartwaste.repository.RouteStopRepository;
import com.example.smartwaste.simulation.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bins")
@RequiredArgsConstructor
public class BinController {
    private final BinRepository binRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final RouteStopRepository routeStopRepository;
    private final SimulationService simulationService;

    @GetMapping
    public List<Bin> getAllBins() {
        return binRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bin> getBin(@PathVariable Long id) {
        return binRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public Bin createBin(@RequestBody Bin bin) {
        return binRepository.save(bin);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Bin> updateBin(@PathVariable Long id, @RequestBody Bin binDetails) {
        return binRepository.findById(id).map(b -> {
            b.setBinCode(binDetails.getBinCode());
            b.setLocationName(binDetails.getLocationName());
            b.setLatitude(binDetails.getLatitude());
            b.setLongitude(binDetails.getLongitude());
            b.setCapacity(binDetails.getCapacity());
            b.setCurrentFillPercentage(binDetails.getCurrentFillPercentage());
            return ResponseEntity.ok(binRepository.save(b));
        }).orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteBin(@PathVariable Long id) {
        if(binRepository.existsById(id)) {
            sensorReadingRepository.deleteByBinId(id);
            routeStopRepository.setBinToNull(id);
            binRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/urgent")
    public List<Bin> getUrgentBins() {
        return binRepository.findByStatus(BinStatus.URGENT);
    }
    
    @GetMapping("/{id}/history")
    public List<SensorReading> getBinHistory(@PathVariable Long id) {
        // Return up to 50 most recent readings, ordered chronologically
        return sensorReadingRepository.findByBinIdOrderByTimestampDesc(id)
                .stream()
                .limit(50)
                .sorted((r1, r2) -> r1.getTimestamp().compareTo(r2.getTimestamp()))
                .collect(java.util.stream.Collectors.toList());
    }
    
    @PostMapping("/reset-demo")
    public ResponseEntity<Void> resetDemoData() {
        simulationService.resetDemoData();
        return ResponseEntity.ok().build();
    }
}
