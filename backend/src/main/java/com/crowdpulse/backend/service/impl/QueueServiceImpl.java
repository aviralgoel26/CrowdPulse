package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.service.QueueService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.service.CommunityService;

import java.util.List;
import java.util.Map;

@Service
public class QueueServiceImpl implements QueueService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;
    private final CommunityService communityService;

    public QueueServiceImpl(PlaceRepository placeRepository,
                            StringRedisTemplate redisTemplate,
                            CommunityService communityService) {
        this.placeRepository = placeRepository;
        this.redisTemplate = redisTemplate;
        this.communityService= communityService;
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

    long now = System.currentTimeMillis();

String value = userId + ":" + groupSize + ":" + now;

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
        long lastSeen = Long.parseLong(parts[2]);

        if (queuedUser.equals(userId)) {
            position = i + 1;
            break;
        }

        long now = System.currentTimeMillis();

// 🔥 SHADOW LOGIC
long diffMinutes = (now - lastSeen) / (1000 * 60);

if (diffMinutes < 30) {
    peopleAhead += groupSize; // ACTIVE + SHADOW
}
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

    // 🔹 Redis queue data
    List<String> queue = redisTemplate.opsForList().range(key, 0, -1);

    int redisPeople = 0;

    if (queue != null) {
        for (String entry : queue) {
            String[] parts = entry.split(":");
            int groupSize = Integer.parseInt(parts[1]);
            redisPeople += groupSize;
        }
    }

    // 🔹 Community data
    CommunityUpdate update = communityService.getLatestUpdate(placeId);

    int communityPeople = (update != null && update.getReportedQueueLength() != null)
            ? update.getReportedQueueLength()
            : 0;

    double throughput = (update != null && update.getThroughputPerMin() != null)
            ? update.getThroughputPerMin()
            : 20;

    // 🔥 FINAL HYBRID LOGIC
    int effectivePeople = Math.max(redisPeople, communityPeople);

    double avgGroupSize = 1.5;

    double waitTime = (effectivePeople * avgGroupSize) / throughput;

    return new WaitTimeResponse(
            (int) Math.ceil(waitTime),
            effectivePeople,
            avgGroupSize,
            throughput
    );
}
}