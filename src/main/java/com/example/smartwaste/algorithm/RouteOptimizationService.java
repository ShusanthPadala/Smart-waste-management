package com.example.smartwaste.algorithm;

import com.example.smartwaste.algorithm.RoutingEngine.Point;
import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.entity.*;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.RouteStopRepository;
import com.example.smartwaste.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Turns "which bins need emptying" into concrete truck routes.
 *
 * <ol>
 *   <li>Pick candidate bins (URGENT / OVERFLOW_RISK, optionally SOON) that are not already on a route.</li>
 *   <li>Keep the most critical ones that fit in the available fleet's combined payload.</li>
 *   <li>Split them between trucks with a capacity-aware sweep around the depot.</li>
 *   <li>Order each truck's stops with nearest-neighbour, then polish the tour with 2-opt.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    public static final Set<BinStatus> NEEDS_PICKUP = EnumSet.of(BinStatus.URGENT, BinStatus.OVERFLOW_RISK);

    private final BinRepository binRepository;
    private final CollectionRouteRepository routeRepository;
    private final SustainabilityConfig config;
    private final VehicleRepository vehicleRepository;
    private final RouteStopRepository routeStopRepository;
    private final Clock clock;

    public record PlanOutcome(int candidateBins, List<CollectionRoute> routes, List<String> unassignedBinCodes) {}

    public record TourPreview(double distanceKm, double fuelLitres, double co2Kg, int stops) {}

    /** Plans fleet routes, replacing any routes that were planned but not yet dispatched. */
    @Transactional
    public PlanOutcome planRoutes(boolean includeSoon) {
        List<CollectionRoute> stalePlans = routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.PLANNED));
        routeRepository.deleteAll(stalePlans);
        routeRepository.flush();

        Set<Long> alreadyOnRoute = new HashSet<>(routeStopRepository.findAssignedBinIds(List.of(RouteStatus.IN_PROGRESS)));
        Set<BinStatus> wanted = EnumSet.copyOf(NEEDS_PICKUP);
        if (includeSoon) wanted.add(BinStatus.SOON);

        List<Bin> candidates = binRepository.findAll().stream()
                .filter(b -> wanted.contains(b.getStatus()) && !alreadyOnRoute.contains(b.getId()))
                .sorted(Comparator.comparing((Bin b) -> b.getStatus().ordinal()).reversed()
                        .thenComparing(Comparator.comparingDouble(Bin::getCurrentFillPercentage).reversed()))
                .toList();

        List<Vehicle> vehicles = vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE);
        if (candidates.isEmpty()) return new PlanOutcome(0, List.of(), List.of());
        if (vehicles.isEmpty()) {
            return new PlanOutcome(candidates.size(), List.of(), candidates.stream().map(Bin::getBinCode).toList());
        }

        // Most critical bins first, until the fleet's combined payload is used up.
        double fleetCapacity = vehicles.stream().mapToDouble(Vehicle::getCapacity).sum();
        List<Bin> selected = new ArrayList<>();
        List<String> unassigned = new ArrayList<>();
        double used = 0;
        for (Bin b : candidates) {
            if (used + b.getCurrentLoadLitres() <= fleetCapacity) {
                selected.add(b);
                used += b.getCurrentLoadLitres();
            } else {
                unassigned.add(b.getBinCode());
            }
        }

        Point depot = depot();
        List<Point> points = selected.stream().map(b -> new Point(b.getLatitude(), b.getLongitude())).toList();
        List<Double> loads = selected.stream().map(Bin::getCurrentLoadLitres).toList();
        List<Double> capacities = vehicles.stream().map(Vehicle::getCapacity).toList();
        List<List<Integer>> clusters = RoutingEngine.sweepClusters(depot, points, loads, capacities);

        List<CollectionRoute> routes = new ArrayList<>();
        for (int v = 0; v < vehicles.size(); v++) {
            List<Bin> clusterBins = clusters.get(v).stream().map(selected::get).toList();
            if (!clusterBins.isEmpty()) routes.add(routeRepository.save(buildRoute(clusterBins, vehicles.get(v))));
        }
        clusters.get(clusters.size() - 1).forEach(i -> unassigned.add(selected.get(i).getBinCode()));

        log.info("Planned {} route(s) for {} candidate bin(s); {} unassigned", routes.size(), candidates.size(), unassigned.size());
        return new PlanOutcome(candidates.size(), routes, unassigned);
    }

    /** Distance, fuel and CO2 for an optimised depot round trip through {@code bins}. Nothing is saved. */
    public TourPreview previewTour(List<Bin> bins) {
        if (bins.isEmpty()) return new TourPreview(0, 0, 0, 0);
        Point depot = depot();
        List<Point> points = bins.stream().map(b -> new Point(b.getLatitude(), b.getLongitude())).toList();
        double km = RoutingEngine.tourLengthKm(depot, points, RoutingEngine.optimisedTour(depot, points));
        double fuel = km / config.getFuelEfficiency();
        return new TourPreview(km, fuel, fuel * config.getCo2Factor(), bins.size());
    }

    CollectionRoute buildRoute(List<Bin> bins, Vehicle vehicle) {
        Point depot = depot();
        List<Point> points = bins.stream().map(b -> new Point(b.getLatitude(), b.getLongitude())).toList();
        List<Integer> nnTour = RoutingEngine.nearestNeighbourTour(depot, points);
        List<Integer> tour = RoutingEngine.twoOpt(depot, points, nnTour);

        CollectionRoute route = new CollectionRoute();
        route.setGeneratedAt(LocalDateTime.now(clock));
        route.setStatus(RouteStatus.PLANNED);
        route.setVehicle(vehicle);
        route.setUnoptimizedDistanceKm(RoutingEngine.tourLengthKm(depot, points, nnTour));

        double speed = config.getAverageSpeedKmh();
        double minutes = 0;
        double km = 0;
        double load = 0;
        Point previous = depot;
        int sequence = 1;
        for (int idx : tour) {
            Bin bin = bins.get(idx);
            Point here = points.get(idx);
            double leg = RoutingEngine.distance(previous, here);
            km += leg;
            minutes += leg / speed * 60.0;

            RouteStop stop = new RouteStop();
            stop.setCollectionRoute(route);
            stop.setBin(bin);
            stop.setBinCode(bin.getBinCode());
            stop.setLocationName(bin.getLocationName());
            stop.setLatitude(bin.getLatitude());
            stop.setLongitude(bin.getLongitude());
            stop.setFillAtPlanning(bin.getCurrentFillPercentage());
            stop.setLoadLitres(bin.getCurrentLoadLitres());
            stop.setArrivalOffsetMinutes(minutes);
            stop.setSequenceOrder(sequence++);
            route.getStops().add(stop);

            minutes += config.getServiceMinutesPerStop();
            load += bin.getCurrentLoadLitres();
            previous = here;
        }
        double home = RoutingEngine.distance(previous, depot);
        km += home;
        minutes += home / speed * 60.0;

        double kmPerLitre = vehicle != null && vehicle.getFuelEfficiency() > 0 ? vehicle.getFuelEfficiency() : config.getFuelEfficiency();
        route.setTotalDistanceKm(km);
        route.setEstimatedTravelTimeMinutes(minutes);
        route.setEstimatedFuelLitres(km / kmPerLitre);
        route.setEstimatedCo2Kg(route.getEstimatedFuelLitres() * config.getCo2Factor());
        route.setTotalLoadLitres(load);
        return route;
    }

    public Point depot() {
        return new Point(config.getDepotLatitude(), config.getDepotLongitude());
    }

    /** Distance between two points in kilometres (Haversine). */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        return RoutingEngine.haversineKm(lat1, lon1, lat2, lon2);
    }
}
