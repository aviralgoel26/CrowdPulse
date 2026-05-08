package com.crowdpulse.backend.service;

import com.crowdpulse.backend.dto.WaitTimeResponse;
import java.util.List;
import java.util.Map;

public interface QueueService {

    // 🔹 Existing
    WaitTimeResponse calculateWaitTime(Long placeId);

    // 🔹 New
    Map<String, Object> joinQueue(Long placeId, String userId, int groupSize);

    Map<String, Object> getStatus(Long placeId, String userId);

    // 🔹 NEW: Leave Queue Properly
    Map<String, Object> leaveQueue(Long placeId, String userId);

    // 🔹 NEW: Historical time-series for charting
    List<Map<String, Object>> getTimeSeries(Long placeId);
}