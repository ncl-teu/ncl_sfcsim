package net.gripps.cloud.nfv.fairscheduling;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class FairnessIndexInfo {

    private double loadFairness;

    private double durationFairness;

    public FairnessIndexInfo(double loadFairness, double durationFairness) {
        this.loadFairness = loadFairness;
        this.durationFairness = durationFairness;
    }

    public double getLoadFairness() {
        return loadFairness;
    }

    public void setLoadFairness(double loadFairness) {
        this.loadFairness = loadFairness;
    }

    public double getDurationFairness() {
        return durationFairness;
    }

    public void setDurationFairness(double durationFairness) {
        this.durationFairness = durationFairness;
    }
}
