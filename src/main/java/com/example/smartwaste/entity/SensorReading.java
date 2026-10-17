package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Data
@Table(indexes = @Index(name = "ix_reading_bin_time", columnList = "bin_id, timestamp"))
public class SensorReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id")
    @JsonIgnore
    private Bin bin;

    private double fillPercentage;
}
