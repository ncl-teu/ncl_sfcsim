package net.gripps.cloud.mapreduce.time;

import java.io.Serializable;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/08
 */
public class HistoryInfo implements Serializable {

    private long startTime;

    private long finishTime;

    private String message;

    private long duration;

    public HistoryInfo(long startTime, long finishTime, String message, long duration) {
        this.startTime = startTime;
        this.finishTime = finishTime;
        this.message = message;
        this.duration = duration;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(long finishTime) {
        this.finishTime = finishTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
