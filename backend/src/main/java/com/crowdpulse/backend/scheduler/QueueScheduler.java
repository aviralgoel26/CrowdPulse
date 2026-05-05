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

                boolean isAlive = Boolean.TRUE.equals(redisTemplate.hasKey("heartbeat:" + placeId + ":" + userId));
                long now = System.currentTimeMillis();

                if (isAlive && !state.equals("ACTIVE")) {
                    // Re-activate if heartbeat reappears
                    String updated = userId + ":" + gSize + ":" + jTime + ":ACTIVE";
                    redisTemplate.opsForList().remove(key, 1, entry);
                    redisTemplate.opsForList().rightPush(key, updated);
                } else if (!isAlive && state.equals("ACTIVE")) {
                    // Move to SHADOW if heartbeat missing
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

        // 3. 🚶 SERVE USERS (Based on Throughput)
        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        if (update != null && "PAUSED".equalsIgnoreCase(update.getQueueStatus())) continue;

        int throughput = (update != null && update.getThroughputPerMin() != null) ? update.getThroughputPerMin() : 18; // Default 18/min
        int peopleToServe = throughput / 6; // Convert to 10-sec window
        if (peopleToServe <= 0) peopleToServe = 1;

        while (peopleToServe > 0) {
            String entry = redisTemplate.opsForList().index(key, 0);
            if (entry == null) break;
            int gSize = Integer.parseInt(entry.split(":")[1]);

            if (gSize <= peopleToServe) {
                redisTemplate.opsForList().leftPop(key);
                peopleToServe -= gSize;
            } else {
                break;
            }
        }

        // 4. 📊 DATA GATHERING (The New Part)
        int activeUsers = 0;
        int shadowUsers = 0;
        int totalPeople = 0;

        List<String> updatedQueue = redisTemplate.opsForList().range(key, 0, -1);
        if (updatedQueue != null) {
            for (String entry : updatedQueue) {
                String[] parts = entry.split(":");
                int gSize = Integer.parseInt(parts[1]);
                String state = parts.length > 3 ? parts[3] : "ACTIVE";

                totalPeople += gSize;
                if ("ACTIVE".equals(state)) activeUsers += gSize;
                else shadowUsers += gSize;
            }
        }

        // 5. 🔥 APPLY CORRECTIONS (Scaling Factor & Offset)
        var place = placeRepository.findById(placeId).orElse(null);
        double scalingFactor = (place != null && place.getScalingFactor() != null) ? place.getScalingFactor() : 1.0;
        int adminOffset = (place != null && place.getAdminOffset() != null) ? place.getAdminOffset() : 0;

        int correctedPeople = (int) ((totalPeople * scalingFactor) + adminOffset);

        // 6. ⏱️ CALCULATE WAIT TIME
        double avgGroupSize = 1.5;
        int waitTime = (int) Math.ceil((correctedPeople * avgGroupSize) / (double)throughput);

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
        } catch (Exception e) {
            System.err.println("Failed to save metrics for place " + placeId + ": " + e.getMessage());
        }

        // 8. 📡 BROADCAST
        messagingTemplate.convertAndSend("/topic/queue/" + placeId, "update");
    }
    }
}