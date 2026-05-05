package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.service.PredictionService;
import com.crowdpulse.backend.service.QueueService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.service.CommunityService;
import java.time.format.DateTimeFormatter;
import com.crowdpulse.backend.model.QueueMetrics;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final QueueService queueService;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;

    public PredictionServiceImpl(QueueService queueService,
                                 CommunityService communityService,
                                 QueueMetricsRepository queueMetricsRepository) {
        this.queueService = queueService;
        this.communityService = communityService;
        this.queueMetricsRepository = queueMetricsRepository;
    }

   @Override
public Map<String, Object> getPrediction(Long placeId) {

    List<QueueMetrics> data =
            queueMetricsRepository.findTop50ByPlaceIdOrderByTimestampDesc(placeId);

    if (data.isEmpty()) {
        return Map.of("message", "Not enough data");
    }

    // 🔹 Sort oldest → latest (important for trend)
    Collections.reverse(data);

    List<Map<String, Object>> timeline = new ArrayList<>();

    for (QueueMetrics m : data) {
        Map<String, Object> point = new HashMap<>();

        point.put("time", m.getTimestamp().toLocalTime().toString());
        point.put("wait", m.getWaitTime());

        timeline.add(point);
    }

    // 🔥 CURRENT
    int currentWait = data.get(data.size() - 1).getWaitTime();

    // 🔥 BEST (MIN WAIT)
    QueueMetrics best = data.stream()
            .min(Comparator.comparingInt(QueueMetrics::getWaitTime))
            .orElse(data.get(0));

    // 🔥 PEAK (MAX WAIT)
    QueueMetrics peak = data.stream()
            .max(Comparator.comparingInt(QueueMetrics::getWaitTime))
            .orElse(data.get(0));

    // 🔥 TREND DETECTION (last 5 points)
    int size = data.size();
    String trend = "STABLE";

    if (size >= 5) {
        int first = data.get(size - 5).getWaitTime();
        int last = data.get(size - 1).getWaitTime();

        if (last > first + 5) trend = "RISING";
        else if (last < first - 5) trend = "FALLING";
    }

    // 🔥 RECOMMENDATION ENGINE
    String recommendation;

    if ("RISING".equals(trend)) {
        recommendation = "Crowd is increasing. Visit earlier if possible.";
    } else if ("FALLING".equals(trend)) {
        recommendation = "Crowd is decreasing. Good time to visit soon.";
    } else {
        recommendation = "Crowd is stable. You can visit anytime.";
    }

    return Map.of(
            "currentWait", currentWait,
            "bestTime", best.getTimestamp().toLocalTime().toString(),
            "bestWait", best.getWaitTime(),
            "peakTime", peak.getTimestamp().toLocalTime().toString(),
            "peakWait", peak.getWaitTime(),
            "trend", trend,
            "recommendation", recommendation,
            "timeline", timeline
    );
}
}