package com.crowdpulse.backend.repository;

import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.model.PlaceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    // Get all active places
    List<Place> findByIsActiveTrue();

    // Filter by type (QUEUE / VIBE / TIMED)
    List<Place> findByType(PlaceType type);
    
}