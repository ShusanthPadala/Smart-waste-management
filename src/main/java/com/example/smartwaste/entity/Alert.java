package com.example.smartwaste.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(indexes = @Index(name = "ix_alert_created", columnList = "createdAt"))
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kept as a plain id so deleting a bin never breaks the alert log. */
    private Long binId;
    private String binCode;

    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    private AlertType type;

    @Column(length = 500)
    private String message;

    private LocalDateTime createdAt;
    private boolean acknowledged;
}
