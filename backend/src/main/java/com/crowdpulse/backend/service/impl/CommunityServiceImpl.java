package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.CommunityUpdateRepository;
import com.crowdpulse.backend.service.CommunityService;

import org.springframework.stereotype.Service;

@Service
public class CommunityServiceImpl implements CommunityService {

    private final CommunityUpdateRepository repository;

    public CommunityServiceImpl(CommunityUpdateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CommunityUpdate saveUpdate(CommunityUpdate update) {
        return repository.save(update);
    }

    @Override
    public CommunityUpdate getLatestUpdate(Long placeId) {
        return repository
                .findTopByPlaceIdOrderByCreatedAtDesc(placeId)
                .orElse(null);
    }
}