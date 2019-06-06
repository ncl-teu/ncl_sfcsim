package net.gripps.cloud.nfv.fairscheduling;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class FairInfoAtHost {
    private String hostID;

    private double duration;

    private double totalExecTime;

    public FairInfoAtHost(String hostID, double duration, double totalExecTime) {
        this.hostID = hostID;
        this.duration = duration;
        this.totalExecTime = totalExecTime;
    }

    public String getHostID() {
        return hostID;
    }

    public void setHostID(String hostID) {
        this.hostID = hostID;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public double getTotalExecTime() {
        return totalExecTime;
    }

    public void setTotalExecTime(double totalExecTime) {
        this.totalExecTime = totalExecTime;
    }
}
