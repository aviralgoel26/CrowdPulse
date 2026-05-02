package com.crowdpulse.backend.service.impl;

import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.model.PlaceDetails;
import com.crowdpulse.backend.model.PlaceType;
import com.crowdpulse.backend.repository.PlaceDetailsRepository;
import com.crowdpulse.backend.repository.PlaceRepository;
import com.crowdpulse.backend.service.PlaceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlaceServiceImpl implements PlaceService {

    private final PlaceRepository placeRepository;

   
    public PlaceServiceImpl(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public List<Place> getAllPlaces() {
        return placeRepository.findByIsActiveTrue();
    }

    @Override
    public List<Place> getPlacesByType(PlaceType type) {
        return placeRepository.findByType(type);
    }

    @Override
    public Place getPlaceById(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Place not found"));
    }

    @Autowired
private PlaceDetailsRepository placeDetailsRepository;

@Override
public PlaceDetails getPlaceDetails(Long placeId) {
    return placeDetailsRepository.findByPlaceId(placeId);
}

}