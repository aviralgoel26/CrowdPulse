package com.crowdpulse.backend.dto;

public class WaitTimeResponse {

    private int waitMinutes;
    private int peopleAhead;
    private double avgGroupSize;
    private double throughput;

    public WaitTimeResponse(int waitMinutes,
                            int peopleAhead,
                            double avgGroupSize,
                            double throughput) {
        this.waitMinutes = waitMinutes;
        this.peopleAhead = peopleAhead;
        this.avgGroupSize = avgGroupSize;
        this.throughput = throughput;
    }

    // getters
    public int getWaitMinutes() { return waitMinutes; }
    public int getPeopleAhead() { return peopleAhead; }
    public double getAvgGroupSize() { return avgGroupSize; }
    public double getThroughput() { return throughput; }
}
