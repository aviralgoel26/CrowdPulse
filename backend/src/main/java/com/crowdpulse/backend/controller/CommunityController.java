package com.crowdpulse.backend.controller;

import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.service.CommunityService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crowdpulse/community")
@CrossOrigin(origins = "http://localhost:5173")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @PostMapping("/update")
    public CommunityUpdate save(@RequestBody CommunityUpdate update) {
        return service.saveUpdate(update);
    }

    @GetMapping("/latest/{placeId}")
    public CommunityUpdate getLatest(@PathVariable Long placeId) {
        return service.getLatestUpdate(placeId);
    }
}