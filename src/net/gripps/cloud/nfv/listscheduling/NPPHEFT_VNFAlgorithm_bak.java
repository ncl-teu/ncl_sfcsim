package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.*;

/**
 * NPPHEFT (v1): Non-splitting Pre-download with Parallel-Segment support.
 *
 * - Based on NPHEFT and DHEFT image-source reuse (repo + cache VM).
 * - Allows splitting image download into multiple segments.
 * - Each segment can be scheduled independently into available time slots.
 * - Maximizes bandwidth utilization and minimizes image download completion time.
 * - Previously reserved tasks are fixed and never reshaped.
 */
public class NPPHEFT_VNFAlgorithm_bak extends DHEFT_VNFAlgorithm {

    private static final double EPS = 0.000001d;
    private final Map<String, BandwidthTimeSlot> linkSlotMap;

    public NPPHEFT_VNFAlgorithm_bak(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.linkSlotMap = new HashMap<String, BandwidthTimeSlot>();
    }

    /**
     * Use absolute slot planning (start/finish on global timeline).
     */
    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu) {
        HashMap<String, Double> map = new HashMap<String, Double>();
        map.put("start", 0.0d);
        map.put("finish", 0.0d);

        if (NFVUtil.cloud_container_dl_mode != 1) {
            return map;
        }

        VM vm = this.findVM(vcpu);
        if (vm == null || vm.containsType(vnf.getType())) {
            return map;
        }

        SegmentedDownloadPlan plan = this.findBestSegmentedPlan(vnf, vcpu, false);
        if (plan != null) {
            map.put("start", plan.getOverallStartTime());
            map.put("finish", plan.getOverallFinishTime());
        }
        return map;
    }

    /**
     * Keep compatibility with call-sites expecting duration.
     */
    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU vcpu) {
        SegmentedDownloadPlan plan = this.findBestSegmentedPlan(vnf, vcpu, false);
        if (plan == null) {
            return this.calcImageComTimeFromRepo(vnf, vcpu);
        }
        return Math.max(0.0d, plan.getOverallFinishTime() - plan.getOverallStartTime());
    }

    /**
     * Commit segmented reservation after placement is fixed.
     */
    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        HashMap<String, Boolean> hadImageBefore = new HashMap<String, Boolean>();
        Iterator<VM> preVmIte = this.env.getGlobal_vmMap().values().iterator();
        while (preVmIte.hasNext()) {
            VM preVm = preVmIte.next();
            hadImageBefore.put(preVm.getVMID(), preVm.containsType(vnf.getType()));
        }

        super.scheduleVNF(vnf, map);

        VCPU assigned = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
        if (assigned == null) {
            return;
        }

        VM vm = this.findVM(assigned);
        boolean vmHadImageBefore = (vm != null) && Boolean.TRUE.equals(hadImageBefore.get(vm.getVMID()));
        if (vm == null || vmHadImageBefore) {
        //if (vm == null || vm.containsType(vnf.getType())) {
            vnf.setDlStartTime(0.0d);
            vnf.setDlFinishTime(0.0d);
            return;
        }

        SegmentedDownloadPlan plan = this.findBestSegmentedPlan(vnf, assigned, true);
        if (plan == null) {
            return;
        }

        vnf.setDlStartTime(plan.getOverallStartTime());
        vnf.setDlFinishTime(plan.getOverallFinishTime());
        assigned.addDLQueue(vnf);
    }

    /**
     * Find the best segmented download plan with maximum bandwidth and earliest completion.
     *
     * This method:
     * 1. Tries both repo and cached VMs as sources.
     * 2. For each source, calculates an optimal segmented download plan.
     * 3. Segments can be placed in different time slots (including idle periods before execution).
     * 4. Each segment is scheduled with maximum available bandwidth.
     * 5. Returns the plan that minimizes overall completion time.
     */
    private SegmentedDownloadPlan findBestSegmentedPlan(VNF vnf, VCPU targetVCPU, boolean commit) {
        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        // Candidate A: repo
        SegmentedDownloadPlan best = this.buildSegmentedPlanFromRepo(vnf, targetVCPU, commit);

        // Candidate B: cached VM
        Iterator<VM> vmIte = this.env.getGlobal_vmMap().values().iterator();
        while (vmIte.hasNext()) {
            VM srcVM = vmIte.next();
            if (!srcVM.containsType(vnf.getType())) {
                continue;
            }
            ComputeHost srcHost = this.getHostByPrefix(srcVM.getHostID());
            if (srcHost == null) {
                continue;
            }
            SegmentedDownloadPlan cand = this.buildSegmentedPlanFromHost(vnf, targetVCPU, srcHost, false, commit);
            if (cand != null && (best == null || cand.getOverallFinishTime() < best.getOverallFinishTime())) {
                best = cand;
            }
        }

        return best;
    }

    private SegmentedDownloadPlan buildSegmentedPlanFromRepo(VNF vnf, VCPU targetVCPU, boolean commit) {
        NFVEnvironment nEnv = (NFVEnvironment) this.env;
        ComputeHost repo = nEnv.getDockerRepository();
        return this.buildSegmentedPlanFromHost(vnf, targetVCPU, repo, true, commit);
    }

    /**
     * Build a segmented download plan that maximizes bandwidth usage and minimizes finish time.
     *
     * Strategy:
     * 1. Identify all available time slots (gaps in bandwidth usage).
     * 2. In each slot, try to schedule as much data as possible with available bandwidth.
     * 3. Greedily fill slots to minimize overall completion time.
     * 4. Return the plan with earliest finish time.
     */
    private SegmentedDownloadPlan buildSegmentedPlanFromHost(VNF vnf, VCPU targetVCPU, ComputeHost srcHost, boolean fromRepo, boolean commit) {
        ComputeHost dstHost = this.getHostByVCPU(targetVCPU);
        if (dstHost == null || srcHost == null) {
            return null;
        }

        // Same machine => no download
        if (srcHost.getMachineID() == dstHost.getMachineID()) {
            SegmentedDownloadPlan p = new SegmentedDownloadPlan();
            DownloadSegment seg = new DownloadSegment(0.0d, 0.0d, 0L, 0L);
            p.addSegment(seg);
            p.fromRepo = fromRepo;
            return p;
        }

        long dataSize = vnf.getImageSize();
        long srcDCID = srcHost.getDcID();
        long dstDCID = dstHost.getDcID();

        Cloud srcCloud = this.env.getDcMap().get(srcDCID);
        Cloud dstCloud = this.env.getDcMap().get(dstDCID);
        if (srcCloud == null || dstCloud == null) {
            return null;
        }

        long dcCap = NFVUtil.MAXValue;
        if (srcDCID != dstDCID) {
            dcCap = Math.min(srcCloud.getBw(), dstCloud.getBw());
        }

        long hostCap = Math.min(srcHost.getBw(), dstHost.getBw());
        long requiredBW = Math.min(dcCap, hostCap);
        if (requiredBW <= 0L) {
            return null;
        }

        BandwidthTimeSlot dcSlot = null;
        if (srcDCID != dstDCID) {
            String dcKey = this.makeDCPairKey(srcDCID, dstDCID);
            dcSlot = this.getOrCreateSlot(dcKey, dcCap);
        }

        String hostKey = this.makeHostPairKey(srcHost.getPrefix(), dstHost.getPrefix());
        BandwidthTimeSlot hostSlot = this.getOrCreateSlot(hostKey, hostCap);

        // Calculate segmented download plan
        SegmentedDownloadPlan plan = this.calculateSegmentedDownload(
            dataSize, dcSlot, hostSlot, requiredBW, vnf, targetVCPU, fromRepo);

        if (commit && plan != null) {
            String taskIdPrefix = this.makeTaskId(vnf, targetVCPU, fromRepo);
            for (DownloadSegment seg : plan.segments) {
                if (dcSlot != null && seg.dataSize > 0) {
                    dcSlot.reserveBW(seg.startTime, seg.finishTime, seg.allocatedBW, taskIdPrefix + "#dc#" + seg.segmentIndex);
                }
                if (hostSlot != null && seg.dataSize > 0) {
                    hostSlot.reserveBW(seg.startTime, seg.finishTime, seg.allocatedBW, taskIdPrefix + "#host#" + seg.segmentIndex);
                }
            }
        }

        if (plan != null) {
            plan.fromRepo = fromRepo;
        }
        return plan;
    }

    /**
     * Calculate optimal segmented download plan.
     *
     * This is the core algorithm that:
     * 1. Finds available time slots in both DC and host links.
     * 2. Greedily schedules segments to maximize bandwidth usage.
     * 3. Minimizes the overall completion time.
     */
    private SegmentedDownloadPlan calculateSegmentedDownload(
            long totalDataSize,
            BandwidthTimeSlot dcSlot,
            BandwidthTimeSlot hostSlot,
            long maxBW,
            VNF vnf,
            VCPU targetVCPU,
            boolean fromRepo) {

        SegmentedDownloadPlan result = new SegmentedDownloadPlan();
        long remainingData = totalDataSize;
        double currentTime = 0.0d;
        int segmentIndex = 0;

        // Iteratively find time slots and schedule segments
        for (int iteration = 0; iteration < 4096 && remainingData > 0; iteration++) {
            // Find the next available time slot with enough bandwidth
            double slotStart = currentTime;
            long availableBW = this.getAvailableBWAt(dcSlot, hostSlot, slotStart, maxBW);

            if (availableBW <= 0) {
                // No bandwidth available, move to next change point
                double nextChange = this.getNextBandwidthChangeTime(dcSlot, hostSlot, slotStart);
                if (nextChange == Double.MAX_VALUE) {
                    // No more changes, use remaining time
                    availableBW = this.getAvailableBWAt(dcSlot, hostSlot, slotStart, maxBW);
                    if (availableBW <= 0) {
                        break;
                    }
                } else {
                    currentTime = nextChange + EPS;
                    continue;
                }
            }

            // Find the end of this time slot (when bandwidth changes)
            double slotEnd = this.findSlotEnd(dcSlot, hostSlot, slotStart, availableBW, maxBW);

            // Determine how much data can fit in this slot
            long slotDurationSeconds = (long) Math.ceil(slotEnd - slotStart);
            long dataToSchedule = Math.min(remainingData, availableBW * slotDurationSeconds);

            if (dataToSchedule > 0) {
                double transferTime = (double) dataToSchedule / (double) availableBW;
                double segmentFinish = slotStart + transferTime;

                DownloadSegment seg = new DownloadSegment(
                    slotStart, segmentFinish, dataToSchedule, availableBW);
                seg.segmentIndex = segmentIndex;
                result.addSegment(seg);

                remainingData -= dataToSchedule;
                currentTime = segmentFinish + EPS;
                segmentIndex++;
            } else {
                // Move to next change point
                double nextChange = this.getNextBandwidthChangeTime(dcSlot, hostSlot, slotStart);
                if (nextChange == Double.MAX_VALUE) {
                    break;
                }
                currentTime = nextChange + EPS;
            }
        }

        // If we couldn't schedule all data, fall back to simple approach
        if (remainingData > 0) {
            result = this.simpleFallbackPlan(totalDataSize, maxBW, dcSlot, hostSlot);
        }

        return result.segments.isEmpty() ? null : result;
    }

    /**
     * Simple fallback: schedule all data in one segment starting from earliest available time.
     */
    private SegmentedDownloadPlan simpleFallbackPlan(
            long totalDataSize,
            long maxBW,
            BandwidthTimeSlot dcSlot,
            BandwidthTimeSlot hostSlot) {

        SegmentedDownloadPlan result = new SegmentedDownloadPlan();
        double start = this.findEarliestWholeSlot(dcSlot, hostSlot, maxBW, totalDataSize, maxBW);
        double transferTime = (double) totalDataSize / (double) maxBW;
        double finish = start + transferTime;

        DownloadSegment seg = new DownloadSegment(start, finish, totalDataSize, maxBW);
        seg.segmentIndex = 0;
        result.addSegment(seg);

        return result;
    }

    /**
     * Get available bandwidth at a specific time across both DC and host links.
     */
    private long getAvailableBWAt(BandwidthTimeSlot dcSlot, BandwidthTimeSlot hostSlot, double time, long maxBW) {
        long dcAvail = (dcSlot == null) ? maxBW : dcSlot.getAvailableBWAt(time);
        long hostAvail = (hostSlot == null) ? maxBW : hostSlot.getAvailableBWAt(time);
        return Math.min(dcAvail, hostAvail);
    }

    /**
     * Get the next time when bandwidth availability changes.
     */
    private double getNextBandwidthChangeTime(BandwidthTimeSlot dcSlot, BandwidthTimeSlot hostSlot, double time) {
        double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(time);
        double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(time);
        return Math.min(nextDc, nextHost);
    }

    /**
     * Find where the current time slot ends (when available bandwidth changes or drops to 0).
     */
    private double findSlotEnd(BandwidthTimeSlot dcSlot, BandwidthTimeSlot hostSlot, double start, long currentBW, long maxBW) {
        double t = start;
        for (int i = 0; i < 512; i++) {
            double nextChange = this.getNextBandwidthChangeTime(dcSlot, hostSlot, t);
            if (nextChange == Double.MAX_VALUE) {
                // No more changes, slot extends indefinitely
                return t + 1e6; // Return a large time
            }
            long bwAfterChange = this.getAvailableBWAt(dcSlot, hostSlot, nextChange + EPS, maxBW);
            if (bwAfterChange < currentBW || bwAfterChange <= 0) {
                return nextChange;
            }
            t = nextChange + EPS;
        }
        return t + 1e6;
    }

    /**
     * Find earliest time where a contiguous slot with transferTime duration and requiredBW is available.
     * This is used as fallback when segmentation doesn't help.
     */
    private double findEarliestWholeSlot(BandwidthTimeSlot dcSlot,
                                         BandwidthTimeSlot hostSlot,
                                         long requiredBW,
                                         long totalDataSize,
                                         long maxBW) {
        double t = 0.0d;
        double transferTime = (double) totalDataSize / (double) maxBW;

        for (int i = 0; i < 2048; i++) {
            double end = t + transferTime;
            boolean okDc = (dcSlot == null) || (dcSlot.getAvailableBW(t, end) >= requiredBW);
            boolean okHost = (hostSlot == null) || (hostSlot.getAvailableBW(t, end) >= requiredBW);
            if (okDc && okHost) {
                return t;
            }

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(t);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(t);
            double next = Math.min(nextDc, nextHost);
            if (next == Double.MAX_VALUE) {
                return t;
            }
            t = next + EPS;
        }

        return t;
    }

    private BandwidthTimeSlot getOrCreateSlot(String key, long capacity) {
        BandwidthTimeSlot slot = this.linkSlotMap.get(key);
        if (slot == null) {
            slot = new BandwidthTimeSlot(key, Math.max(1L, capacity));
            this.linkSlotMap.put(key, slot);
        }
        return slot;
    }

    private ComputeHost getHostByVCPU(VCPU vcpu) {
        long dcID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        long hostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        Cloud c = this.env.getDcMap().get(dcID);
        return c == null ? null : c.getComputeHostMap().get(hostID);
    }

    private ComputeHost getHostByPrefix(String hostPrefix) {
        return this.env.getGlobal_hostMap().get(hostPrefix);
    }

    private String makeDCPairKey(long a, long b) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return "DCLink^" + min + "^" + max;
    }

    private String makeHostPairKey(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return "HostLink^" + a + "^" + b;
        }
        return "HostLink^" + b + "^" + a;
    }

    private String makeTaskId(VNF vnf, VCPU vcpu, boolean fromRepo) {
        return "NPPHEFT^" + vnf.getIDVector().get(0) + "^" + vnf.getIDVector().get(1) + "^" + vcpu.getPrefix() + "^" + (fromRepo ? "repo" : "cache");
    }

    /**
     * Represents a single download segment with timing and bandwidth information.
     */
    private static class DownloadSegment {
        double startTime;
        double finishTime;
        long dataSize;
        long allocatedBW;
        int segmentIndex;

        DownloadSegment(double startTime, double finishTime, long dataSize, long allocatedBW) {
            this.startTime = startTime;
            this.finishTime = finishTime;
            this.dataSize = dataSize;
            this.allocatedBW = allocatedBW;
            this.segmentIndex = -1;
        }
    }

    /**
     * Represents a complete download plan comprising multiple segments.
     */
    private static class SegmentedDownloadPlan {
        List<DownloadSegment> segments = new ArrayList<DownloadSegment>();
        boolean fromRepo;

        void addSegment(DownloadSegment seg) {
            this.segments.add(seg);
        }

        double getOverallStartTime() {
            if (this.segments.isEmpty()) {
                return 0.0d;
            }
            double min = Double.MAX_VALUE;
            for (DownloadSegment seg : this.segments) {
                min = Math.min(min, seg.startTime);
            }
            return min;
        }

        double getOverallFinishTime() {
            if (this.segments.isEmpty()) {
                return 0.0d;
            }
            double max = -Double.MAX_VALUE;
            for (DownloadSegment seg : this.segments) {
                max = Math.max(max, seg.finishTime);
            }
            return Math.max(0.0d, max);
        }
    }
}


