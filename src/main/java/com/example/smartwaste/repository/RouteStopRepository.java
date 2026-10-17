package com.example.smartwaste.repository;

import com.example.smartwaste.entity.RouteStatus;
import com.example.smartwaste.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    @Modifying
    @Query("UPDATE RouteStop r SET r.bin = null WHERE r.bin.id = :binId")
    void setBinToNull(Long binId);

    /** Ids of bins that are already on a route that is planned or under way. */
    @Query("SELECT DISTINCT r.bin.id FROM RouteStop r WHERE r.collected = false AND r.bin IS NOT NULL AND r.collectionRoute.status IN :statuses")
    List<Long> findAssignedBinIds(Collection<RouteStatus> statuses);
}
