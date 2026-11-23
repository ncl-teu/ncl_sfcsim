package net.gripps.cloud.nfv.sfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A view over several bandwidth records that one transfer crosses at the same time,
 * for example a DC pair and the external links of both DCs.
 * Queries return the tightest value over all members; a reservation is recorded on
 * every member or on none. The members are ordinary {@link BandwidthTimeSlot}s owned
 * by the scheduler, so their own {@code validateOccupancy} checks each capacity.
 */
public class CompositeBandwidthSlot extends BandwidthTimeSlot {
    private final List<BandwidthTimeSlot> members;

    public CompositeBandwidthSlot(String resourceId, List<BandwidthTimeSlot> members) {
        super(resourceId, minimumCapacity(members));
        this.members = Collections.unmodifiableList(new ArrayList<BandwidthTimeSlot>(members));
    }

    private static long minimumCapacity(List<BandwidthTimeSlot> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("A composite bandwidth slot needs at least one member");
        }
        long capacity = Long.MAX_VALUE;
        for (BandwidthTimeSlot member : members) {
            if (member == null || member instanceof CompositeBandwidthSlot) {
                throw new IllegalArgumentException("Members must be plain bandwidth slots");
            }
            capacity = Math.min(capacity, member.getTotalBW());
        }
        return capacity;
    }

    public List<BandwidthTimeSlot> getMembers() {
        return this.members;
    }

    @Override
    public long getAvailableBW(double startTime, double endTime) {
        long available = Long.MAX_VALUE;
        for (BandwidthTimeSlot member : this.members) {
            available = Math.min(available, member.getAvailableBW(startTime, endTime));
        }
        return available;
    }

    @Override
    public long getUsedBWAt(double time) {
        long used = 0L;
        for (BandwidthTimeSlot member : this.members) {
            used = Math.max(used, member.getUsedBWAt(time));
        }
        return used;
    }

    @Override
    public long getAvailableBWAt(double time) {
        long available = Long.MAX_VALUE;
        for (BandwidthTimeSlot member : this.members) {
            available = Math.min(available, member.getAvailableBWAt(time));
        }
        return available;
    }

    @Override
    public double getNextChangeTime(double time) {
        double next = Double.MAX_VALUE;
        for (BandwidthTimeSlot member : this.members) {
            next = Math.min(next, member.getNextChangeTime(time));
        }
        return next;
    }

    @Override
    public boolean reserveBW(double startTime, double endTime, long requiredBW, String taskId) {
        if (requiredBW == 0 || startTime == endTime) {
            return super.reserveBW(startTime, endTime, requiredBW, taskId);
        }
        if (this.getAvailableBW(startTime, endTime) < requiredBW) {
            return false;
        }
        List<BandwidthTimeSlot> done = new ArrayList<BandwidthTimeSlot>();
        for (BandwidthTimeSlot member : this.members) {
            if (!member.reserveBW(startTime, endTime, requiredBW, taskId)) {
                for (BandwidthTimeSlot reserved : done) {
                    reserved.releaseBWExact(taskId);
                }
                return false;
            }
            done.add(member);
        }
        return true;
    }

    @Override
    public void releaseBW(String taskIdPrefix) {
        for (BandwidthTimeSlot member : this.members) {
            member.releaseBW(taskIdPrefix);
        }
    }

    @Override
    public void releaseBWExact(String taskId) {
        for (BandwidthTimeSlot member : this.members) {
            member.releaseBWExact(taskId);
        }
    }

    @Override
    public void validateOccupancy() {
        for (BandwidthTimeSlot member : this.members) {
            member.validateOccupancy();
        }
    }

    @Override
    public String toString() {
        return "CompositeBandwidthSlot{members=" + this.members + "}";
    }
}
