package com.crowdpulse.backend.dto;

public class HeartbeatRequest {

    private Long placeId;
    private String userId;
    private long timestamp;

    // ✅ Getter methods (IMPORTANT)
    public Long getPlaceId() {
        return placeId;
    }

    public String getUserId() {
        return userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // ✅ Setter methods (needed for JSON mapping)
    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}