package com.crowdpulse.backend.service;

import com.crowdpulse.backend.dto.RecommendationResponse;
import com.crowdpulse.backend.dto.VibeResponse;

public interface MetricsService {

    void updateHeartbeat(Long placeId, String userId, long timestamp);

    int getActiveUsers(Long placeId);

    int getVibeScore(Long placeId);

   
    VibeResponse getVibeDetails(Long placeId);

    String getTrend(Long placeId);
    RecommendationResponse getRecommendation(Long placeId);

}