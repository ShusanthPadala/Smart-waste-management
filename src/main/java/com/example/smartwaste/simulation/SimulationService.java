package com.example.smartwaste.simulation;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.config.SimulationClock;
import com.example.smartwaste.config.DistrictSettings;
import com.example.smartwaste.dto.SimulationState;
import com.example.smartwaste.entity.*;
import com.example.smartwaste.repository.*;
import com.example.smartwaste.service.LiveUpdateService;
import com.example.smartwaste.service.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Stands in for a city's IoT sensor network.
 *
 * <p>On reset it builds a demo district with seven days of hourly history. While running, every tick
 * advances the {@link SimulationClock}: bins fill according to their site profile and the time of day,
 * a sensor reading is stored every simulated hour, trucks on dispatched routes drive and empty bins,
 * predictions are refreshed and connected dashboards are notified.
 */
@Service
@Slf4j
public class SimulationService {

    private final BinRepository binRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final VehicleRepository vehicleRepository;
    private final CollectionRouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final AlertRepository alertRepository;
    private final PredictionService predictionService;
    private final RouteOptimizationService routeOptimizationService;
    private final RouteService routeService;
    private final LiveUpdateService liveUpdateService;
    private final DistrictSettings district;
    private final SimulationClock clock;
    private final TransactionTemplate tx;
    private final WorldLock worldLock;

    @Value("${smartwaste.simulation.enabled:true}")
    private boolean enabled = true;

    @Value("${smartwaste.simulation.history-days:7}")
    private int historyDays = 7;

    @Value("${smartwaste.simulation.reading-interval-minutes:60}")
    private int readingIntervalMinutes = 60;

    @Value("${smartwaste.simulation.retention-days:14}")
    private int retentionDays = 14;

    private volatile boolean autopilot;
    private volatile DemoCity currentCity;
    private LocalDateTime lastTick;
    private LocalDateTime lastReadingAt;
    private LocalDateTime lastAutopilotPlan;
    private final Random random = new Random();

    public SimulationService(BinRepository binRepository, SensorReadingRepository sensorReadingRepository,
                             VehicleRepository vehicleRepository, CollectionRouteRepository routeRepository,
                             RouteStopRepository routeStopRepository, AlertRepository alertRepository, PredictionService predictionService,
                             RouteOptimizationService routeOptimizationService, RouteService routeService,
                             LiveUpdateService liveUpdateService, DistrictSettings district,
                             SimulationClock clock, PlatformTransactionManager transactionManager, WorldLock worldLock,
                             @Value("${smartwaste.simulation.autopilot:false}") boolean autopilot,
                             @Value("${smartwaste.demo.city:visakhapatnam}") String demoCity) {
        this.worldLock = worldLock;
        this.binRepository = binRepository;
        this.sensorReadingRepository = sensorReadingRepository;
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.alertRepository = alertRepository;
        this.predictionService = predictionService;
        this.routeOptimizationService = routeOptimizationService;
        this.routeService = routeService;
        this.liveUpdateService = liveUpdateService;
        this.district = district;
        this.currentCity = DemoCity.byKey(demoCity).orElse(DemoCity.VISAKHAPATNAM);
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.autopilot = autopilot;
    }

    // ------------------------------------------------------------------ demo district

    /** Reloads the demo city that is currently selected (Visakhapatnam unless changed in Settings). */
    @Transactional
    public void resetDemoData() {
        loadDemoCity(currentCity.key());
    }

    /** Wipes everything and loads a ready-made demo city: depot, fleet, 24 bins and 7 days of history. */
    @Transactional
    public void loadDemoCity(String key) {
        DemoCity city = DemoCity.byKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown demo city: " + key));
        log.info("Loading demo city {}...", city.name());
        wipe();
        currentCity = city;
        district.update(city.name(), city.depotLatitude(), city.depotLongitude());
        createFleet();

        LocalDateTime now = LocalDateTime.now(clock);
        Random seeded = new Random(42);
        List<SensorReading> readings = new ArrayList<>();
        for (DemoCity.Site s : city.sites()) {
            Bin bin = new Bin();
            bin.setBinCode(s.code());
            bin.setLocationName(s.name());
            bin.setZone(s.zone());
            bin.setWasteType(s.type());
            bin.setSiteProfile(s.profile());
            bin.setLatitude(s.lat());
            bin.setLongitude(s.lon());
            bin.setCapacity(s.capacity());
            bin.setBaseFillRatePerHour(s.baseRate());
            bin.setCurrentFillPercentage(s.targetFill());
            bin.setLastUpdated(now);
            bin.setStatus(BinStatus.NORMAL);
            bin = binRepository.save(bin);
            readings.addAll(backfillHistory(bin, s.profile(), s.baseRate(), s.targetFill(), now, seeded));
        }
        sensorReadingRepository.saveAll(readings);
        predictionService.runPredictionsForAllBins();
        log.info("Demo city {} loaded: {} bins, {} readings.", city.name(), city.sites().size(), readings.size());
    }

