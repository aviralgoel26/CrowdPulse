package com.crowdpulse.backend.model;

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

    // Getters and Setters
    public int getCapacity() {
    return capacity;
    }
    public void setCapacity(int capacity) {
    this.capacity = capacity;
    }

    
    @Column(name = "is_active")
    private boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}