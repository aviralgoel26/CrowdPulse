package com.crowdpulse.backend.dto;

public class RecommendationResponse {

    private int vibeScore;
    private String vibeLabel;
    private String trend;
    private String recommendation;

    public RecommendationResponse(int vibeScore, String vibeLabel, String trend, String recommendation) {
        this.vibeScore = vibeScore;
        this.vibeLabel = vibeLabel;
        this.trend = trend;
        this.recommendation = recommendation;
    }

    public int getVibeScore() { return vibeScore; }
    public String getVibeLabel() { return vibeLabel; }
    public String getTrend() { return trend; }
    public String getRecommendation() { return recommendation; }
}