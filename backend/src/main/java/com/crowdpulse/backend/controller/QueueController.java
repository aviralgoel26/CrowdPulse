package com.crowdpulse.backend.controller;

import org.springframework.web.bind.annotation.*;
import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.service.QueueService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crowdpulse/queue")
@CrossOrigin(origins = "http://localhost:5173")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    // 🎟️ JOIN QUEUE
    @PostMapping("/join/{placeId}")
    public Map<String, Object> joinQueue(@PathVariable Long placeId,
                                         @RequestParam String userId) {
        return queueService.joinQueue(placeId, userId);
    }

    // 📊 GET STATUS
    @GetMapping("/status/{placeId}")
    public Map<String, Object> getStatus(@PathVariable Long placeId,
                                         @RequestParam String userId) {
        return queueService.getStatus(placeId, userId);
    }

    // ⏱️ WAIT TIME
    @GetMapping("/wait-time/{placeId}")
    public WaitTimeResponse getWaitTime(@PathVariable Long placeId) {
        return queueService.calculateWaitTime(placeId);
    }

    
}