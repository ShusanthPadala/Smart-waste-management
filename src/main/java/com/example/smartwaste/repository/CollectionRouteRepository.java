package com.example.smartwaste.repository;

import com.example.smartwaste.entity.CollectionRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionRouteRepository extends JpaRepository<CollectionRoute, Long> {
    CollectionRoute findTopByOrderByGeneratedAtDesc();
}
