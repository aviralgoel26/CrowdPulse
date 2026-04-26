package com.crowdpulse.backend.service;

import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.model.PlaceType;

import java.util.List;

public interface PlaceService {

    List<Place> getAllPlaces();

    List<Place> getPlacesByType(PlaceType type);

    Place getPlaceById(Long id);
}