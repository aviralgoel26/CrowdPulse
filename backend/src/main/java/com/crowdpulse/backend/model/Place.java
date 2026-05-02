package com.crowdpulse.backend.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "places")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private PlaceType type;

    private String city;
    private String state;
    private String country;

    private double latitude;
    private double longitude;

    private int radius;
    private int capacity;


    private double scalingFactor = 1.2; // default
    private int adminOffset = 0;

    // Getters and Setters
    public int getCapacity() {
    return capacity;
    }
    public void setCapacity(int capacity) {
    this.capacity = capacity;
    }

    public PlaceType getType() {
    return type;
}
public void setType(PlaceType type) {
    this.type = type;
}
public double getScalingFactor() {
    return scalingFactor;
}

public int getAdminOffset() {
    return adminOffset;
}

    
    @Column(name = "is_active")
    private boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "place", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
@JsonIgnore
private PlaceDetails placeDetails;
}