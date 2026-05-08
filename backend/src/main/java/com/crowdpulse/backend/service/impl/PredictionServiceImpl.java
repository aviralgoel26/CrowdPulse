package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.service.PredictionService;
import com.crowdpulse.backend.service.QueueService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.service.CommunityService;
import java.time.format.DateTimeFormatter;
import com.crowdpulse.backend.model.QueueMetrics;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.repository.PlaceRepository;

import java.time.LocalTime;
import java.util.*;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final QueueService queueService;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;
    private final PlaceRepository placeRepository;

    public PredictionServiceImpl(QueueService queueService,
                                 CommunityService communityService,
                                 QueueMetricsRepository queueMetricsRepository,
                                 PlaceRepository placeRepository ) {
        this.queueService = queueService;
        this.communityService = communityService;
        this.queueMetricsRepository = queueMetricsRepository;
        this.placeRepository = placeRepository;
    }

  @Override
public Map<String, Object> getPrediction(Long placeId) {

    List<QueueMetrics> data =
            queueMetricsRepository.findTop50ByPlaceIdOrderByTimestampDesc(placeId);

    if (data.isEmpty()) {
        return Map.of("message", "Not enough data");
    }

    // 🔥 CURRENT WAIT
    int currentWait = data.get(0).getWaitTime();

    // 🔥 FUTURE HOURLY FORECAST
    List<Map<String, Object>> timeline =
            new ArrayList<>();

    LocalTime now = LocalTime.now();

    for (int i = 0; i < 8; i++) {

        LocalTime future = now.plusHours(i);

        int hour = future.getHour();

        double multiplier = 1.0;

        // 🔥 Kedarnath operational logic

        // Morning rush
        if (hour >= 8 && hour <= 12) {
            multiplier = 2.0;
        }

        // Evening rush
        else if (hour >= 16 && hour <= 19) {
            multiplier = 1.7;
        }

        // Afternoon lighter crowd
        else if (hour >= 13 && hour <= 15) {
            multiplier = 0.8;
        }

        int predictedWait =
                (int) Math.ceil(currentWait * multiplier);

        Map<String, Object> point =
                new HashMap<>();

        point.put(
                "time",
                String.format("%02d:00", hour)
        );

        point.put("wait", predictedWait);

        timeline.add(point);
    }

    // 🔥 BEST TIME
    Map<String, Object> best =
            timeline.stream()
                    .min(Comparator.comparingInt(
                            p -> (int) p.get("wait")
                    ))
                    .orElse(null);

    // 🔥 PEAK TIME
    Map<String, Object> peak =
            timeline.stream()
                    .max(Comparator.comparingInt(
                            p -> (int) p.get("wait")
                    ))
                    .orElse(null);

    // 🔥 TREND
    String trend = "STABLE";

    if (currentWait > 90) {
        trend = "RISING";
    }
    else if (currentWait < 30) {
        trend = "FALLING";
    }

    // 🔥 RECOMMENDATION
    String recommendation;

    if ("RISING".equals(trend)) {
        recommendation =
                "Heavy crowd expected in upcoming hours.";
    }
    else if ("FALLING".equals(trend)) {
        recommendation =
                "Crowd expected to reduce gradually.";
    }
    else {
        recommendation =
                "Crowd likely to remain stable.";
    }

    // 🔥 ALERT LEVEL
    String alert = "NORMAL";

    if (currentWait > 180) {
        alert = "EXTREME";
    }
    else if (currentWait > 90) {
        alert = "HIGH";
    }
    else if (currentWait > 45) {
        alert = "MODERATE";
    }

    Map<String, Object> result = new HashMap<>();
    result.put("currentWait", currentWait);
    result.put("bestTime", best != null ? best.get("time") : "N/A");
    result.put("bestWait", best != null ? best.get("wait") : 0);
    result.put("peakTime", peak != null ? peak.get("time") : "N/A");
    result.put("peakWait", peak != null ? peak.get("wait") : 0);
    result.put("trend", trend);
    result.put("recommendation", recommendation);
    result.put("timeline", timeline);
    result.put("alert", alert);

    return result;
}
}