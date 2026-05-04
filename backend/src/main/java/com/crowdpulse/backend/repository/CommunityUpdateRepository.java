package com.crowdpulse.backend.repository;

import com.crowdpulse.backend.model.CommunityUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityUpdateRepository extends JpaRepository<CommunityUpdate, Long> {

    Optional<CommunityUpdate> findTopByPlaceIdOrderByCreatedAtDesc(Long placeId);
}