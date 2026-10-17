package com.example.smartwaste.controller;

import com.example.smartwaste.config.DistrictSettings;
import com.example.smartwaste.service.DistrictService;
import com.example.smartwaste.simulation.DemoCity;
import com.example.smartwaste.simulation.SimulationService;
import com.example.smartwaste.simulation.WorldLock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "District name, depot location and demo datasets")
public class SettingsController {

    private final DistrictSettings district;
    private final DistrictService districtService;
    private final SimulationService simulationService;
    private final WorldLock worldLock;

    public record DistrictRequest(
            @NotBlank @Size(max = 60) String cityName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double depotLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double depotLongitude) {}

    public record DemoCityRequest(@NotBlank String city) {}

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cityName", district.getCityName());
        m.put("depot", Map.of("lat", district.getDepotLatitude(), "lon", district.getDepotLongitude()));
        m.put("demoCity", simulationService.currentCity().key());
        List<Map<String, Object>> cities = DemoCity.ALL.stream()
                .map(c -> Map.<String, Object>of("key", c.key(), "name", c.name(), "bins", c.sites().size()))
                .toList();
        m.put("demoCities", cities);
        return m;
    }

    @PutMapping("/district")
    @Operation(summary = "Rename the district or move the depot",
            description = "Moving the depot drops undispatched plans and is refused while routes are in progress.")
    public Map<String, Object> updateDistrict(@Valid @RequestBody DistrictRequest req) {
        worldLock.run(() -> districtService.update(req.cityName(), req.depotLatitude(), req.depotLongitude()));
        return get();
    }

    @PostMapping("/demo-city")
    @Operation(summary = "Replace all data with a ready-made demo city")
    public Map<String, Object> loadDemoCity(@Valid @RequestBody DemoCityRequest req) {
        worldLock.run(() -> simulationService.loadDemoCity(req.city()));
        return get();
    }

    @PostMapping("/clear")
    @Operation(summary = "Remove all bins, routes, alerts and readings to start mapping from scratch")
    public Map<String, Object> clear() {
        worldLock.run(simulationService::clearDistrict);
        return get();
    }
}
