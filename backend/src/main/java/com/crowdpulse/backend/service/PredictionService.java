package com.crowdpulse.backend.service;

import java.util.Map;

public interface PredictionService {

    Map<String, Object> getPrediction(Long placeId);
}