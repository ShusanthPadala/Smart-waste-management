package com.example.smartwaste;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.simulation.SimulationService;
import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.CollectionRoute;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.smartwaste.controller.RouteController;

import java.util.List;

@SpringBootTest
class SmartWasteApplicationTests {

    @Autowired
    private SimulationService simulationService;
    
    @Autowired
    private PredictionService predictionService;
    
    @Autowired
    private RouteController routeController;
    
    @Autowired
    private com.example.smartwaste.repository.BinRepository binRepository;

    @Test
    void generateOutput() throws Exception {
        // 1. Generate data
        simulationService.generateHistoricalData();
        
        // Force bin 4 to be urgent for demonstration of route
        binRepository.findById(4L).ifPresent(bin -> {
            bin.setCurrentFillPercentage(95.0);
            binRepository.save(bin);
        });
        
        // 2. Predict
        List<PredictionResult> predictions = predictionService.runPredictionsForAllBins();
        
        // 3. Route
        Object routeDTO = routeController.optimizeRoute();
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        System.out.println("=== PREDICTION JSON ===");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(predictions));
        
        System.out.println("=== ROUTE JSON ===");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(routeDTO));
    }
}
