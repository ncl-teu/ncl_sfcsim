package net.gripps.cloud.nfv.sfc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 带宽时间槽管理器
 * 用于跟踪DC/Host的带宽占用情况，实现先到先得的资源调度
 */
public class BandwidthTimeSlot {

    /**
     * 资源ID (如 "dc_1", "host_1^0", 等)
     */
    private String resourceId;

    /**
     * 总带宽
     */
    private long totalBW;

    /**
     * 占用记录列表
     */
    private List<BandwidthOccupancy> occupancyList = new ArrayList<BandwidthOccupancy>();

    public BandwidthTimeSlot(String resourceId, long totalBW) {
        this.resourceId = resourceId;
        this.totalBW = totalBW;
    }

    /**
     * 获取 [startTime, endTime] 时间段内可用的最小带宽
     */
    public long getAvailableBW(double startTime, double endTime) {
        long minAvailableBW = this.totalBW;

        Iterator<BandwidthOccupancy> ite = occupancyList.iterator();
        while (ite.hasNext()) {
            BandwidthOccupancy occ = ite.next();
            if (occ.isOverlap(startTime, endTime)) {
                minAvailableBW -= occ.occupiedBW;
                if (minAvailableBW <= 0) {
                    return 0;
                }
            }
        }

        return minAvailableBW;
    }

    /**
     * 获取某个时刻的已用带宽
     */
    public long getUsedBWAt(double time) {
        long used = 0L;
        Iterator<BandwidthOccupancy> ite = occupancyList.iterator();
        while (ite.hasNext()) {
            BandwidthOccupancy occ = ite.next();
            if (occ.startTime <= time && time < occ.endTime) {
                used += occ.occupiedBW;
            }
        }
        return used;
    }

    /**
     * 获取某个时刻的可用带宽
     */
    public long getAvailableBWAt(double time) {
        return Math.max(0L, this.totalBW - this.getUsedBWAt(time));
    }

    /**
     * 获取time之后最近的带宽变化时刻
     */
    public double getNextChangeTime(double time) {
        double next = Double.MAX_VALUE;
        Iterator<BandwidthOccupancy> ite = occupancyList.iterator();
        while (ite.hasNext()) {
            BandwidthOccupancy occ = ite.next();
            if (occ.startTime > time && occ.startTime < next) {
                next = occ.startTime;
            }
            if (occ.endTime > time && occ.endTime < next) {
                next = occ.endTime;
            }
        }
        return next;
    }

    /**
     * 预留带宽
     */
    public boolean reserveBW(double startTime, double endTime, long requiredBW, String taskId) {
        if (requiredBW <= 0) {
            return true;
        }
        long availableBW = getAvailableBW(startTime, endTime);

        if (availableBW >= requiredBW) {
            BandwidthOccupancy occ = new BandwidthOccupancy(startTime, endTime, requiredBW, taskId);
            occupancyList.add(occ);
            return true;
        }
        return false;
    }

    /**
     * 释放带宽
     */
    public void releaseBW(String taskIdPrefix) {
        occupancyList.removeIf(occ -> occ.taskId.startsWith(taskIdPrefix));
    }

    /**
     * 获取在给定时刻之后，带宽何时可满足requiredBW
     */
    public double getNextAvailableTime(double currentTime, long requiredBW) {
        if (this.getAvailableBWAt(currentTime) >= requiredBW) {
            return currentTime;
        }

        double t = currentTime;
        for (int i = 0; i < 512; i++) {
            double next = this.getNextChangeTime(t);
            if (next == Double.MAX_VALUE) {
                return t;
            }
            if (this.getAvailableBWAt(next) >= requiredBW) {
                return next;
            }
            t = next + 0.000001d;
        }
        return t;
    }

    /**
     * 内部类：单次带宽占用记录
     */
    public static class BandwidthOccupancy {
        public double startTime;
        public double endTime;
        public long occupiedBW;
        public String taskId;

        public BandwidthOccupancy(double startTime, double endTime, long occupiedBW, String taskId) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.occupiedBW = occupiedBW;
            this.taskId = taskId;
        }

        public boolean isOverlap(double s, double e) {
            return !(this.endTime <= s || this.startTime >= e);
        }
    }

    @Override
    public String toString() {
        return "BandwidthTimeSlot{" +
                "resourceId='" + resourceId + '\'' +
                ", totalBW=" + totalBW +
                ", occupancyCount=" + occupancyList.size() +
                '}';
    }
}
