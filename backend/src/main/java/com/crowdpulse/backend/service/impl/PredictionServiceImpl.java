package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.service.PredictionService;
import com.crowdpulse.backend.service.QueueService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.service.CommunityService;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final QueueService queueService;
    private final CommunityService communityService;

    public PredictionServiceImpl(QueueService queueService,
                                 CommunityService communityService) {
        this.queueService = queueService;
        this.communityService = communityService;
    }

    @Override
    public Map<String, Object> getPrediction(Long placeId) {

        // Current wait
        int currentWait = queueService.calculateWaitTime(placeId).getWaitMinutes();

        CommunityUpdate update = communityService.getLatestUpdate(placeId);

        int throughput = (update != null && update.getThroughputPerMin() != null)
                ? update.getThroughputPerMin()
                : 20;

        // 🔥 Simulate next 3 hours
        List<Map<String, Object>> timeline = new ArrayList<>();

        int people = queueService.calculateWaitTime(placeId).getPeopleAhead();

        for (int i = 1; i <= 6; i++) { // every 30 mins

            int futurePeople = Math.max(0, people - (throughput * i * 5));

            int futureWait = (int) Math.ceil((futurePeople * 1.5) / throughput);

            Map<String, Object> point = new HashMap<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            point.put("time", LocalTime.now().plusMinutes(i * 30).format(formatter));
            point.put("wait", futureWait);

            timeline.add(point);
        }

        // 🔥 Find best time
        Map<String, Object> best = timeline.stream()
                .min(Comparator.comparingInt(p -> (int) p.get("wait")))
                .orElse(null);

        return Map.of(
                "currentWait", currentWait,
                "bestTime", best != null ? best.get("time") : "N/A",
                "bestWait", best != null ? best.get("wait") : currentWait,
                "timeline", timeline
        );
    }
}