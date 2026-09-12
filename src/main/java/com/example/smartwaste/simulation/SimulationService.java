package com.example.smartwaste.simulation;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final BinRepository binRepository;
    private final SensorReadingRepository sensorReadingRepository;

    @Value("${app.simulation.reading-interval-hours:4}")
    private int readingIntervalHours;

    @Value("${app.simulation.days-to-simulate:7}")
    private int daysToSimulate;
    
    @Transactional
    public void resetDemoData() {
        log.info("Resetting demo data...");
        sensorReadingRepository.deleteAll();
        binRepository.deleteAll();
        
        Bin b1 = new Bin();
        b1.setBinCode("B001");
        b1.setLocationName("Green Park Residency");
        b1.setLatitude(40.7128);
        b1.setLongitude(-74.0060);
        b1.setCapacity(100.0);
        b1.setCurrentFillPercentage(0.0);
        b1.setStatus(BinStatus.NORMAL);
        b1.setLastUpdated(LocalDateTime.now().minusDays(1));
        
        Bin b2 = new Bin();
        b2.setBinCode("B002");
        b2.setLocationName("Sector 12 Apartments");
        b2.setLatitude(40.7138);
        b2.setLongitude(-74.0050);
        b2.setCapacity(100.0);
        b2.setCurrentFillPercentage(0.0);
        b2.setStatus(BinStatus.NORMAL);
        b2.setLastUpdated(LocalDateTime.now().minusDays(1));
        
        Bin b3 = new Bin();
        b3.setBinCode("B003");
        b3.setLocationName("Lakeview Society");
        b3.setLatitude(40.7150);
        b3.setLongitude(-74.0030);
        b3.setCapacity(100.0);
        b3.setCurrentFillPercentage(0.0);
        b3.setStatus(BinStatus.NORMAL);
        b3.setLastUpdated(LocalDateTime.now().minusDays(1));
        
        Bin b4 = new Bin();
        b4.setBinCode("B004");
        b4.setLocationName("Community Market");
        b4.setLatitude(40.7110);
        b4.setLongitude(-74.0090);
        b4.setCapacity(100.0);
        b4.setCurrentFillPercentage(0.0);
        b4.setStatus(BinStatus.NORMAL);
        b4.setLastUpdated(LocalDateTime.now().minusDays(1));

        binRepository.saveAll(List.of(b1, b2, b3, b4));
        
        generateHistoricalData();
        
        binRepository.findAll().stream()
            .filter(b -> b.getBinCode().equals("B004"))
            .findFirst()
            .ifPresent(bin -> {
                bin.setCurrentFillPercentage(95.0);
                binRepository.save(bin);
            });
            
        log.info("Demo data reset complete.");
    }

    /**
     * Generates simulated sensor readings for all existing bins.
     */
    @Transactional
    public void generateHistoricalData() {
        log.info("Starting generation of historical data...");
        List<Bin> bins = binRepository.findAll();
        sensorReadingRepository.deleteAll(); // clear old readings
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(daysToSimulate).truncatedTo(ChronoUnit.HOURS);
        
        Random random = new Random(42); // fixed seed for reproducibility
        
        for (Bin bin : bins) {
            double currentFill = 0.0;
            // Determine a "fill rate" category for the bin (e.g. slow, medium, fast)
            double baseRate = 1.0 + (random.nextDouble() * 4.0); // 1% to 5% per interval
            if (bin.getBinCode().equals("B004")) { // Shopping mall is fast
                baseRate = 5.0 + (random.nextDouble() * 5.0); 
            }
            
            LocalDateTime currentTime = startTime;
            while (currentTime.isBefore(now)) {
                SensorReading reading = new SensorReading();
                reading.setBin(bin);
                reading.setTimestamp(currentTime);
                
                // Add some random noise to fill rate
                double stepFill = baseRate * (0.8 + (random.nextDouble() * 0.4));
                currentFill += stepFill;
                
                // If it overflows, simulate a collection
                if (currentFill > 100.0) {
                    currentFill = 0.0; // Emptied
                }
                
                reading.setFillPercentage(Math.min(currentFill, 100.0));
                sensorReadingRepository.save(reading);
                
                // Update bin's current status (so it matches the last reading)
                bin.setCurrentFillPercentage(reading.getFillPercentage());
                bin.setLastUpdated(currentTime);
                
                currentTime = currentTime.plusHours(readingIntervalHours);
            }
            binRepository.save(bin);
        }
        log.info("Finished generation of historical data.");
    }

    /**
     * Loads historical data from a CSV file.
     * Expected format: timestamp(ISO),binId,fillPercentage
     */
    @Transactional
    public void loadDataFromCsv(String filePath) {
        log.info("Loading sensor data from CSV: {}", filePath);
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            log.error("CSV file not found: {}", filePath);
            throw new RuntimeException("CSV file not found: " + filePath);
        }
        
        sensorReadingRepository.deleteAll(); // Clear old readings for demo
        
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length != 3) continue;
                
                LocalDateTime timestamp = LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                Long binId = Long.parseLong(parts[1]);
                double fillPercentage = Double.parseDouble(parts[2]);
                
                Bin bin = binRepository.findById(binId).orElse(null);
                if (bin != null) {
                    SensorReading reading = new SensorReading();
                    reading.setTimestamp(timestamp);
                    reading.setBin(bin);
                    reading.setFillPercentage(fillPercentage);
                    sensorReadingRepository.save(reading);
                    
                    // Keep bin currentFill up to date with the latest reading loaded
                    if (bin.getLastUpdated() == null || timestamp.isAfter(bin.getLastUpdated())) {
                        bin.setLastUpdated(timestamp);
                        bin.setCurrentFillPercentage(fillPercentage);
                        binRepository.save(bin);
                    }
                }
            }
            log.info("Finished loading sensor data from CSV.");
        } catch (Exception e) {
            log.error("Error parsing CSV: ", e);
            throw new RuntimeException("Failed to load CSV: " + e.getMessage());
        }
    }
}
