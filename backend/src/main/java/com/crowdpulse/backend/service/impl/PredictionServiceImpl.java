package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.service.PredictionService;
import com.crowdpulse.backend.service.QueueService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.service.CommunityService;
import com.crowdpulse.backend.model.QueueMetrics;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.dto.WaitTimeResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final QueueService queueService;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;
    private final PlaceRepository placeRepository;

    public PredictionServiceImpl(QueueService queueService,
                                 CommunityService communityService,
                                 QueueMetricsRepository queueMetricsRepository,
                                 PlaceRepository placeRepository) {
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

        // 🔥 CURRENT WAIT — synced with calculateWaitTime() for consistency with Estimated Wait card
        WaitTimeResponse liveWait = queueService.calculateWaitTime(placeId);
        int currentWait = liveWait.getWaitMinutes();

        // ══════════════════════════════════════════════════════════
        // 🔥 9-HOUR WINDOW TIMELINE
        // 3 past hours + current hour + 5 future hours
        // ══════════════════════════════════════════════════════════
        int currentHour = LocalTime.now().getHour();
        int windowStart = currentHour - 3;  // 3 hours in the past

        // Get historical QueueMetrics for today (for past hours of the forecast line)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<QueueMetrics> todayMetrics = queueMetricsRepository
                .findByPlaceIdAndTimestampAfterOrderByTimestampAsc(placeId, startOfDay);

        // Group historical metrics by hour → average wait time
        Map<Integer, Double> historicalByHour = todayMetrics.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getTimestamp().getHour(),
                        Collectors.averagingInt(QueueMetrics::getWaitTime)
                ));

        // Build the 9-hour forecast timeline
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            int hour = (windowStart + i + 24) % 24; // handle wrap-around past midnight
            String timeLabel = String.format("%02d:00", hour);

            int waitValue;

            if (hour <= currentHour && historicalByHour.containsKey(hour)) {
                // Past/current hours: use actual recorded historical data
                waitValue = (int) Math.round(historicalByHour.get(hour));
            } else if (hour == currentHour) {
                // Current hour with no historical data: use live wait
                waitValue = currentWait;
            } else {
                // Future hours: extrapolate from current wait using multipliers
                double multiplier = getHourlyMultiplier(hour);
                waitValue = (int) Math.ceil(currentWait * multiplier);
            }

            Map<String, Object> point = new HashMap<>();
            point.put("time", timeLabel);
            point.put("wait", waitValue);
            timeline.add(point);
        }

        // ══════════════════════════════════════════════════════════
        // 🔥 COMMUNITY TIMELINE (Actual line — ground truth from community)
        // ══════════════════════════════════════════════════════════
        LocalDateTime windowStartTime = LocalDate.now().atTime(Math.max(windowStart, 0), 0);
        if (windowStart < 0) {
            // If window goes before midnight, start from yesterday
            windowStartTime = LocalDate.now().minusDays(1).atTime((windowStart + 24) % 24, 0);
        }

        List<CommunityUpdate> communityUpdates =
                communityService.getUpdatesAfter(placeId, windowStartTime);

        // Group community updates by hour → calculate implied wait time
        List<Map<String, Object>> communityTimeline = new ArrayList<>();

        Map<Integer, List<CommunityUpdate>> communityByHour = communityUpdates.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getCreatedAt().getHour()
                ));

        for (Map.Entry<Integer, List<CommunityUpdate>> entry : communityByHour.entrySet()) {
            int hour = entry.getKey();
            List<CommunityUpdate> updates = entry.getValue();

            // Average the community reports for this hour
            double avgQueueLength = updates.stream()
                    .filter(u -> u.getReportedQueueLength() != null)
                    .mapToInt(CommunityUpdate::getReportedQueueLength)
                    .average()
                    .orElse(0);

            double avgThroughput = updates.stream()
                    .filter(u -> u.getThroughputPerMin() != null && u.getThroughputPerMin() > 0)
                    .mapToInt(CommunityUpdate::getThroughputPerMin)
                    .average()
                    .orElse(30.0);

            // Calculate community-implied wait time using efficiency factor
            double efficiencyFactor;
            if (avgQueueLength <= 50)        efficiencyFactor = 0.55;
            else if (avgQueueLength <= 200)  efficiencyFactor = 0.45;
            else if (avgQueueLength <= 500)  efficiencyFactor = 0.35;
            else if (avgQueueLength <= 1000) efficiencyFactor = 0.25;
            else                              efficiencyFactor = 0.15;

            double effectiveThroughput = avgThroughput * efficiencyFactor;
            int communityWait = (effectiveThroughput > 0)
                    ? (int) Math.ceil(avgQueueLength / effectiveThroughput)
                    : 0;

            Map<String, Object> point = new HashMap<>();
            point.put("time", String.format("%02d:00", hour));
            point.put("wait", communityWait);
            communityTimeline.add(point);
        }

        // Sort by time
        communityTimeline.sort(Comparator.comparing(p -> (String) p.get("time")));

        // ══════════════════════════════════════════════════════════
        // 🔥 BEST TIME / PEAK TIME / TREND / ALERT (from forecast)
        // ══════════════════════════════════════════════════════════
        Map<String, Object> best = timeline.stream()
                .min(Comparator.comparingInt(p -> (int) p.get("wait")))
                .orElse(null);

        Map<String, Object> peak = timeline.stream()
                .max(Comparator.comparingInt(p -> (int) p.get("wait")))
                .orElse(null);

        // Trend
        String trend = "STABLE";
        if (currentWait > 90)      trend = "RISING";
        else if (currentWait < 30) trend = "FALLING";

        // Recommendation
        String recommendation;
        if ("RISING".equals(trend))       recommendation = "Heavy crowd expected in upcoming hours.";
        else if ("FALLING".equals(trend)) recommendation = "Crowd expected to reduce gradually.";
        else                               recommendation = "Crowd likely to remain stable.";

        // Alert level
        String alert = "NORMAL";
        if (currentWait > 180)      alert = "EXTREME";
        else if (currentWait > 90)  alert = "HIGH";
        else if (currentWait > 45)  alert = "MODERATE";

        // ══════════════════════════════════════════════════════════
        // 🔥 BUILD RESPONSE
        // ══════════════════════════════════════════════════════════
        Map<String, Object> result = new HashMap<>();
        result.put("currentWait", currentWait);
        result.put("currentHour", String.format("%02d:00", currentHour));
        result.put("bestTime", best != null ? best.get("time") : "N/A");
        result.put("bestWait", best != null ? best.get("wait") : 0);
        result.put("peakTime", peak != null ? peak.get("time") : "N/A");
        result.put("peakWait", peak != null ? peak.get("wait") : 0);
        result.put("trend", trend);
        result.put("recommendation", recommendation);
        result.put("timeline", timeline);
        result.put("communityTimeline", communityTimeline);
        result.put("alert", alert);

        return result;
    }

    /**
     * Returns the hourly crowd multiplier based on temple operational patterns.
     */
    private double getHourlyMultiplier(int hour) {
        if (hour >= 8 && hour <= 12)  return 2.0;   // Morning rush
        if (hour >= 16 && hour <= 19) return 1.7;   // Evening rush
        if (hour >= 13 && hour <= 15) return 0.8;   // Afternoon lighter
        return 1.0;
    }
}