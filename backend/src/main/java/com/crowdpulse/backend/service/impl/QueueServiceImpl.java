package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.service.QueueService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.service.CommunityService;

import java.time.LocalTime;
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

    var place = placeRepository.findById(placeId).orElse(null);

if (place != null) {

    String status = place.getQueueStatus();

    if ("CLOSED".equals(status)) {

        return Map.of(
                "message",
                "Queue is currently closed"
        );
    }

    if ("PAUSED".equals(status)) {

        return Map.of(
                "message",
                "Queue temporarily paused"
        );
    }
}
    String key = getQueueKey(placeId);

    // Prevent duplicate
    List<String> existing = redisTemplate.opsForList().range(key, 0, -1);
    if (existing != null && existing.stream().anyMatch(e -> e.startsWith(userId + ":"))) {
        return Map.of("message", "Already in queue");
    }

    long now = System.currentTimeMillis();

String value = userId + ":" + groupSize + ":" + now + ":ACTIVE";

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

    CommunityUpdate update =
        communityService.getLatestUpdate(placeId);

int communityPeople =
        (update != null &&
         update.getReportedQueueLength() != null)
        ? update.getReportedQueueLength()
        : 0;

        var place =
        placeRepository.findById(placeId).orElse(null);

double scalingFactor =
        (place != null &&
         place.getScalingFactor() != null)
        ? place.getScalingFactor()
        : 1.0;
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

    String heartbeatKey = "heartbeat:" + placeId + ":" + queuedUser;

    boolean isAlive = Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey));

    if (queuedUser.equals(userId)) {
        position = peopleAhead + 1;
        break;
    }

    // ACTIVE + SHADOW both counted
    peopleAhead += groupSize;
}
int virtualPeopleAhead =
        (int) Math.ceil(
                Math.max(
                        peopleAhead,
                        communityPeople
                ) * scalingFactor
        );

int virtualPosition =
        virtualPeopleAhead + 1;

        double throughput = 30;

if (place != null &&
    place.getBaseThroughput() != null) {

    throughput = place.getBaseThroughput();
}

double waitMinutes =
        (virtualPeopleAhead * 1.5) / throughput;

LocalTime eta =
        LocalTime.now()
                 .plusMinutes((long) waitMinutes);
    if (position == 0) {
        return Map.of("message", "User not in queue");
    }

   return Map.of(

        "position", virtualPosition,

        "peopleAhead", virtualPeopleAhead,

        "estimatedDarshanTime",
        eta.toString(),

        "groupSize",
        queue.stream()
                .filter(q -> q.startsWith(userId + ":"))
                .map(q -> Integer.parseInt(q.split(":")[1]))
                .findFirst()
                .orElse(1)
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

    

    // 🔥 FINAL HYBRID LOGIC
    // 🔹 Get place data
var place = placeRepository.findById(placeId).orElse(null);
double throughput = 30;

if (place != null && place.getBaseThroughput() != null) {
    throughput = place.getBaseThroughput();
}

LocalTime now = LocalTime.now();

if (place != null &&
    place.getPeakStart() != null &&
    place.getPeakEnd() != null) {

    LocalTime peakStart = LocalTime.parse(place.getPeakStart());
    LocalTime peakEnd = LocalTime.parse(place.getPeakEnd());

    if (now.isAfter(peakStart) && now.isBefore(peakEnd)) {

        throughput = throughput / place.getPeakMultiplier();
    }
}

if (place != null && place.getSeasonMultiplier() != null) {

    throughput = throughput / place.getSeasonMultiplier();
}

double scalingFactor = (place != null && place.getScalingFactor() != null)
        ? place.getScalingFactor()
        : 1.0;

int adminOffset = (place != null && place.getAdminOffset() != null)
        ? place.getAdminOffset()
        : 0;

// 🔹 Hybrid base
int basePeople = Math.max(redisPeople, communityPeople);

// 🔥 HEURISTIC CORRECTION
double correctedPeople =
        (basePeople * scalingFactor) + adminOffset;

    double avgGroupSize = 1.5;

    double waitTime = (correctedPeople * avgGroupSize) / throughput;
String queueStatus =
        (place != null)
                ? place.getQueueStatus()
                : "ACTIVE";
    return new WaitTimeResponse(
            (int) Math.ceil(waitTime),
            (int) Math.ceil(correctedPeople),
            avgGroupSize,
            throughput,
            queueStatus
    );
}
}