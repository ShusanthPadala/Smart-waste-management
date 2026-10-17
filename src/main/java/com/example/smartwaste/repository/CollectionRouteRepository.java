package com.example.smartwaste.repository;

import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.entity.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface CollectionRouteRepository extends JpaRepository<CollectionRoute, Long> {
    CollectionRoute findTopByOrderByGeneratedAtDesc();
    List<CollectionRoute> findByStatusInOrderByGeneratedAtDesc(Collection<RouteStatus> statuses);
    List<CollectionRoute> findByStatusOrderByCompletedAtAsc(RouteStatus status);
    List<CollectionRoute> findTop30ByOrderByGeneratedAtDesc();
}
