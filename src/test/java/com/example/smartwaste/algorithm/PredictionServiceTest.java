package com.example.smartwaste.algorithm;

import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

public class PredictionServiceTest {

    @Mock
    private BinRepository binRepository;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    @InjectMocks
    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(predictionService, "urgentFillThreshold", 90.0);
        ReflectionTestUtils.setField(predictionService, "urgentHoursThreshold", 8.0);
        ReflectionTestUtils.setField(predictionService, "soonHoursThreshold", 24.0);
    }
    
    private SensorReading createReading(Long id, Bin bin, double fill, LocalDateTime time) {
        SensorReading reading = new SensorReading();
        reading.setId(id);
        reading.setBin(bin);
        reading.setFillPercentage(fill);
        reading.setTimestamp(time);
        return reading;
    }

    @Test
    void testInsufficientData() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(10.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(List.of(
            createReading(1L, bin, 10.0, LocalDateTime.now())
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        assertEquals(1, results.size());
        PredictionResult result = results.get(0);
        
        assertEquals(10.0, result.getCurrentFillPercentage());
        assertEquals(10.0, result.getPredictedFillPercentage());
        assertNull(result.getEstimatedHoursToFull());
        assertEquals(0.0, result.getConfidence());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }

    @Test
    void testNegativeOrZeroSlope() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(30.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 50.0, now.minusHours(3)),
            createReading(2L, bin, 40.0, now.minusHours(2)),
            createReading(3L, bin, 30.0, now.minusHours(1))
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        assertEquals(30.0, result.getCurrentFillPercentage());
        assertEquals(30.0, result.getPredictedFillPercentage());
        assertNull(result.getEstimatedHoursToFull());
        assertEquals(0.0, result.getConfidence());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }

    @Test
    void testFastFillingUrgent() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(80.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 10.0, now.minusHours(3)),
            createReading(2L, bin, 40.0, now.minusHours(2)),
            createReading(3L, bin, 80.0, now.minusHours(1)) // Fast fill, 35% per hr
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        assertEquals(80.0, result.getCurrentFillPercentage());
        assertTrue(result.getPredictedFillPercentage() > 95.0); // Next 24h will exceed 100
        assertNotNull(result.getEstimatedHoursToFull());
        assertTrue(result.getConfidence() > 0.5);
        assertEquals(BinStatus.OVERFLOW_RISK, result.getRecommendedStatus());
    }
    
    @Test
    void testOverflowRisk() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(98.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 96.0, now.minusHours(3)),
            createReading(2L, bin, 97.0, now.minusHours(2)),
            createReading(3L, bin, 98.0, now.minusHours(1))
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        assertEquals(BinStatus.URGENT, result.getRecommendedStatus());
    }

    @Test
    void testSoonStatus() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(50.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        // 10% fill per hour, current fill is 50%. So it will take 5 hours to reach 100%. 5 is < 8 (Urgent).
        // Wait, 5 < 8 means URGENT. To test SOON, hoursToFull needs to be between 8 and 24. Let's make it 15 hours.
        // 100 - 50 = 50. 50 / 15 = 3.33% fill per hour.
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 40.0, now.minusHours(3)),
            createReading(2L, bin, 43.33, now.minusHours(2)),
            createReading(3L, bin, 46.66, now.minusHours(1))
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        assertEquals(BinStatus.SOON, result.getRecommendedStatus());
    }

    @Test
    void testCollectionEventWithinTrendWindow() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(15.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 90.0, now.minusHours(4)),
            createReading(2L, bin, 95.0, now.minusHours(3)),
            createReading(3L, bin, 100.0, now.minusHours(2)),
            createReading(4L, bin, 5.0, now.minusHours(1)),  // Collection event
            createReading(5L, bin, 15.0, now)                // New trend (10% per hour)
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        // With 10% per hr, hours to full = (100 - 15) / 10 = 8.5 hours.
        // Status should be SOON (8.5 <= 24).
        assertEquals(BinStatus.SOON, result.getRecommendedStatus());
        assertTrue(result.getConfidence() > 0.0);
    }

    @Test
    void testCollectionEventWithInsufficientDataAfter() {
        Bin bin = new Bin();
        bin.setId(1L);
        bin.setCapacity(100.0);
        bin.setCurrentFillPercentage(5.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
        
        LocalDateTime now = LocalDateTime.now();
        when(sensorReadingRepository.findByBinIdOrderByTimestampAsc(1L)).thenReturn(Arrays.asList(
            createReading(1L, bin, 95.0, now.minusHours(2)),
            createReading(2L, bin, 100.0, now.minusHours(1)),
            createReading(3L, bin, 5.0, now)  // Collection event, but only 1 reading after!
        ));

        List<PredictionResult> results = predictionService.runPredictionsForAllBins();
        PredictionResult result = results.get(0);
        
        // Should fallback to 0 confidence because it can't draw a trend line with 1 point
        assertEquals(0.0, result.getConfidence());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }
}
