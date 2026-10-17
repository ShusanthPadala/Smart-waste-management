package com.example.smartwaste.algorithm;

import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.AlertSeverity;
import com.example.smartwaste.entity.AlertType;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.BinStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import com.example.smartwaste.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneId.of("UTC"));
    private final LocalDateTime now = LocalDateTime.now(clock);

    private BinRepository binRepository;
    private SensorReadingRepository readingRepository;
    private AlertService alertService;
    private PredictionService predictionService;
    private Bin bin;

    @BeforeEach
    void setUp() {
        binRepository = mock(BinRepository.class);
        readingRepository = mock(SensorReadingRepository.class);
        alertService = mock(AlertService.class);
        predictionService = new PredictionService(binRepository, readingRepository, alertService, clock);

        bin = new Bin();
        bin.setId(1L);
        bin.setBinCode("B001");
        bin.setLocationName("Test Street");
        bin.setCapacity(100.0);
        when(binRepository.findAll()).thenReturn(List.of(bin));
    }

    private void givenReadings(SensorReading... readings) {
        when(readingRepository.findByBinIdAndTimestampAfterOrderByTimestampAsc(eq(1L), any())).thenReturn(Arrays.asList(readings));
    }

    private SensorReading reading(double fill, int hoursAgo) {
        SensorReading r = new SensorReading();
        r.setBin(bin);
        r.setFillPercentage(fill);
        r.setTimestamp(now.minusHours(hoursAgo));
        return r;
    }

    private PredictionResult predict() {
        return predictionService.runPredictionsForAllBins().get(0);
    }

    @Test
    void insufficientDataFallsBackToCurrentFill() {
        bin.setCurrentFillPercentage(10.0);
        givenReadings(reading(10.0, 0));

        PredictionResult result = predict();

        assertEquals(10.0, result.getPredictedFillPercentage());
        assertNull(result.getEstimatedHoursToFull());
        assertEquals(0.0, result.getConfidence());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }

    @Test
    void fallingTrendHasNoOverflowForecast() {
        bin.setCurrentFillPercentage(30.0);
        givenReadings(reading(50, 3), reading(40, 2), reading(30, 1));

        PredictionResult result = predict();

        assertEquals(30.0, result.getPredictedFillPercentage());
        assertNull(result.getEstimatedHoursToFull());
        assertEquals(0.0, result.getFillRatePerHour());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }

    @Test
    void fastFillingBinIsOverflowRisk() {
        bin.setCurrentFillPercentage(80.0);
        givenReadings(reading(10, 3), reading(40, 2), reading(80, 1)); // ~35% per hour

        PredictionResult result = predict();

        assertTrue(result.getPredictedFillPercentage() > 95.0);
        assertNotNull(result.getEstimatedHoursToFull());
        assertTrue(result.getConfidence() > 0.5);
        assertEquals(35.0, result.getFillRatePerHour(), 0.01);
        assertEquals(BinStatus.OVERFLOW_RISK, result.getRecommendedStatus());
    }

    @Test
    void highFillIsUrgent() {
        bin.setCurrentFillPercentage(98.0);
        givenReadings(reading(96, 3), reading(97, 2), reading(98, 1));
        assertEquals(BinStatus.URGENT, predict().getRecommendedStatus());
    }

    @Test
    void fullBinIsOverflowRiskEvenWithoutTrend() {
        bin.setCurrentFillPercentage(100.0);
        givenReadings(reading(100, 1));
        assertEquals(BinStatus.OVERFLOW_RISK, predict().getRecommendedStatus());
    }

    @Test
    void fullWithinADayIsSoon() {
        bin.setCurrentFillPercentage(50.0);
        givenReadings(reading(40.0, 3), reading(43.33, 2), reading(46.66, 1)); // ~3.3%/h -> ~15h to full
        assertEquals(BinStatus.SOON, predict().getRecommendedStatus());
    }

    @Test
    void trendRestartsAfterACollection() {
        bin.setCurrentFillPercentage(15.0);
        givenReadings(reading(90, 4), reading(95, 3), reading(100, 2), reading(5, 1), reading(15, 0));

        PredictionResult result = predict();

        // 10% per hour from 15% -> 8.5 h to full -> SOON.
        assertEquals(BinStatus.SOON, result.getRecommendedStatus());
        assertEquals(8.5, result.getEstimatedHoursToFull(), 0.01);
        assertTrue(result.getConfidence() > 0.0);
    }

    @Test
    void singleReadingAfterCollectionGivesNoTrend() {
        bin.setCurrentFillPercentage(5.0);
        givenReadings(reading(95, 2), reading(100, 1), reading(5, 0));

        PredictionResult result = predict();

        assertEquals(0.0, result.getConfidence());
        assertEquals(BinStatus.NORMAL, result.getRecommendedStatus());
    }

    @Test
    void escalationRaisesAnAlertOnceAndPersistsStatus() {
        bin.setStatus(BinStatus.NORMAL);
        bin.setCurrentFillPercentage(92.0);
        givenReadings(reading(90, 2), reading(91, 1), reading(92, 0));

        predict();

        verify(alertService).raiseForBin(eq(bin), eq(AlertType.URGENT), eq(AlertSeverity.WARNING), anyString());
        verify(binRepository).save(bin);
        assertEquals(BinStatus.URGENT, bin.getStatus());

        // Same status on the next run: no new alert, no extra save.
        predict();
        verify(alertService, times(1)).raiseForBin(any(), any(), any(), anyString());
        verify(binRepository, times(1)).save(bin);
    }
}
