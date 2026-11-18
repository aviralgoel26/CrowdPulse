package com.crowdpulse.backend.service;

import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.repository.QueueMetricsRepository;
import com.crowdpulse.backend.service.impl.QueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CommunityService communityService;

    @Mock
    private QueueMetricsRepository queueMetricsRepository;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    private QueueServiceImpl queueService;

    // Simulated in-memory Redis store for list operations
    private final Map<String, List<String>> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        redisStore.clear();

        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Simulate opsForList().range(key, 0, -1)
        lenient().when(listOps.range(anyString(), eq(0L), eq(-1L))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            List<String> list = redisStore.get(key);
            return list != null ? new ArrayList<>(list) : Collections.emptyList();
        });

        // Simulate opsForList().rightPush(key, value)
        lenient().when(listOps.rightPush(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            redisStore.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
            return (long) redisStore.get(key).size();
        });

        // Simulate opsForList().size(key)
        lenient().when(listOps.size(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            List<String> list = redisStore.get(key);
            return list != null ? (long) list.size() : 0L;
        });

        // Simulate opsForList().remove(key, 1, value)
        lenient().when(listOps.remove(anyString(), eq(1L), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(2);
            List<String> list = redisStore.get(key);
            if (list != null && list.remove(val)) {
                return 1L;
            }
            return 0L;
        });

        // Simulate opsForValue().set(key, val, duration)
        lenient().doNothing().when(valueOps).set(anyString(), anyString(), any(Duration.class));

        // Simulate redisTemplate.delete(key)
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);

        queueService = new QueueServiceImpl(
                placeRepository,
                redisTemplate,
                communityService,
                queueMetricsRepository
        );
    }

    private Place createActivePlace(Long id) {
        Place place = new Place();
        place.setId(id);
        place.setName("Test Temple");
        place.setQueueStatus("ACTIVE");
        place.setBaseThroughput(30);
        place.setScalingFactor(1.0);
        place.setAdminOffset(0);
        return place;
    }

    @Test
    @DisplayName("POS-01: Community count of 500 does NOT inflate an individual queue position or peopleAhead")
    void testCommunityCountDoesNotInflateIndividualPosition() {
        Long placeId = 1L;
        String userId = "alice";
        long now = System.currentTimeMillis();

        // 1 user in Redis queue
        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                userId + ":2:" + now + ":ACTIVE"
        )));

        // Place configured with scalingFactor=2.0 and adminOffset=50
        Place place = createActivePlace(placeId);
        place.setScalingFactor(2.0);
        place.setAdminOffset(50);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));

        // Community report says 500 people are at the temple
        CommunityUpdate communityUpdate = new CommunityUpdate();
        communityUpdate.setReportedQueueLength(500);
        when(communityService.getLatestUpdate(placeId)).thenReturn(communityUpdate);

        Map<String, Object> status = queueService.getStatus(placeId, userId);

        assertTrue((Boolean) status.get("isUserInQueue"));
        assertEquals(1L, status.get("position"), "Position must remain 1 (1-based list index) and not be inflated to 501");
        assertEquals(0, status.get("peopleAhead"), "People ahead must remain 0 and not be inflated to 500+");
        assertEquals(0, status.get("estimatedWaitMinutes"), "First user in queue must have 0 wait minutes");
    }

    @Test
    @DisplayName("POS-02: FIFO live rank and group-size-based peopleAhead calculation")
    void testFifoLiveRankAndGroupSizeBasedPeopleAhead() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        // 3 entries: Alice (gSize=2), Bob (gSize=3), Carol (gSize=1)
        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + (now - 30000) + ":ACTIVE",
                "bob:3:" + (now - 20000) + ":ACTIVE",
                "carol:1:" + (now - 10000) + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        // Check Alice
        Map<String, Object> aliceStatus = queueService.getStatus(placeId, "alice");
        assertEquals(1L, aliceStatus.get("position"));
        assertEquals(0, aliceStatus.get("peopleAhead"));
        assertEquals(2, aliceStatus.get("groupSize"));

        // Check Bob
        Map<String, Object> bobStatus = queueService.getStatus(placeId, "bob");
        assertEquals(2L, bobStatus.get("position"));
        assertEquals(2, bobStatus.get("peopleAhead"), "Bob must have Alice's groupSize (2) ahead");
        assertEquals(3, bobStatus.get("groupSize"));

        // Check Carol
        Map<String, Object> carolStatus = queueService.getStatus(placeId, "carol");
        assertEquals(3L, carolStatus.get("position"));
        assertEquals(5, carolStatus.get("peopleAhead"), "Carol must have Alice(2) + Bob(3) = 5 people ahead");
        assertEquals(1, carolStatus.get("groupSize"));
    }

    @Test
    @DisplayName("POS-03: Different place queues remain strictly isolated")
    void testDifferentPlaceQueuesRemainIsolated() {
        long now = System.currentTimeMillis();

        // Place 10 has 5 people
        redisStore.put("queue:place:10", new ArrayList<>(List.of(
                "user10_a:5:" + now + ":ACTIVE"
        )));

        // Place 20 has 1 person
        redisStore.put("queue:place:20", new ArrayList<>(List.of(
                "user20_b:2:" + now + ":ACTIVE"
        )));

        when(placeRepository.findById(20L)).thenReturn(Optional.of(createActivePlace(20L)));
        when(communityService.getLatestUpdate(20L)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(20L, "user20_b");

        assertEquals(1L, status.get("position"), "User at Place 20 must have rank 1");
        assertEquals(0, status.get("peopleAhead"), "User at Place 20 must have 0 people ahead, completely isolated from Place 10");
    }

    @Test
    @DisplayName("POS-04: ACTIVE and valid SHADOW entries ahead are both counted")
    void testActiveAndShadowEntriesAheadAreCounted() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        // Entry 1: ACTIVE (size 2)
        // Entry 2: SHADOW (size 3) - valid 5-field entry
        // Entry 3: ACTIVE (size 1) - Carol
        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + (now - 40000) + ":ACTIVE",
                "bob:3:" + (now - 30000) + ":SHADOW:" + (now - 5000),
                "carol:1:" + (now - 10000) + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        // Carol's status
        Map<String, Object> carolStatus = queueService.getStatus(placeId, "carol");
        assertEquals(3L, carolStatus.get("position"));
        assertEquals(5, carolStatus.get("peopleAhead"), "SHADOW entry ahead must be counted (2 + 3 = 5)");

        // Bob's status (SHADOW user himself)
        Map<String, Object> bobStatus = queueService.getStatus(placeId, "bob");
        assertEquals(2L, bobStatus.get("position"));
        assertEquals(2, bobStatus.get("peopleAhead"));
        assertEquals("SHADOW", bobStatus.get("queueState"));
    }

    @Test
    @DisplayName("POS-05: Position matches joinQueue for a newly joined user")
    void testPositionMatchesJoinQueueForNewlyJoinedUser() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        // Existing queue with 2 users
        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + (now - 20000) + ":ACTIVE",
                "bob:2:" + (now - 10000) + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        // Carol joins
        Map<String, Object> joinResult = queueService.joinQueue(placeId, "carol", 1);
        assertTrue((Boolean) joinResult.get("success"));
        assertEquals(3L, joinResult.get("position"), "joinQueue should return position 3");

        // Carol checks status
        Map<String, Object> statusResult = queueService.getStatus(placeId, "carol");
        assertEquals(3L, statusResult.get("position"), "getStatus must match joinQueue position 3");
        assertEquals(4, statusResult.get("peopleAhead"), "People ahead should be 2 + 2 = 4");
    }

    @Test
    @DisplayName("POS-06: Position and peopleAhead update correctly when an earlier user leaves")
    void testPositionAndPeopleAheadUpdateCorrectlyWhenEarlierUserLeaves() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + (now - 30000) + ":ACTIVE",
                "bob:3:" + (now - 20000) + ":ACTIVE",
                "carol:1:" + (now - 10000) + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        // Before Alice leaves: Bob is #2 with 2 ahead, Carol is #3 with 5 ahead
        assertEquals(2L, queueService.getStatus(placeId, "bob").get("position"));
        assertEquals(2, queueService.getStatus(placeId, "bob").get("peopleAhead"));
        assertEquals(3L, queueService.getStatus(placeId, "carol").get("position"));
        assertEquals(5, queueService.getStatus(placeId, "carol").get("peopleAhead"));

        // Alice leaves the queue
        Map<String, Object> leaveResult = queueService.leaveQueue(placeId, "alice");
        assertTrue((Boolean) leaveResult.get("success"));

        // After Alice leaves: Bob advances to #1 with 0 ahead, Carol advances to #2 with 3 ahead
        Map<String, Object> bobStatus = queueService.getStatus(placeId, "bob");
        assertEquals(1L, bobStatus.get("position"), "Bob must advance to rank 1");
        assertEquals(0, bobStatus.get("peopleAhead"), "Bob must now have 0 people ahead");

        Map<String, Object> carolStatus = queueService.getStatus(placeId, "carol");
        assertEquals(2L, carolStatus.get("position"), "Carol must advance to rank 2");
        assertEquals(3, carolStatus.get("peopleAhead"), "Carol must now have Bob's group size (3) ahead");
    }

    @Test
    @DisplayName("POS-07: No simulated processedPeople subtraction - elapsed time does not reduce peopleAhead")
    void testNoSimulatedProcessedPeopleSubtraction() {
        Long placeId = 1L;
        // User joined 3 hours ago, but preceding users have not left Redis
        long threeHoursAgo = System.currentTimeMillis() - (3 * 3600 * 1000L);

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:4:" + (threeHoursAgo - 60000) + ":ACTIVE",
                "bob:1:" + threeHoursAgo + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> bobStatus = queueService.getStatus(placeId, "bob");

        assertEquals(2L, bobStatus.get("position"));
        assertEquals(4, bobStatus.get("peopleAhead"),
                "peopleAhead must remain 4 because Alice is still in Redis, without artificial processedPeople subtraction");
    }

    @Test
    @DisplayName("POS-08: PAUSED queue returns null estimated wait and appropriate status message")
    void testPausedQueueEtaBehavior() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:5:" + (now - 20000) + ":ACTIVE",
                "bob:1:" + now + ":ACTIVE"
        )));

        Place place = createActivePlace(placeId);
        place.setQueueStatus("PAUSED");
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "bob");

        assertNull(status.get("estimatedWaitMinutes"), "Paused queue must return null estimated wait");
        assertNotNull(status.get("estimatedDarshanTime"), "Estimated darshan time should contain status indicator");
        assertTrue(((String) status.get("estimatedDarshanTime")).contains("PAUSED"));
        assertEquals("PAUSED", status.get("queueState"));
        assertEquals(5, status.get("peopleAhead"), "peopleAhead count is still reported correctly");
    }

    @Test
    @DisplayName("POS-09: CLOSED queue returns null estimated wait and appropriate status message")
    void testClosedQueueEtaBehavior() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + now + ":ACTIVE"
        )));

        Place place = createActivePlace(placeId);
        place.setQueueStatus("CLOSED");
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "alice");

        assertNull(status.get("estimatedWaitMinutes"), "Closed queue must return null estimated wait");
        assertTrue(((String) status.get("estimatedDarshanTime")).contains("CLOSED"));
        assertEquals("CLOSED", status.get("queueState"));
    }

    @Test
    @DisplayName("POS-10: Community update queueStatus overrides place config (precedence)")
    void testCommunityStatusPrecedence() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + now + ":ACTIVE"
        )));

        // Place says ACTIVE, but community update says PAUSED
        Place place = createActivePlace(placeId);
        place.setQueueStatus("ACTIVE");
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));

        CommunityUpdate communityUpdate = new CommunityUpdate();
        communityUpdate.setQueueStatus("PAUSED");
        when(communityService.getLatestUpdate(placeId)).thenReturn(communityUpdate);

        Map<String, Object> status = queueService.getStatus(placeId, "alice");

        assertNull(status.get("estimatedWaitMinutes"), "Community PAUSED override must result in null estimated wait");
        assertEquals("PAUSED", status.get("queueState"));
    }

    @Test
    @DisplayName("POS-11: Active queue ETA uses actual Redis peopleAhead")
    void testActiveQueueEtaUsesActualPeopleAhead() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        // 10 people ahead of Bob (Alice has gSize=10)
        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:10:" + (now - 10000) + ":ACTIVE",
                "bob:1:" + now + ":ACTIVE"
        )));

        Place place = createActivePlace(placeId);
        place.setBaseThroughput(30); // 30 people/min
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "bob");

        assertNotNull(status.get("estimatedWaitMinutes"));
        int wait = (Integer) status.get("estimatedWaitMinutes");
        assertTrue(wait > 0, "Wait minutes should be positive based on 10 people ahead");
        assertNotNull(status.get("estimatedDarshanTime"));
    }

    @Test
    @DisplayName("POS-12: User not present in queue returns isUserInQueue = false")
    void testUserNotPresentInQueue() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:2:" + now + ":ACTIVE"
        )));

        Map<String, Object> status = queueService.getStatus(placeId, "charlie_not_in_queue");

        assertFalse((Boolean) status.get("isUserInQueue"));
        assertEquals("User not in queue", status.get("message"));
    }

    @Test
    @DisplayName("POS-13: Malformed queue entries ahead are handled safely without crashing")
    void testMalformedQueueEntriesHandledSafely() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "no_colons_malformed",
                "bad_user:not_a_number:" + now + ":ACTIVE",
                "valid_user:3:" + now + ":ACTIVE",
                "target_user:1:" + now + ":ACTIVE"
        )));

        when(placeRepository.findById(placeId)).thenReturn(Optional.of(createActivePlace(placeId)));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "target_user");

        assertTrue((Boolean) status.get("isUserInQueue"));
        assertEquals(4L, status.get("position"), "Target user is at index 3 -> position 4");
        assertEquals(3, status.get("peopleAhead"), "Only the valid entry's groupSize (3) should be counted");
    }

    @Test
    @DisplayName("POS-14: Zero effective throughput when peopleAhead > 0 returns null estimated wait and ETA")
    void testZeroThroughputWithPeopleAheadReturnsNullWaitTime() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:5:" + (now - 10000) + ":ACTIVE",
                "bob:1:" + now + ":ACTIVE"
        )));

        Place place = createActivePlace(placeId);
        place.setBaseThroughput(0); // Zero throughput
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "bob");

        assertTrue((Boolean) status.get("isUserInQueue"));
        assertEquals(2L, status.get("position"));
        assertEquals(5, status.get("peopleAhead"));
        assertNull(status.get("estimatedWaitMinutes"),
                "estimatedWaitMinutes must be null when peopleAhead > 0 and throughput is zero");
        assertNull(status.get("estimatedDarshanTime"),
                "estimatedDarshanTime must be null when peopleAhead > 0 and throughput is zero");
    }

    @Test
    @DisplayName("POS-15: Non-positive (negative) effective throughput when peopleAhead > 0 returns null estimated wait and ETA")
    void testNegativeThroughputWithPeopleAheadReturnsNullWaitTime() {
        Long placeId = 1L;
        long now = System.currentTimeMillis();

        redisStore.put("queue:place:" + placeId, new ArrayList<>(List.of(
                "alice:5:" + (now - 10000) + ":ACTIVE",
                "bob:1:" + now + ":ACTIVE"
        )));

        Place place = createActivePlace(placeId);
        place.setBaseThroughput(-10); // Negative throughput
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(communityService.getLatestUpdate(placeId)).thenReturn(null);

        Map<String, Object> status = queueService.getStatus(placeId, "bob");

        assertTrue((Boolean) status.get("isUserInQueue"));
        assertEquals(2L, status.get("position"));
        assertEquals(5, status.get("peopleAhead"));
        assertNull(status.get("estimatedWaitMinutes"),
                "estimatedWaitMinutes must be null when peopleAhead > 0 and throughput is negative");
        assertNull(status.get("estimatedDarshanTime"),
                "estimatedDarshanTime must be null when peopleAhead > 0 and throughput is negative");
    }
}
