package com.example.smartwaste.controller;

import com.example.smartwaste.dto.DashboardSummary;
import com.example.smartwaste.dto.VehicleView;
import com.example.smartwaste.entity.Alert;
import com.example.smartwaste.service.AlertService;
import com.example.smartwaste.service.DashboardService;
import com.example.smartwaste.service.LiveUpdateService;
import com.example.smartwaste.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** Dashboard summary, fleet, alerts and the live event stream. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Operations")
public class OperationsController {
    private final DashboardService dashboardService;
    private final RouteService routeService;
    private final AlertService alertService;
    private final LiveUpdateService liveUpdateService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Headline numbers for the dashboard")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/fleet")
    @Operation(summary = "Vehicles with live position and active route")
    public List<VehicleView> fleet() {
        return routeService.fleet();
    }

    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestParam(defaultValue = "false") boolean open) {
        return alertService.list(open);
    }

    @PostMapping("/alerts/{id}/ack")
    public Alert acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }

    @PostMapping("/alerts/ack-all")
    public Map<String, Integer> acknowledgeAll() {
        return Map.of("acknowledged", alertService.acknowledgeAll());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events: 'tick' after each simulation step, 'alert' when one is raised")
    public SseEmitter stream() {
        return liveUpdateService.subscribe();
    }
}
