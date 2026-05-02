package com.crowdpulse.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "place_details")
@Data
public class PlaceDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 RELATION
    @OneToOne
    @JoinColumn(name = "place_id")
    private Place place;

    private String description;

    private String significance;

    private String bestTime;

    @Column(columnDefinition = "json")
    private String peakMonths;

    @Column(columnDefinition = "json")
    private String offPeakMonths;

    @Column(columnDefinition = "json")
    private String dailyTimings;

    @Column(columnDefinition = "json")
    private String rituals;

    @Column(columnDefinition = "json")
    private String reachInfo;

    @Column(columnDefinition = "json")
    private String images;

    @Column(columnDefinition = "json")
    private String tags;

    private Integer avgDailyFootfall;

    private Double festivalRushMultiplier;

    @Column(columnDefinition = "json")
    private String queueConfig;
}