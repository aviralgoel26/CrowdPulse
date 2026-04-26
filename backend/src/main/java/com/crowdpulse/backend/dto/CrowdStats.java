package com.crowdpulse.backend.dto;

public class CrowdStats {

    private int activeUsers;
    private int shadowUsers;
    private double effectiveUsers;

    public CrowdStats(int activeUsers, int shadowUsers, double effectiveUsers) {
        this.activeUsers = activeUsers;
        this.shadowUsers = shadowUsers;
        this.effectiveUsers = effectiveUsers;
    }

    public int getActiveUsers() {
        return activeUsers;
    }

    public int getShadowUsers() {
        return shadowUsers;
    }

    public double getEffectiveUsers() {
        return effectiveUsers;
    }
}