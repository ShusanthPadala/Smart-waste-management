package com.example.smartwaste.repository;

import com.example.smartwaste.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findByBinIdOrderByTimestampAsc(Long binId);
    List<SensorReading> findByBinIdOrderByTimestampDesc(Long binId);
    void deleteByBinId(Long binId);
}
