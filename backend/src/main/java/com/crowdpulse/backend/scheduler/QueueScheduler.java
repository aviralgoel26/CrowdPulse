package com.crowdpulse.backend.scheduler;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class QueueScheduler {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate; // 1. Added template

    // 2. Updated constructor for Injection
    public QueueScheduler(StringRedisTemplate redisTemplate, 
                          SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 10000)
    public void processQueue() {

        System.out.println("⏱️ Scheduler running...");

        Set<String> keys = redisTemplate.keys("queue:place:*");

        if (keys == null || keys.isEmpty()) {
            System.out.println("No queues found");
            return;
        }

        for (String key : keys) {
            Long size = redisTemplate.opsForList().size(key);

            if (size != null && size > 0) {
               int peopleToServe = 3; // temporary

StringBuilder servedLog = new StringBuilder();

while (peopleToServe > 0) {

    String entry = redisTemplate.opsForList().index(key, 0);

    if (entry == null) break;

    String[] parts = entry.split(":");
    int groupSize = Integer.parseInt(parts[1]);

    if (groupSize <= peopleToServe) {

        redisTemplate.opsForList().leftPop(key);

        servedLog.append(entry).append(" ");

        peopleToServe -= groupSize;

    } else {
        break;
    }
}
                
                System.out.println("🚶 Served user: " + servedLog + " from " + key);

                // 3. Extract placeId from the key (e.g., "queue:place:123" -> "123")
                String[] parts = key.split(":");
                if (parts.length >= 3) {
                    String placeId = parts[2];

                    // 4. Broadcast the update to the specific topic
                    messagingTemplate.convertAndSend(
                        "/topic/queue/" + placeId,
                        "update"
                    );
                    System.out.println("🚀 Broadcast sent to /topic/queue/" + placeId);
                }
            }
        }
    }
}