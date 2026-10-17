package com.example.smartwaste.controller;

import com.example.smartwaste.dto.CollectionRouteDTO;
import com.example.smartwaste.dto.RoutePlanResult;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.service.RouteService;
import com.example.smartwaste.simulation.WorldLock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Plan, dispatch and track collection routes")
public class RouteController {
    private final RouteService routeService;
    private final CollectionRouteRepository routeRepository;
    private final WorldLock worldLock;

    @PostMapping("/plan")
    @Operation(summary = "Plan fleet routes for bins that need pickup",
            description = "Replaces routes that are planned but not yet dispatched. includeSoon also schedules SOON bins.")
    public RoutePlanResult plan(@RequestParam(defaultValue = "false") boolean includeSoon) {
        return worldLock.call(() -> routeService.plan(includeSoon));
    }

    /** Original endpoint name, now backed by fleet planning. */
    @PostMapping("/optimize")
    public RoutePlanResult optimizeRoute() {
        return plan(false);
    }

    @GetMapping
    @Operation(summary = "The 30 most recent routes in any state")
    public List<CollectionRouteDTO> recent() {
        return routeService.recent();
    }

    @GetMapping("/active")
    @Operation(summary = "Routes that are planned or in progress")
    public List<CollectionRouteDTO> active() {
        return routeService.active();
    }

    @GetMapping("/latest")
    @Transactional(readOnly = true)
    public ResponseEntity<CollectionRouteDTO> getLatestRoute() {
        var route = routeRepository.findTopByOrderByGeneratedAtDesc();
        return route != null ? ResponseEntity.ok(routeService.toDto(route)) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}")
    public CollectionRouteDTO get(@PathVariable Long id) {
        return routeService.get(id);
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Send the assigned truck out; bins are emptied as it reaches them")
    public CollectionRouteDTO dispatch(@PathVariable Long id) {
        return worldLock.call(() -> routeService.dispatch(id));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Finish the route now, emptying any remaining stops")
    public CollectionRouteDTO complete(@PathVariable Long id) {
        return worldLock.call(() -> routeService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public CollectionRouteDTO cancel(@PathVariable Long id) {
        return worldLock.call(() -> routeService.cancel(id));
    }
}
