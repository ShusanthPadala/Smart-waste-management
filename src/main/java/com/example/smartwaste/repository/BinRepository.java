package com.example.smartwaste.repository;

import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BinRepository extends JpaRepository<Bin, Long> {
    List<Bin> findByStatus(BinStatus status);
    List<Bin> findByStatusIn(Collection<BinStatus> statuses);
    Optional<Bin> findByBinCodeIgnoreCase(String binCode);
}
