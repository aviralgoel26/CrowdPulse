package com.crowdpulse.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_metrics")
public class QueueMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long placeId;

    private LocalDateTime timestamp;

    private int activeUsers;
    private int shadowUsers;
    private int totalPeople;
    private int correctedPeople;

    private double throughput;
    private int waitTime;

    // Getters & Setters

    public Long getId() { return id; }

    public Long getPlaceId() { return placeId; }
    public void setPlaceId(Long placeId) { this.placeId = placeId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public int getActiveUsers() { return activeUsers; }
    public void setActiveUsers(int activeUsers) { this.activeUsers = activeUsers; }

    public int getShadowUsers() { return shadowUsers; }
    public void setShadowUsers(int shadowUsers) { this.shadowUsers = shadowUsers; }

    public int getTotalPeople() { return totalPeople; }
    public void setTotalPeople(int totalPeople) { this.totalPeople = totalPeople; }

    public int getCorrectedPeople() { return correctedPeople; }
    public void setCorrectedPeople(int correctedPeople) { this.correctedPeople = correctedPeople; }

    public double getThroughput() { return throughput; }
    public void setThroughput(double throughput) { this.throughput = throughput; }

    public int getWaitTime() { return waitTime; }
    public void setWaitTime(int waitTime) { this.waitTime = waitTime; }
}