    /**
     * Removes every bin, route, alert and reading but keeps the district settings and a fresh fleet,
     * so the operator can map their own bins from scratch.
     */
    @Transactional
    public void clearDistrict() {
        log.info("Clearing district {}", district.getCityName());
        wipe();
        createFleet();
    }

    public DemoCity currentCity() {
        return currentCity;
    }

    private void wipe() {
        clock.resetToRealTime();
        // Bulk deletes run immediately (unlike deleteAll, which Hibernate would flush after later inserts).
        routeStopRepository.deleteAllInBatch();
        routeRepository.deleteAllInBatch();
        alertRepository.deleteAllInBatch();
        sensorReadingRepository.deleteAllInBatch();
        binRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        predictionService.clearCache();
        LocalDateTime now = LocalDateTime.now(clock);
        lastTick = now;
        lastReadingAt = now;
        lastAutopilotPlan = null;
    }

    /** Re-generates 7 days of history for the current bins, keeping their current fill level. */
    @Transactional
    public void generateHistoricalData() {
        LocalDateTime now = LocalDateTime.now(clock);
        sensorReadingRepository.deleteAllInBatch();
        Random seeded = new Random(42);
        List<SensorReading> readings = new ArrayList<>();
        for (Bin bin : binRepository.findAll()) {
            readings.addAll(backfillHistory(bin, profileOf(bin), rateOf(bin), bin.getCurrentFillPercentage(), now, seeded));
        }
        sensorReadingRepository.saveAll(readings);
        predictionService.clearCache();
        predictionService.runPredictionsForAllBins();
    }

    /**
     * Walks backwards from "now" so the newest reading equals {@code targetFill}. Each step subtracts
     * an hour of usage; when the bin would drop below empty, that was a collection, and before it the
     * bin stood at a typical pickup level.
     */
    private List<SensorReading> backfillHistory(Bin bin, SiteProfile profile, double baseRate, double targetFill,
                                                LocalDateTime now, Random rnd) {
        List<SensorReading> out = new ArrayList<>();
        double fill = targetFill;
        LocalDateTime t = now;
        LocalDateTime start = now.minusDays(historyDays);
        LocalDateTime lastCollection = null;
        while (!t.isBefore(start)) {
            SensorReading r = new SensorReading();
            r.setBin(bin);
            r.setTimestamp(t);
            r.setFillPercentage(round1(fill));
            out.add(r);

            fill -= hourlyIncrement(profile, baseRate, t.minusMinutes(30), rnd);
            if (fill < 1.0) {
                if (lastCollection == null) lastCollection = t;
                fill = 70 + rnd.nextDouble() * 25; // the level it was emptied at
            }
            t = t.minusHours(1);
        }
        bin.setLastCollectedAt(lastCollection);
        Collections.reverse(out);
        return out;
    }

    private void createFleet() {
        vehicleRepository.saveAll(List.of(
                vehicle("TRK-01", "Crew Alpha", 6000, 4.5),
                vehicle("TRK-02", "Crew Bravo", 6000, 4.5),
                vehicle("TRK-03", "Crew Charlie", 4000, 5.5)));
    }

    private Vehicle vehicle(String number, String crew, double capacity, double kmPerLitre) {
        Vehicle v = new Vehicle();
        v.setVehicleNumber(number);
        v.setDriverName(crew);
        v.setCapacity(capacity);
        v.setFuelEfficiency(kmPerLitre);
        v.setStatus(VehicleStatus.AVAILABLE);
        v.setCurrentLatitude(district.getDepotLatitude());
        v.setCurrentLongitude(district.getDepotLongitude());
        return v;
    }

    // ------------------------------------------------------------------ usage model

    /** Fill added over one hour around {@code at}, in percentage points. */
    static double hourlyIncrement(SiteProfile profile, double baseRate, LocalDateTime at, Random rnd) {
        return baseRate * profile.usageMultiplier(at) * (0.85 + rnd.nextDouble() * 0.3);
    }

    private static SiteProfile profileOf(Bin bin) {
        return bin.getSiteProfile() != null ? bin.getSiteProfile() : SiteProfile.RESIDENTIAL;
    }

    private static double rateOf(Bin bin) {
        return bin.getBaseFillRatePerHour() > 0 ? bin.getBaseFillRatePerHour() : profileOf(bin).defaultRatePerHour();
    }

    // ------------------------------------------------------------------ live loop

    @Scheduled(fixedDelayString = "${smartwaste.simulation.tick-ms:2000}", initialDelayString = "${smartwaste.simulation.tick-ms:2000}")
    public void tick() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now(clock);
        if (lastTick == null) {
            lastTick = now;
            lastReadingAt = now;
            return;
        }
        double dtHours = Duration.between(lastTick, now).toMillis() / 3_600_000.0;
        if (dtHours <= 0) return; // paused
        lastTick = now;

