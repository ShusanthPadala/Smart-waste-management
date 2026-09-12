package com.example.smartwaste.algorithm;

import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.dto.SustainabilityComparisonResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SustainabilityService {

    private final BinRepository binRepository;
    private final CollectionRouteRepository routeRepository;
    private final RouteOptimizationService routeOptimizationService;
    private final SustainabilityConfig config;

    @Value("${app.depot.latitude:40.7120}")
    private double depotLat;

    @Value("${app.depot.longitude:-74.0040}")
    private double depotLon;

    public SustainabilityComparisonResult compareStrategies() {
        SustainabilityComparisonResult result = new SustainabilityComparisonResult();
        
        List<Bin> allBins = binRepository.findAll();
        
        // Calculate Baseline (visiting all bins)
        SustainabilityComparisonResult.Metrics baseline = calculateBaselineMetrics(allBins);
        result.setBaseline(baseline);
        
        // Calculate Optimized (latest generated route)
        CollectionRoute latestRoute = routeRepository.findTopByOrderByGeneratedAtDesc();
        SustainabilityComparisonResult.Metrics optimized = new SustainabilityComparisonResult.Metrics();
        
        if (latestRoute != null) {
            optimized.setTotalDistanceKm(latestRoute.getTotalDistanceKm());
            optimized.setEstimatedFuelLitres(latestRoute.getEstimatedFuelLitres());
            optimized.setEstimatedCo2Kg(latestRoute.getEstimatedCo2Kg());
            optimized.setBinsCollected(latestRoute.getStops().size());
            optimized.setTotalTrips(1);
        } else {
            optimized.setTotalDistanceKm(0);
            optimized.setEstimatedFuelLitres(0);
            optimized.setEstimatedCo2Kg(0);
            optimized.setBinsCollected(0);
            optimized.setTotalTrips(0);
        }
        result.setOptimized(optimized);
        
        // Calculate Improvements
        if (baseline.getTotalDistanceKm() > 0) {
            result.setDistanceSavedPct(((baseline.getTotalDistanceKm() - optimized.getTotalDistanceKm()) / baseline.getTotalDistanceKm()) * 100);
            result.setFuelSavedPct(((baseline.getEstimatedFuelLitres() - optimized.getEstimatedFuelLitres()) / baseline.getEstimatedFuelLitres()) * 100);
            result.setCo2SavedPct(((baseline.getEstimatedCo2Kg() - optimized.getEstimatedCo2Kg()) / baseline.getEstimatedCo2Kg()) * 100);
        }
        
        return result;
    }
    
    private SustainabilityComparisonResult.Metrics calculateBaselineMetrics(List<Bin> allBins) {
        SustainabilityComparisonResult.Metrics metrics = new SustainabilityComparisonResult.Metrics();
        if (allBins.isEmpty()) return metrics;
        
        List<Bin> unvisited = new ArrayList<>(allBins);
        double currentLat = depotLat;
        double currentLon = depotLon;
        double totalDistance = 0.0;
        
        while (!unvisited.isEmpty()) {
            Bin nearest = null;
            double minDistance = Double.MAX_VALUE;
            for (Bin bin : unvisited) {
                double dist = routeOptimizationService.calculateHaversineDistance(currentLat, currentLon, bin.getLatitude(), bin.getLongitude());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = bin;
                }
            }
            unvisited.remove(nearest);
            totalDistance += minDistance;
            currentLat = nearest.getLatitude();
            currentLon = nearest.getLongitude();
        }
        totalDistance += routeOptimizationService.calculateHaversineDistance(currentLat, currentLon, depotLat, depotLon);
        
        metrics.setTotalDistanceKm(totalDistance);
        metrics.setEstimatedFuelLitres(totalDistance / config.getFuelEfficiency());
        metrics.setEstimatedCo2Kg(metrics.getEstimatedFuelLitres() * config.getCo2Factor());
        metrics.setBinsCollected(allBins.size());
        metrics.setTotalTrips(1);
        
        return metrics;
    }
}
