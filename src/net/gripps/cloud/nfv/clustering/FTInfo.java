package net.gripps.cloud.nfv.clustering;

import java.io.Serializable;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/18.
 */
public class FTInfo implements Serializable {
    private double finishTime;

    private String prefix;

    public FTInfo() {

    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
