package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.repository.CollectionRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.smartwaste.dto.CollectionRouteDTO;
import com.example.smartwaste.dto.RouteStopDTO;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {
    private final RouteOptimizationService routeOptimizationService;
    private final CollectionRouteRepository routeRepository;

    @PostMapping("/optimize")
    public CollectionRouteDTO optimizeRoute() {
        return mapToDTO(routeOptimizationService.optimizeRoute());
    }

    @GetMapping("/latest")
    public ResponseEntity<CollectionRouteDTO> getLatestRoute() {
        CollectionRoute route = routeRepository.findTopByOrderByGeneratedAtDesc();
        if (route != null) return ResponseEntity.ok(mapToDTO(route));
        return ResponseEntity.notFound().build();
    }
    
    private CollectionRouteDTO mapToDTO(CollectionRoute route) {
        if (route == null) return null;
        CollectionRouteDTO dto = new CollectionRouteDTO();
        dto.setId(route.getId());
        dto.setGeneratedAt(route.getGeneratedAt());
        dto.setTotalDistanceKm(route.getTotalDistanceKm());
        dto.setEstimatedTravelTimeMinutes(route.getEstimatedTravelTimeMinutes());
        dto.setEstimatedFuelLitres(route.getEstimatedFuelLitres());
        dto.setEstimatedCo2Kg(route.getEstimatedCo2Kg());
        
        if (route.getStops() != null) {
            List<RouteStopDTO> stopDTOs = route.getStops().stream().map(stop -> {
                RouteStopDTO stopDTO = new RouteStopDTO();
                stopDTO.setId(stop.getId());
                stopDTO.setSequenceOrder(stop.getSequenceOrder());
                if (stop.getBin() != null) {
                    stopDTO.setBinCode(stop.getBin().getBinCode());
                    stopDTO.setLocationName(stop.getBin().getLocationName());
                }
                return stopDTO;
            }).collect(Collectors.toList());
            dto.setStops(stopDTOs);
        }
        return dto;
    }
}
