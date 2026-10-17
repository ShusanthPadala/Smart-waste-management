package com.example.smartwaste.service;

import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.algorithm.SustainabilityService;
import com.example.smartwaste.dto.DashboardSummary;
import com.example.smartwaste.dto.ZoneStat;
import com.example.smartwaste.entity.*;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BinRepository binRepository;
    private final VehicleRepository vehicleRepository;
    private final CollectionRouteRepository routeRepository;
    private final AlertService alertService;
    private final SustainabilityService sustainabilityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        List<Bin> bins = binRepository.findAll();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BinStatus s : BinStatus.values()) counts.put(s.name(), 0L);
        bins.forEach(b -> counts.merge(b.getStatus() != null ? b.getStatus().name() : BinStatus.NORMAL.name(), 1L, Long::sum));

        long needsPickup = bins.stream().filter(b -> RouteOptimizationService.NEEDS_PICKUP.contains(b.getStatus())).count();
        double avgFill = bins.stream().mapToDouble(Bin::getCurrentFillPercentage).average().orElse(0);
        double load = bins.stream().mapToDouble(Bin::getCurrentLoadLitres).sum();

        List<Vehicle> vehicles = vehicleRepository.findAll();
        int available = (int) vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.AVAILABLE).count();
        int onRoute = (int) vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.ON_ROUTE).count();
        int activeRoutes = routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.PLANNED, RouteStatus.IN_PROGRESS)).size();

        return new DashboardSummary(LocalDateTime.now(clock), bins.size(), counts, needsPickup, avgFill, load,
                available, onRoute, activeRoutes, alertService.openCount(), sustainabilityService.lifetimeImpact());
    }

    @Transactional(readOnly = true)
    public List<ZoneStat> zones() {
        Map<String, List<Bin>> byZone = binRepository.findAll().stream()
                .collect(Collectors.groupingBy(b -> b.getZone() == null ? "Unzoned" : b.getZone(), TreeMap::new, Collectors.toList()));
        List<ZoneStat> out = new ArrayList<>();
        byZone.forEach((zone, bins) -> out.add(new ZoneStat(zone, bins.size(),
                bins.stream().mapToDouble(Bin::getCurrentFillPercentage).average().orElse(0),
                bins.stream().filter(b -> RouteOptimizationService.NEEDS_PICKUP.contains(b.getStatus())).count())));
        out.sort(Comparator.comparingDouble(ZoneStat::averageFill).reversed());
        return out;
    }
}
