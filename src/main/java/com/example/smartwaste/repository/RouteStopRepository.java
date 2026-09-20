package com.example.smartwaste.repository;

import com.example.smartwaste.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    
    @Modifying
    @Query("UPDATE RouteStop r SET r.bin = null WHERE r.bin.id = :binId")
    void setBinToNull(Long binId);
}
