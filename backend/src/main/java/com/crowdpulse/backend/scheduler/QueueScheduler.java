package com.crowdpulse.backend.scheduler;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.crowdpulse.backend.service.CommunityService;
import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.repository.QueueMetricsRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class QueueScheduler {

    public static final long SHADOW_EXPIRY_THRESHOLD_MS = 30L * 60 * 1000; // 30 min

    public static final DefaultRedisScript<Long> FIND_AND_UPDATE_SCRIPT;

    @SuppressWarnings("rawtypes")
    public static final DefaultRedisScript<List> PROCESS_SHADOW_SCRIPT;

    static {
        FIND_AND_UPDATE_SCRIPT = new DefaultRedisScript<>(
            "local list = redis.call('LRANGE', KEYS[1], 0, -1) " +
            "for i, v in ipairs(list) do " +
            "  if v == ARGV[1] then " +
            "    redis.call('LSET', KEYS[1], i - 1, ARGV[2]) " +
            "    return 1 " +
            "  end " +
            "end " +
            "return 0",
            Long.class);

        PROCESS_SHADOW_SCRIPT = new DefaultRedisScript<>(
            "local now = tonumber(ARGV[1]) " +
            "local threshold = tonumber(ARGV[2]) " +
            "local placeId = ARGV[3] " +
            "if now == nil then return redis.error_reply('ERR TSK01: ARGV[1] (now) must be a numeric timestamp') end " +
            "if threshold == nil then return redis.error_reply('ERR TSK01: ARGV[2] (threshold) must be a numeric duration') end " +
            "if placeId == nil or placeId == '' then return redis.error_reply('ERR TSK01: ARGV[3] (placeId) must be a non-empty string') end " +
            "local list = redis.call('LRANGE', KEYS[1], 0, -1) " +
            "if #list == 0 then return {0, 0} end " +
            "local retained = {} " +
            "local removed = 0 " +
            "local migrated = 0 " +
            "for _, v in ipairs(list) do " +
            "  local parts = {} " +
            "  for part in string.gmatch(v, '[^:]+') do table.insert(parts, part) end " +
            "  if #parts < 3 then " +
            "    table.insert(retained, v) " +
            "  elseif #parts >= 4 and parts[4] == 'SHADOW' then " +
            "    if #parts >= 5 then " +
            "      local st = tonumber(parts[5]) " +
            "      if st == nil then " +
            "        local fixed = parts[1] .. ':' .. parts[2] .. ':' .. parts[3] .. ':SHADOW:' .. tostring(now) " +
            "        table.insert(retained, fixed) " +
            "        migrated = migrated + 1 " +
            "      elseif (now - st) > threshold then " +
            "        local hbKey = 'heartbeat:' .. placeId .. ':' .. parts[1] " +
            "        if redis.call('EXISTS', hbKey) == 1 then " +
            "          table.insert(retained, v) " +
            "        else " +
            "          removed = removed + 1 " +
            "        end " +
            "      else " +
            "        table.insert(retained, v) " +
            "      end " +
            "    else " +
            "      table.insert(retained, v .. ':' .. tostring(now)) " +
            "      migrated = migrated + 1 " +
            "    end " +
            "  else " +
            "    table.insert(retained, v) " +
            "  end " +
            "end " +
            "if #retained == 0 then " +
            "  redis.call('DEL', KEYS[1]) " +
            "  redis.call('DEL', KEYS[2]) " +
            "  return {removed, migrated} " +
            "end " +
            "redis.call('DEL', KEYS[2]) " +
            "for _, v in ipairs(retained) do " +
            "  local pr = redis.pcall('RPUSH', KEYS[2], v) " +
            "  if type(pr) == 'table' and pr.err then " +
            "    redis.call('DEL', KEYS[2]) " +
            "    return redis.error_reply('ERR TSK01: RPUSH failed: ' .. tostring(pr.err)) " +
            "  end " +
            "end " +
            "local rr = redis.pcall('RENAME', KEYS[2], KEYS[1]) " +
            "if type(rr) == 'table' and rr.err then " +
            "  redis.call('DEL', KEYS[2]) " +
            "  return redis.error_reply('ERR TSK01: RENAME failed: ' .. tostring(rr.err)) " +
            "end " +
            "return {removed, migrated}",
            List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final CommunityService communityService;
    private final QueueMetricsRepository queueMetricsRepository;
private final PlaceRepository placeRepository;

    public QueueScheduler(StringRedisTemplate redisTemplate,
                          SimpMessagingTemplate messagingTemplate,
                          CommunityService communityService,
                          QueueMetricsRepository queueMetricsRepository,
                          PlaceRepository placeRepository) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.communityService = communityService;
        this.queueMetricsRepository = queueMetricsRepository;
        this.placeRepository = placeRepository;
    }

    public static final String QUEUE_KEY_PATTERN = "queue:place:*";
    public static final long SCAN_BATCH_SIZE = 100L;

    /**
     * Validates that the key matches the exact queue key format: queue:place:{placeId}
     * where placeId is a valid positive numeric ID.
     * Excludes auxiliary/temporary rebuild keys (e.g. queue:place:1:temp_rebuild) and malformed keys.
     */
    public static boolean isValidQueueKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String[] parts = key.split(":");
        if (parts.length != 3) {
            return false;
        }
        if (!"queue".equals(parts[0]) || !"place".equals(parts[1])) {
            return false;
        }
        try {
            long placeId = Long.parseLong(parts[2]);
            return placeId > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Incrementally scans Redis for active place queue keys using SCAN with MATCH queue:place:*
     * and COUNT 100 hint.
     * Guaranteed cursor closure via try-with-resources.
     * Collects keys into a LinkedHashSet to guarantee deduplication and discovery order.
     * Excludes temporary rebuild keys and non-queue keys.
     *
     * @return Set of unique, valid queue keys.
     * @throws RuntimeException if cursor creation or iteration fails.
     */
    public Set<String> scanQueueKeys() {
        return scanQueueKeys(this.redisTemplate);
    }

    public static Set<String> scanQueueKeys(StringRedisTemplate redisTemplate) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(QUEUE_KEY_PATTERN)
                .count(SCAN_BATCH_SIZE)
                .build();

        Set<String> queueKeys = new LinkedHashSet<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (isValidQueueKey(key)) {
                    queueKeys.add(key);
                }
            }
            return queueKeys;
        } catch (Exception e) {
            System.err.println("❌ [Scheduler] Error during Redis SCAN iteration: " + e.getMessage());
            throw new RuntimeException("Redis SCAN failed: " + e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 10000) // runs every 10 sec
    public void processQueue() {
        System.out.println("⏱️ Scheduler running...");
        Set<String> keys;
        try {
            keys = scanQueueKeys();
        } catch (Exception e) {
            System.err.println("❌ [Scheduler] Aborting scheduler tick due to SCAN failure: " + e.getMessage());
            return;
        }

        if (keys == null || keys.isEmpty()) {
            System.out.println("No queues found");
            return;
        }

        for (String key : keys) {
            try {
                // 1. Get Place ID from Key
                String[] keyParts = key.split(":");
                Long placeId = Long.parseLong(keyParts[2]);

        // 2. 🧹 CLEANUP & STATE MANAGEMENT (Heartbeat Logic)
        List<String> currentQueue = redisTemplate.opsForList().range(key, 0, -1);
        long now = System.currentTimeMillis();

        if (currentQueue != null) {
            for (String entry : currentQueue) {
                String[] parts = entry.split(":");
                if (parts.length < 3) continue;

                String userId = parts[0];
                int gSize;
                long jTime;
                try {
                    gSize = Integer.parseInt(parts[1]);
                    jTime = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ [Scheduler] Malformed entry skipped: " + entry);
                    continue;
                }

                String state = parts.length > 3 ? parts[3] : "ACTIVE";
                long userAge = now - jTime;
                
                // ✅ CRITICAL FIX: Grace period for new users (first 90 seconds)
                // Don't mark as SHADOW during first 90 seconds of joining
                // This gives users time to load the app and establish heartbeat
                if (userAge < 90000L) {
                    continue; // Skip state management for new users
                }

                boolean isAlive = Boolean.TRUE.equals(redisTemplate.hasKey("heartbeat:" + placeId + ":" + userId));

                if (isAlive && !state.equals("ACTIVE")) {
                    // Re-activate if heartbeat reappears: strip shadowTimestamp and set to ACTIVE
                    String updated = userId + ":" + gSize + ":" + jTime + ":ACTIVE";
                    Long found = redisTemplate.execute(FIND_AND_UPDATE_SCRIPT,
                            Collections.singletonList(key), entry, updated);
                    if (found == null || found == 0L) {
                        System.out.println("⚠️ [Scheduler] SHADOW→ACTIVE: entry for " + userId
                                + " not found (removed concurrently). Skipping.");
                    } else {
                        System.out.println("✅ [Scheduler] " + userId
                                + " reactivated in-place for place " + placeId);
                    }
                } else if (!isAlive && state.equals("ACTIVE")) {
                    // Move to SHADOW if heartbeat missing: add shadowTimestamp = now
                    String updated = userId + ":" + gSize + ":" + jTime + ":SHADOW:" + now;
                    Long found = redisTemplate.execute(FIND_AND_UPDATE_SCRIPT,
                            Collections.singletonList(key), entry, updated);
                    if (found == null || found == 0L) {
                        System.out.println("⚠️ [Scheduler] ACTIVE→SHADOW: entry for " + userId
                                + " not found (removed concurrently). Skipping.");
                    } else {
                        System.out.println("🌑 [Scheduler] " + userId
                                + " moved to SHADOW in-place for place " + placeId);
                    }
                }
                // Note: SHADOW users with missing heartbeat are processed atomically below by PROCESS_SHADOW_SCRIPT
            }
        }

        // Atomic shadow expiry and legacy 4-field migration (once per queue key)
        String tempKey = key + ":temp_rebuild";
        List<?> shadowResult = redisTemplate.execute(
                PROCESS_SHADOW_SCRIPT,
                Arrays.asList(key, tempKey),
                String.valueOf(now),
                String.valueOf(SHADOW_EXPIRY_THRESHOLD_MS),
                String.valueOf(placeId));

        long expiredCount = (shadowResult != null && !shadowResult.isEmpty())
                ? ((Long) shadowResult.get(0)) : 0L;
        long migratedCount = (shadowResult != null && shadowResult.size() > 1)
                ? ((Long) shadowResult.get(1)) : 0L;

        if (expiredCount > 0) {
            System.out.println("🗑️ [Scheduler] Expired " + expiredCount
                    + " SHADOW user(s) from place " + placeId);
        }
        if (migratedCount > 0) {
            System.out.println("🔄 [Scheduler] Migrated " + migratedCount
                    + " legacy SHADOW entry/entries for place " + placeId);
        }

        // 3. 🚶 SERVE USERS (VIRTUAL PROGRESSION)
        // Throughput-driven movement is simulated natively in QueueServiceImpl via elapsed time.
        // We do NOT destructively leftPop real active users here, as it kicks them out of their UI.
        // Users are removed smoothly via Heartbeat expiration once they close the app post-darshan.

        // 4. 📊 DATA GATHERING (The New Part)
        int activeUsers = 0;
        int shadowUsers = 0;
        int redisPeople = 0;

        List<String> updatedQueue = redisTemplate.opsForList().range(key, 0, -1);
        if (updatedQueue != null) {
            for (String entry : updatedQueue) {
                String[] parts = entry.split(":");
                if (parts.length < 2) continue;
                int gSize = Integer.parseInt(parts[1]);
                String state = parts.length > 3 ? parts[3] : "ACTIVE";

                redisPeople += gSize;
                if ("ACTIVE".equals(state)) activeUsers += gSize;
                else shadowUsers += gSize;
            }
        }

        // 5. 🔥 APPLY CORRECTIONS (Scaling Factor & Offset & Community)
        CommunityUpdate update = communityService.getLatestUpdate(placeId);
        int communityPeople = (update != null && update.getReportedQueueLength() != null)
                ? update.getReportedQueueLength()
                : 0;
                
        // Use MAX of redis people + community estimate (Hybrid approach matching QueueService)
        int basePeople = Math.max(redisPeople, communityPeople);
        int totalPeople = basePeople;  // ✅ FIX: Define totalPeople before using

        var place = placeRepository.findById(placeId).orElse(null);
        double scalingFactor = (place != null && place.getScalingFactor() != null) ? place.getScalingFactor() : 1.0;
        int adminOffset = (place != null && place.getAdminOffset() != null) ? place.getAdminOffset() : 0;

        int correctedPeople = (int) ((basePeople * scalingFactor) + adminOffset);

        // 6. ⏱️ CALCULATE WAIT TIME (Dynamic Queue Efficiency Coefficient)
        double throughput = (update != null && update.getThroughputPerMin() != null) 
                ? update.getThroughputPerMin() 
                : (place != null && place.getBaseThroughput() != null ? place.getBaseThroughput() : 30.0);

        // 🔥 Dynamic Queue Efficiency Coefficient based on congestion
        double queueEfficiencyFactor;
        if (correctedPeople <= 50)        queueEfficiencyFactor = 0.55;
        else if (correctedPeople <= 200)  queueEfficiencyFactor = 0.45;
        else if (correctedPeople <= 500)  queueEfficiencyFactor = 0.35;
        else if (correctedPeople <= 1000) queueEfficiencyFactor = 0.25;
        else                              queueEfficiencyFactor = 0.15;

        double effectiveThroughput = throughput * queueEfficiencyFactor;
        double avgGroupSize = 1.5;
        int waitTime = (correctedPeople > 0 && effectiveThroughput > 0) 
                ? (int) Math.ceil(correctedPeople / effectiveThroughput) 
                : 0;

        // 7. 💾 SAVE METRICS TO DB
        try {
            com.crowdpulse.backend.model.QueueMetrics metrics = new com.crowdpulse.backend.model.QueueMetrics();
            metrics.setPlaceId(placeId);
            metrics.setTimestamp(java.time.LocalDateTime.now());
            metrics.setActiveUsers(activeUsers);
            metrics.setShadowUsers(shadowUsers);
            metrics.setTotalPeople(totalPeople);
            metrics.setCorrectedPeople(correctedPeople);
            metrics.setThroughput(throughput);
            metrics.setEfficiencyFactor(queueEfficiencyFactor);
            metrics.setWaitTime(waitTime);

            queueMetricsRepository.save(metrics);
            System.out.println("✅ Metrics saved - Place:" + placeId + " Active:" + activeUsers + " Shadow:" + shadowUsers + " Total:" + totalPeople + " Wait:" + waitTime + "min");
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Failed to save metrics for place " + placeId + ": " + e.getMessage());
            e.printStackTrace();  // Log full stack trace for debugging
        }

        // 8. 📡 BROADCAST UPDATE TO WEBSOCKET SUBSCRIBERS
        try {
            String queueStatus = (update != null && update.getQueueStatus() != null)
                    ? update.getQueueStatus()
                    : "ACTIVE";

            String broadcastMessage = String.format(
                "{\"placeId\":%d,\"timestamp\":%d,\"totalWaitMinutes\":%d,\"totalPeople\":%d,\"queueStatus\":\"%s\",\"throughput\":%.2f,\"efficiencyFactor\":%.2f}",
                placeId, System.currentTimeMillis(), waitTime, correctedPeople, queueStatus, (double)throughput, queueEfficiencyFactor
            );

            messagingTemplate.convertAndSend("/topic/queue/" + placeId, broadcastMessage);
            System.out.println("📡 Broadcast sent to /topic/queue/" + placeId);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to broadcast queue update for place " + placeId + ": " + e.getMessage());
        }
    } catch (Exception e) {
        System.err.println("❌ [Scheduler] Error processing queue key " + key + ": " + e.getMessage());
    }
}
}
}