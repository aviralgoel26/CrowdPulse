package com.crowdpulse.backend.service;
import com.crowdpulse.backend.model.CommunityUpdate;

public interface CommunityService {

    CommunityUpdate saveUpdate(CommunityUpdate update);

    CommunityUpdate getLatestUpdate(Long placeId);
}
