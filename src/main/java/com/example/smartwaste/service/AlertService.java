package com.example.smartwaste.service;

import com.example.smartwaste.entity.Alert;
import com.example.smartwaste.entity.AlertSeverity;
import com.example.smartwaste.entity.AlertType;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.repository.AlertRepository;
import com.example.smartwaste.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final List<AlertType> BIN_LEVEL_TYPES = List.of(AlertType.URGENT, AlertType.OVERFLOW_RISK);

    private final AlertRepository alertRepository;
    private final LiveUpdateService liveUpdateService;
    private final Clock clock;

    /** Raises a bin alert unless an identical one is still open. */
    @Transactional
    public void raiseForBin(Bin bin, AlertType type, AlertSeverity severity, String message) {
        if (bin.getId() != null && alertRepository.existsByBinIdAndTypeAndAcknowledgedFalse(bin.getId(), type)) {
            return;
        }
        Alert alert = new Alert();
        alert.setBinId(bin.getId());
        alert.setBinCode(bin.getBinCode());
        save(alert, type, severity, message);
    }

    @Transactional
    public void raise(AlertType type, AlertSeverity severity, String message) {
        save(new Alert(), type, severity, message);
    }

    /** Closes open fill-level alerts once a bin has been emptied. */
    @Transactional
    public void resolveBinAlerts(Long binId) {
        alertRepository.acknowledgeForBin(binId, BIN_LEVEL_TYPES);
    }

    public List<Alert> list(boolean openOnly) {
        return openOnly
                ? alertRepository.findTop100ByAcknowledgedFalseOrderByCreatedAtDesc()
                : alertRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public long openCount() {
        return alertRepository.countByAcknowledgedFalse();
    }

    @Transactional
    public Alert acknowledge(Long id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new NotFoundException("Alert " + id + " not found"));
        alert.setAcknowledged(true);
        return alertRepository.save(alert);
    }

    @Transactional
    public int acknowledgeAll() {
        return alertRepository.acknowledgeAll();
    }

    private void save(Alert alert, AlertType type, AlertSeverity severity, String message) {
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setCreatedAt(LocalDateTime.now(clock));
        liveUpdateService.broadcast("alert", alertRepository.save(alert));
    }
}
