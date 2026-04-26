package com.crowdpulse.backend.controller;
import com.crowdpulse.backend.dto.VibeResponse;
import com.crowdpulse.backend.dto.HeartbeatRequest;
import com.crowdpulse.backend.dto.RecommendationResponse;
import com.crowdpulse.backend.service.MetricsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crowdpulse/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostMapping("/heartbeat")
    public String updateHeartbeat(@RequestBody HeartbeatRequest request) {
        metricsService.updateHeartbeat(
                request.getPlaceId(),
                request.getUserId(),
                request.getTimestamp()
        );
        return "Heartbeat received";
    }

    @GetMapping("/{placeId}")
    public int getActiveUsers(@PathVariable Long placeId) {
        return metricsService.getActiveUsers(placeId);
    }
    @GetMapping("/vibe/{placeId}")
    public int getVibeScore(@PathVariable Long placeId) {
            return metricsService.getVibeScore(placeId);
}
    @GetMapping("/vibe/details/{placeId}")
    public VibeResponse getVibeDetails(@PathVariable Long placeId) {
        return metricsService.getVibeDetails(placeId);
    }

    @GetMapping("/trend/{placeId}")
public String getTrend(@PathVariable Long placeId) {
    return metricsService.getTrend(placeId);
}

@GetMapping("/recommendation/{placeId}")
public RecommendationResponse getRecommendation(@PathVariable Long placeId) {
    return metricsService.getRecommendation(placeId);
}
}