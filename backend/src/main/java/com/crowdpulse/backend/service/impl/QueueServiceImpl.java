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

    // ══════════════════════════════════════════════════════════════
    // 🔧 QUEUE EFFICIENCY COEFFICIENT (Dynamic, Adaptive)
    // ══════════════════════════════════════════════════════════════
    // This replaces the hardcoded 0.35 multiplier with a congestion-aware
    // damping factor. Theoretical throughput is reduced to simulate real-world
    // operational inefficiencies: crowd clustering, security pauses, darshan
    // delays, and non-linear pedestrian movement.
    // ══════════════════════════════════════════════════════════════

    private static final double EFFICIENCY_LOW_TRAFFIC    = 0.55; // 0–50 people
    private static final double EFFICIENCY_NORMAL         = 0.45; // 51–200 people
    private static final double EFFICIENCY_MODERATE       = 0.35; // 201–500 people
    private static final double EFFICIENCY_HEAVY          = 0.25; // 501–1000 people
    private static final double EFFICIENCY_EXTREME        = 0.15; // 1000+ people

    /**
     * Calculates the Queue Efficiency Coefficient based on current crowd density.
     * This accounts for real-world operational inefficiencies such as:
     * - Crowd clustering and batch movement
     * - Security checkpoints and verification pauses
     * - Darshan ritual delays
     * - Non-linear pedestrian flow
     * - Bottlenecks at entry/exit points
     */
    private double calculateEfficiencyFactor(int correctedPeople) {
        if (correctedPeople <= 50)   return EFFICIENCY_LOW_TRAFFIC;
        if (correctedPeople <= 200)  return EFFICIENCY_NORMAL;
        if (correctedPeople <= 500)  return EFFICIENCY_MODERATE;
        if (correctedPeople <= 1000) return EFFICIENCY_HEAVY;
        return EFFICIENCY_EXTREME;
    }

    // 🔧 Calculate Throughput for a Place (Community-aware)
    private double calculateThroughput(Long placeId) {
        Place place = getPlaceConfig(placeId);

        // 🔥 Community throughput takes priority (freshest ground-truth)
        CommunityUpdate communityUpdate = communityService.getLatestUpdate(placeId);
        if (communityUpdate != null && communityUpdate.getThroughputPerMin() != null
                && communityUpdate.getThroughputPerMin() > 0) {
            return communityUpdate.getThroughputPerMin();
        }

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
        // STEP 3: INDIVIDUAL POSITION & QUEUE STATUS
        // ========================================
        long position = (long) (userPositionInList + 1);

        // Determine queue status using existing precedence (Place config overridden by CommunityUpdate)
        Place place = getPlaceConfig(placeId);
        String queueStatus = (place != null && place.getQueueStatus() != null)
                ? place.getQueueStatus()
                : "ACTIVE";

        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        if (update != null && update.getQueueStatus() != null) {
            queueStatus = update.getQueueStatus();
        }

        boolean isPausedOrClosed = "PAUSED".equalsIgnoreCase(queueStatus) || "CLOSED".equalsIgnoreCase(queueStatus);
        String effectiveQueueState = isPausedOrClosed ? queueStatus.toUpperCase() : userQueueState;

        // ========================================
        // STEP 4: CALCULATE DYNAMIC THROUGHPUT & ETA
        // ========================================
        double throughput = calculateThroughput(placeId);
        Integer estimatedWaitMinutes = null;
        String estimatedDarshanTime = null;
        String statusMessage = null;

        if (isPausedOrClosed) {
            // Queue is paused or closed: do not claim progress or compute false ETA
            estimatedWaitMinutes = null;
            estimatedDarshanTime = "Queue " + queueStatus.toUpperCase();
            statusMessage = "Queue is currently " + queueStatus.toLowerCase();
        } else if (peopleAhead == 0) {
            // User is at the front of the queue
            estimatedWaitMinutes = 0;
            estimatedDarshanTime = LocalTime.now().toString();
        } else {
            // Active queue with people ahead: compute ETA from actual Redis peopleAhead
            double queueEfficiencyFactor = calculateEfficiencyFactor(peopleAhead);
            double effectiveThroughput = throughput * queueEfficiencyFactor;

            if (effectiveThroughput > 0) {
                double remainingWaitMinutes = peopleAhead / effectiveThroughput;
                int waitMinutesRounded = (int) Math.ceil(remainingWaitMinutes);

                estimatedWaitMinutes = waitMinutesRounded;
                estimatedDarshanTime = LocalTime.now().plusMinutes(waitMinutesRounded).toString();
            } else {
                estimatedWaitMinutes = null;
                estimatedDarshanTime = null;
            }
        }

        // ========================================
        // STEP 5: RETURN SUCCESS RESPONSE
        // ========================================
        Map<String, Object> response = new HashMap<>();
        response.put("position", position);
        response.put("peopleAhead", peopleAhead);
        response.put("estimatedWaitMinutes", estimatedWaitMinutes);
        response.put("estimatedDarshanTime", estimatedDarshanTime);
        response.put("groupSize", userGroupSize);
        response.put("isUserInQueue", true);
        response.put("queueState", effectiveQueueState);
        response.put("throughput", throughput);
        if (statusMessage != null) {
            response.put("message", statusMessage);
        }

        return response;
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

        // 🔹 Calculate throughput (community-aware via calculateThroughput)
        double throughput = calculateThroughput(placeId);
        double queueEfficiencyFactor = calculateEfficiencyFactor((int) Math.ceil(correctedPeople));
        double effectiveThroughput = throughput * queueEfficiencyFactor;

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
                queueStatus,
                queueEfficiencyFactor
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