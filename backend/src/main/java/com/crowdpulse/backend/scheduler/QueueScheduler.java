package com.crowdpulse.backend.scheduler;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.crowdpulse.backend.service.CommunityService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.repository.QueueMetricsRepository;

import java.util.List;
import java.util.Set;

@Component
public class QueueScheduler {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;
private final PlaceRepository placeRepository;

    public QueueScheduler(StringRedisTemplate redisTemplate,
                          SimpMessagingTemplate messagingTemplate,
                          CommunityService communityService,
                          QueueMetricsRepository queueMetricsRepository,
                          PlaceRepository placeRepository) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.communityService = communityService;
        this.queueMetricsRepository = queueMetricsRepository;
        this.placeRepository = placeRepository;
    }

   @Scheduled(fixedRate = 10000) // runs every 10 sec
public void processQueue() {
    System.out.println("⏱️ Scheduler running...");
    Set<String> keys = redisTemplate.keys("queue:place:*");

    if (keys == null || keys.isEmpty()) {
        System.out.println("No queues found");
        return;
    }

    for (String key : keys) {
        // 1. Get Place ID from Key
        String[] keyParts = key.split(":");
        Long placeId = Long.parseLong(keyParts[2]);

        // 2. 🧹 CLEANUP & STATE MANAGEMENT (Heartbeat Logic)
        List<String> currentQueue = redisTemplate.opsForList().range(key, 0, -1);
        if (currentQueue != null) {
            for (String entry : currentQueue) {
                String[] parts = entry.split(":");
                if (parts.length < 3) continue;

                String userId = parts[0];
                int gSize = Integer.parseInt(parts[1]);
                long jTime = Long.parseLong(parts[2]);
                String state = parts.length > 3 ? parts[3] : "ACTIVE";

                long now = System.currentTimeMillis();
                long userAge = now - jTime;
                
                // ✅ CRITICAL FIX: Grace period for new users (first 90 seconds)
                // Don't mark as SHADOW during first 90 seconds of joining
                // This gives users time to load the app and establish heartbeat
                if (userAge < 90000) {
                    continue; // Skip state management for new users
                }

                boolean isAlive = Boolean.TRUE.equals(redisTemplate.hasKey("heartbeat:" + placeId + ":" + userId));

                if (isAlive && !state.equals("ACTIVE")) {
                    // Re-activate if heartbeat reappears
                    String updated = userId + ":" + gSize + ":" + jTime + ":ACTIVE";
                    redisTemplate.opsForList().remove(key, 1, entry);
                    redisTemplate.opsForList().rightPush(key, updated);
                } else if (!isAlive && state.equals("ACTIVE")) {
                    // Move to SHADOW if heartbeat missing (after grace period)
                    String updated = userId + ":" + gSize + ":" + jTime + ":SHADOW";
                    redisTemplate.opsForList().remove(key, 1, entry);
                    redisTemplate.opsForList().rightPush(key, updated);
                } else if (!isAlive && state.equals("SHADOW")) {
                    // Expire if shadow too long
                    if ((now - jTime) > (30 * 60 * 1000)) {
                        redisTemplate.opsForList().remove(key, 1, entry);
                    }
                }
            }
        }

        // 3. 🚶 SERVE USERS (VIRTUAL PROGRESSION)
        // Throughput-driven movement is simulated natively in QueueServiceImpl via elapsed time.
        // We do NOT destructively leftPop real active users here, as it kicks them out of their UI.
        // Users are removed smoothly via Heartbeat expiration once they close the app post-darshan.

        // 4. 📊 DATA GATHERING (The New Part)
        int activeUsers = 0;
        int shadowUsers = 0;
        int redisPeople = 0;

        List<String> updatedQueue = redisTemplate.opsForList().range(key, 0, -1);
        if (updatedQueue != null) {
            for (String entry : updatedQueue) {
                String[] parts = entry.split(":");
                if (parts.length < 2) continue;
                int gSize = Integer.parseInt(parts[1]);
                String state = parts.length > 3 ? parts[3] : "ACTIVE";

                redisPeople += gSize;
                if ("ACTIVE".equals(state)) activeUsers += gSize;
                else shadowUsers += gSize;
            }
        }

        // 5. 🔥 APPLY CORRECTIONS (Scaling Factor & Offset & Community)
        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        int communityPeople = (update != null && update.getReportedQueueLength() != null)
                ? update.getReportedQueueLength()
                : 0;
                
        // Use MAX of redis people + community estimate (Hybrid approach matching QueueService)
        int basePeople = Math.max(redisPeople, communityPeople);
        int totalPeople = basePeople;  // ✅ FIX: Define totalPeople before using

        var place = placeRepository.findById(placeId).orElse(null);
        double scalingFactor = (place != null && place.getScalingFactor() != null) ? place.getScalingFactor() : 1.0;
        int adminOffset = (place != null && place.getAdminOffset() != null) ? place.getAdminOffset() : 0;

        int correctedPeople = (int) ((basePeople * scalingFactor) + adminOffset);

        // 6. ⏱️ CALCULATE WAIT TIME
        double throughput = (update != null && update.getThroughputPerMin() != null) 
                ? update.getThroughputPerMin() 
                : (place != null && place.getBaseThroughput() != null ? place.getBaseThroughput() : 30.0);
                
        double effectiveThroughput = throughput * 0.35; // realistic simulation
        double avgGroupSize = 1.5;
        int waitTime = (correctedPeople > 0 && effectiveThroughput > 0) 
                ? (int) Math.ceil(correctedPeople / effectiveThroughput) 
                : 0;

        // 7. 💾 SAVE METRICS TO DB
        try {
            com.crowdpulse.backend.model.QueueMetrics metrics = new com.crowdpulse.backend.model.QueueMetrics();
            metrics.setPlaceId(placeId);
            metrics.setTimestamp(java.time.LocalDateTime.now());
            metrics.setActiveUsers(activeUsers);
            metrics.setShadowUsers(shadowUsers);
            metrics.setTotalPeople(totalPeople);
            metrics.setCorrectedPeople(correctedPeople);
            metrics.setThroughput(throughput);
            metrics.setWaitTime(waitTime);

            queueMetricsRepository.save(metrics);
            System.out.println("✅ Metrics saved - Place:" + placeId + " Active:" + activeUsers + " Shadow:" + shadowUsers + " Total:" + totalPeople + " Wait:" + waitTime + "min");
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Failed to save metrics for place " + placeId + ": " + e.getMessage());
            e.printStackTrace();  // Log full stack trace for debugging
        }

        // 8. 📡 BROADCAST UPDATE TO WEBSOCKET SUBSCRIBERS
        try {
            String queueStatus = (update != null && update.getQueueStatus() != null)
                    ? update.getQueueStatus()
                    : "ACTIVE";

            String broadcastMessage = String.format(
                "{\"placeId\":%d,\"timestamp\":%d,\"totalWaitMinutes\":%d,\"totalPeople\":%d,\"queueStatus\":\"%s\",\"throughput\":%.2f}",
                placeId, System.currentTimeMillis(), waitTime, correctedPeople, queueStatus, (double)throughput
            );

            messagingTemplate.convertAndSend("/topic/queue/" + placeId, broadcastMessage);
            System.out.println("📡 Broadcast sent to /topic/queue/" + placeId);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to broadcast queue update for place " + placeId + ": " + e.getMessage());
        }
    }
}
}