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
public Map<String, Object> joinQueue(Long placeId, String userId, int groupSize) {

    String key = getQueueKey(placeId);

    // Prevent duplicate
    List<String> existing = redisTemplate.opsForList().range(key, 0, -1);
    if (existing != null && existing.stream().anyMatch(e -> e.startsWith(userId + ":"))) {
        return Map.of("message", "Already in queue");
    }

    // Store as "userId:groupSize"
    String value = userId + ":" + groupSize;

    redisTemplate.opsForList().rightPush(key, value);

    Long position = redisTemplate.opsForList().size(key);

    return Map.of(
            "position", position,
            "groupSize", groupSize,
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

    if (queue == null || queue.isEmpty()) {
        return Map.of("message", "Queue empty");
    }

    int position = 0;
    int peopleAhead = 0;

    for (int i = 0; i < queue.size(); i++) {

        String entry = queue.get(i);
        String[] parts = entry.split(":");

        String queuedUser = parts[0];
        int groupSize = Integer.parseInt(parts[1]);

        if (queuedUser.equals(userId)) {
            position = i + 1;
            break;
        }

        peopleAhead += groupSize;
    }

    if (position == 0) {
        return Map.of("message", "User not in queue");
    }

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

        List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

int totalPeople = 0;

if (queue != null) {
    for (String entry : queue) {
        String[] parts = entry.split(":");
        int groupSize = Integer.parseInt(parts[1]);
        totalPeople += groupSize;
    }
}
int peopleAhead = totalPeople;
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