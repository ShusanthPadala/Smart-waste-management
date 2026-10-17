package com.example.smartwaste.service;

import com.example.smartwaste.config.DistrictSettings;
import com.example.smartwaste.entity.RouteStatus;
import com.example.smartwaste.entity.Vehicle;
import com.example.smartwaste.entity.VehicleStatus;
import com.example.smartwaste.repository.CollectionRouteRepository;
import com.example.smartwaste.repository.VehicleRepository;
import com.example.smartwaste.web.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Changes to the district itself: its name and where the depot is. */
@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictSettings district;
    private final CollectionRouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final LiveUpdateService liveUpdateService;

    /**
     * Renames the district and/or moves the depot. Moving the depot invalidates routes that were
     * planned from the old one, so those are dropped; it is refused while trucks are on the road.
     */
    @Transactional
    public void update(String cityName, double depotLatitude, double depotLongitude) {
        boolean depotMoved = depotLatitude != district.getDepotLatitude() || depotLongitude != district.getDepotLongitude();
        if (depotMoved) {
            if (!routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.IN_PROGRESS)).isEmpty()) {
                throw new ConflictException("Trucks are on the road. Complete or cancel active routes before moving the depot.");
            }
            routeRepository.deleteAll(routeRepository.findByStatusInOrderByGeneratedAtDesc(List.of(RouteStatus.PLANNED)));
            for (Vehicle v : vehicleRepository.findAll()) {
                if (v.getStatus() == VehicleStatus.AVAILABLE) {
                    v.setCurrentLatitude(depotLatitude);
                    v.setCurrentLongitude(depotLongitude);
                }
            }
        }
        district.update(cityName.trim(), depotLatitude, depotLongitude);
        liveUpdateService.broadcast("tick", "district");
    }
}
