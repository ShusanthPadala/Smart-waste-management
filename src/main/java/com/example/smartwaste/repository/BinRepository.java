package com.example.smartwaste.repository;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BinRepository extends JpaRepository<Bin, Long> {
    List<Bin> findByStatus(BinStatus status);
}
