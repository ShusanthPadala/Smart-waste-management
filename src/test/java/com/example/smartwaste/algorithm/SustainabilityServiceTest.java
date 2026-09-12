package com.example.smartwaste.algorithm;

import com.example.smartwaste.dto.SustainabilityComparisonResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.config.SustainabilityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SustainabilityServiceTest {

    @Mock
    private BinRepository binRepository;

    @Mock
    private CollectionRouteRepository routeRepository;
    
    @Mock
    private RouteOptimizationService routeOptimizationService;
    
    @Mock
    private SustainabilityConfig config;

    @InjectMocks
    private SustainabilityService sustainabilityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFuelAndCo2Math() {
        when(config.getFuelEfficiency()).thenReturn(5.0);
        when(config.getCo2Factor()).thenReturn(2.68);
        when(routeOptimizationService.calculateHaversineDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5.0);
        
        Bin bin1 = new Bin(); bin1.setLatitude(40.0); bin1.setLongitude(-74.0);
        Bin bin2 = new Bin(); bin2.setLatitude(40.1); bin2.setLongitude(-74.1);
        
        when(binRepository.findAll()).thenReturn(Arrays.asList(bin1, bin2));
        
        CollectionRoute route = new CollectionRoute();
        route.setTotalDistanceKm(10.0);
        route.setEstimatedFuelLitres(2.0);
        route.setEstimatedCo2Kg(5.36);
        
        when(routeRepository.findTopByOrderByGeneratedAtDesc()).thenReturn(route);
        
        SustainabilityComparisonResult result = sustainabilityService.compareStrategies();
        
        // The TSP calculation for the baseline involves bin1 and bin2. 
        // We just need to check if the math (distance, fuel, co2) works.
        assertTrue(result.getBaseline().getTotalDistanceKm() > 0);
        assertTrue(result.getBaseline().getEstimatedFuelLitres() > 0);
        assertTrue(result.getBaseline().getEstimatedCo2Kg() > 0);
        
        assertEquals(10.0, result.getOptimized().getTotalDistanceKm());
        assertEquals(2.0, result.getOptimized().getEstimatedFuelLitres());
        assertEquals(5.36, result.getOptimized().getEstimatedCo2Kg());
        
        // Percentage improvement
        assertTrue(result.getDistanceSavedPct() > 0);
        assertTrue(result.getFuelSavedPct() > 0);
        assertTrue(result.getCo2SavedPct() > 0);
    }
}
