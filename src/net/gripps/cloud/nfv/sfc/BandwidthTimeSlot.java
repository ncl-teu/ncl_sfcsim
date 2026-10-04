package net.gripps.cloud.nfv.sfc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        if (totalBW <= 0) throw new IllegalArgumentException("Bandwidth must be positive: " + resourceId);
        this.resourceId = resourceId;
        this.totalBW = totalBW;
    }

    /**
     * 获取 [startTime, endTime] 时间段内可用的最小带宽
     */
    public long getAvailableBW(double startTime, double endTime) {
        checkInterval(startTime, endTime);
        long used = getUsedBWAt(startTime);
        long peak = used;
        TreeMap<Double, Long> changes = new TreeMap<Double, Long>();
        for (BandwidthOccupancy occ : occupancyList) {
            if (occ.startTime > startTime && occ.startTime < endTime)
                changes.merge(occ.startTime, occ.occupiedBW, Long::sum);
            if (occ.endTime > startTime && occ.endTime < endTime)
                changes.merge(occ.endTime, -occ.occupiedBW, Long::sum);
        }
        // Adjacent reservations do not consume bandwidth simultaneously.
        for (long delta : changes.values()) {
            used += delta;
            peak = Math.max(peak, used);
        }
        return Math.max(0L, totalBW - peak);
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
        checkInterval(startTime, endTime);
        if (requiredBW < 0 || taskId == null) throw new IllegalArgumentException("Invalid reservation");
        if (requiredBW == 0 || startTime == endTime) {
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

    public void releaseBWExact(String taskId) {
        occupancyList.removeIf(occ -> occ.taskId.equals(taskId));
    }

    private static void checkInterval(double start, double end) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end < start)
            throw new IllegalArgumentException("Invalid bandwidth interval: " + start + ", " + end);
    }

    public void validateOccupancy() {
        TreeMap<Double, Long> changes = new TreeMap<Double, Long>();
        for (BandwidthOccupancy occ : occupancyList) {
            checkInterval(occ.startTime, occ.endTime);
            if (occ.endTime == occ.startTime || occ.occupiedBW <= 0 || occ.taskId == null)
                throw new IllegalStateException("Invalid occupancy on " + resourceId);
            changes.merge(occ.startTime, occ.occupiedBW, Long::sum);
            changes.merge(occ.endTime, -occ.occupiedBW, Long::sum);
        }
        long used = 0;
        for (Map.Entry<Double, Long> change : changes.entrySet()) {
            used += change.getValue();
            if (used < 0 || used > totalBW)
                throw new IllegalStateException("Bandwidth capacity exceeded on " + resourceId + " at " + change.getKey());
        }
        if (used != 0) throw new IllegalStateException("Unbalanced bandwidth state on " + resourceId);
    }

    /**
     * 获取在给定时刻之后，带宽何时可满足requiredBW
     */
    public double getNextAvailableTime(double currentTime, long requiredBW) {
        checkInterval(currentTime, currentTime);
        if (requiredBW < 0) throw new IllegalArgumentException("Negative requested bandwidth");
        if (requiredBW > totalBW) return Double.POSITIVE_INFINITY;
        if (this.getAvailableBWAt(currentTime) >= requiredBW) {
            return currentTime;
        }

        double t = currentTime;
        while (true) {
            double next = this.getNextChangeTime(t);
            if (next == Double.MAX_VALUE) {
                return Double.POSITIVE_INFINITY;
            }
            if (this.getAvailableBWAt(next) >= requiredBW) {
                return next;
            }
            t = next;
        }
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
