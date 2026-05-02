package com.crowdpulse.backend.controller;
import org.springframework.web.bind.annotation.*;
import com.crowdpulse.backend.dto.WaitTimeResponse;
import com.crowdpulse.backend.service.QueueService;

@RestController
@RequestMapping("/api/v1/crowdpulse/queue")
@CrossOrigin(origins = "http://localhost:5173")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/wait-time/{placeId}")
    public WaitTimeResponse getWaitTime(@PathVariable Long placeId) {
        return queueService.calculateWaitTime(placeId);
    }
}
