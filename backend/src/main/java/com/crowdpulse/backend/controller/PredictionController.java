package com.crowdpulse.backend.controller;

import com.crowdpulse.backend.service.PredictionService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crowdpulse/predict")
@CrossOrigin(origins = "http://localhost:5173")
public class PredictionController {

    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @GetMapping("/{placeId}")
    public Map<String, Object> predict(@PathVariable Long placeId) {
        return service.getPrediction(placeId);
    }
}