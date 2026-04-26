package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.service.MetricsService;
import com.crowdpulse.backend.dto.CrowdStats;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.dto.RecommendationResponse;
import com.crowdpulse.backend.dto.VibeResponse;
import java.util.HashMap;
import java.util.Map;



@Service
public class MetricsServiceImpl implements MetricsService {

    // Inject PlaceRepository to fetch place details for vibe score calculation
private final PlaceRepository placeRepository;
private final StringRedisTemplate redisTemplate;


    public MetricsServiceImpl(PlaceRepository placeRepository,
                          StringRedisTemplate redisTemplate) {
    this.placeRepository = placeRepository;
    this.redisTemplate = redisTemplate;
}

    private static final long TIMEOUT = 60 * 1000; // 60 seconds


    //  In-memory map to store last active user count for trend analysis
private final Map<Long, Integer> lastActiveUsersMap = new HashMap<>();

    // Update heartbeat for a user in a place
    @Override
public void updateHeartbeat(Long placeId, String userId, long timestamp) {

    String key = "place:" + placeId + ":users";

    redisTemplate.opsForHash().put(
            key,
            userId,
            String.valueOf(System.currentTimeMillis())
    );

    
}


// Active users are those who have sent a heartbeat within the last 60 seconds
   @Override
public int getActiveUsers(Long placeId) {
    return (int) getCrowdStats(placeId).getEffectiveUsers();
}


// Vibe score is calculated based on the ratio of active users to the place's capacity
   @Override
public int getVibeScore(Long placeId) {

   CrowdStats stats = getCrowdStats(placeId);
double effectiveUsers = stats.getEffectiveUsers();

    Place place = placeRepository.findById(placeId)
            .orElseThrow(() -> new RuntimeException("Place not found"));

    int capacity = place.getCapacity();

    if (capacity == 0) return 10;

    double density = effectiveUsers / capacity;

    int vibe = (int) Math.ceil((1 - density) * 10);

    return Math.min(Math.max(vibe, 1), 10);
}




// Get vibe details including score and label
@Override
public VibeResponse getVibeDetails(Long placeId) {

    int vibe = getVibeScore(placeId); // already exists

    String label;

    if (vibe >= 9) {
        label = "🔥 Chill AF";
    } else if (vibe >= 7) {
        label = "😌 Nice vibe";
    } else if (vibe >= 5) {
        label = "🙂 Decent";
    } else if (vibe >= 3) {
        label = "😕 Busy";
    } else {
        label = "😵 Overcrowded";
    }

    return new VibeResponse(vibe, label);
}

// Simple trend analysis based on change in active users

@Override
public String getTrend(Long placeId) {

    int currentUsers = getActiveUsers(placeId);

    Integer previousUsers = lastActiveUsersMap.get(placeId);

if (previousUsers == null) {
    lastActiveUsersMap.put(placeId, currentUsers);
    return "➖ Stable";
}

    String trend;

if (currentUsers > previousUsers) {
    trend = "📈 Crowd increasing";
} else if (currentUsers < previousUsers) {
    trend = "📉 Crowd decreasing";
} else {
    trend = "➖ Stable";
}

lastActiveUsersMap.put(placeId, currentUsers);

return trend;
}

//  Generate recommendation based on vibe score and trend
@Override
public RecommendationResponse getRecommendation(Long placeId) {

    VibeResponse vibeData = getVibeDetails(placeId);
    String trend = getTrend(placeId);

    int vibe = vibeData.getVibeScore();
    String label = vibeData.getLabel();

    String recommendation;

    if (vibe >= 8 && trend.contains("increasing")) {
        recommendation = "🔥 Go now before it gets crowded";
    } else if (vibe >= 8) {
        recommendation = "😌 Perfect time to visit";
    } else if (vibe >= 5) {
        recommendation = "🙂 Decent time, but expect some crowd";
    } else if (trend.contains("decreasing")) {
        recommendation = "⏳ Wait a bit, crowd is reducing";
    } else {
        recommendation = "🚫 Too crowded right now";
    }

    return new RecommendationResponse(vibe, label, trend, recommendation);
}


public CrowdStats getCrowdStats(Long placeId) {

    // 1. Redis key
    String key = "place:" + placeId + ":users";

    // 2. Get all users from Redis
    Map<Object, Object> users = redisTemplate.opsForHash().entries(key);

    long now = System.currentTimeMillis();

    // 3. Get place details
    Place place = placeRepository.findById(placeId)
            .orElseThrow(() -> new RuntimeException("Place not found"));

    int active = 0;
    int shadow = 0;

    long activeThreshold = 60_000; // 60 sec

    long shadowThreshold;

    // 🔥 4. Decide shadow duration based on place type
    switch (place.getType()) {

        case QUEUE:
            shadowThreshold = 60 * 60 * 1000; // 60 min
            break;

        case TIMED:
            shadowThreshold = 30 * 60 * 1000; // 30 min
            break;

        default: // VIBE
            shadowThreshold = 10 * 60 * 1000; // 10 min
    }

    // 5. Loop through users
    for (Object value : users.values()) {

        long lastSeen = Long.parseLong(value.toString());
        long diff = now - lastSeen;

        if (diff < activeThreshold) {
            active++;
        } else if (diff < shadowThreshold) {
            shadow++;
        }
        // else ignore (user left)
    }

    // 6. Calculate effective users
    double effective = active + (shadow * 0.5);

double scaled = (effective * place.getScalingFactor()) + place.getAdminOffset();

return new CrowdStats(active, shadow, scaled);
}
}