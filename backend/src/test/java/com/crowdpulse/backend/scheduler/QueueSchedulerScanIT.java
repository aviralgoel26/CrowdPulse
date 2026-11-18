package com.crowdpulse.backend.scheduler;

import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb_scan;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class QueueSchedulerScanIT {

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

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private QueueMetricsRepository queueMetricsRepository;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private QueueScheduler queueScheduler;

    @BeforeEach
    void cleanRedis() {
        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys != null && !allKeys.isEmpty()) {
            redisTemplate.delete(allKeys);
        }
    }

    // =========================================================================
    // 1. Key Validation Unit Tests (isValidQueueKey)
    // =========================================================================
    @Nested
    @DisplayName("Key Validation Tests")
    class KeyValidationTests {

        @ParameterizedTest(name = "Valid key: {0}")
        @ValueSource(strings = {
            "queue:place:1",
            "queue:place:2",
            "queue:place:42",
            "queue:place:999999",
            "queue:place:1000000000"
        })
        @DisplayName("Accepts valid queue:place:{positiveLong} keys")
        void testValidQueueKeys(String key) {
            assertTrue(QueueScheduler.isValidQueueKey(key), "Expected valid key: " + key);
        }

        @ParameterizedTest(name = "Invalid key: {0}")
        @ValueSource(strings = {
            "queue:place:1:temp_rebuild",       // TSK-01 auxiliary rebuild key
            "queue:place:2:temp_rebuild",
            "queue:place:1:backup",
            "queue:place:abc",                  // non-numeric place ID
            "queue:place:0",                    // non-positive place ID
            "queue:place:-1",                   // negative place ID
            "queue:place:-99",
            "queue:place:1:extra:segments",
            "queue:place:",
            "queue:place",
            "heartbeat:1:user1",                // heartbeat key
            "heartbeat:place:1",
            "place:config:1",
            "unrelated:key",
            "queue:places:1",
            "queues:place:1"
        })
        @DisplayName("Rejects malformed, auxiliary, temporary rebuild, and non-queue keys")
        void testInvalidQueueKeys(String key) {
            assertFalse(QueueScheduler.isValidQueueKey(key), "Expected invalid key: " + key);
        }

        @ParameterizedTest(name = "Null or empty key")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Rejects null, empty, or whitespace keys")
        void testNullAndEmptyKeys(String key) {
            assertFalse(QueueScheduler.isValidQueueKey(key));
        }
    }

    // =========================================================================
    // 2. Redis SCAN Integration Tests (with live Redis container)
    // =========================================================================
    @Nested
    @DisplayName("Redis SCAN Integration Tests")
    class RedisScanIntegrationTests {

        @Test
        @DisplayName("SCAN-01: Empty keyspace returns empty Set without error")
        void testEmptyKeyspace() {
            Set<String> discovered = queueScheduler.scanQueueKeys();
            assertNotNull(discovered, "Discovered keys set must not be null");
            assertTrue(discovered.isEmpty(), "Discovered keys set must be empty for empty Redis");
        }

        @Test
        @DisplayName("SCAN-02: Discovers multiple valid queues correctly")
        void testDiscoverMultipleValidQueues() {
            redisTemplate.opsForList().rightPush("queue:place:1", "user1:1:" + System.currentTimeMillis() + ":ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:2", "user2:2:" + System.currentTimeMillis() + ":ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:3", "user3:1:" + System.currentTimeMillis() + ":ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:42", "user4:3:" + System.currentTimeMillis() + ":ACTIVE");

            Set<String> discovered = queueScheduler.scanQueueKeys();

            assertNotNull(discovered);
            assertEquals(4, discovered.size());
            assertTrue(discovered.contains("queue:place:1"));
            assertTrue(discovered.contains("queue:place:2"));
            assertTrue(discovered.contains("queue:place:3"));
            assertTrue(discovered.contains("queue:place:42"));
        }

        @Test
        @DisplayName("SCAN-03: Excludes temporary rebuild keys and auxiliary keys matching pattern")
        void testExcludesTemporaryRebuildAndAuxiliaryKeys() {
            // Valid active queues
            redisTemplate.opsForList().rightPush("queue:place:1", "user1:1:" + System.currentTimeMillis() + ":ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:2", "user2:1:" + System.currentTimeMillis() + ":ACTIVE");

            // TSK-01 temporary rebuild keys that match queue:place:* pattern
            redisTemplate.opsForList().rightPush("queue:place:1:temp_rebuild", "user1:1:1000:ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:2:temp_rebuild", "user2:1:1000:ACTIVE");

            // Malformed keys that match queue:place:* pattern
            redisTemplate.opsForValue().set("queue:place:invalid", "junk");
            redisTemplate.opsForValue().set("queue:place:-5", "negative");
            redisTemplate.opsForValue().set("queue:place:0", "zero");

            // Other keys (heartbeats, config)
            redisTemplate.opsForValue().set("heartbeat:1:user1", "alive");
            redisTemplate.opsForValue().set("heartbeat:2:user2", "alive");
            redisTemplate.opsForValue().set("place:config:1", "config");
            redisTemplate.opsForValue().set("unrelated:data", "val");

            Set<String> discovered = queueScheduler.scanQueueKeys();

            assertNotNull(discovered);
            assertEquals(2, discovered.size(), "Only the 2 valid queues should be discovered");
            assertTrue(discovered.contains("queue:place:1"));
            assertTrue(discovered.contains("queue:place:2"));
            assertFalse(discovered.contains("queue:place:1:temp_rebuild"));
            assertFalse(discovered.contains("queue:place:2:temp_rebuild"));
            assertFalse(discovered.contains("queue:place:invalid"));
            assertFalse(discovered.contains("queue:place:-5"));
            assertFalse(discovered.contains("queue:place:0"));
            assertFalse(discovered.contains("heartbeat:1:user1"));
        }

        @Test
        @DisplayName("SCAN-04: Returns LinkedHashSet guaranteeing deduplication and discovery order")
        void testDeduplicationAndCollectionType() {
            redisTemplate.opsForList().rightPush("queue:place:10", "u1:1:1000:ACTIVE");
            redisTemplate.opsForList().rightPush("queue:place:20", "u2:1:1000:ACTIVE");

            Set<String> discovered = queueScheduler.scanQueueKeys();

            assertInstanceOf(LinkedHashSet.class, discovered, "Discovered keys should be a LinkedHashSet");
            assertEquals(2, discovered.size());
        }

        @Test
        @DisplayName("SCAN-05: Traverses large keyspace across multiple SCAN batches without omissions")
        void testLargeKeyspaceTraversal() {
            // Populate 150 valid queue keys (SCAN_BATCH_SIZE is 100, requiring multiple cursor steps)
            int totalQueues = 150;
            for (int i = 1; i <= totalQueues; i++) {
                redisTemplate.opsForList().rightPush("queue:place:" + i, "user:1:1000:ACTIVE");
            }

            // Also populate 300 non-queue keys to increase keyspace density
            for (int i = 1; i <= 300; i++) {
                redisTemplate.opsForValue().set("heartbeat:1:user_" + i, "active");
            }

            Set<String> discovered = queueScheduler.scanQueueKeys();

            assertNotNull(discovered);
            assertEquals(totalQueues, discovered.size(), "All 150 queue keys must be discovered across SCAN batches");

            for (int i = 1; i <= totalQueues; i++) {
                assertTrue(discovered.contains("queue:place:" + i), "Missing expected queue: place " + i);
            }
        }
    }

    // =========================================================================
    // 3. Cursor Lifecycle & Exception Handling Tests
    // =========================================================================
    @Nested
    @DisplayName("Cursor Lifecycle & Error Handling Tests")
    class CursorLifecycleTests {

        @Test
        @DisplayName("CUR-01: Cursor is guaranteed closed on normal iteration completion")
        @SuppressWarnings("unchecked")
        void testCursorClosedOnNormalCompletion() throws IOException {
            StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
            Cursor<String> mockCursor = mock(Cursor.class);

            when(mockRedisTemplate.scan(any(ScanOptions.class))).thenReturn(mockCursor);
            when(mockCursor.hasNext()).thenReturn(true, true, false);
            when(mockCursor.next()).thenReturn("queue:place:1", "queue:place:2");

            Set<String> keys = QueueScheduler.scanQueueKeys(mockRedisTemplate);

            assertEquals(2, keys.size());
            assertTrue(keys.contains("queue:place:1"));
            assertTrue(keys.contains("queue:place:2"));
            // Verify cursor close was called via try-with-resources
            verify(mockCursor, times(1)).close();
        }

        @Test
        @DisplayName("CUR-02: Cursor is guaranteed closed when iteration throws an exception")
        @SuppressWarnings("unchecked")
        void testCursorClosedOnIterationException() throws IOException {
            StringRedisTemplate mockRedisTemplate = mock(StringRedisTemplate.class);
            Cursor<String> mockCursor = mock(Cursor.class);

            when(mockRedisTemplate.scan(any(ScanOptions.class))).thenReturn(mockCursor);
            when(mockCursor.hasNext()).thenReturn(true);
            when(mockCursor.next()).thenThrow(new RuntimeException("Redis connection interrupted"));

            RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    QueueScheduler.scanQueueKeys(mockRedisTemplate));

            assertTrue(ex.getMessage().contains("Redis SCAN failed"));
            // Verify cursor close was invoked even on exception
            verify(mockCursor, times(1)).close();
        }

        @Test
        @DisplayName("CUR-03: ProcessQueue tick aborts cleanly when scan fails without partial processing")
        void testProcessQueueAbortsOnScanFailure() {
            StringRedisTemplate failingRedisTemplate = mock(StringRedisTemplate.class);
            PlaceRepository mockPlaceRepo = mock(PlaceRepository.class);
            QueueMetricsRepository mockMetricsRepo = mock(QueueMetricsRepository.class);
            SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
            CommunityService mockComm = mock(CommunityService.class);

            when(failingRedisTemplate.scan(any(ScanOptions.class)))
                    .thenThrow(new RuntimeException("Redis cluster unreachable"));

            QueueScheduler testScheduler = new QueueScheduler(
                    failingRedisTemplate,
                    mockMessaging,
                    mockComm,
                    mockMetricsRepo,
                    mockPlaceRepo
            );

            // Should not throw exception out of scheduled method (aborts tick cleanly)
            assertDoesNotThrow(testScheduler::processQueue);

            // Verify no queue processing occurred
            verify(mockPlaceRepo, never()).findById(any());
            verify(mockMetricsRepo, never()).save(any());
            verify(mockMessaging, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("CUR-04: Per-queue exception isolation preserves processing of remaining queues")
        void testProcessQueuePerQueueIsolation() {
            StringRedisTemplate isolatedRedis = mock(StringRedisTemplate.class);
            Cursor<String> mockCursor = mock(Cursor.class);
            PlaceRepository mockPlaceRepo = mock(PlaceRepository.class);
            QueueMetricsRepository mockMetricsRepo = mock(QueueMetricsRepository.class);
            SimpMessagingTemplate mockMessaging = mock(SimpMessagingTemplate.class);
            CommunityService mockComm = mock(CommunityService.class);

            // SCAN yields place 1 and place 2
            when(isolatedRedis.scan(any(ScanOptions.class))).thenReturn(mockCursor);
            when(mockCursor.hasNext()).thenReturn(true, true, false);
            when(mockCursor.next()).thenReturn("queue:place:1", "queue:place:2");

            org.springframework.data.redis.core.ListOperations<String, String> listOps = mock(org.springframework.data.redis.core.ListOperations.class);
            when(isolatedRedis.opsForList()).thenReturn(listOps);

            // Place 1 throws an error during queue retrieval
            when(listOps.range("queue:place:1", 0, -1)).thenThrow(new RuntimeException("Data corrupt for place 1"));

            // Place 2 succeeds
            when(listOps.range("queue:place:2", 0, -1)).thenReturn(java.util.Collections.emptyList());
            Place place2 = Place.builder().id(2L).name("Test Place 2").baseThroughput(20).build();
            when(mockPlaceRepo.findById(2L)).thenReturn(Optional.of(place2));

            QueueScheduler isolatedScheduler = new QueueScheduler(
                    isolatedRedis,
                    mockMessaging,
                    mockComm,
                    mockMetricsRepo,
                    mockPlaceRepo
            );

            // Should complete tick without crashing
            assertDoesNotThrow(isolatedScheduler::processQueue);

            // Place 2 was still processed despite Place 1 failure
            verify(mockPlaceRepo, times(1)).findById(2L);
            verify(mockMetricsRepo, times(1)).save(any());
        }
    }
}
