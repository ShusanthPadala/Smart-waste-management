package com.example.smartwaste.repository;

import com.example.smartwaste.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findByBinIdOrderByTimestampAsc(Long binId);
    List<SensorReading> findByBinIdOrderByTimestampDesc(Long binId);
    List<SensorReading> findByBinIdAndTimestampAfterOrderByTimestampAsc(Long binId, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM SensorReading r WHERE r.bin.id = :binId")
    void deleteByBinId(Long binId);

    @Modifying
    @Query("DELETE FROM SensorReading r WHERE r.timestamp < :cutoff")
    int deleteOlderThan(LocalDateTime cutoff);
}
