package net.gripps.cloud.nfv.fairscheduling;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class HostStatistics {

    private String hostID;

    private double attr_load;

    private double attr_duration;

    public HostStatistics(String hostID, double attr_load, double attr_duration) {
        this.hostID = hostID;
        this.attr_load = attr_load;
        this.attr_duration = attr_duration;
    }

    public String getHostID() {
        return hostID;
    }

    public void setHostID(String hostID) {
        this.hostID = hostID;
    }

    public double getAttr_load() {
        return attr_load;
    }

    public void setAttr_load(double attr_load) {
        this.attr_load = attr_load;
    }

    public double getAttr_duration() {
        return attr_duration;
    }

    public void setAttr_duration(double attr_duration) {
        this.attr_duration = attr_duration;
    }
}
