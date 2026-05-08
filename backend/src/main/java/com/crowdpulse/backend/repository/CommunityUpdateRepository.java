package com.crowdpulse.backend.repository;

import com.crowdpulse.backend.model.CommunityUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface CommunityUpdateRepository extends JpaRepository<CommunityUpdate, Long> {

    Optional<CommunityUpdate> findTopByPlaceIdOrderByCreatedAtDesc(Long placeId);
    List<CommunityUpdate> findTop10ByPlaceIdOrderByCreatedAtDesc(Long placeId);
    List<CommunityUpdate> findByPlaceIdAndCreatedAtAfterOrderByCreatedAtAsc(Long placeId, LocalDateTime after);
}