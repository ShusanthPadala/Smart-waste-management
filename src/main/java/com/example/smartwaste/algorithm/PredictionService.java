package com.example.smartwaste.algorithm;

import com.example.smartwaste.dto.BinForecast;
import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.AlertSeverity;
import com.example.smartwaste.entity.AlertType;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import com.example.smartwaste.service.AlertService;
import com.example.smartwaste.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forecasts when each bin will overflow with a least-squares trend line over its recent readings,
 * then classifies it as NORMAL / SOON / URGENT / OVERFLOW_RISK.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    /** Readings older than this are never needed for a trend. */
    static final int LOOKBACK_HOURS = 72;
    /** Trend window: the most recent N readings since the last collection. */
    static final int TREND_WINDOW = 10;
    /** A drop larger than this between consecutive readings is treated as a collection. */
    static final double COLLECTION_DROP = 10.0;

    private final BinRepository binRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final AlertService alertService;
    private final Clock clock;

    private final Map<Long, PredictionResult> latest = new ConcurrentHashMap<>();

    @Value("${app.threshold.urgent:90.0}")
    private double urgentFillThreshold = 90.0;

    @Value("${app.threshold.urgent-hours:8.0}")
    private double urgentHoursThreshold = 8.0;

    @Value("${app.threshold.soon-hours:24.0}")
    private double soonHoursThreshold = 24.0;

    @Transactional
    public List<PredictionResult> runPredictionsForAllBins() {
        return binRepository.findAll().stream().map(this::predictAndClassifyBin).toList();
    }

    @Transactional
    public PredictionResult predictAndClassifyBin(Bin bin) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SensorReading> readings = sensorReadingRepository
                .findByBinIdAndTimestampAfterOrderByTimestampAsc(bin.getId(), now.minusHours(LOOKBACK_HOURS));

        PredictionResult result = computePrediction(bin, readings, now);

        BinStatus previous = bin.getStatus();
        BinStatus next = result.getRecommendedStatus();
        if (next != previous) {
            raiseEscalationAlert(bin, previous, next, result);
            bin.setStatus(next);
            binRepository.save(bin);
        }
        if (bin.getId() != null) latest.put(bin.getId(), result);
        return result;
    }

    /** Last computed prediction for a bin, computing one if none is cached. */
    @Transactional
    public PredictionResult latestFor(Bin bin) {
        PredictionResult cached = latest.get(bin.getId());
        return cached != null ? cached : predictAndClassifyBin(bin);
    }

    public void evict(Long binId) {
        latest.remove(binId);
    }

    public void clearCache() {
        latest.clear();
    }

    /** History (last 72h) plus an hourly projection for the next 24 hours along the trend line. */
    @Transactional(readOnly = true)
    public BinForecast forecast(Long binId) {
        Bin bin = binRepository.findById(binId).orElseThrow(() -> new NotFoundException("Bin " + binId + " not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        List<SensorReading> readings = sensorReadingRepository
                .findByBinIdAndTimestampAfterOrderByTimestampAsc(binId, now.minusHours(LOOKBACK_HOURS));
        PredictionResult p = computePrediction(bin, readings, now);

        List<BinForecast.Point> history = new ArrayList<>();
        for (SensorReading r : readings) history.add(new BinForecast.Point(r.getTimestamp(), round1(r.getFillPercentage())));
        history.add(new BinForecast.Point(now, round1(bin.getCurrentFillPercentage())));

        List<BinForecast.Point> forecast = new ArrayList<>();
        double fill = bin.getCurrentFillPercentage();
        for (int h = 0; h <= 24; h++) {
            forecast.add(new BinForecast.Point(now.plusHours(h), round1(Math.min(100.0, fill + p.getFillRatePerHour() * h))));
        }
        return new BinForecast(binId, bin.getBinCode(), history, forecast,
                p.getFillRatePerHour(), p.getConfidence(), p.getEstimatedHoursToFull());
    }

    /** Pure prediction: no persistence, no side effects. {@code readings} must be in time order. */
    PredictionResult computePrediction(Bin bin, List<SensorReading> readings, LocalDateTime now) {
        PredictionResult result = new PredictionResult();
        result.setBinId(bin.getId());
        result.setBinCode(bin.getBinCode());
        result.setCurrentFillPercentage(bin.getCurrentFillPercentage());
        result.setPredictedFillPercentage(bin.getCurrentFillPercentage());
        result.setConfidence(0.0);

        List<SensorReading> recent = trendWindow(readings);
        if (recent.size() < 2) {
            result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), null));
            return result;
        }

        // Least-squares fit of y = m*x + c, with x in hours since the first reading of the window.
        LocalDateTime baseTime = recent.get(0).getTimestamp();
        int n = recent.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (SensorReading r : recent) {
            double x = hoursBetween(baseTime, r.getTimestamp());
            double y = r.getFillPercentage();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denominator = n * sumX2 - sumX * sumX;
        double slope = denominator != 0 ? (n * sumXY - sumX * sumY) / denominator : 0;

        if (slope <= 0) {
            // Flat or emptying - no overflow forecast.
            result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), null));
            return result;
        }

        double intercept = (sumY - slope * sumX) / n;
        double currentHours = hoursBetween(baseTime, now);

        double hoursToFull = Math.max(0, (100 - intercept) / slope - currentHours);
        result.setEstimatedHoursToFull(hoursToFull);
        result.setPredictedOverflowTime(now.plusMinutes((long) (hoursToFull * 60)));
        result.setFillRatePerHour(slope);

        double predicted = Math.min(intercept + slope * (currentHours + 24), 100.0);
        result.setPredictedFillPercentage(Math.max(predicted, bin.getCurrentFillPercentage()));

        // R-squared as confidence, penalised when the window has fewer than 5 points.
        double meanY = sumY / n;
        double ssTot = 0, ssRes = 0;
        for (SensorReading r : recent) {
            double x = hoursBetween(baseTime, r.getTimestamp());
            double y = r.getFillPercentage();
            double f = intercept + slope * x;
            ssTot += (y - meanY) * (y - meanY);
            ssRes += (y - f) * (y - f);
        }
        double rSquared = ssTot > 0 ? 1.0 - ssRes / ssTot : (ssRes > 0 ? 0.0 : 1.0);
        double dataPenalty = Math.min(n / 5.0, 1.0);
        result.setConfidence(Math.max(0.0, Math.min(rSquared * dataPenalty, 1.0)));

        result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), hoursToFull));
        return result;
    }

    /** Last {@link #TREND_WINDOW} readings, trimmed to those after the most recent collection. */
    private List<SensorReading> trendWindow(List<SensorReading> readings) {
        int start = Math.max(0, readings.size() - TREND_WINDOW);
        List<SensorReading> recent = readings.subList(start, readings.size());
        int lastCollection = -1;
        for (int i = 1; i < recent.size(); i++) {
            if (recent.get(i).getFillPercentage() < recent.get(i - 1).getFillPercentage() - COLLECTION_DROP) {
                lastCollection = i;
            }
        }
        return lastCollection == -1 ? recent : recent.subList(lastCollection, recent.size());
    }

    BinStatus classifyStatus(double currentFill, Double hoursToFull) {
        if (currentFill >= 100.0 || (hoursToFull != null && hoursToFull <= 0)) return BinStatus.OVERFLOW_RISK;
        if (currentFill >= urgentFillThreshold) return BinStatus.URGENT;
        if (hoursToFull != null) {
            if (hoursToFull <= urgentHoursThreshold) return BinStatus.URGENT;
            if (hoursToFull <= soonHoursThreshold) return BinStatus.SOON;
        }
        return BinStatus.NORMAL;
    }

    private void raiseEscalationAlert(Bin bin, BinStatus previous, BinStatus next, PredictionResult p) {
        if (alertService == null || severity(next) <= severity(previous)) return;
        String where = bin.getBinCode() + " (" + bin.getLocationName() + ")";
        if (next == BinStatus.OVERFLOW_RISK) {
            alertService.raiseForBin(bin, AlertType.OVERFLOW_RISK, AlertSeverity.CRITICAL,
                    where + " is at " + Math.round(bin.getCurrentFillPercentage()) + "% and about to overflow. Collect immediately.");
        } else if (next == BinStatus.URGENT) {
            String eta = p.getEstimatedHoursToFull() != null ? String.format(" - full in ~%.1f h", p.getEstimatedHoursToFull()) : "";
            alertService.raiseForBin(bin, AlertType.URGENT, AlertSeverity.WARNING,
                    where + " needs collection: " + Math.round(bin.getCurrentFillPercentage()) + "% full" + eta + ".");
        }
    }

    private static int severity(BinStatus s) {
        return s == null ? 0 : s.ordinal();
    }

    private static double hoursBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toSeconds() / 3600.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
