package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * NHEFT: DHEFT + bandwidth-aware concurrent image download.
 *
 * Dynamic behavior:
 * - Reuse cached image source when available.
 * - Track link occupancy with slots.
 * - During download simulation, task bandwidth changes over time as other tasks end/start.
 */
public class NHEFT_VNFAlgorithm_bak2 extends DHEFT_VNFAlgorithm {

    // EPS: a tiny time offset used to avoid getting stuck on exact boundary timestamps.
    // EPS：微小时间偏移，用于跨过边界时刻，避免在同一时间点反复判断导致死循环。
    private static final double EPS = 0.000001d;
    // If dynamic plan is slower than DHEFT static estimate, cap pessimism.

    // Keyed by logical link id (DC pair / host pair), value is the time-slot BW occupancy model.
    // 按“逻辑链路ID（DC对/Host对）”索引带宽时隙，用于记录和查询带宽占用。
    private final Map<String, BandwidthTimeSlot> linkSlotMap;

    public NHEFT_VNFAlgorithm_bak2(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.linkSlotMap = new HashMap<String, BandwidthTimeSlot>();
    }

    //Data:2026-04-14
    /**
     * NHEFT uses absolute-time link simulation, so EST must read absolute DL start/finish.
     * Base getDLInfo() is duration/queue based and can underestimate when plan.startTime > 0.
     */
    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu) {
        // Return absolute download [start, finish] on the global timeline.
        // 返回全局时间轴上的下载开始/结束时刻（不是单纯时长）。
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

        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan != null) {
            map.put("start", plan.startTime);
            map.put("finish", plan.finishTime);
        }
        return map;
    }

    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU vcpu) {
        // Compatibility path for callers that still expect a duration.
        // 兼容旧调用方：这里返回“下载时长”，而不是绝对完成时刻。
        VM vm = this.findVM(vcpu);
        if (vm == null) {
            return NFVUtil.MAXValue;
        }
        if (vm.containsType(vnf.getType())) {
            return 0.0d;
        }

        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan == null) {
            return this.calcImageComTimeFromRepo(vnf, vcpu);
        }
        // Base getDLInfo() expects duration, not absolute finish time.
        return Math.max(0.0d, plan.finishTime - plan.startTime);
    }

    /**
     * Commit actual reservations after the VNF has been assigned by scheduler.
     */
    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        HashMap<String, Boolean> hadImageBefore = new HashMap<String, Boolean>();
        Iterator<VM> preVmIte = this.env.getGlobal_vmMap().values().iterator();
        while (preVmIte.hasNext()) {
            VM preVm = preVmIte.next();
            // Snapshot pre-schedule state to avoid contamination by base markImageTypeForVM.
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
            vnf.setDlStartTime(0.0d);
            vnf.setDlFinishTime(0.0d);
            return;
        }

        // Use optimistic non-committing evaluation to avoid cascading over-reservation spikes.
        DownloadPlan plan = this.findBestPlan(vnf, assigned, false);
        if (plan == null) {
            return;
        }

        vnf.setDlStartTime(plan.startTime);
        vnf.setDlFinishTime(plan.finishTime);
    }

    private DownloadPlan findBestPlan(VNF vnf, VCPU targetVCPU, boolean commit) {
        // Find the fastest source plan for this target:
        // 1) repo as candidate A
        // 2) every cache-eligible VM as candidate B
        // choose the one with minimum finishTime.
        // 为目标vCPU选择最快下载方案：
        // 先评估repo，再评估所有可复用缓存VM，最终选择finishTime最小的方案。
        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        // Candidate A: Docker repository
        DownloadPlan best = this.buildPlanFromRepo(vnf, targetVCPU, commit);

        // Candidate B: cache VMs that have this image type in typeSet.
        // Keep this optimistic enough for the dynamic model: if typeSet says the image exists,
        // let the plan builder evaluate it directly instead of applying extra ready-time pruning.
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
            DownloadPlan candidate = this.buildPlanFromHost(vnf, targetVCPU, srcHost, false, commit);
            if (candidate != null && (best == null || candidate.finishTime < best.finishTime)) {
                best = candidate;
            }
        }

        best = this.applyPessimismGuard(vnf, targetVCPU, best);

        return best;
    }

    private DownloadPlan applyPessimismGuard(VNF vnf, VCPU targetVCPU, DownloadPlan best) {
        if (best == null) {
            return null;
        }
        double dynamicDur = Math.max(0.0d, best.finishTime - best.startTime);
        double staticDur = super.calcDownloadImageTime(vnf, targetVCPU);
        if (staticDur <= 0.0d || staticDur >= NFVUtil.MAXValue) {
            return best;
        }
        if (dynamicDur <= staticDur) {
            return best;
        }

        // Anchor bounded pre-download near arrival to avoid huge early pessimistic tails.
        double arrival = this.calcDeadLine(vnf, targetVCPU).get("arrival_time");
        double start = Math.max(0.0d, arrival - staticDur);

        DownloadPlan bounded = new DownloadPlan();
        bounded.startTime = start;
        bounded.finishTime = start + staticDur;
        bounded.fromRepo = best.fromRepo;
        return bounded;
    }

    private DownloadPlan buildPlanFromRepo(VNF vnf, VCPU targetVCPU, boolean commit) {
        NFVEnvironment nEnv = (NFVEnvironment) this.env;
        ComputeHost repo = nEnv.getDockerRepository();
        return this.buildPlanFromHost(vnf, targetVCPU, repo, true, commit);
    }

    private DownloadPlan buildPlanFromHost(VNF vnf, VCPU targetVCPU, ComputeHost srcHost, boolean fromRepo, boolean commit) {
        // Build one concrete plan from a fixed source host to the target host.
        // 从一个固定源主机构建到目标主机的具体下载计划。
        ComputeHost dstHost = this.getHostByVCPU(targetVCPU);
        if (dstHost == null || srcHost == null) {
            return null;
        }

        // Same machine => no download needed
        if (srcHost.getMachineID() == dstHost.getMachineID()) {
            DownloadPlan p = new DownloadPlan();
            p.startTime = 0.0d;
            p.finishTime = 0.0d;
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
        long maxTransferBW = Math.min(dcCap, hostCap);
        if (maxTransferBW <= 0) {
            return null;
        }

        BandwidthTimeSlot dcSlot = null;
        if (srcDCID != dstDCID) {
            String dcKey = this.makeDCPairKey(srcDCID, dstDCID);
            dcSlot = this.getOrCreateSlot(dcKey, dcCap);
        }

        String hostKey = this.makeHostPairKey(srcHost.getPrefix(), dstHost.getPrefix());
        BandwidthTimeSlot hostSlot = this.getOrCreateSlot(hostKey, hostCap);

        // Keep pre-download close to execution demand to avoid excessive early reservations.
        double arrival = this.calcDeadLine(vnf, targetVCPU).get("arrival_time");
        double roughDuration = (double) dataSize / (double) maxTransferBW;
        double searchFrom = Math.max(0.0d, arrival - roughDuration);
        double baselineStart = this.findEarliestPositiveStart(dcSlot, hostSlot, searchFrom);
        double start = this.adjustDownloadStartForVariant(
                vnf,
                targetVCPU,
                dcSlot,
                hostSlot,
                maxTransferBW,
                dataSize,
                baselineStart
        );
        DynamicResult res = this.simulateDynamicDownload(start, dataSize, maxTransferBW, dcSlot, hostSlot);

        if (commit) {
            String taskId = this.makeTaskId(vnf, targetVCPU, fromRepo);
            this.commitDynamicReservation(taskId, dcSlot, hostSlot, res);
        }

        DownloadPlan p = new DownloadPlan();
        p.startTime = start;
        p.finishTime = res.finishTime;
        p.fromRepo = fromRepo;
        p.dynamicResult = res;
        return p;
    }

    /**
     * Extension hook for variants (for example NPHEFT) that only want to adjust
     * the planned download start time while reusing all baseline NHEFT logic.
     */
    protected double adjustDownloadStartForVariant(VNF vnf,
                                                   VCPU targetVCPU,
                                                   BandwidthTimeSlot dcSlot,
                                                   BandwidthTimeSlot hostSlot,
                                                   long maxTransferBW,
                                                   long dataSize,
                                                   double baselineStart) {
        return baselineStart;
    }

    private double findEarliestPositiveStart(BandwidthTimeSlot dcSlot, BandwidthTimeSlot hostSlot, double fromTime) {
        // Scan timeline to find the first time point with strictly positive available BW.
        // 扫描时间轴，找到“可用带宽>0”的最早时刻作为下载起点候选。
        double t = Math.max(0.0d, fromTime);
        for (int i = 0; i < 1024; i++) {
            long dcAvail = (dcSlot == null) ? NFVUtil.MAXValue : dcSlot.getAvailableBWAt(t);
            long hostAvail = (hostSlot == null) ? NFVUtil.MAXValue : hostSlot.getAvailableBWAt(t);
            if (Math.min(dcAvail, hostAvail) > 0) {
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

    protected DynamicResult simulateDynamicDownload(double startTime,
                                                    long dataSize,
                                                    long maxTransferBW,
                                                    BandwidthTimeSlot dcSlot,
                                                    BandwidthTimeSlot hostSlot) {
        // Core NHEFT simulation:
        // advance on each BW-change boundary, allocate min(maxTransferBW, link-available),
        // and accumulate segments until all image bytes are transferred.
        // NHEFT核心：
        // 在每个带宽变化边界推进时间，按 min(链路可用, 最大传输带宽) 分配带宽，
        // 持续累积分段，直到镜像全部传完。
        DynamicResult result = new DynamicResult();
        double t = startTime;
        double remaining = (double) dataSize;

        for (int i = 0; i < 4096 && remaining > 0.0d; i++) {
            long dcAvail = (dcSlot == null) ? NFVUtil.MAXValue : dcSlot.getAvailableBWAt(t);
            long hostAvail = (hostSlot == null) ? NFVUtil.MAXValue : hostSlot.getAvailableBWAt(t);
            long allocBW = Math.min(maxTransferBW, Math.min(dcAvail, hostAvail));

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(t);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(t);
            double nextChange = Math.min(nextDc, nextHost);

            if (allocBW <= 0L) {
                if (nextChange == Double.MAX_VALUE) {
                    // no progress path; fallback to avoid dead loop
                    nextChange = t + 1.0d;
                }
                t = nextChange + EPS;
                continue;
            }

            if (nextChange == Double.MAX_VALUE) {
                double dur = remaining / (double) allocBW;
                double end = t + dur;
                result.addSegment(t, end, allocBW);
                t = end;
                remaining = 0.0d;
                break;
            }

            double dur = Math.max(EPS, nextChange - t);
            double transferable = dur * (double) allocBW;
            if (transferable >= remaining) {
                double needDur = remaining / (double) allocBW;
                double end = t + needDur;
                result.addSegment(t, end, allocBW);
                t = end;
                remaining = 0.0d;
                break;
            } else {
                double end = t + dur;
                result.addSegment(t, end, allocBW);
                remaining -= transferable;
                t = end + EPS;
            }
        }

        result.finishTime = t;
        return result;
    }

    private void commitDynamicReservation(String taskId,
                                          BandwidthTimeSlot dcSlot,
                                          BandwidthTimeSlot hostSlot,
                                          DynamicResult result) {
        // Persist each simulated segment into slot occupancy to affect later tasks.
        // 将模拟出的每个分段写入带宽占用表，影响后续任务的可用带宽判断。
        for (int i = 0; i < result.segmentCount; i++) {
            double s = result.startTimes[i];
            double e = result.endTimes[i];
            long bw = result.bandwidths[i];
            String segTaskId = taskId + "#seg" + i;
            if (dcSlot != null) {
                dcSlot.reserveBW(s, e, bw, segTaskId + "#dc");
            }
            if (hostSlot != null) {
                hostSlot.reserveBW(s, e, bw, segTaskId + "#host");
            }
        }
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
        return "NHEFT^" + vnf.getIDVector().get(0) + "^" + vnf.getIDVector().get(1) + "^" + vcpu.getPrefix() + "^" + (fromRepo ? "repo" : "cache");
    }



    private static class DownloadPlan {
        // Absolute timeline fields for one selected source plan.
        // 单个已选下载方案的绝对时间字段。
        double startTime;
        double finishTime;
        boolean fromRepo;
        DynamicResult dynamicResult;
    }

    protected static class DynamicResult {
        // Piecewise transfer trace generated by dynamic simulation.
        // 动态仿真得到的分段传输轨迹。
        int segmentCount = 0;
        double finishTime = 0.0d;
        double[] startTimes = new double[4096];
        double[] endTimes = new double[4096];
        long[] bandwidths = new long[4096];

        void addSegment(double s, double e, long bw) {
            if (segmentCount >= startTimes.length) {
                return;
            }
            startTimes[segmentCount] = s;
            endTimes[segmentCount] = e;
            bandwidths[segmentCount] = bw;
            segmentCount++;
        }
    }
}
