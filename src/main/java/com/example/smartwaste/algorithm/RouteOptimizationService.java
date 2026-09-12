package com.example.smartwaste.algorithm;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.entity.RouteStop;
import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private final BinRepository binRepository;
    private final CollectionRouteRepository routeRepository;
    private final SustainabilityConfig sustainabilityConfig;

    @Value("${app.depot.latitude:40.7120}")
    private double depotLat;

    @Value("${app.depot.longitude:-74.0040}")
    private double depotLon;

    @Transactional
    public CollectionRoute optimizeRoute() {
        log.info("Generating optimal route for urgent bins...");
        
        List<Bin> urgentBins = binRepository.findAll().stream()
                .filter(b -> b.getStatus() == BinStatus.URGENT || b.getStatus() == BinStatus.OVERFLOW_RISK)
                .toList();
                
        if (urgentBins.isEmpty()) {
            log.info("No urgent bins found. No route generated.");
            return null;
        }
        
        List<Bin> unvisited = new ArrayList<>(urgentBins);
        CollectionRoute route = new CollectionRoute();
        route.setGeneratedAt(LocalDateTime.now());
        
        double currentLat = depotLat;
        double currentLon = depotLon;
        double totalDistance = 0.0;
        int sequence = 1;
        
        while (!unvisited.isEmpty()) {
            Bin nearest = null;
            double minDistance = Double.MAX_VALUE;
            
            for (Bin bin : unvisited) {
                double dist = calculateHaversineDistance(currentLat, currentLon, bin.getLatitude(), bin.getLongitude());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = bin;
                }
            }
            
            unvisited.remove(nearest);
            totalDistance += minDistance;
            currentLat = nearest.getLatitude();
            currentLon = nearest.getLongitude();
            
            RouteStop stop = new RouteStop();
            stop.setCollectionRoute(route);
            stop.setBin(nearest);
            stop.setBinCode(nearest.getBinCode());
            stop.setSequenceOrder(sequence++);
            route.getStops().add(stop);
        }
        
        // Return to depot
        totalDistance += calculateHaversineDistance(currentLat, currentLon, depotLat, depotLon);
        
        route.setTotalDistanceKm(totalDistance);
        
        // Assuming average speed of 30 km/h in city
        route.setEstimatedTravelTimeMinutes((totalDistance / 30.0) * 60.0);
        route.setEstimatedFuelLitres(totalDistance / sustainabilityConfig.getFuelEfficiency());
        route.setEstimatedCo2Kg(route.getEstimatedFuelLitres() * sustainabilityConfig.getCo2Factor());
        
        return routeRepository.save(route);
    }
    
    /**
     * Calculates distance between two points in latitude and longitude using Haversine formula.
     * @return distance in kilometers
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
