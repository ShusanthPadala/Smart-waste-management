package com.example.smartwaste.config;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.Vehicle;
import com.example.smartwaste.entity.VehicleStatus;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import java.util.List;

import com.example.smartwaste.simulation.SimulationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(BinRepository binRepository, VehicleRepository vehicleRepository, SimulationService simulationService) {
        return args -> {
            boolean binsCreated = false;
            if (binRepository.count() == 0) {
                binsCreated = true;
            }

            if (vehicleRepository.count() == 0) {
                Vehicle v1 = new Vehicle();
                v1.setVehicleNumber("TRK-001");
                v1.setCapacity(1000.0);
                v1.setFuelEfficiency(4.5);
                v1.setStatus(VehicleStatus.AVAILABLE);
                vehicleRepository.save(v1);
            }
            
            // Generate data if this is a fresh database
            if (binsCreated) {
                simulationService.resetDemoData();
            }
        };
    }
}
