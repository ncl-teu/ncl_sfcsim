package net.gripps.cloud.nfv.fairscheduling;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class FairInfoAtVCPU {

    private double startTime;

    private double finishTime;

    private double totalExecTime;

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    public FairInfoAtVCPU(double startTime, double finishTime) {
        this.startTime = startTime;
        this.finishTime = finishTime;
    }

    public double getTotalExecTime() {
        return totalExecTime;
    }

    public void setTotalExecTime(double totalExecTime) {
        this.totalExecTime = totalExecTime;
    }
}
