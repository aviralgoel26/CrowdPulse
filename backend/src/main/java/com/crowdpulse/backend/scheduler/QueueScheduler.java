package com.crowdpulse.backend.scheduler;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.crowdpulse.backend.service.CommunityService;
import com.crowdpulse.backend.model.CommunityUpdate;

import java.util.List;
import java.util.Set;

@Component
public class QueueScheduler {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityService communityService;

    public QueueScheduler(StringRedisTemplate redisTemplate,
                          SimpMessagingTemplate messagingTemplate,
                          CommunityService communityService) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.communityService = communityService;
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

            // ==============================
            // 🧹 CLEANUP EXPIRED USERS
            // ==============================
            List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

            if (queue != null) {
                for (String entry : queue) {

                    String[] parts = entry.split(":");

                    if (parts.length < 3) continue;

                    long lastSeen = Long.parseLong(parts[2]);
                    long now = System.currentTimeMillis();

                    long diffMinutes = (now - lastSeen) / (1000 * 60);

                    // Remove users inactive > 60 mins
                    if (diffMinutes > 60) {
                        redisTemplate.opsForList().remove(key, 1, entry);
                        System.out.println("🧹 Removed expired user: " + entry);
                    }
                }
            }

            // ==============================
            // 📊 CHECK QUEUE SIZE
            // ==============================
            Long size = redisTemplate.opsForList().size(key);

            if (size == null || size == 0) {
                continue;
            }

            // ==============================
            // 📊 GET PLACE ID
            // ==============================
            String[] keyParts = key.split(":");
            Long placeId = Long.parseLong(keyParts[2]);

            // ==============================
            // 📊 COMMUNITY DATA
            // ==============================
            CommunityUpdate update = communityService.getLatestUpdate(placeId);

            // 🚦 PAUSE CHECK
            if (update != null && "PAUSED".equalsIgnoreCase(update.getQueueStatus())) {
                System.out.println("⛔ Queue paused for place: " + placeId);
                continue;
            }

            // ==============================
            // ⚡ DYNAMIC THROUGHPUT
            // ==============================
            int peopleToServe;

            if (update != null && update.getThroughputPerMin() != null) {

                // Convert per minute → per 10 sec
                peopleToServe = update.getThroughputPerMin() / 6;

                if (peopleToServe <= 0) {
                    peopleToServe = 1; // safeguard
                }

            } else {
                peopleToServe = 3; // fallback
            }

            // ==============================
            // 🚶 SERVE USERS
            // ==============================
            int servedUsers = 0;
            int servedPeople = 0;

            while (peopleToServe > 0) {

                String entry = redisTemplate.opsForList().index(key, 0);

                if (entry == null) break;

                String[] entryParts = entry.split(":");

                if (entryParts.length < 3) break;

                String userId = entryParts[0];
                int groupSize = Integer.parseInt(entryParts[1]);

                if (groupSize <= peopleToServe) {

                    redisTemplate.opsForList().leftPop(key);

                    peopleToServe -= groupSize;
                    servedUsers++;
                    servedPeople += groupSize;

                    System.out.println("🚶 Served user: " + userId + " (" + groupSize + ")");

                } else {
                    break;
                }
            }

            System.out.println("📊 Total served: " + servedUsers + " users (" + servedPeople + " people)");

            // ==============================
            // 📡 WEBSOCKET BROADCAST
            // ==============================
            messagingTemplate.convertAndSend(
                    "/topic/queue/" + placeId,
                    "update"
            );

            System.out.println("🚀 Broadcast sent to /topic/queue/" + placeId);
        }
    }
}