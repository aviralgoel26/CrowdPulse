package com.crowdpulse.backend.service;
import com.crowdpulse.backend.model.CommunityUpdate;
import java.time.LocalDateTime;
import java.util.List;

public interface CommunityService {

    CommunityUpdate saveUpdate(CommunityUpdate update);

    CommunityUpdate getLatestUpdate(Long placeId);
    List<CommunityUpdate> getRecentUpdates(Long placeId);
    List<CommunityUpdate> getUpdatesAfter(Long placeId, LocalDateTime after);
}
