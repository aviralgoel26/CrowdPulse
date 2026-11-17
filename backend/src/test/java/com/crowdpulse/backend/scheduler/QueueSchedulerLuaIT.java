package com.crowdpulse.backend.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.crowdpulse.backend.scheduler.QueueScheduler.FIND_AND_UPDATE_SCRIPT;
import static com.crowdpulse.backend.scheduler.QueueScheduler.PROCESS_SHADOW_SCRIPT;

@Testcontainers
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class QueueSchedulerLuaIT {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long THRESHOLD = QueueScheduler.SHADOW_EXPIRY_THRESHOLD_MS; // 30 min in ms
    private static final String PLACE_ID = "1";
    private String queueKey;
    private String tempKey;

    @BeforeEach
    void setUp() {
        queueKey = "queue:place:" + PLACE_ID;
        tempKey = queueKey + ":temp_rebuild";
        redisTemplate.delete(queueKey);
        redisTemplate.delete(tempKey);
        redisTemplate.delete("heartbeat:" + PLACE_ID + ":user1");
        redisTemplate.delete("heartbeat:" + PLACE_ID + ":user2");
        redisTemplate.delete("heartbeat:" + PLACE_ID + ":user3");
    }

    private List<?> runShadowScript(long injectedNow) {
        return redisTemplate.execute(PROCESS_SHADOW_SCRIPT,
                Arrays.asList(queueKey, tempKey),
                String.valueOf(injectedNow),
                String.valueOf(THRESHOLD),
                PLACE_ID);
    }

    @Nested
    @DisplayName("FIND_AND_UPDATE_SCRIPT Tests")
    class FindAndUpdateTests {

        @Test
        @DisplayName("FU-01: Entry found and updated in place preserving FIFO order")
        void testEntryFoundAndUpdatedInPlace() {
            String a = "userA:1:1000:ACTIVE";
            String b = "userB:2:2000:ACTIVE";
            String c = "userC:1:3000:ACTIVE";
            redisTemplate.opsForList().rightPushAll(queueKey, a, b, c);

            String bUpdated = "userB:2:2000:SHADOW:5000";
            Long result = redisTemplate.execute(FIND_AND_UPDATE_SCRIPT,
                    Collections.singletonList(queueKey), b, bUpdated);

            assertEquals(1L, result);
            List<String> current = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(current);
            assertEquals(3, current.size());
            assertEquals(a, current.get(0));
            assertEquals(bUpdated, current.get(1));
            assertEquals(c, current.get(2));
        }

        @Test
        @DisplayName("FU-02: Entry not found returns 0 and leaves list intact")
        void testEntryNotFoundReturnsZero() {
            String a = "userA:1:1000:ACTIVE";
            String c = "userC:1:3000:ACTIVE";
            redisTemplate.opsForList().rightPushAll(queueKey, a, c);

            Long result = redisTemplate.execute(FIND_AND_UPDATE_SCRIPT,
                    Collections.singletonList(queueKey), "userB:2:2000:ACTIVE", "userB:2:2000:SHADOW:5000");

            assertEquals(0L, result);
            List<String> current = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(current);
            assertEquals(2, current.size());
            assertEquals(a, current.get(0));
            assertEquals(c, current.get(1));
        }

        @Test
        @DisplayName("FU-08: SHADOW to ACTIVE strips 5th field in place")
        void testShadowToActiveStripsFifthField() {
            String a = "userA:1:1000:ACTIVE";
            String bShadow = "userB:2:2000:SHADOW:5000";
            redisTemplate.opsForList().rightPushAll(queueKey, a, bShadow);

            String bActive = "userB:2:2000:ACTIVE";
            Long result = redisTemplate.execute(FIND_AND_UPDATE_SCRIPT,
                    Collections.singletonList(queueKey), bShadow, bActive);

            assertEquals(1L, result);
            List<String> current = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(current);
            assertEquals(2, current.size());
            assertEquals(a, current.get(0));
            assertEquals(bActive, current.get(1));
        }
    }

    @Nested
    @DisplayName("PROCESS_SHADOW_SCRIPT Tests")
    class ProcessShadowTests {

        @Test
        @DisplayName("TTL & Persistence Verification: Queue remains persistent (TTL = -1) and tempKey is removed")
        void testQueuePersistenceAndTempKeyAbsent() {
            long now = System.currentTimeMillis();
            redisTemplate.opsForList().rightPushAll(queueKey,
                    "user1:1:" + now + ":ACTIVE",
                    "user2:1:" + now + ":SHADOW:" + (now - 5000));

            // Verify initial TTL is -1 (persistent)
            Long initialTtl = redisTemplate.getExpire(queueKey);
            assertEquals(-1L, initialTtl, "Queue should not have an expiration before rebuilding");

            List<?> result = runShadowScript(now);
            assertNotNull(result);

            // Verify queue is still persistent
            Long postTtl = redisTemplate.getExpire(queueKey);
            assertEquals(-1L, postTtl, "Queue MUST retain TTL = -1 (no expiration) after rebuild");

            // Verify temporary rebuild key does not exist
            Boolean tempExists = redisTemplate.hasKey(tempKey);
            assertFalse(Boolean.TRUE.equals(tempExists), "Temporary rebuild key must be absent after success");
        }

        @Test
        @DisplayName("PS-09: Two-step lifecycle: legacy migration at T0, followed by expiry at T0 + 31m")
        void testTwoStepMigrationThenExpiry() {
            long t0 = 1000000000L;
            String legacy = "user1:1:" + (t0 - 60000) + ":SHADOW";
            redisTemplate.opsForList().rightPush(queueKey, legacy);

            // Step 1: Migration tick at T0
            List<?> migrationResult = runShadowScript(t0);
            assertNotNull(migrationResult);
            assertEquals(0L, migrationResult.get(0), "Should not be removed on migration tick");
            assertEquals(1L, migrationResult.get(1), "Should be recorded as migrated");

            List<String> afterStep1 = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(afterStep1);
            assertEquals(1, afterStep1.size());
            assertEquals(legacy + ":" + t0, afterStep1.get(0), "Should receive shadowTimestamp = T0");

            // Step 2: Expiry tick at T0 + 31 minutes + 1 ms
            long tFuture = t0 + (31 * 60 * 1000L) + 1L;
            List<?> expiryResult = runShadowScript(tFuture);
            assertNotNull(expiryResult);
            assertEquals(1L, expiryResult.get(0), "Should be expired on second tick");

            List<String> afterStep2 = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertTrue(afterStep2 == null || afterStep2.isEmpty(), "Queue should be empty after expiry");
        }

        @Test
        @DisplayName("PS-17: Strict boundary check: now - shadowTime == threshold is NOT expired, +1ms IS expired")
        void testStrictThresholdBoundary() {
            long now = 2000000000L;
            long exactBoundaryTime = now - THRESHOLD;
            String boundaryUser = "userBoundary:1:1000:SHADOW:" + exactBoundaryTime;

            redisTemplate.opsForList().rightPush(queueKey, boundaryUser);

            // (now - exactBoundaryTime) == THRESHOLD -> THRESHOLD > THRESHOLD is FALSE -> Retained
            List<?> res1 = runShadowScript(now);
            assertNotNull(res1);
            assertEquals(0L, res1.get(0));
            assertEquals(1, redisTemplate.opsForList().size(queueKey));

            // Advance clock by 1ms -> (now - exactBoundaryTime) == THRESHOLD + 1 -> TRUE -> Expired
            List<?> res2 = runShadowScript(now + 1L);
            assertNotNull(res2);
            assertEquals(1L, res2.get(0));
            assertEquals(0, redisTemplate.opsForList().size(queueKey));
        }

        @Test
        @DisplayName("Heartbeat Restoration Guard: Expired SHADOW entry is retained if heartbeat key exists")
        void testHeartbeatGuardRetainsExpiredUser() {
            long now = 3000000000L;
            long oldShadowTime = now - (35 * 60 * 1000L); // 35 min ago (> 30 min)
            String user = "user1:1:1000:SHADOW:" + oldShadowTime;
            redisTemplate.opsForList().rightPush(queueKey, user);

            // Set heartbeat key (simulates user reconnecting)
            String heartbeatKey = "heartbeat:" + PLACE_ID + ":user1";
            redisTemplate.opsForValue().set(heartbeatKey, String.valueOf(now));

            List<?> result = runShadowScript(now);
            assertNotNull(result);
            assertEquals(0L, result.get(0), "User must be retained due to active heartbeat");

            List<String> list = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(list);
            assertEquals(1, list.size());
            assertEquals(user, list.get(0));

            // Now remove heartbeat and re-run -> user is removed
            redisTemplate.delete(heartbeatKey);
            List<?> resultAfterHbDeleted = runShadowScript(now);
            assertNotNull(resultAfterHbDeleted);
            assertEquals(1L, resultAfterHbDeleted.get(0), "User should be removed once heartbeat is gone");
        }

        @Test
        @DisplayName("PS-13 & PS-14: FIFO order preserved across multiple mixed expirations and migrations")
        void testFifoOrderPreservedAfterMixedOperations() {
            long now = 4000000000L;
            long expiredTime = now - (35 * 60 * 1000L);
            long validTime = now - (5 * 60 * 1000L);

            String user1Expired = "u1:1:1000:SHADOW:" + expiredTime;
            String user2Active = "u2:2:2000:ACTIVE";
            String user3Legacy = "u3:1:3000:SHADOW";
            String user4ValidShadow = "u4:1:4000:SHADOW:" + validTime;
            String user5Expired = "u5:3:5000:SHADOW:" + expiredTime;

            redisTemplate.opsForList().rightPushAll(queueKey,
                    user1Expired, user2Active, user3Legacy, user4ValidShadow, user5Expired);

            List<?> result = runShadowScript(now);
            assertNotNull(result);
            assertEquals(2L, result.get(0), "2 expired users should be removed");
            assertEquals(1L, result.get(1), "1 legacy user should be migrated");

            List<String> current = redisTemplate.opsForList().range(queueKey, 0, -1);
            assertNotNull(current);
            assertEquals(3, current.size());
            assertEquals(user2Active, current.get(0));
            assertEquals(user3Legacy + ":" + now, current.get(1));
            assertEquals(user4ValidShadow, current.get(2));
        }

        @Test
        @DisplayName("PS-01 & PS-03: Empty queue and malformed entries handled safely")
        void testEmptyQueueAndMalformedEntries() {
            // Empty queue
            List<?> emptyRes = runShadowScript(System.currentTimeMillis());
            assertNotNull(emptyRes);
            assertEquals(0L, emptyRes.get(0));
            assertEquals(0L, emptyRes.get(1));

            // Malformed entry
            redisTemplate.opsForList().rightPush(queueKey, "malformedEntryWithoutEnoughParts");
            List<?> malformedRes = runShadowScript(System.currentTimeMillis());
            assertNotNull(malformedRes);
            assertEquals(0L, malformedRes.get(0));
            assertEquals(1, redisTemplate.opsForList().size(queueKey));
        }

        @Test
        @DisplayName("Argument Validation: Non-numeric or missing arguments return Lua error")
        void testArgumentValidationThrowsError() {
            redisTemplate.opsForList().rightPush(queueKey, "u1:1:1000:ACTIVE");

            // Non-numeric now
            assertThrows(Exception.class, () ->
                    redisTemplate.execute(PROCESS_SHADOW_SCRIPT,
                            Arrays.asList(queueKey, tempKey),
                            "NOT_A_NUMBER", String.valueOf(THRESHOLD), PLACE_ID));

            // Non-numeric threshold
            assertThrows(Exception.class, () ->
                    redisTemplate.execute(PROCESS_SHADOW_SCRIPT,
                            Arrays.asList(queueKey, tempKey),
                            String.valueOf(System.currentTimeMillis()), "INVALID", PLACE_ID));

            // Empty placeId
            assertThrows(Exception.class, () ->
                    redisTemplate.execute(PROCESS_SHADOW_SCRIPT,
                            Arrays.asList(queueKey, tempKey),
                            String.valueOf(System.currentTimeMillis()), String.valueOf(THRESHOLD), ""));
        }
    }
}
