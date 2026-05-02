package com.crowdpulse.backend.controller;

import com.crowdpulse.backend.model.Place;
import com.crowdpulse.backend.model.PlaceDetails;
import com.crowdpulse.backend.model.PlaceType;
import com.crowdpulse.backend.service.PlaceService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crowdpulse/places")
@CrossOrigin(origins = "http://localhost:5173")
public class PlaceController {

    private final PlaceService placeService;

    // Constructor Injection
    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    // GET all places
    @GetMapping
    public List<Place> getAllPlaces() {
        return placeService.getAllPlaces();
    }

    // GET place by ID
    @GetMapping("/{id}")
    public Place getPlaceById(@PathVariable Long id) {
        return placeService.getPlaceById(id);
    }

    // GET places by type
    @GetMapping("/type/{type}")
    public List<Place> getPlacesByType(@PathVariable PlaceType type) {
        return placeService.getPlacesByType(type);
    }

    @GetMapping("/{id}/details")
public ResponseEntity<PlaceDetails> getPlaceDetails(@PathVariable Long id) {
    return ResponseEntity.ok(placeService.getPlaceDetails(id));
}

}