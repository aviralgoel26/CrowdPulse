package com.crowdpulse.backend.controller;

import org.springframework.web.bind.annotation.*;
import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.service.QueueService;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/crowdpulse/queue")
@CrossOrigin(origins = "http://localhost:5173")
public class QueueController {

    private final QueueService queueService;
    private final StringRedisTemplate redisTemplate;

    public QueueController(QueueService queueService, StringRedisTemplate redisTemplate) {
        this.queueService = queueService;
        this.redisTemplate = redisTemplate;
    }

    // 🎟️ JOIN QUEUE
    @PostMapping("/join/{placeId}")
    public Map<String, Object> joinQueue(@PathVariable Long placeId,
                                         @RequestParam String userId,
                                        @RequestParam(defaultValue = "1")int groupSize) {
        return queueService.joinQueue(placeId, userId, groupSize);
    }

    // 📊 GET STATUS
    @GetMapping("/status/{placeId}")
    public Map<String, Object> getStatus(@PathVariable Long placeId,
                                         @RequestParam String userId) {
        return queueService.getStatus(placeId, userId);
    }

    // ⏱️ WAIT TIME
    @GetMapping("/wait-time/{placeId}")
    public WaitTimeResponse getWaitTime(@PathVariable Long placeId) {
        return queueService.calculateWaitTime(placeId);
    }

    @PostMapping("/heartbeat/{placeId}/{userId}")
public String heartbeat(@PathVariable Long placeId,
                        @PathVariable String userId) {

    String key = "queue:place:" + placeId;

    List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

    if (queue == null) return "No queue";

    for (int i = 0; i < queue.size(); i++) {

        String entry = queue.get(i);
        String[] parts = entry.split(":");

        if (parts[0].equals(userId)) {

            int groupSize = Integer.parseInt(parts[1]);

            String updated = userId + ":" + groupSize + ":" + System.currentTimeMillis();

            redisTemplate.opsForList().set(key, i, updated);

            return "Heartbeat updated";
        }
    }

    return "User not found";
}

    
}