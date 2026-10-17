package com.example.smartwaste.repository;

import com.example.smartwaste.entity.Alert;
import com.example.smartwaste.entity.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findTop100ByOrderByCreatedAtDesc();
    List<Alert> findTop100ByAcknowledgedFalseOrderByCreatedAtDesc();
    long countByAcknowledgedFalse();
    boolean existsByBinIdAndTypeAndAcknowledgedFalse(Long binId, AlertType type);

    @Modifying
    @Query("UPDATE Alert a SET a.acknowledged = true WHERE a.acknowledged = false")
    int acknowledgeAll();

    @Modifying
    @Query("UPDATE Alert a SET a.acknowledged = true WHERE a.binId = :binId AND a.acknowledged = false AND a.type IN :types")
    int acknowledgeForBin(Long binId, Collection<AlertType> types);
}
