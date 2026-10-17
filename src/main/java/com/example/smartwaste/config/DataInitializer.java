package com.example.smartwaste.config;

import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.simulation.SimulationService;
import com.example.smartwaste.simulation.WorldLock;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    /** Seeds the demo district on a fresh database. */
    @Bean
    public CommandLineRunner initData(BinRepository binRepository, SimulationService simulationService, WorldLock worldLock) {
        return args -> {
            if (binRepository.count() == 0) {
                worldLock.run(simulationService::resetDemoData);
            }
        };
    }
}
