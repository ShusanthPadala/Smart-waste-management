package com.example.smartwaste.algorithm;

import com.example.smartwaste.config.DistrictSettings;
import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.dto.SustainabilityComparisonResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.RouteStopRepository;
import com.example.smartwaste.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SustainabilityServiceTest {

    private BinRepository binRepository;
    private CollectionRouteRepository routeRepository;
    private SustainabilityService service;
    private final SustainabilityConfig config = new SustainabilityConfig();

    @BeforeEach
    void setUp() {
        binRepository = mock(BinRepository.class);
        routeRepository = mock(CollectionRouteRepository.class);
        RouteOptimizationService optimizer = new RouteOptimizationService(binRepository, routeRepository, config,
                mock(VehicleRepository.class), mock(RouteStopRepository.class), Clock.systemDefaultZone(), new DistrictSettings());
        service = new SustainabilityService(binRepository, routeRepository, optimizer, config, Clock.systemDefaultZone());
    }

    private static Bin bin(BinStatus status, double fill, double lat, double lon) {
        Bin b = new Bin();
        b.setStatus(status);
        b.setCurrentFillPercentage(fill);
        b.setLatitude(lat);
        b.setLongitude(lon);
        return b;
    }

    @Test
    void optimisedRouteBeatsFixedSchedule() {
        when(binRepository.findAll()).thenReturn(List.of(
                bin(BinStatus.URGENT, 95, 17.75, 83.36),
                bin(BinStatus.NORMAL, 20, 17.77, 83.33),
                bin(BinStatus.NORMAL, 35, 17.72, 83.39),
                bin(BinStatus.SOON, 70, 17.71, 83.34)));

        SustainabilityComparisonResult r = service.compareStrategies();

        assertEquals(4, r.getBaseline().getBinsCollected());
        assertEquals(1, r.getOptimized().getBinsCollected());
        assertEquals(3, r.getBinsSkipped());
        assertTrue(r.getBaseline().getTotalDistanceKm() > r.getOptimized().getTotalDistanceKm());
        assertEquals(r.getBaseline().getTotalDistanceKm() / config.getFuelEfficiency(), r.getBaseline().getEstimatedFuelLitres(), 1e-9);
        assertEquals(r.getBaseline().getEstimatedFuelLitres() * config.getCo2Factor(), r.getBaseline().getEstimatedCo2Kg(), 1e-9);
        assertTrue(r.getCo2SavedPct() > 0 && r.getCo2SavedPct() < 100);
        assertEquals(95, r.getOptimized().getAverageFillAtPickup(), 1e-9);
        assertEquals(55, r.getBaseline().getAverageFillAtPickup(), 1e-9);
        assertEquals(r.getAnnualCo2SavedKg() / config.getCo2PerTreeKgPerYear(), r.getTreesEquivalent(), 1e-9);
        verify(routeRepository, never()).save(any());
    }

    @Test
    void nothingToCollectSavesEverything() {
        when(binRepository.findAll()).thenReturn(List.of(bin(BinStatus.NORMAL, 20, 17.75, 83.36)));

        SustainabilityComparisonResult r = service.compareStrategies();

        assertEquals(0, r.getOptimized().getTotalDistanceKm());
        assertEquals(100.0, r.getCo2SavedPct(), 1e-9);
        assertEquals(0, r.getAnnualCo2SavedKg(), "an empty cycle is not annualised");
    }

    @Test
    void noBinsMeansNoSavings() {
        when(binRepository.findAll()).thenReturn(List.of());
        SustainabilityComparisonResult r = service.compareStrategies();
        assertEquals(0, r.getCo2SavedPct());
        assertEquals(0, r.getAnnualCo2SavedKg());
    }
}
