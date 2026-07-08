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
 * NPHEFT (v1): non-splitting pre-download.
 *
 * - Based on DHEFT image-source reuse (repo + cache VM).
 * - Allows "insert into holes" pre-download when a full contiguous slot exists.
 * - Does NOT split one download into multiple segments.
 * - Already reserved tasks are fixed and never reshaped.
 */
public class NPHEFT_VNFAlgorithm_bak extends DHEFT_VNFAlgorithm {

    private static final double EPS = 0.000001d;
    private final Map<String, BandwidthTimeSlot> linkSlotMap;

    public NPHEFT_VNFAlgorithm_bak(CloudEnvironment env, SFC sfc) {
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

        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan != null) {
            map.put("start", plan.startTime);
            map.put("finish", plan.finishTime);
        }
        return map;
    }

    /**
     * Keep compatibility with call-sites expecting duration.
     */
    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU vcpu) {
        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan == null) {
            return this.calcImageComTimeFromRepo(vnf, vcpu);
        }
        return Math.max(0.0d, plan.finishTime - plan.startTime);
    }

    /**
     * Commit single-interval reservation after placement is fixed.
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

        DownloadPlan plan = this.findBestPlan(vnf, assigned, true);
        if (plan == null) {
            return;
        }

        vnf.setDlStartTime(plan.startTime);
        vnf.setDlFinishTime(plan.finishTime);
        assigned.addDLQueue(vnf);
    }

    private DownloadPlan findBestPlan(VNF vnf, VCPU targetVCPU, boolean commit) {
        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        // Candidate A: repo
        DownloadPlan best = this.buildPlanFromRepo(vnf, targetVCPU, commit);

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
            DownloadPlan cand = this.buildPlanFromHost(vnf, targetVCPU, srcHost, false, commit);
            if (cand != null && (best == null || cand.finishTime < best.finishTime)) {
                best = cand;
            }
        }

        return best;
    }

    private DownloadPlan buildPlanFromRepo(VNF vnf, VCPU targetVCPU, boolean commit) {
        NFVEnvironment nEnv = (NFVEnvironment) this.env;
        ComputeHost repo = nEnv.getDockerRepository();
        return this.buildPlanFromHost(vnf, targetVCPU, repo, true, commit);
    }

    private DownloadPlan buildPlanFromHost(VNF vnf, VCPU targetVCPU, ComputeHost srcHost, boolean fromRepo, boolean commit) {
        ComputeHost dstHost = this.getHostByVCPU(targetVCPU);
        if (dstHost == null || srcHost == null) {
            return null;
        }

        // Same machine => no download
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
        long requiredBW = Math.min(dcCap, hostCap);
        if (requiredBW <= 0L) {
            return null;
        }

        double transferTime = CloudUtil.getRoundedValue((double) dataSize / (double) requiredBW);

        BandwidthTimeSlot dcSlot = null;
        if (srcDCID != dstDCID) {
            String dcKey = this.makeDCPairKey(srcDCID, dstDCID);
            dcSlot = this.getOrCreateSlot(dcKey, dcCap);
        }

        String hostKey = this.makeHostPairKey(srcHost.getPrefix(), dstHost.getPrefix());
        BandwidthTimeSlot hostSlot = this.getOrCreateSlot(hostKey, hostCap);

        double start = this.findEarliestWholeSlot(dcSlot, hostSlot, requiredBW, transferTime);
        double finish = start + transferTime;

        if (commit) {
            String taskId = this.makeTaskId(vnf, targetVCPU, fromRepo);
            if (dcSlot != null) {
                dcSlot.reserveBW(start, finish, requiredBW, taskId + "#dc");
            }
            hostSlot.reserveBW(start, finish, requiredBW, taskId + "#host");
        }

        DownloadPlan p = new DownloadPlan();
        p.startTime = start;
        p.finishTime = finish;
        p.fromRepo = fromRepo;
        return p;
    }

    /**
     * Find earliest contiguous interval [start, start+transferTime] with enough bandwidth.
     * No splitting is allowed.
     */
    private double findEarliestWholeSlot(BandwidthTimeSlot dcSlot,
                                         BandwidthTimeSlot hostSlot,
                                         long requiredBW,
                                         double transferTime) {
        double t = 0.0d;

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
        return "NPHEFT^" + vnf.getIDVector().get(0) + "^" + vnf.getIDVector().get(1) + "^" + vcpu.getPrefix() + "^" + (fromRepo ? "repo" : "cache");
    }

    private static class DownloadPlan {
        double startTime;
        double finishTime;
        boolean fromRepo;
    }
}

