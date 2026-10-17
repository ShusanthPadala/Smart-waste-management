package com.example.smartwaste.service;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.dto.BinRequest;
import com.example.smartwaste.dto.BinView;
import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.entity.Bin;
import com.example.smartwaste.entity.RouteStatus;
import com.example.smartwaste.entity.SensorReading;
import com.example.smartwaste.entity.SiteProfile;
import com.example.smartwaste.entity.WasteType;
import com.example.smartwaste.repository.BinRepository;
import com.example.smartwaste.repository.RouteStopRepository;
import com.example.smartwaste.repository.SensorReadingRepository;
import com.example.smartwaste.web.ConflictException;
import com.example.smartwaste.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BinService {

    private final BinRepository binRepository;
    private final SensorReadingRepository readingRepository;
    private final RouteStopRepository routeStopRepository;
    private final PredictionService predictionService;
    private final AlertService alertService;
    private final Clock clock;

    @Transactional
    public List<BinView> list() {
        Set<Long> onRoute = new HashSet<>(routeStopRepository.findAssignedBinIds(List.of(RouteStatus.PLANNED, RouteStatus.IN_PROGRESS)));
        return binRepository.findAll().stream()
                .sorted(Comparator.comparing(Bin::getBinCode))
                .map(b -> toView(b, predictionService.latestFor(b), onRoute.contains(b.getId())))
                .toList();
    }

    @Transactional
    public BinView get(Long id) {
        Bin bin = find(id);
        Set<Long> onRoute = new HashSet<>(routeStopRepository.findAssignedBinIds(List.of(RouteStatus.PLANNED, RouteStatus.IN_PROGRESS)));
        return toView(bin, predictionService.latestFor(bin), onRoute.contains(id));
    }

    @Transactional
    public BinView create(BinRequest req) {
        ensureCodeFree(req.binCode(), null);
        Bin bin = new Bin();
        apply(bin, req);
        bin = binRepository.save(bin);
        recordReading(bin);
        return toView(bin, predictionService.predictAndClassifyBin(bin), false);
    }

    @Transactional
    public BinView update(Long id, BinRequest req) {
        Bin bin = find(id);
        ensureCodeFree(req.binCode(), id);
        double previousFill = bin.getCurrentFillPercentage();
        apply(bin, req);
        binRepository.save(bin);
        if (Math.abs(previousFill - bin.getCurrentFillPercentage()) > 0.01) recordReading(bin);
        return get(predictionService.predictAndClassifyBin(bin).getBinId());
    }

    @Transactional
    public void delete(Long id) {
        if (!binRepository.existsById(id)) throw new NotFoundException("Bin " + id + " not found");
        readingRepository.deleteByBinId(id);
        routeStopRepository.setBinToNull(id);
        alertService.resolveBinAlerts(id);
        binRepository.deleteById(id);
        predictionService.evict(id);
    }

    @Transactional(readOnly = true)
    public List<SensorReading> history(Long id) {
        find(id);
        List<SensorReading> desc = readingRepository.findByBinIdOrderByTimestampDesc(id);
        return desc.stream().limit(50).sorted(Comparator.comparing(SensorReading::getTimestamp)).toList();
    }

    private void apply(Bin bin, BinRequest req) {
        bin.setBinCode(req.binCode().trim().toUpperCase());
        bin.setLocationName(req.locationName().trim());
        bin.setZone(req.zone() == null || req.zone().isBlank() ? "Unzoned" : req.zone().trim());
        bin.setWasteType(req.wasteType() != null ? req.wasteType() : WasteType.GENERAL);
        SiteProfile profile = req.siteProfile() != null ? req.siteProfile()
                : bin.getSiteProfile() != null ? bin.getSiteProfile() : SiteProfile.RESIDENTIAL;
        // A new or changed usage pattern brings that pattern's typical fill rate with it.
        if (profile != bin.getSiteProfile() || bin.getBaseFillRatePerHour() <= 0) {
            bin.setBaseFillRatePerHour(profile.defaultRatePerHour());
        }
        bin.setSiteProfile(profile);
        bin.setLatitude(req.latitude());
        bin.setLongitude(req.longitude());
        bin.setCapacity(req.capacity());
        bin.setCurrentFillPercentage(req.currentFillPercentage());
        bin.setLastUpdated(LocalDateTime.now(clock));
    }

    private void recordReading(Bin bin) {
        SensorReading r = new SensorReading();
        r.setBin(bin);
        r.setTimestamp(LocalDateTime.now(clock));
        r.setFillPercentage(bin.getCurrentFillPercentage());
        readingRepository.save(r);
    }

    private void ensureCodeFree(String code, Long selfId) {
        binRepository.findByBinCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) throw new ConflictException("Bin code " + code + " is already in use");
        });
    }

    private Bin find(Long id) {
        return binRepository.findById(id).orElseThrow(() -> new NotFoundException("Bin " + id + " not found"));
    }

    static BinView toView(Bin b, PredictionResult p, boolean onRoute) {
        return new BinView(b.getId(), b.getBinCode(), b.getLocationName(), b.getZone(), b.getWasteType(), b.getSiteProfile(),
                b.getLatitude(), b.getLongitude(), b.getCapacity(), b.getCurrentFillPercentage(), b.getStatus(),
                b.getLastUpdated(), b.getLastCollectedAt(),
                p.getPredictedFillPercentage(), p.getEstimatedHoursToFull(), p.getPredictedOverflowTime(),
                p.getConfidence(), p.getFillRatePerHour(), onRoute);
    }
}
