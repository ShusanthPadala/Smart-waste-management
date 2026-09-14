package com.example.smartwaste.algorithm;

import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    private final BinRepository binRepository;
    private final SensorReadingRepository sensorReadingRepository;

    @Value("${app.threshold.urgent:90.0}")
    private double urgentFillThreshold;

    @Value("${app.threshold.urgent-hours:8.0}")
    private double urgentHoursThreshold;

    @Value("${app.threshold.soon-hours:24.0}")
    private double soonHoursThreshold;

    @Transactional
    public List<PredictionResult> runPredictionsForAllBins() {
        log.info("Running predictions for all bins");
        List<Bin> bins = binRepository.findAll();
        return bins.stream().map(this::predictAndClassifyBin).collect(Collectors.toList());
    }

    @Transactional
    public PredictionResult predictAndClassifyBin(Bin bin) {
        List<SensorReading> readings = sensorReadingRepository.findByBinIdOrderByTimestampAsc(bin.getId());
        
        PredictionResult result = new PredictionResult();
        result.setBinId(bin.getId());
        result.setCurrentFillPercentage(bin.getCurrentFillPercentage());
        
        // Linear regression: y = mx + c
        if (readings.size() < 2) {
            log.warn("Insufficient data for bin {}", bin.getBinCode());
            result.setPredictedFillPercentage(bin.getCurrentFillPercentage());
            result.setConfidence(0.0);
            result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), null));
        } else {
            // Use last N readings to capture current trend better
            int startIdx = Math.max(0, readings.size() - 10);
            List<SensorReading> recentReadings = new java.util.ArrayList<>(readings.subList(startIdx, readings.size()));
            
            // If a collection happened recently (fill dropped significantly), only use data AFTER the collection
            int lastCollectionIdx = -1;
            for (int i = 1; i < recentReadings.size(); i++) {
                if (recentReadings.get(i).getFillPercentage() < recentReadings.get(i-1).getFillPercentage() - 10.0) {
                    lastCollectionIdx = i;
                }
            }
            if (lastCollectionIdx != -1) {
                recentReadings = recentReadings.subList(lastCollectionIdx, recentReadings.size());
            }
            
            if (recentReadings.size() < 2) {
                log.warn("Insufficient data for bin {} after recent collection", bin.getBinCode());
                result.setPredictedFillPercentage(bin.getCurrentFillPercentage());
                result.setConfidence(0.0);
                result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), null));
                bin.setStatus(result.getRecommendedStatus());
                bin.setLastUpdated(LocalDateTime.now());
                binRepository.save(bin);
                return result;
            }
            
            LocalDateTime baseTime = recentReadings.get(0).getTimestamp();
            
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            int n = recentReadings.size();
            
            for (SensorReading r : recentReadings) {
                double x = Duration.between(baseTime, r.getTimestamp()).toMinutes() / 60.0;
                double y = r.getFillPercentage();
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
            }
            
            double denominator = (n * sumX2 - sumX * sumX);
            double slope = 0; // fill percentage change per hour
            if (denominator != 0) {
                slope = (n * sumXY - sumX * sumY) / denominator;
            }
            
            if (slope <= 0) {
                // Bin is emptying or flat, no overflow risk
                result.setPredictedFillPercentage(bin.getCurrentFillPercentage());
                result.setConfidence(0.0); // Low confidence when no computable filling trend
                result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), null));
            } else {
                double currentHours = Duration.between(baseTime, LocalDateTime.now()).toMinutes() / 60.0;
                double intercept = (sumY - slope * sumX) / n;
                
                // y = mx + c => 100 = m * x_full + c => x_full = (100 - c) / m
                double xFull = (100 - intercept) / slope;
                
                double hoursToFull = xFull - currentHours;
                if (hoursToFull < 0) hoursToFull = 0; 
                
                result.setEstimatedHoursToFull(hoursToFull);
                result.setPredictedOverflowTime(LocalDateTime.now().plusMinutes((long)(hoursToFull * 60)));
                
                double predictedNext24h = intercept + slope * (currentHours + 24);
                double predictedFill = Math.min(predictedNext24h, 100.0);
            // Clamp to current fill to avoid decreasing prediction without collection
            predictedFill = Math.max(predictedFill, bin.getCurrentFillPercentage());
            result.setPredictedFillPercentage(predictedFill);
                
                // Calculate R-squared for confidence score
                double meanY = sumY / n;
                double ssTot = 0, ssRes = 0;
                for (SensorReading r : recentReadings) {
                    double x = Duration.between(baseTime, r.getTimestamp()).toMinutes() / 60.0;
                    double y = r.getFillPercentage();
                    double f = intercept + slope * x;
                    ssTot += (y - meanY) * (y - meanY);
                    ssRes += (y - f) * (y - f);
                }
                
                double rSquared = 1.0;
                if (ssTot > 0) {
                    rSquared = 1.0 - (ssRes / ssTot);
                } else if (ssRes > 0) {
                    rSquared = 0.0;
                }
                
                // Penalize confidence if we have very few data points (e.g. less than 5)
                double dataPenalty = Math.min(n / 5.0, 1.0); 
                double finalConfidence = Math.max(0.0, Math.min(rSquared * dataPenalty, 1.0));
                
                result.setConfidence(finalConfidence);
                result.setRecommendedStatus(classifyStatus(bin.getCurrentFillPercentage(), hoursToFull));
            }
        }
        
        bin.setStatus(result.getRecommendedStatus());
        bin.setLastUpdated(LocalDateTime.now());
        binRepository.save(bin);
        
        return result;
    }
    
    private BinStatus classifyStatus(double currentFill, Double hoursToFull) {
        if (hoursToFull != null) {
            if (hoursToFull <= 0) {
                return BinStatus.OVERFLOW_RISK;
            }
        }
        
        if (currentFill >= urgentFillThreshold) {
            return BinStatus.URGENT;
        }
        
        if (hoursToFull != null) {
            if (hoursToFull <= urgentHoursThreshold) {
                return BinStatus.URGENT;
            }
            if (hoursToFull <= soonHoursThreshold) {
                return BinStatus.SOON;
            }
        }
        
        return BinStatus.NORMAL;
    }
}
