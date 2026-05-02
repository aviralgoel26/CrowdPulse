package com.crowdpulse.backend.service;
import com.crowdpulse.backend.dto.WaitTimeResponse;
public interface QueueService {
    WaitTimeResponse calculateWaitTime(Long placeId);
}
