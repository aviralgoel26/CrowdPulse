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

    // 🧹 HEARTBEAT
    @PostMapping("/heartbeat")
public String heartbeat(@RequestParam Long placeId,
                        @RequestParam String userId) {

    String key = "heartbeat:" + placeId + ":" + userId;

    redisTemplate.opsForValue().set(
            key,
            String.valueOf(System.currentTimeMillis()),
            java.time.Duration.ofSeconds(40)
    );

    return "Heartbeat updated";
}

    
}