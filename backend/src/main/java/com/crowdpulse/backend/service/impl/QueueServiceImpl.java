package com.crowdpulse.backend.service.impl;
import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.service.QueueService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueServiceImpl implements QueueService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;

    public QueueServiceImpl(PlaceRepository placeRepository,
                            StringRedisTemplate redisTemplate) {
        this.placeRepository = placeRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public WaitTimeResponse calculateWaitTime(Long placeId) {

        // 🔴 MOCK (replace later with real queue + throughput)
        int peopleAhead = getActiveUsers(placeId);   // from Redis
        double avgGroupSize = 1.5;                   // configurable
        double throughput = 20;                      // users/min

        double waitTime = (peopleAhead * avgGroupSize) / throughput;

        return new WaitTimeResponse(
                (int) Math.ceil(waitTime),
                peopleAhead,
                avgGroupSize,
                throughput
        );
    }

    private int getActiveUsers(Long placeId) {
        String key = "place:" + placeId + ":users";
        Long size = redisTemplate.opsForHash().size(key);
        return size != null ? size.intValue() : 0;
    }
}
