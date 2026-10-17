package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(indexes = @Index(name = "ux_bin_code", columnList = "binCode", unique = true))
public class Bin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String binCode;
    private String locationName;
    private String zone;

    @Enumerated(EnumType.STRING)
    private WasteType wasteType = WasteType.GENERAL;

    /** How the surroundings are used (residential, market, ...); shapes the simulated fill rate. */
    @Enumerated(EnumType.STRING)
    private SiteProfile siteProfile = SiteProfile.RESIDENTIAL;

    private double latitude;
    private double longitude;

    /** Capacity in litres. */
    private double capacity;
    private double currentFillPercentage;

    /** Baseline fill rate (% per hour) used by the simulator to model this site's usage. */
    private double baseFillRatePerHour;

    private LocalDateTime lastUpdated;
    private LocalDateTime lastCollectedAt;

    @Enumerated(EnumType.STRING)
    private BinStatus status = BinStatus.NORMAL;

    /** Litres of waste currently in the bin. */
    @Transient
    public double getCurrentLoadLitres() {
        return capacity * currentFillPercentage / 100.0;
    }
}
