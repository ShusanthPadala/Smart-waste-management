package com.example.smartwaste.algorithm;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.smartwaste.config.SustainabilityConfig;
import org.springframework.test.util.ReflectionTestUtils;

public class RouteOptimizationServiceTest {

    @Mock
    private BinRepository binRepository;

    @Mock
    private CollectionRouteRepository routeRepository;

    @Mock
    private SustainabilityConfig config;

    @InjectMocks
    private RouteOptimizationService routeOptimizationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(routeRepository.save(any(CollectionRoute.class))).thenAnswer(i -> i.getArguments()[0]);
        when(config.getFuelEfficiency()).thenReturn(5.0);
        when(config.getCo2Factor()).thenReturn(2.68);
    }

    @Test
    void testZeroBins() {
        when(binRepository.findAll()).thenReturn(Collections.emptyList());

        CollectionRoute route = routeOptimizationService.optimizeRoute();
        
        assertNull(route);
    }

    @Test
    void testOneBin() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setLatitude(40.7128); // NYC
        bin.setLongitude(-74.0060);
        bin.setStatus(BinStatus.URGENT);

        when(binRepository.findAll()).thenReturn(List.of(bin));

        CollectionRoute route = routeOptimizationService.optimizeRoute();
        
        assertEquals(1, route.getStops().size());
        // Depot is at 40.7128, -74.0060 as well (mocked in the service), so distance might be 0.
        // Even if depot is different, it should have a distance.
        assertTrue(route.getTotalDistanceKm() >= 0);
        assertNotNull(route.getEstimatedCo2Kg());
    }

    @Test
    void testMultipleBinsDistanceCalc() {
        Bin bin1 = new Bin();
        bin1.setId(1L);
        bin1.setLatitude(40.7128); 
        bin1.setLongitude(-74.0060);
        bin1.setStatus(BinStatus.URGENT);

        Bin bin2 = new Bin();
        bin2.setId(2L);
        bin2.setLatitude(34.0522); // LA
        bin2.setLongitude(-118.2437);
        bin2.setStatus(BinStatus.URGENT);

        when(binRepository.findAll()).thenReturn(Arrays.asList(bin1, bin2));

        CollectionRoute route = routeOptimizationService.optimizeRoute();
        
        assertEquals(2, route.getStops().size());
        assertTrue(route.getTotalDistanceKm() > 1000); // Distance to LA is > 1000km
        assertTrue(route.getEstimatedFuelLitres() > 0);
        assertTrue(route.getEstimatedCo2Kg() > 0);
    }
}
