package com.example.smartwaste.controller;

import com.example.smartwaste.algorithm.PredictionService;
import com.example.smartwaste.dto.PredictionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {
    private final PredictionService predictionService;

    @GetMapping
    public List<PredictionResult> getPredictions() {
        return predictionService.runPredictionsForAllBins();
    }

    @PostMapping("/run")
    public List<PredictionResult> runPredictions() {
        return predictionService.runPredictionsForAllBins();
    }
}