        try {
            final double step = Math.min(dtHours, 2.0);
            worldLock.run(() -> tx.executeWithoutResult(status -> advanceWorld(now, step)));
            liveUpdateService.broadcast("tick", Map.of("simTime", now.toString()));
        } catch (RuntimeException e) {
            log.error("Simulation tick failed", e);
        }
    }

    private void advanceWorld(LocalDateTime now, double dtHours) {
        // Trucks first: a collection is back-dated to the truck's arrival, so the hourly reading taken
        // below must already see the emptied bin - otherwise the trend would jump from ~0% to the old level.
        routeService.advance(now);

        boolean storeReading = Duration.between(lastReadingAt, now).toMinutes() >= readingIntervalMinutes;
        List<Bin> bins = binRepository.findAll();
        List<SensorReading> readings = new ArrayList<>();
        for (Bin bin : bins) {
            double inc = hourlyIncrement(profileOf(bin), rateOf(bin), now, random) * dtHours;
            bin.setCurrentFillPercentage(Math.min(100.0, bin.getCurrentFillPercentage() + inc));
            bin.setLastUpdated(now);
            if (storeReading) {
                SensorReading r = new SensorReading();
                r.setBin(bin);
                r.setTimestamp(now);
                r.setFillPercentage(round1(bin.getCurrentFillPercentage()));
                readings.add(r);
            }
        }
        binRepository.saveAll(bins);
        if (storeReading) {
            sensorReadingRepository.saveAll(readings);
            sensorReadingRepository.deleteOlderThan(now.minusDays(retentionDays));
            lastReadingAt = now;
        }

        predictionService.runPredictionsForAllBins();
        if (autopilot) runAutopilot(now);
    }

    /**
     * Plans and dispatches routes on its own, at most every 30 simulated minutes, whenever a bin
     * needs pickup and a truck is free.
     */
    private void runAutopilot(LocalDateTime now) {
        if (lastAutopilotPlan != null && Duration.between(lastAutopilotPlan, now).toMinutes() < 30) return;
        boolean waiting = !binRepository.findByStatusIn(RouteOptimizationService.NEEDS_PICKUP).isEmpty();
        if (!waiting || vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.AVAILABLE).isEmpty()) return;
        lastAutopilotPlan = now;
        // Once a truck has to go out anyway, top it up with bins that will be due within a day.
        routeService.plan(true);
        int dispatched = routeService.dispatchAllPlanned();
        if (dispatched > 0) log.info("Autopilot dispatched {} route(s)", dispatched);
    }

    // ------------------------------------------------------------------ controls

    public SimulationState state() {
        return new SimulationState(LocalDateTime.now(clock), clock.getSpeed(), clock.isPaused(), enabled);
    }

    public SimulationState update(Double speed, Boolean paused) {
        if (speed != null) {
            if (speed < 1 || speed > 3600) throw new IllegalArgumentException("speed must be between 1 and 3600");
            clock.setSpeed(speed);
        }
        if (paused != null) clock.setPaused(paused);
        lastTick = LocalDateTime.now(clock);
        return state();
    }

    public boolean isAutopilot() {
        return autopilot;
    }

    public void setAutopilot(boolean on) {
        this.autopilot = on;
        this.lastAutopilotPlan = null;
    }

    // ------------------------------------------------------------------ CSV import

    /**
     * Appends sensor readings from CSV: {@code timestamp,bin,fillPercentage} where {@code bin}
     * is a bin code (e.g. B004) or numeric id. The header row is optional.
     *
     * @return number of readings imported
     */
    @Transactional
    public int importCsv(InputStream in) throws IOException {
        Map<String, Bin> byKey = new HashMap<>();
        for (Bin b : binRepository.findAll()) {
            byKey.put(b.getBinCode().toUpperCase(), b);
            byKey.put(String.valueOf(b.getId()), b);
        }
        List<SensorReading> readings = new ArrayList<>();
        int lineNo = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || (lineNo == 1 && line.toLowerCase().startsWith("timestamp"))) continue;
                String[] parts = line.split(",");
                if (parts.length != 3) throw new IllegalArgumentException("Line " + lineNo + ": expected 3 columns");
                Bin bin = byKey.get(parts[1].trim().toUpperCase());
                if (bin == null) throw new IllegalArgumentException("Line " + lineNo + ": unknown bin '" + parts[1].trim() + "'");
                try {
                    SensorReading r = new SensorReading();
                    r.setBin(bin);
                    r.setTimestamp(LocalDateTime.parse(parts[0].trim()));
                    r.setFillPercentage(Math.max(0, Math.min(100, Double.parseDouble(parts[2].trim()))));
                    readings.add(r);
                    if (bin.getLastUpdated() == null || !r.getTimestamp().isBefore(bin.getLastUpdated())) {
                        bin.setLastUpdated(r.getTimestamp());
                        bin.setCurrentFillPercentage(r.getFillPercentage());
                    }
                } catch (DateTimeParseException | NumberFormatException e) {
                    throw new IllegalArgumentException("Line " + lineNo + ": " + e.getMessage());
                }
            }
        }
        sensorReadingRepository.saveAll(readings);
        binRepository.saveAll(new HashSet<>(byKey.values()));
        predictionService.clearCache();
        predictionService.runPredictionsForAllBins();
        return readings.size();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
