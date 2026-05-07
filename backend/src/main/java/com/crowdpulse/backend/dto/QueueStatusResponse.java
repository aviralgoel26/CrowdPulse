package com.crowdpulse.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueStatusResponse {

    private Long position;                    // Stable sequence number
    private Integer peopleAhead;              // Dynamic count of people ahead
    private Integer estimatedWaitMinutes;     // Wait time in minutes
    private String estimatedDarshanTime;      // ETA in HH:mm:ss format
    private Integer groupSize;                // User's group size
    private Boolean isUserInQueue;            // Explicit flag
    private String queueState;                // ACTIVE, SHADOW, NOT_FOUND
    private String message;                   // For errors or info
    private Double throughput;                // For debugging/transparency

    // Constructors
    public QueueStatusResponse() {
    }

    public QueueStatusResponse(String message) {
        this.message = message;
        this.isUserInQueue = false;
    }

    // Success constructor
    public QueueStatusResponse(Long position, Integer peopleAhead, Integer estimatedWaitMinutes,
                              String estimatedDarshanTime, Integer groupSize, String queueState,
                              Double throughput) {
        this.position = position;
        this.peopleAhead = peopleAhead;
        this.estimatedWaitMinutes = estimatedWaitMinutes;
        this.estimatedDarshanTime = estimatedDarshanTime;
        this.groupSize = groupSize;
        this.queueState = queueState;
        this.isUserInQueue = true;
        this.throughput = throughput;
    }

    // Getters and Setters
    public Long getPosition() { return position; }
    public void setPosition(Long position) { this.position = position; }

    public Integer getPeopleAhead() { return peopleAhead; }
    public void setPeopleAhead(Integer peopleAhead) { this.peopleAhead = peopleAhead; }

    public Integer getEstimatedWaitMinutes() { return estimatedWaitMinutes; }
    public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) { this.estimatedWaitMinutes = estimatedWaitMinutes; }

    public String getEstimatedDarshanTime() { return estimatedDarshanTime; }
    public void setEstimatedDarshanTime(String estimatedDarshanTime) { this.estimatedDarshanTime = estimatedDarshanTime; }

    public Integer getGroupSize() { return groupSize; }
    public void setGroupSize(Integer groupSize) { this.groupSize = groupSize; }

    public Boolean getIsUserInQueue() { return isUserInQueue; }
    public void setIsUserInQueue(Boolean isUserInQueue) { this.isUserInQueue = isUserInQueue; }

    public String getQueueState() { return queueState; }
    public void setQueueState(String queueState) { this.queueState = queueState; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getThroughput() { return throughput; }
    public void setThroughput(Double throughput) { this.throughput = throughput; }
}
