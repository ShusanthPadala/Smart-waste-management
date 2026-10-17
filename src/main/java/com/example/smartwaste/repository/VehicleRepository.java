package com.example.smartwaste.repository;

import com.example.smartwaste.entity.Vehicle;
import com.example.smartwaste.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByStatusOrderByIdAsc(VehicleStatus status);
}
