package com.example.smartwaste.algorithm;

import com.example.smartwaste.config.SustainabilityConfig;
import com.example.smartwaste.dto.DailyImpact;
import com.example.smartwaste.dto.LifetimeImpact;
import com.example.smartwaste.dto.SustainabilityComparisonResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.CollectionRoute;
import com.example.smartwaste.entity.RouteStatus;
import com.example.smartwaste.entity.RouteStop;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.CollectionRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * Measures the environmental case for data-driven collection: what a fixed "visit every bin"
 * schedule would cost right now versus an optimised route through only the bins that need it,
 * plus running totals from routes that actually completed.
 */
@Service
@RequiredArgsConstructor
public class SustainabilityService {

    private final BinRepository binRepository;
    private final CollectionRouteRepository routeRepository;
    private final RouteOptimizationService routeOptimizationService;
    private final SustainabilityConfig config;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SustainabilityComparisonResult compareStrategies() {
        List<Bin> allBins = binRepository.findAll();
        List<Bin> needed = allBins.stream()
                .filter(b -> RouteOptimizationService.NEEDS_PICKUP.contains(b.getStatus()))
                .toList();

        SustainabilityComparisonResult result = new SustainabilityComparisonResult();
        result.setBaseline(metrics(routeOptimizationService.previewTour(allBins), allBins));
        result.setOptimized(metrics(routeOptimizationService.previewTour(needed), needed));
        result.setBinsSkipped(allBins.size() - needed.size());

        SustainabilityComparisonResult.Metrics base = result.getBaseline();
        SustainabilityComparisonResult.Metrics opt = result.getOptimized();
        if (base.getTotalDistanceKm() > 0) {
            result.setDistanceSavedPct(pct(base.getTotalDistanceKm(), opt.getTotalDistanceKm()));
            result.setFuelSavedPct(pct(base.getEstimatedFuelLitres(), opt.getEstimatedFuelLitres()));
            result.setCo2SavedPct(pct(base.getEstimatedCo2Kg(), opt.getEstimatedCo2Kg()));
        }
        // Annualise only a real cycle. With nothing to collect the saving is 100% but momentary,
        // and multiplying it by 365 would overstate the impact.
        double annual = needed.isEmpty() ? 0 : Math.max(0, base.getEstimatedCo2Kg() - opt.getEstimatedCo2Kg()) * 365;
        result.setAnnualCo2SavedKg(annual);
        result.setTreesEquivalent(annual / config.getCo2PerTreeKgPerYear());
        return result;
    }

    @Transactional(readOnly = true)
    public LifetimeImpact lifetimeImpact() {
        List<CollectionRoute> done = routeRepository.findByStatusOrderByCompletedAtAsc(RouteStatus.COMPLETED);
        int bins = 0;
        double km = 0, fuel = 0, co2 = 0, fillSum = 0, litres = 0;
        for (CollectionRoute r : done) {
            km += r.getTotalDistanceKm();
            fuel += r.getEstimatedFuelLitres();
            co2 += r.getEstimatedCo2Kg();
            for (RouteStop s : r.getStops()) {
                if (!s.isCollected()) continue;
                bins++;
                fillSum += s.getFillAtPlanning();
                litres += s.getLoadLitres();
            }
        }
        return new LifetimeImpact(done.size(), bins, km, fuel, co2, bins > 0 ? fillSum / bins : 0, litres);
    }

    /** Completed-route totals per simulated day for the last {@code days} days (oldest first, gaps filled). */
    @Transactional(readOnly = true)
    public List<DailyImpact> dailyImpact(int days) {
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, double[]> byDay = new TreeMap<>();
        for (int i = days - 1; i >= 0; i--) byDay.put(today.minusDays(i), new double[4]);

        for (CollectionRoute r : routeRepository.findByStatusOrderByCompletedAtAsc(RouteStatus.COMPLETED)) {
            if (r.getCompletedAt() == null) continue;
            double[] acc = byDay.get(r.getCompletedAt().toLocalDate());
            if (acc == null) continue;
            acc[0] += 1;
            acc[1] += r.getStops().stream().filter(RouteStop::isCollected).count();
            acc[2] += r.getTotalDistanceKm();
            acc[3] += r.getEstimatedCo2Kg();
        }
        List<DailyImpact> out = new ArrayList<>();
        byDay.forEach((d, a) -> out.add(new DailyImpact(d, (int) a[0], (int) a[1], a[2], a[3])));
        return out;
    }

    private SustainabilityComparisonResult.Metrics metrics(RouteOptimizationService.TourPreview tour, List<Bin> bins) {
        SustainabilityComparisonResult.Metrics m = new SustainabilityComparisonResult.Metrics();
        m.setTotalDistanceKm(tour.distanceKm());
        m.setEstimatedFuelLitres(tour.fuelLitres());
        m.setEstimatedCo2Kg(tour.co2Kg());
        m.setBinsCollected(bins.size());
        m.setTotalTrips(bins.isEmpty() ? 0 : 1);
        m.setAverageFillAtPickup(bins.stream().mapToDouble(Bin::getCurrentFillPercentage).average().orElse(0));
        return m;
    }

    private static double pct(double baseline, double optimised) {
        return (baseline - optimised) / baseline * 100.0;
    }
}
