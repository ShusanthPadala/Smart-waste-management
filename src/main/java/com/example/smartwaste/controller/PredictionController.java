package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.dto.PredictionResult;
import com.example.smartwaste.simulation.WorldLock;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {
    private final PredictionService predictionService;
    private final WorldLock worldLock;

    @GetMapping
    public List<PredictionResult> getPredictions() {
        return worldLock.call(predictionService::runPredictionsForAllBins);
    }

    @PostMapping("/run")
    public List<PredictionResult> runPredictions() {
        return getPredictions();
    }
}
