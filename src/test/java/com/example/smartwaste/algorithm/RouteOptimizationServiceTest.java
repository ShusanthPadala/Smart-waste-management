package com.example.smartwaste.algorithm;

import com.example.smartwaste.config.DistrictSettings;
import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.entity.*;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.RouteStopRepository;
import com.example.smartwaste.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteOptimizationServiceTest {

    private BinRepository binRepository;
    private CollectionRouteRepository routeRepository;
    private VehicleRepository vehicleRepository;
    private RouteStopRepository routeStopRepository;
    private RouteOptimizationService service;

    @BeforeEach
    void setUp() {
        binRepository = mock(BinRepository.class);
        routeRepository = mock(CollectionRouteRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        routeStopRepository = mock(RouteStopRepository.class);
        service = new RouteOptimizationService(binRepository, routeRepository, new SustainabilityConfig(),
                vehicleRepository, routeStopRepository, Clock.systemDefaultZone(), new DistrictSettings());
        when(routeRepository.save(any(CollectionRoute.class))).thenAnswer(i -> i.getArgument(0));
        when(routeRepository.findByStatusInOrderByGeneratedAtDesc(any())).thenReturn(List.of());
        when(routeStopRepository.findAssignedBinIds(any())).thenReturn(List.of());
    }

    private static Bin bin(long id, BinStatus status, double fill, double capacity, double lat, double lon) {
        Bin b = new Bin();
        b.setId(id);
        b.setBinCode("B" + id);
        b.setStatus(status);
        b.setCurrentFillPercentage(fill);
        b.setCapacity(capacity);
        b.setLatitude(lat);
        b.setLongitude(lon);
        return b;
    }

    private static Vehicle truck(long id, double capacity) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setVehicleNumber("TRK-" + id);
        v.setCapacity(capacity);
        v.setFuelEfficiency(5.0);
        v.setStatus(VehicleStatus.AVAILABLE);
        return v;
    }

    @Test
    void noCandidatesMeansNoRoutes() {
        when(binRepository.findAll()).thenReturn(List.of(bin(1, BinStatus.NORMAL, 20, 1000, 17.75, 83.36)));
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of(truck(1, 5000)));

        RouteOptimizationService.PlanOutcome outcome = service.planRoutes(false);

        assertEquals(0, outcome.candidateBins());
        assertTrue(outcome.routes().isEmpty());
    }

    @Test
    void noVehiclesLeavesEveryCandidateUnassigned() {
        when(binRepository.findAll()).thenReturn(List.of(bin(1, BinStatus.URGENT, 95, 1000, 17.75, 83.36)));
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of());

        RouteOptimizationService.PlanOutcome outcome = service.planRoutes(false);

        assertTrue(outcome.routes().isEmpty());
        assertEquals(List.of("B1"), outcome.unassignedBinCodes());
    }

    @Test
    void routesOnlyBinsThatNeedPickupAndComputesMetrics() {
        when(binRepository.findAll()).thenReturn(List.of(
                bin(1, BinStatus.URGENT, 95, 1000, 17.75, 83.36),
                bin(2, BinStatus.OVERFLOW_RISK, 100, 1000, 17.76, 83.37),
                bin(3, BinStatus.NORMAL, 30, 1000, 17.73, 83.34),
                bin(4, BinStatus.SOON, 70, 1000, 17.74, 83.38)));
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of(truck(1, 5000)));

        RouteOptimizationService.PlanOutcome outcome = service.planRoutes(false);

        assertEquals(1, outcome.routes().size());
        CollectionRoute route = outcome.routes().get(0);
        Set<String> codes = new HashSet<>();
        route.getStops().forEach(s -> codes.add(s.getBinCode()));
        assertEquals(Set.of("B1", "B2"), codes);
        assertEquals(1950, route.getTotalLoadLitres(), 1e-6);
        assertTrue(route.getTotalDistanceKm() > 0);
        assertEquals(route.getTotalDistanceKm() / 5.0, route.getEstimatedFuelLitres(), 1e-9);
        assertEquals(route.getEstimatedFuelLitres() * 2.68, route.getEstimatedCo2Kg(), 1e-9);
        assertTrue(route.getTotalDistanceKm() <= route.getUnoptimizedDistanceKm() + 1e-9);

        // Arrival times increase along the route.
        double previous = -1;
        for (RouteStop s : route.getStops()) {
            assertTrue(s.getArrivalOffsetMinutes() > previous);
            previous = s.getArrivalOffsetMinutes();
        }
    }

    @Test
    void includeSoonAddsSoonBins() {
        when(binRepository.findAll()).thenReturn(List.of(
                bin(1, BinStatus.URGENT, 95, 1000, 17.75, 83.36),
                bin(4, BinStatus.SOON, 70, 1000, 17.74, 83.38)));
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of(truck(1, 5000)));

        assertEquals(2, service.planRoutes(true).routes().get(0).getStops().size());
    }

    @Test
    void splitsAcrossTrucksAndDropsLowestPriorityWhenFleetIsFull() {
        List<Bin> bins = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            bins.add(bin(i, i == 6 ? BinStatus.URGENT : BinStatus.OVERFLOW_RISK, 100, 1000,
                    17.74 + 0.01 * Math.cos(i), 83.35 + 0.01 * Math.sin(i)));
        }
        when(binRepository.findAll()).thenReturn(bins);
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of(truck(1, 2500), truck(2, 2500)));

        RouteOptimizationService.PlanOutcome outcome = service.planRoutes(false);

        assertEquals(2, outcome.routes().size());
        outcome.routes().forEach(r -> assertTrue(r.getTotalLoadLitres() <= 2500));
        int routed = outcome.routes().stream().mapToInt(r -> r.getStops().size()).sum();
        assertEquals(6, routed + outcome.unassignedBinCodes().size());
        assertTrue(outcome.unassignedBinCodes().contains("B6"), "URGENT bin loses to OVERFLOW_RISK bins when space runs out");
    }

    @Test
    void binsAlreadyOnAnActiveRouteAreSkipped() {
        when(binRepository.findAll()).thenReturn(List.of(bin(1, BinStatus.URGENT, 95, 1000, 17.75, 83.36)));
        when(routeStopRepository.findAssignedBinIds(any())).thenReturn(List.of(1L));
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE)).thenReturn(List.of(truck(1, 5000)));

        assertEquals(0, service.planRoutes(false).candidateBins());
    }

    @Test
    void previewTourOfNothingIsZero() {
        RouteOptimizationService.TourPreview p = service.previewTour(List.of());
        assertEquals(0, p.distanceKm());
        assertEquals(0, p.co2Kg());
        verify(routeRepository, never()).save(any());
    }
}
