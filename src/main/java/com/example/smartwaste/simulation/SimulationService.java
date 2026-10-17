package com.example.smartwaste.simulation;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.algorithm.RouteOptimizationService;
import com.example.smartwaste.config.SimulationClock;
import com.example.smartwaste.config.SustainabilityConfig;
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
import java.time.DayOfWeek;
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
    private final SustainabilityConfig config;
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
    private LocalDateTime lastTick;
    private LocalDateTime lastReadingAt;
    private LocalDateTime lastAutopilotPlan;
    private final Random random = new Random();

    public SimulationService(BinRepository binRepository, SensorReadingRepository sensorReadingRepository,
                             VehicleRepository vehicleRepository, CollectionRouteRepository routeRepository,
                             RouteStopRepository routeStopRepository, AlertRepository alertRepository, PredictionService predictionService,
                             RouteOptimizationService routeOptimizationService, RouteService routeService,
                             LiveUpdateService liveUpdateService, SustainabilityConfig config,
                             SimulationClock clock, PlatformTransactionManager transactionManager, WorldLock worldLock,
                             @Value("${smartwaste.simulation.autopilot:false}") boolean autopilot) {
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
        this.config = config;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.autopilot = autopilot;
    }

    // ------------------------------------------------------------------ demo district

    enum Profile { RESIDENTIAL, MARKET, COMMERCIAL, PARK, TRANSIT }

    /** A demo site. {@code targetFill} is where the bin stands "now" when the demo is reset. */
    record Site(String code, String name, String zone, WasteType type, double lat, double lon,
                double capacity, Profile profile, double baseRate, double targetFill) {}

    static final List<Site> DEMO_SITES = List.of(
            new Site("B001", "Green Park Residency", "Gachibowli", WasteType.GENERAL, 17.4423, 78.3560, 1100, Profile.RESIDENTIAL, 1.1, 42),
            new Site("B002", "Sector 12 Apartments", "Kondapur", WasteType.GENERAL, 17.4615, 78.3647, 1100, Profile.RESIDENTIAL, 1.3, 63),
            new Site("B003", "Lakeview Society", "Khajaguda", WasteType.RECYCLABLE, 17.4150, 78.3800, 660, Profile.RESIDENTIAL, 0.9, 28),
            new Site("B004", "Community Market", "Madhapur", WasteType.ORGANIC, 17.4483, 78.3915, 2400, Profile.MARKET, 2.6, 95),
            new Site("B005", "HITEC City Metro Plaza", "HITEC City", WasteType.GENERAL, 17.4474, 78.3762, 1100, Profile.TRANSIT, 2.0, 91),
            new Site("B006", "Cyber Towers Food Court", "HITEC City", WasteType.ORGANIC, 17.4504, 78.3812, 1100, Profile.COMMERCIAL, 2.2, 82),
            new Site("B007", "Kondapur Vegetable Market", "Kondapur", WasteType.ORGANIC, 17.4632, 78.3555, 2400, Profile.MARKET, 2.8, 97),
            new Site("B008", "Botanical Garden Gate", "Kondapur", WasteType.GENERAL, 17.4589, 78.3557, 660, Profile.PARK, 0.8, 35),
            new Site("B009", "Gachibowli Stadium", "Gachibowli", WasteType.RECYCLABLE, 17.4467, 78.3475, 1100, Profile.PARK, 1.0, 22),
            new Site("B010", "DLF Cyber City", "Gachibowli", WasteType.RECYCLABLE, 17.4486, 78.3637, 1100, Profile.COMMERCIAL, 1.6, 71),
            new Site("B011", "Nanakramguda Tech Park", "Financial District", WasteType.RECYCLABLE, 17.4198, 78.3440, 1100, Profile.COMMERCIAL, 1.5, 48),
            new Site("B012", "Financial District Food Street", "Financial District", WasteType.ORGANIC, 17.4147, 78.3417, 1100, Profile.MARKET, 2.3, 79),
            new Site("B013", "Manikonda Main Road", "Manikonda", WasteType.GENERAL, 17.4040, 78.3860, 1100, Profile.RESIDENTIAL, 1.2, 57),
            new Site("B014", "Puppalaguda Colony", "Manikonda", WasteType.GENERAL, 17.3985, 78.3735, 660, Profile.RESIDENTIAL, 1.0, 18),
            new Site("B015", "Jubilee Hills Check Post", "Jubilee Hills", WasteType.GENERAL, 17.4300, 78.4080, 1100, Profile.TRANSIT, 1.8, 66),
            new Site("B016", "Durgam Cheruvu Lake Walk", "Madhapur", WasteType.RECYCLABLE, 17.4300, 78.3880, 660, Profile.PARK, 1.1, 52),
            new Site("B017", "Kothaguda Junction", "Kondapur", WasteType.GENERAL, 17.4610, 78.3700, 1100, Profile.TRANSIT, 1.7, 39),
            new Site("B018", "Raidurg Metro Station", "HITEC City", WasteType.GENERAL, 17.4402, 78.3830, 1100, Profile.TRANSIT, 1.9, 87),
            new Site("B019", "Lingampally Bus Depot", "Lingampally", WasteType.GENERAL, 17.4840, 78.3170, 1100, Profile.TRANSIT, 1.6, 44),
            new Site("B020", "Hafeezpet Railway Station", "Hafeezpet", WasteType.GENERAL, 17.4790, 78.3560, 1100, Profile.TRANSIT, 1.5, 61),
            new Site("B021", "Indira Nagar", "Gachibowli", WasteType.GENERAL, 17.4370, 78.3550, 1100, Profile.RESIDENTIAL, 1.2, 33),
            new Site("B022", "University Hostel Block", "Gachibowli", WasteType.ORGANIC, 17.4565, 78.3280, 1100, Profile.RESIDENTIAL, 1.4, 74),
            new Site("B023", "Whitefields Residency", "Kondapur", WasteType.RECYCLABLE, 17.4570, 78.3700, 660, Profile.RESIDENTIAL, 1.0, 12),
            new Site("B024", "Gachibowli Recycling Point", "Gachibowli", WasteType.RECYCLABLE, 17.4400, 78.3640, 2400, Profile.COMMERCIAL, 1.4, 55)
    );

    /** Wipes everything and rebuilds the demo district, fleet and history. */
    @Transactional
    public void resetDemoData() {
        log.info("Resetting demo data...");
        clock.resetToRealTime();
        // Bulk deletes run immediately (unlike deleteAll, which Hibernate would flush after the inserts below).
        routeStopRepository.deleteAllInBatch();
        routeRepository.deleteAllInBatch();
        alertRepository.deleteAllInBatch();
        sensorReadingRepository.deleteAllInBatch();
        binRepository.deleteAllInBatch();
        vehicleRepository.deleteAllInBatch();
        predictionService.clearCache();

        LocalDateTime now = LocalDateTime.now(clock);
        createFleet();

        Random seeded = new Random(42);
        List<SensorReading> readings = new ArrayList<>();
        for (Site s : DEMO_SITES) {
            Bin bin = new Bin();
            bin.setBinCode(s.code());
            bin.setLocationName(s.name());
            bin.setZone(s.zone());
            bin.setWasteType(s.type());
            bin.setLatitude(s.lat());
            bin.setLongitude(s.lon());
            bin.setCapacity(s.capacity());
            bin.setBaseFillRatePerHour(s.baseRate());
            bin.setCurrentFillPercentage(s.targetFill());
            bin.setLastUpdated(now);
            bin.setStatus(BinStatus.NORMAL);
            bin = binRepository.save(bin);
            readings.addAll(backfillHistory(bin, s, now, seeded));
        }
        sensorReadingRepository.saveAll(readings);

        lastTick = now;
        lastReadingAt = now;
        lastAutopilotPlan = null;
        predictionService.runPredictionsForAllBins();
        log.info("Demo data reset complete: {} bins, {} readings.", DEMO_SITES.size(), readings.size());
    }

    /** Re-generates history for the existing bins (kept for the original /api/simulation/run endpoint). */
    @Transactional
    public void generateHistoricalData() {
        LocalDateTime now = LocalDateTime.now(clock);
        sensorReadingRepository.deleteAllInBatch();
        Random seeded = new Random(42);
        List<SensorReading> readings = new ArrayList<>();
        for (Bin bin : binRepository.findAll()) {
            Site site = DEMO_SITES.stream().filter(s -> s.code().equals(bin.getBinCode())).findFirst()
                    .orElse(new Site(bin.getBinCode(), bin.getLocationName(), bin.getZone(), bin.getWasteType(),
                            bin.getLatitude(), bin.getLongitude(), bin.getCapacity(), Profile.RESIDENTIAL,
                            bin.getBaseFillRatePerHour() > 0 ? bin.getBaseFillRatePerHour() : 1.2,
                            bin.getCurrentFillPercentage()));
            bin.setCurrentFillPercentage(site.targetFill());
            readings.addAll(backfillHistory(bin, site, now, seeded));
        }
        sensorReadingRepository.saveAll(readings);
        predictionService.clearCache();
        predictionService.runPredictionsForAllBins();
    }

    /**
     * Walks backwards from "now" so the newest reading equals the site's target fill. Each step
     * subtracts an hour of usage; when the bin would drop below empty, that was a collection, and
     * before it the bin stood at a typical pickup level.
     */
    private List<SensorReading> backfillHistory(Bin bin, Site site, LocalDateTime now, Random rnd) {
        List<SensorReading> out = new ArrayList<>();
        double fill = site.targetFill();
        LocalDateTime t = now;
        LocalDateTime start = now.minusDays(historyDays);
        LocalDateTime lastCollection = null;
        while (!t.isBefore(start)) {
            SensorReading r = new SensorReading();
            r.setBin(bin);
            r.setTimestamp(t);
            r.setFillPercentage(round1(fill));
            out.add(r);

            fill -= hourlyIncrement(site.profile(), site.baseRate(), t.minusMinutes(30), rnd);
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
        v.setCurrentLatitude(config.getDepotLatitude());
        v.setCurrentLongitude(config.getDepotLongitude());
        return v;
    }

    // ------------------------------------------------------------------ usage model

    /** Fill added over one hour around {@code at}, in percentage points. */
    static double hourlyIncrement(Profile profile, double baseRate, LocalDateTime at, Random rnd) {
        return baseRate * usageMultiplier(profile, at) * (0.85 + rnd.nextDouble() * 0.3);
    }

    /** How busy a site is at a given time relative to its average (roughly averages to 1 over a week). */
    static double usageMultiplier(Profile profile, LocalDateTime at) {
        int h = at.getHour();
        boolean weekend = at.getDayOfWeek() == DayOfWeek.SATURDAY || at.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean night = h < 6;
        return switch (profile) {
            case RESIDENTIAL -> night ? 0.25 : (h >= 7 && h <= 9) || (h >= 19 && h <= 22) ? 1.8 : 0.9;
            case MARKET -> night ? 0.1 : (h >= 8 && h <= 20 ? 1.6 : 0.5) * (weekend ? 1.4 : 1.0);
            case COMMERCIAL -> weekend ? 0.3 : (h >= 9 && h <= 18 ? 2.0 : 0.25);
            case PARK -> night ? 0.1 : ((h >= 6 && h <= 9) || (h >= 16 && h <= 20) ? 1.7 : 0.8) * (weekend ? 1.6 : 0.9);
            case TRANSIT -> night ? 0.2 : (h >= 8 && h <= 10) || (h >= 17 && h <= 20) ? 2.0 : 1.0;
        };
    }

    private static Profile profileFor(Bin bin) {
        return DEMO_SITES.stream().filter(s -> s.code().equals(bin.getBinCode())).map(Site::profile)
                .findFirst().orElse(Profile.RESIDENTIAL);
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
            double rate = bin.getBaseFillRatePerHour() > 0 ? bin.getBaseFillRatePerHour() : 1.2;
            double inc = hourlyIncrement(profileFor(bin), rate, now, random) * dtHours;
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
