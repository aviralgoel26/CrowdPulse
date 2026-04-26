package com.crowdpulse.backend.dto;

public class VibeResponse {

    private int vibeScore;
    private String label;

    public VibeResponse(int vibeScore, String label) {
        this.vibeScore = vibeScore;
        this.label = label;
    }

    public int getVibeScore() {
        return vibeScore;
    }

    public String getLabel() {
        return label;
    }
}