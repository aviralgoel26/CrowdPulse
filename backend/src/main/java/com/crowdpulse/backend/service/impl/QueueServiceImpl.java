package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.service.QueueService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueueServiceImpl implements QueueService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;

    public QueueServiceImpl(PlaceRepository placeRepository,
                            StringRedisTemplate redisTemplate) {
        this.placeRepository = placeRepository;
        this.redisTemplate = redisTemplate;
    }

    // 🔑 Redis Queue Key
    private String getQueueKey(Long placeId) {
        return "queue:place:" + placeId;
    }

    // ==============================
    // 🎟️ JOIN QUEUE
    // ==============================
    @Override
    public Map<String, Object> joinQueue(Long placeId, String userId) {

        String key = getQueueKey(placeId);

        // Prevent duplicate join
        List<String> existing = redisTemplate.opsForList().range(key, 0, -1);
        if (existing != null && existing.contains(userId)) {
            return Map.of("message", "Already in queue");
        }

        redisTemplate.opsForList().rightPush(key, userId);

        Long position = redisTemplate.opsForList().size(key);

        return Map.of(
                "position", position,
                "message", "Joined queue successfully"
        );
    }

    // ==============================
    // 📊 GET STATUS
    // ==============================
    @Override
    public Map<String, Object> getStatus(Long placeId, String userId) {

        String key = getQueueKey(placeId);

        List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

        if (queue == null || !queue.contains(userId)) {
            return Map.of("message", "User not in queue");
        }

        int position = queue.indexOf(userId) + 1;
        int peopleAhead = position - 1;

        return Map.of(
                "position", position,
                "peopleAhead", peopleAhead
        );
    }

    // ==============================
    // ⏱️ WAIT TIME CALCULATION
    // ==============================
    @Override
    public WaitTimeResponse calculateWaitTime(Long placeId) {

        String key = getQueueKey(placeId);

        Long queueSize = redisTemplate.opsForList().size(key);
        int peopleAhead = queueSize != null ? queueSize.intValue() : 0;

        double avgGroupSize = 1.5;
        double throughput = 20; // persons/min

        double waitTime = (peopleAhead * avgGroupSize) / throughput;

        return new WaitTimeResponse(
                (int) Math.ceil(waitTime),
                peopleAhead,
                avgGroupSize,
                throughput
        );
    }
}