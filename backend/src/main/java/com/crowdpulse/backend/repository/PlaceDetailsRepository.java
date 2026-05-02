package com.crowdpulse.backend.repository;

import com.crowdpulse.backend.model.PlaceDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceDetailsRepository extends JpaRepository<PlaceDetails, Long> {
    PlaceDetails findByPlaceId(Long placeId);
}