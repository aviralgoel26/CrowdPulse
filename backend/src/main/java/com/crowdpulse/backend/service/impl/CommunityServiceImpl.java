package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.model.CommunityUpdate;
import com.crowdpulse.backend.repository.CommunityUpdateRepository;
import com.crowdpulse.backend.service.CommunityService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class CommunityServiceImpl implements CommunityService {

    private final CommunityUpdateRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    public CommunityServiceImpl(CommunityUpdateRepository repository, SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public CommunityUpdate saveUpdate(CommunityUpdate update) {
        CommunityUpdate saved =
        repository.save(update);

messagingTemplate.convertAndSend(
        "/topic/queue/" + update.getPlaceId(),
        "COMMUNITY_UPDATE"
);

return saved;
    }

    @Override
    public CommunityUpdate getLatestUpdate(Long placeId) {
        return repository
                .findTopByPlaceIdOrderByCreatedAtDesc(placeId)
                .orElse(null);
    }
    @Override
public List<CommunityUpdate> getRecentUpdates(Long placeId) {

    return repository
            .findTop10ByPlaceIdOrderByCreatedAtDesc(placeId);
}
}