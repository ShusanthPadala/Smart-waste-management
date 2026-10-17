package com.example.smartwaste.service;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.algorithm.RoutingEngine;
import com.example.smartwaste.algorithm.RoutingEngine.Point;
import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.dto.CollectionRouteDTO;
import com.example.smartwaste.dto.RoutePlanResult;
import com.example.smartwaste.dto.RouteStopDTO;
import com.example.smartwaste.dto.VehicleView;
import com.example.smartwaste.entity.*;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import com.example.smartwaste.repository.VehicleRepository;
import com.example.smartwaste.web.ConflictException;
import com.example.smartwaste.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** Route lifecycle: plan -> dispatch -> (truck drives, bins get emptied) -> complete. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteService {

    private static final List<RouteStatus> ACTIVE = List.of(RouteStatus.PLANNED, RouteStatus.IN_PROGRESS);

    private final RouteOptimizationService optimizer;
    private final CollectionRouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final BinRepository binRepository;
    private final SensorReadingRepository readingRepository;
    private final PredictionService predictionService;
    private final AlertService alertService;
    private final SustainabilityConfig config;
    private final Clock clock;
    private final Random residualNoise = new Random();

    @Transactional
    public RoutePlanResult plan(boolean includeSoon) {
        RouteOptimizationService.PlanOutcome outcome = optimizer.planRoutes(includeSoon);
        List<CollectionRouteDTO> routes = outcome.routes().stream().map(this::toDto).toList();

        String message;
        if (outcome.candidateBins() == 0) {
            message = "No bins need collection right now.";
        } else if (routes.isEmpty()) {
            message = "No trucks are available - " + outcome.candidateBins() + " bin(s) are waiting.";
        } else {
            int stops = routes.stream().mapToInt(r -> r.getStops().size()).sum();
            message = String.format("Planned %d route(s) covering %d bin(s).", routes.size(), stops);
        }
        if (!outcome.unassignedBinCodes().isEmpty()) {
            alertService.raise(AlertType.UNASSIGNED_BINS, AlertSeverity.WARNING,
                    "Fleet capacity exceeded: " + String.join(", ", outcome.unassignedBinCodes()) + " could not be scheduled.");
        }
        return new RoutePlanResult(outcome.candidateBins(), routes, outcome.unassignedBinCodes(), message);
    }

    @Transactional(readOnly = true)
    public List<CollectionRouteDTO> active() {
        return routeRepository.findByStatusInOrderByGeneratedAtDesc(ACTIVE).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionRouteDTO> recent() {
        return routeRepository.findTop30ByOrderByGeneratedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CollectionRouteDTO get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public CollectionRouteDTO dispatch(Long id) {
        CollectionRoute route = find(id);
        if (route.getStatus() != RouteStatus.PLANNED) {
            throw new ConflictException("Route " + id + " is " + route.getStatus() + " and cannot be dispatched");
        }
        Vehicle vehicle = route.getVehicle();
        if (vehicle == null) throw new ConflictException("Route " + id + " has no vehicle assigned");
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException(vehicle.getVehicleNumber() + " is " + vehicle.getStatus());
        }
        route.setStatus(RouteStatus.IN_PROGRESS);
        route.setDispatchedAt(LocalDateTime.now(clock));
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        alertService.raise(AlertType.ROUTE_DISPATCHED, AlertSeverity.INFO, String.format(
                "%s dispatched on route #%d: %d stops, %.1f km, ~%.0f min.",
                vehicle.getVehicleNumber(), route.getId(), route.getStops().size(),
                route.getTotalDistanceKm(), route.getEstimatedTravelTimeMinutes()));
        return toDto(route);
    }

    /** Finishes a route immediately, emptying any stops the truck has not reached yet. */
    @Transactional
    public CollectionRouteDTO complete(Long id) {
        CollectionRoute route = find(id);
        if (route.getStatus() != RouteStatus.IN_PROGRESS && route.getStatus() != RouteStatus.PLANNED) {
            throw new ConflictException("Route " + id + " is already " + route.getStatus());
        }
        if (route.getDispatchedAt() == null) route.setDispatchedAt(LocalDateTime.now(clock));
        finish(route, LocalDateTime.now(clock));
        return toDto(route);
    }

    @Transactional
    public CollectionRouteDTO cancel(Long id) {
        CollectionRoute route = find(id);
        if (route.getStatus() == RouteStatus.COMPLETED || route.getStatus() == RouteStatus.CANCELLED) {
            throw new ConflictException("Route " + id + " is already " + route.getStatus());
        }
        route.setStatus(RouteStatus.CANCELLED);
        route.setCompletedAt(LocalDateTime.now(clock));
        parkAtDepot(route.getVehicle());
        return toDto(route);
    }

    /**
     * Moves every in-progress route forward to {@code now}: trucks advance along their legs,
     * reached bins are emptied, and finished routes are closed.
     *
     * @return true when anything changed
     */
    @Transactional
    public boolean advance(LocalDateTime now) {
        boolean changed = false;
        for (CollectionRoute route : routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.IN_PROGRESS))) {
            double elapsed = Duration.between(route.getDispatchedAt(), now).toSeconds() / 60.0;
            for (RouteStop stop : route.getStops()) {
                if (!stop.isCollected() && stop.getArrivalOffsetMinutes() <= elapsed) {
                    collectStop(stop, route.getDispatchedAt().plusSeconds((long) (stop.getArrivalOffsetMinutes() * 60)));
                    changed = true;
                }
            }
            if (elapsed >= route.getEstimatedTravelTimeMinutes()) {
                finish(route, route.getDispatchedAt().plusSeconds((long) (route.getEstimatedTravelTimeMinutes() * 60)));
                changed = true;
            } else if (route.getVehicle() != null) {
                Point p = positionAt(route, elapsed);
                route.getVehicle().setCurrentLatitude(p.lat());
                route.getVehicle().setCurrentLongitude(p.lon());
                changed = true;
            }
        }
        return changed;
    }

    /** Dispatches every planned route whose truck is free. Used by autopilot. */
    @Transactional
    public int dispatchAllPlanned() {
        int count = 0;
        for (CollectionRoute r : routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.PLANNED))) {
            if (r.getVehicle() != null && r.getVehicle().getStatus() == VehicleStatus.AVAILABLE) {
                dispatch(r.getId());
                count++;
            }
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<VehicleView> fleet() {
        Map<Long, Long> activeByVehicle = new HashMap<>();
        for (CollectionRoute r : routeRepository.findByStatusInOrderByGeneratedAtDesc(ACTIVE)) {
            if (r.getVehicle() != null) activeByVehicle.putIfAbsent(r.getVehicle().getId(), r.getId());
        }
        Point depot = optimizer.depot();
        return vehicleRepository.findAll().stream()
                .sorted(Comparator.comparing(Vehicle::getId))
                .map(v -> new VehicleView(v.getId(), v.getVehicleNumber(), v.getDriverName(), v.getCapacity(),
                        v.getFuelEfficiency(), v.getStatus(),
                        v.getCurrentLatitude() != null ? v.getCurrentLatitude() : depot.lat(),
                        v.getCurrentLongitude() != null ? v.getCurrentLongitude() : depot.lon(),
                        activeByVehicle.get(v.getId())))
                .toList();
    }

    /** Empties a bin: residual fill of 0-3%, a fresh sensor reading, and its fill alerts resolved. */
    @Transactional
    public void collectBin(Bin bin, LocalDateTime at) {
        bin.setCurrentFillPercentage(Math.round(residualNoise.nextDouble() * 30) / 10.0);
        bin.setLastCollectedAt(at);
        bin.setLastUpdated(at);
        bin.setStatus(BinStatus.NORMAL);
        binRepository.save(bin);

        SensorReading reading = new SensorReading();
        reading.setBin(bin);
        reading.setTimestamp(at);
        reading.setFillPercentage(bin.getCurrentFillPercentage());
        readingRepository.save(reading);

        alertService.resolveBinAlerts(bin.getId());
        predictionService.evict(bin.getId());
    }

    private void collectStop(RouteStop stop, LocalDateTime at) {
        stop.setCollected(true);
        stop.setCollectedAt(at);
        if (stop.getBin() != null) collectBin(stop.getBin(), at);
    }

    private void finish(CollectionRoute route, LocalDateTime at) {
        for (RouteStop stop : route.getStops()) {
            if (!stop.isCollected()) collectStop(stop, at);
        }
        route.setStatus(RouteStatus.COMPLETED);
        route.setCompletedAt(at);
        parkAtDepot(route.getVehicle());
        String truck = route.getVehicle() != null ? route.getVehicle().getVehicleNumber() : "Route";
        alertService.raise(AlertType.ROUTE_COMPLETED, AlertSeverity.INFO, String.format(
                "%s completed route #%d: %d bins emptied, %.0f L collected, %.1f km driven.",
                truck, route.getId(), route.getStops().size(), route.getTotalLoadLitres(), route.getTotalDistanceKm()));
    }

    private void parkAtDepot(Vehicle vehicle) {
        if (vehicle == null) return;
        Point depot = optimizer.depot();
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setCurrentLatitude(depot.lat());
        vehicle.setCurrentLongitude(depot.lon());
    }

    /** Where the truck is {@code elapsed} minutes after dispatch: driving a leg, or parked at a stop. */
    private Point positionAt(CollectionRoute route, double elapsed) {
        Point depot = optimizer.depot();
        double speed = config.getAverageSpeedKmh();
        Point from = depot;
        double departAt = 0;
        List<RouteStop> stops = route.getStops();
        for (int i = 0; i <= stops.size(); i++) {
            Point to = i < stops.size() ? new Point(stops.get(i).getLatitude(), stops.get(i).getLongitude()) : depot;
            double arriveAt = i < stops.size()
                    ? stops.get(i).getArrivalOffsetMinutes()
                    : departAt + RoutingEngine.distance(from, depot) / speed * 60.0;
            if (elapsed < arriveAt) {
                double t = arriveAt > departAt ? (elapsed - departAt) / (arriveAt - departAt) : 1;
                t = Math.max(0, Math.min(1, t));
                return new Point(from.lat() + (to.lat() - from.lat()) * t, from.lon() + (to.lon() - from.lon()) * t);
            }
            departAt = arriveAt + config.getServiceMinutesPerStop();
            if (elapsed < departAt) return to; // servicing this stop
            from = to;
        }
        return depot;
    }

    private CollectionRoute find(Long id) {
        return routeRepository.findById(id).orElseThrow(() -> new NotFoundException("Route " + id + " not found"));
    }

    public CollectionRouteDTO toDto(CollectionRoute route) {
        if (route == null) return null;
        CollectionRouteDTO dto = new CollectionRouteDTO();
        dto.setId(route.getId());
        dto.setStatus(route.getStatus());
        if (route.getVehicle() != null) {
            dto.setVehicleId(route.getVehicle().getId());
            dto.setVehicleNumber(route.getVehicle().getVehicleNumber());
            dto.setVehicleCapacityLitres(route.getVehicle().getCapacity());
        }
        dto.setGeneratedAt(route.getGeneratedAt());
        dto.setDispatchedAt(route.getDispatchedAt());
        dto.setCompletedAt(route.getCompletedAt());
        dto.setTotalDistanceKm(route.getTotalDistanceKm());
        dto.setUnoptimizedDistanceKm(route.getUnoptimizedDistanceKm());
        dto.setEstimatedTravelTimeMinutes(route.getEstimatedTravelTimeMinutes());
        dto.setEstimatedFuelLitres(route.getEstimatedFuelLitres());
        dto.setEstimatedCo2Kg(route.getEstimatedCo2Kg());
        dto.setTotalLoadLitres(route.getTotalLoadLitres());
        dto.setProgress(progress(route));

        List<RouteStopDTO> stops = new ArrayList<>();
        for (RouteStop stop : route.getStops()) {
            RouteStopDTO s = new RouteStopDTO();
            s.setId(stop.getId());
            s.setBinId(stop.getBin() != null ? stop.getBin().getId() : null);
            s.setSequenceOrder(stop.getSequenceOrder());
            s.setBinCode(stop.getBinCode());
            s.setLocationName(stop.getLocationName());
            s.setLatitude(stop.getLatitude());
            s.setLongitude(stop.getLongitude());
            s.setFillAtPlanning(stop.getFillAtPlanning());
            s.setLoadLitres(stop.getLoadLitres());
            s.setArrivalOffsetMinutes(stop.getArrivalOffsetMinutes());
            s.setCollected(stop.isCollected());
            s.setCollectedAt(stop.getCollectedAt());
            stops.add(s);
        }
        dto.setStops(stops);
        return dto;
    }

    private double progress(CollectionRoute route) {
        if (route.getStatus() == RouteStatus.COMPLETED) return 1.0;
        if (route.getStatus() != RouteStatus.IN_PROGRESS || route.getDispatchedAt() == null
                || route.getEstimatedTravelTimeMinutes() <= 0) return 0.0;
        double elapsed = Duration.between(route.getDispatchedAt(), LocalDateTime.now(clock)).toSeconds() / 60.0;
        return Math.max(0, Math.min(1, elapsed / route.getEstimatedTravelTimeMinutes()));
    }
}
