package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.dto.QueueStatusResponse;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.model.QueueMetrics;
import com.crowdpulse.backend.service.QueueService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.service.CommunityService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueueServiceImpl implements QueueService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;

    public QueueServiceImpl(PlaceRepository placeRepository,
                            StringRedisTemplate redisTemplate,
                            CommunityService communityService,
                            QueueMetricsRepository queueMetricsRepository) {
        this.placeRepository = placeRepository;
        this.redisTemplate = redisTemplate;
        this.communityService = communityService;
        this.queueMetricsRepository = queueMetricsRepository;
    }

    // 🔑 Redis Queue Key
    private String getQueueKey(Long placeId) {
        return "queue:place:" + placeId;
    }

    // 🔧 Utility: Parse queue entry
    private String[] parseQueueEntry(String entry) {
        return entry.split(":");
    }

    // 🔧 Get Place Configuration
    private Place getPlaceConfig(Long placeId) {
        return placeRepository.findById(placeId).orElse(null);
    }

    // 🔧 Calculate Throughput for a Place
    private double calculateThroughput(Long placeId) {
        Place place = getPlaceConfig(placeId);
        if (place == null || place.getBaseThroughput() == null) {
            return 30.0; // Default throughput
        }

        double throughput = place.getBaseThroughput();
        LocalTime now = LocalTime.now();

        // Apply peak time multiplier
        if (place.getPeakStart() != null && place.getPeakEnd() != null) {
            LocalTime peakStart = LocalTime.parse(place.getPeakStart());
            LocalTime peakEnd = LocalTime.parse(place.getPeakEnd());

            if (now.isAfter(peakStart) && now.isBefore(peakEnd)) {
                throughput = throughput / place.getPeakMultiplier();
            }
        }

        // Apply season multiplier
        if (place.getSeasonMultiplier() != null) {
            throughput = throughput / place.getSeasonMultiplier();
        }

        return throughput;
    }

    // ==============================
    // 🎟️ JOIN QUEUE
    // ==============================
    @Override
    public Map<String, Object> joinQueue(Long placeId, String userId, int groupSize) {

        Place place = getPlaceConfig(placeId);

        if (place != null) {
            String status = place.getQueueStatus();

            if ("CLOSED".equals(status)) {
                System.out.println("ℹ️ Queue CLOSED for place " + placeId);
                return Map.of(
                        "success", false,
                        "message", "Queue is currently closed"
                );
            }

            if ("PAUSED".equals(status)) {
                System.out.println("ℹ️ Queue PAUSED for place " + placeId);
                return Map.of(
                        "success", false,
                        "message", "Queue temporarily paused"
                );
            }
        }

        String key = getQueueKey(placeId);

        // Prevent duplicate
        List<String> existing = redisTemplate.opsForList().range(key, 0, -1);
        if (existing != null && existing.stream().anyMatch(e -> e.startsWith(userId + ":"))) {
            System.out.println("⚠️ User " + userId + " already in queue for place " + placeId);
            return Map.of(
                    "success", false,
                    "message", "Already in queue"
            );
        }

        long now = System.currentTimeMillis();
        String value = userId + ":" + groupSize + ":" + now + ":ACTIVE";

        redisTemplate.opsForList().rightPush(key, value);
        Long position = redisTemplate.opsForList().size(key);

        // ✅ CRITICAL FIX: Create heartbeat IMMEDIATELY to prevent scheduler from marking as SHADOW
        String heartbeatKey = "heartbeat:" + placeId + ":" + userId;
        redisTemplate.opsForValue().set(
                heartbeatKey,
                String.valueOf(now),
                java.time.Duration.ofSeconds(60)  // ✅ INCREASED: Match scheduler grace period
        );

        System.out.println("✅ User " + userId + " joined queue at position " + position + " for place " + placeId);

        return Map.of(
                "success", true,
                "position", position,
                "groupSize", groupSize,
                "message", "Joined queue successfully"
        );
    }
    // ==============================
    // 📊 GET STATUS - FIXED LOGIC
    // ==============================
    @Override
    public Map<String, Object> getStatus(Long placeId, String userId) {

        String key = getQueueKey(placeId);
        List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

        // Validate queue exists
        if (queue == null || queue.isEmpty()) {
            return Map.of(
                    "isUserInQueue", false,
                    "message", "Queue is empty"
            );
        }

        // ========================================
        // STEP 1: FIND USER IN QUEUE (WITH ERROR HANDLING)
        // ========================================
        int userPositionInList = -1;
        long userJoinTime = 0;
        int userGroupSize = 0;
        String userQueueState = "ACTIVE";

        try {
            for (int i = 0; i < queue.size(); i++) {
                String entry = queue.get(i);
                String[] parts = parseQueueEntry(entry);

                if (parts.length < 3) continue;

                String queuedUser = parts[0];

                if (queuedUser.equals(userId)) {
                    try {
                        userPositionInList = i;
                        userGroupSize = Integer.parseInt(parts[1]);
                        userJoinTime = Long.parseLong(parts[2]);
                        userQueueState = parts.length > 3 ? parts[3] : "ACTIVE";
                        break;
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid queue entry format for user " + userId + ": " + entry);
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching queue for user " + userId + ": " + e.getMessage());
            return Map.of(
                    "isUserInQueue", false,
                    "message", "Error retrieving queue status"
            );
        }

        // User not in queue
        if (userPositionInList == -1) {
            return Map.of(
                    "isUserInQueue", false,
                    "message", "User not in queue"
            );
        }

        // ========================================
        // STEP 2: COUNT PEOPLE AHEAD (WITH ERROR HANDLING)
        // ========================================
        int peopleAhead = 0;
        try {
            for (int i = 0; i < userPositionInList; i++) {  // Only count before user
                String entry = queue.get(i);
                String[] parts = parseQueueEntry(entry);

                if (parts.length < 2) continue;

                try {
                    int groupSize = Integer.parseInt(parts[1]);
                    peopleAhead += groupSize;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid group size in queue entry: " + entry);
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("Error counting people ahead: " + e.getMessage());
            return Map.of(
                    "isUserInQueue", true,  // ← Still confirm user is in queue
                    "position", (long)(userPositionInList + 1),
                    "peopleAhead", 0,
                    "estimatedWaitMinutes", 0,
                    "estimatedDarshanTime", LocalTime.now().toString(),
                    "groupSize", userGroupSize,
                    "queueState", userQueueState,
                    "throughput", 30.0,
                    "message", "Warning: Error in calculation, showing defaults"
            );
        }

        // ========================================
        // STEP 3: APPLY HEURISTIC CORRECTIONS
        // ========================================
        Place place = getPlaceConfig(placeId);
        double scalingFactor = (place != null && place.getScalingFactor() != null)
                ? place.getScalingFactor()
                : 1.0;

        int adminOffset = (place != null && place.getAdminOffset() != null)
                ? place.getAdminOffset()
                : 0;

        // Get community estimate
        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        int communityPeople = (update != null && update.getReportedQueueLength() != null)
                ? update.getReportedQueueLength()
                : 0;

        // Use MAX of redis people + community estimate
        int basePeople = Math.max(peopleAhead, communityPeople);

        // Apply scaling and offset
        double correctedPeople = (basePeople * scalingFactor) + adminOffset;
        int virtualPeopleAhead = Math.max((int) Math.ceil(correctedPeople), 0);

        // Position is always one more than people ahead
        long position = virtualPeopleAhead + 1;

        // ========================================
        // STEP 4: CALCULATE DYNAMIC THROUGHPUT & ELAPSED TIME CORRECTION
        // ========================================
        double throughput = calculateThroughput(placeId);
        double effectiveThroughput = throughput * 0.35; // realistic simulation

        long now = System.currentTimeMillis();
        double elapsedMinutes = (now - userJoinTime) / (1000.0 * 60.0);

        // People who have already been served (moved forward)
        int processedPeople = (int) Math.floor(elapsedMinutes * effectiveThroughput);

        // Live people ahead after accounting for processed
        int livePeopleAhead = Math.max(virtualPeopleAhead - processedPeople, 0);

        // ========================================
        // STEP 5: CALCULATE ETA
        // ========================================
        // livePeopleAhead is individuals, so divide by effective individuals/minute
        double remainingWaitMinutes = (livePeopleAhead > 0 && effectiveThroughput > 0) 
                ? (livePeopleAhead / effectiveThroughput) 
                : 0;
        int waitMinutesRounded = (int) Math.ceil(remainingWaitMinutes);

        LocalTime eta = LocalTime.now().plusMinutes(waitMinutesRounded);

        // ========================================
        // STEP 6: RETURN SUCCESS RESPONSE
        // ========================================
        QueueStatusResponse response = new QueueStatusResponse(
                position,
                livePeopleAhead,
                waitMinutesRounded,
                eta.toString(),
                userGroupSize,
                userQueueState,
                throughput
        );

        return Map.of(
                "position", position,
                "peopleAhead", livePeopleAhead,
                "estimatedWaitMinutes", waitMinutesRounded,
                "estimatedDarshanTime", eta.toString(),
                "groupSize", userGroupSize,
                "isUserInQueue", true,
                "queueState", userQueueState,
                "throughput", throughput
        );
    }
    // ==============================
    // ⏱️ WAIT TIME CALCULATION (Global Queue View)
    // ==============================
    @Override
    public WaitTimeResponse calculateWaitTime(Long placeId) {

        String key = getQueueKey(placeId);

        // 🔹 Get place config
        Place place = getPlaceConfig(placeId);
        String queueStatus = (place != null) ? place.getQueueStatus() : "ACTIVE";

        // 🔹 Check community status override
        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        if (update != null && update.getQueueStatus() != null) {
            queueStatus = update.getQueueStatus();
        }

        // 🔹 Get Redis queue data
        List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

        int redisPeople = 0;
        if (queue != null) {
            for (String entry : queue) {
                String[] parts = parseQueueEntry(entry);
                if (parts.length < 2) continue;
                int groupSize = Integer.parseInt(parts[1]);
                redisPeople += groupSize;
            }
        }

        // 🔹 Get community data
        int communityPeople = (update != null && update.getReportedQueueLength() != null)
                ? update.getReportedQueueLength()
                : 0;

        // 🔹 Use MAX of both sources (hybrid estimate)
        int basePeople = Math.max(redisPeople, communityPeople);

        double scalingFactor = (place != null && place.getScalingFactor() != null)
                ? place.getScalingFactor()
                : 1.0;

        int adminOffset = (place != null && place.getAdminOffset() != null)
                ? place.getAdminOffset()
                : 0;

        // 🔥 Apply heuristic corrections
        double correctedPeople = (basePeople * scalingFactor) + adminOffset;

        // 🔹 Calculate throughput
        double throughput = calculateThroughput(placeId);
        double effectiveThroughput = throughput * 0.35; // realistic simulation

        // 🔹 Calculate wait time
        double avgGroupSize = 1.5;
        double waitTime = (correctedPeople > 0 && effectiveThroughput > 0) 
                ? (correctedPeople / effectiveThroughput) 
                : 0;

        return new WaitTimeResponse(
                (int) Math.ceil(waitTime),
                (int) Math.ceil(correctedPeople),
                avgGroupSize,
                throughput,
                queueStatus
        );
    }

    // ==============================
    // 🚪 LEAVE QUEUE (NEW - CRITICAL FIX)
    // ==============================
    @Override
    public Map<String, Object> leaveQueue(Long placeId, String userId) {
        try {
            String key = getQueueKey(placeId);

            // Find and remove user from queue
            List<String> queue = redisTemplate.opsForList().range(key, 0, -1);
            if (queue != null) {
                for (String entry : queue) {
                    String[] parts = parseQueueEntry(entry);
                    if (parts.length > 0 && parts[0].equals(userId)) {
                        redisTemplate.opsForList().remove(key, 1, entry);
                        break;
                    }
                }
            }

            // ✅ CRITICAL: Remove heartbeat to ensure cleanup
            String heartbeatKey = "heartbeat:" + placeId + ":" + userId;
            redisTemplate.delete(heartbeatKey);

            System.out.println("✅ User " + userId + " left queue for place " + placeId);

            return Map.of(
                    "success", true,
                    "message", "Successfully left queue"
            );
        } catch (Exception e) {
            System.err.println("❌ Error leaving queue for user " + userId + ": " + e.getMessage());
            return Map.of(
                    "success", false,
                    "message", "Error leaving queue: " + e.getMessage()
            );
        }
    }

    // ==============================
    // 📈 TIME SERIES (Historical Hourly Data)
    // ==============================
    @Override
    public List<Map<String, Object>> getTimeSeries(Long placeId) {
        // Fetch all metrics from today (start of day)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<QueueMetrics> metrics = queueMetricsRepository
                .findByPlaceIdAndTimestampAfterOrderByTimestampAsc(placeId, startOfDay);

        if (metrics.isEmpty()) {
            return Collections.emptyList();
        }

        // Aggregate by hour: compute average waitTime per hour
        Map<Integer, List<QueueMetrics>> byHour = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getTimestamp().getHour()));

        List<Map<String, Object>> timeSeries = new ArrayList<>();
        for (Map.Entry<Integer, List<QueueMetrics>> entry : new TreeMap<>(byHour).entrySet()) {
            int hour = entry.getKey();
            List<QueueMetrics> hourMetrics = entry.getValue();

            double avgWait = hourMetrics.stream()
                    .mapToInt(QueueMetrics::getWaitTime)
                    .average()
                    .orElse(0);

            double avgPeople = hourMetrics.stream()
                    .mapToInt(QueueMetrics::getCorrectedPeople)
                    .average()
                    .orElse(0);

            Map<String, Object> point = new HashMap<>();
            point.put("time", String.format("%02d:00", hour));
            point.put("wait", (int) Math.round(avgWait));
            point.put("people", (int) Math.round(avgPeople));
            point.put("samples", hourMetrics.size());
            timeSeries.add(point);
        }

        return timeSeries;
    }
}