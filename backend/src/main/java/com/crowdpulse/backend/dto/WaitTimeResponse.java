package com.crowdpulse.backend.dto;

public class WaitTimeResponse {

    private int waitMinutes;
    private int peopleAhead;
    private double avgGroupSize;
    private double throughput;
    private String queueStatus;
    private double efficiencyFactor;

    public WaitTimeResponse(int waitMinutes,
                            int peopleAhead,
                            double avgGroupSize,
                            double throughput,
                        String queueStatus,
                        double efficiencyFactor) {
        this.waitMinutes = waitMinutes;
        this.peopleAhead = peopleAhead;
        this.avgGroupSize = avgGroupSize;
        this.throughput = throughput;
        this.queueStatus = queueStatus;
        this.efficiencyFactor = efficiencyFactor;
    }

    // getters
    public int getWaitMinutes() { return waitMinutes; }
    public int getPeopleAhead() { return peopleAhead; }
    public double getAvgGroupSize() { return avgGroupSize; }
    public double getThroughput() { return throughput; }
    public String getQueueStatus() { return queueStatus; }
    public double getEfficiencyFactor() { return efficiencyFactor; }
}
