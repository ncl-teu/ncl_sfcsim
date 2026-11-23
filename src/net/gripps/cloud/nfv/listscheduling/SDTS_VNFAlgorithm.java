package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.CompositeBandwidthSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Edge Scheduling variant of SDTS (Lou, Tang, Jia, Zhao, Li, "Startup-Aware Dependent Task
 * Scheduling With Bandwidth Constraints in Edge Computing", IEEE TMC 23(2), 2024): task
 * prioritization and edge server selection of Alg. 1-2, without cloud clones and scheduling
 * refinement, i.e. the authors' own "Edge Scheduling" variant of Sec. VI-C / Fig. 8.
 *
 * <ul>
 * <li>Server = VM; virtual cores = the VM's candidate vCPUs (any configured budget applies).
 * Execution time on a vCPU is workload / speed. Environment preparation time is zero,
 * as for every algorithm in this simulator.</li>
 * <li>Priority (Eq. 9-10): average environment download time over servers plus the upward
 * rank built from average execution times over servers and the average per-unit transfer
 * time over all server pairs (zero within a host). The ready task with the highest
 * priority goes first; ties go to the smaller task ID.</li>
 * <li>Environment download (Eq. 2, 12): one sequential download queue per VM. A task whose
 * image is missing on the VM downloads it from the repository, appended to the queue tail of
 * the chosen VM; the download may start before the task's predecessors finish.</li>
 * <li>Platform rule, as for every algorithm: a container runtime keeps the images it has
 * pulled. An image already on the VM is used as is, and one still downloading to it is
 * waited for, not downloaded again. There is no image transfer between VMs.</li>
 * <li>Platform rule shared with Dep-Comp: one fixed bottleneck rate (repository, target
 * host and, across DCs, the DC links); the transfer starts at the first time not before
 * the queue tail at which the full rate fits on every shared link it uses
 * (full duplex). Never split a transfer, change its rate, or use a peer.</li>
 * <li>Server selection (Eq. 11-19): start = insertion-based earliest idle slot on the VM's
 * vCPUs not before max(environment ready, input data ready); pick the vCPU with the
 * earliest finish time (Eq. 19 prints arg max; the text says earliest finish). Ties go
 * to the smaller vCPU prefix.</li>
 * </ul>
 *
 * <p>Enable with {@code run_sdts=1}; disabled by default.</p>
 */
public final class SDTS_VNFAlgorithm extends DHEFT_VNFAlgorithm {
    public static final String LABEL = "SDTS";
    public static final String MODEL_REVISION = "edge-scheduling-vm-reuse-v2";
    /** Data unit for the average per-unit transfer time; keeps rounding negligible. */
    private static final long UNIT_DATA = 1000000L;

    private final Map<String, BandwidthTimeSlot> repositoryLinks = new HashMap<String, BandwidthTimeSlot>();
    private final Map<Long, Double> priority = new HashMap<Long, Double>();

    public SDTS_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.computePriorities();
    }

    // ---------------------------------------------------------------- prioritization

    /** VMs and their candidate vCPUs, in a fixed order. */
    private TreeMap<String, List<VCPU>> serversOf(Map<String, VCPU> candidates) {
        TreeMap<String, List<VCPU>> servers = new TreeMap<String, List<VCPU>>();
        for (VCPU cpu : new TreeMap<String, VCPU>(candidates).values()) {
            VM vm = this.findVM(cpu);
            if (vm == null || cpu.getMips() <= 0L) continue;
            List<VCPU> cores = servers.get(vm.getVMID());
            if (cores == null) {
                cores = new ArrayList<VCPU>();
                servers.put(vm.getVMID(), cores);
            }
            cores.add(cpu);
        }
        return servers;
    }

    private void computePriorities() {
        TreeMap<String, List<VCPU>> servers = this.serversOf(this.vcpuMap);
        List<VCPU> representatives = new ArrayList<VCPU>();
        List<Long> downloadRates = new ArrayList<Long>();
        for (List<VCPU> cores : servers.values()) {
            representatives.add(cores.get(0));
            downloadRates.add(this.downloadRate(this.findVM(cores.get(0))));
        }
        int serverCount = representatives.size();
        if (serverCount == 0) throw new IllegalStateException("SDTS: no VM-bound vCPU available");

        // Average time per unit of data over all ordered server pairs (zero on the diagonal).
        double perUnitSum = 0.0d;
        for (VCPU from : representatives) {
            for (VCPU to : representatives) {
                perUnitSum += this.calcComTime(UNIT_DATA, from, to) / (double) UNIT_DATA;
            }
        }
        double averagePerUnit = perUnitSum / ((double) serverCount * serverCount);

        Map<Long, Double> averageExec = new HashMap<Long, Double>();
        Map<Long, Double> averageDownload = new HashMap<Long, Double>();
        for (VNF task : this.sfc.getVnfMap().values()) {
            double execSum = 0.0d;
            double downloadSum = 0.0d;
            for (Map.Entry<String, List<VCPU>> server : servers.entrySet()) {
                double coreSum = 0.0d;
                for (VCPU cpu : server.getValue()) coreSum += this.calcExecTime(task.getWorkLoad(), cpu);
                execSum += coreSum / server.getValue().size();
            }
            if (this.downloads(task)) {
                for (Long rate : downloadRates) {
                    downloadSum += rate > 0L ? (double) task.getImageSize() / rate : NFVUtil.MAXValue;
                }
            }
            averageExec.put(task.getIDVector().get(1), execSum / serverCount);
            averageDownload.put(task.getIDVector().get(1), downloadSum / serverCount);
        }

        Map<Long, Double> upwardRank = new HashMap<Long, Double>();
        for (VNF task : this.sfc.getVnfMap().values()) {
            this.upwardRank(task, averageExec, averagePerUnit, upwardRank);
        }
        for (VNF task : this.sfc.getVnfMap().values()) {
            Long id = task.getIDVector().get(1);
            this.priority.put(id, averageDownload.get(id) + upwardRank.get(id));
        }
    }

    /** Eq. (10): r_j = max over successors (r_i + e_{j,i} * average per-unit time) + average t_j. */
    private double upwardRank(VNF task, Map<Long, Double> averageExec, double averagePerUnit,
                              Map<Long, Double> memo) {
        Long id = task.getIDVector().get(1);
        Double known = memo.get(id);
        if (known != null) return known;
        double tail = 0.0d;
        for (DataDependence edge : task.getDsucList()) {
            VNF successor = this.sfc.findVNFByLastID(edge.getToID().get(1));
            double value = this.upwardRank(successor, averageExec, averagePerUnit, memo)
                    + edge.getMaxDataSize() * averagePerUnit;
            tail = Math.max(tail, value);
        }
        double rank = tail + averageExec.get(id);
        memo.put(id, rank);
        return rank;
    }

    public double getPriority(VNF task) {
        return this.priority.get(task.getIDVector().get(1));
    }

    @Override
    public VNF selectVNF() {
        VNF selected = null;
        double best = Double.NEGATIVE_INFINITY;
        Iterator<Long> ids = this.freeVNFSet.iterator();
        while (ids.hasNext()) {
            Long id = ids.next();
            double value = this.priority.get(id);
            if (selected == null || value > best
                    || (value == best && id < selected.getIDVector().get(1))) {
                selected = this.sfc.findVNFByLastID(id);
                best = value;
            }
        }
        return selected;
    }

    // ---------------------------------------------------------------- server selection

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> candidates) {
        if (this.scheduleVirtualBoundary(vnf, candidates)) return;

        VCPU bestCpu = null;
        DownloadPlan bestPlan = null;
        double bestStart = NFVUtil.MAXValue;
        double bestFinish = NFVUtil.MAXValue;
        for (List<VCPU> cores : this.serversOf(candidates).values()) {
            DownloadPlan plan = this.planDownload(vnf, this.findVM(cores.get(0)));
            if (plan == null) continue;
            for (VCPU cpu : cores) {
                double execution = this.calcExecTime(vnf.getWorkLoad(), cpu);
                double dataReady = this.calcDeadLine(vnf, cpu).get("arrival_time");
                double ready = Math.max(plan.readyAt, dataReady);
                double start = this.findEarliestExecutionStart(vnf, cpu, ready, execution);
                double finish = start + execution;
                if (!Double.isFinite(finish) || finish >= NFVUtil.MAXValue) continue;
                if (finish < bestFinish
                        || (finish == bestFinish && cpu.getPrefix().compareTo(bestCpu.getPrefix()) < 0)) {
                    bestCpu = cpu;
                    bestPlan = plan;
                    bestStart = start;
                    bestFinish = finish;
                }
            }
        }
        if (bestCpu == null) {
            throw new IllegalStateException("No feasible SDTS placement for VNF " + vnf.getIDVector().get(1));
        }
        this.commitPlacement(vnf, bestCpu, bestPlan, bestStart, bestFinish);
    }

    private boolean downloads(VNF task) {
        return NFVUtil.cloud_container_dl_mode == 1 && task.getImageSize() > 0L
                && !isVirtualBoundary(task);
    }

    /** Bottleneck rate from the repository to the VM's host (b_k of the server). */
    private long downloadRate(VM target) {
        ComputeHost repo = ((NFVEnvironment) this.env).getDockerRepository();
        ComputeHost host = target == null ? null : this.env.getGlobal_hostMap().get(target.getHostID());
        if (repo == null || host == null) return 0L;
        long rate = Math.min(repo.getBw(), host.getBw());
        if (!repo.getDcID().equals(host.getDcID())) {
            Cloud source = this.env.getDcMap().get(repo.getDcID());
            Cloud destination = this.env.getDcMap().get(host.getDcID());
            if (source == null || destination == null) return 0L;
            rate = Math.min(rate, Math.min(source.getBw(), destination.getBw()));
        }
        return rate;
    }

    /** The environment download of {@code vnf} if it were placed on {@code target}; null if impossible. */
    private DownloadPlan planDownload(VNF vnf, VM target) {
        if (target == null) return null;
        if (!this.downloads(vnf)) return DownloadPlan.none();
        // Same-VM reuse (platform rule): ready, or still downloading to this VM.
        double localReady = target.getImageReadyTime(vnf.getType());
        if (localReady < NFVUtil.MAXValue) return DownloadPlan.reuse(localReady);
        if (target.containsType(vnf.getType())) return DownloadPlan.reuse(0.0d);

        ComputeHost repo = ((NFVEnvironment) this.env).getDockerRepository();
        ComputeHost host = this.env.getGlobal_hostMap().get(target.getHostID());
        if (repo == null || host == null) return null;
        long hostCapacity = Math.min(repo.getBw(), host.getBw());
        if (hostCapacity <= 0L) return null;
        long rate = hostCapacity;
        BandwidthTimeSlot hostLink = new CompositeBandwidthSlot("repo-host-path:" + host.getPrefix(), Arrays.asList(
                this.storedLink("repo-host:" + host.getPrefix(), hostCapacity),
                this.storedLink("nic-out:" + repo.getPrefix(), repo.getBw()),
                this.storedLink("nic-in:" + host.getPrefix(), host.getBw())));
        BandwidthTimeSlot dcLink = null;
        if (!repo.getDcID().equals(host.getDcID())) {
            Cloud source = this.env.getDcMap().get(repo.getDcID());
            Cloud destination = this.env.getDcMap().get(host.getDcID());
            if (source == null || destination == null) return null;
            long dcCapacity = Math.min(source.getBw(), destination.getBw());
            if (dcCapacity <= 0L) return null;
            rate = Math.min(rate, dcCapacity);
            dcLink = new CompositeBandwidthSlot("repo-dc-path:" + host.getDcID(), Arrays.asList(
                    this.storedLink("repo-dc:" + host.getDcID(), dcCapacity),
                    this.storedLink("dc-ext-out:" + repo.getDcID(), source.getBw()),
                    this.storedLink("dc-ext-in:" + host.getDcID(), destination.getBw())));
        }

        double duration = (double) vnf.getImageSize() / rate;
        double start = target.getDlQueueFinishTime();   // queue tail tc_k (Eq. 12)
        while (Double.isFinite(start) && start < NFVUtil.MAXValue) {
            double end = start + duration;
            if (end <= start || end >= NFVUtil.MAXValue) return null;
            if (hostLink.getAvailableBW(start, end) >= rate
                    && (dcLink == null || dcLink.getAvailableBW(start, end) >= rate)) {
                return new DownloadPlan(start, end, rate, hostLink, dcLink);
            }
            double next = hostLink.getNextChangeTime(start);
            if (dcLink != null) next = Math.min(next, dcLink.getNextChangeTime(start));
            start = next;
        }
        return null;
    }

    /** The stored record for one link, created empty on first use. */
    private BandwidthTimeSlot storedLink(String key, long capacity) {
        BandwidthTimeSlot link = this.repositoryLinks.get(key);
        if (link == null) {
            link = new BandwidthTimeSlot(key, capacity);
            this.repositoryLinks.put(key, link);
        }
        return link;
    }

    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU cpu) {
        DownloadPlan plan = this.planDownload(vnf, this.findVM(cpu));
        HashMap<String, Double> info = new HashMap<String, Double>();
        info.put("start", plan == null ? (double) NFVUtil.MAXValue : plan.start);
        info.put("finish", plan == null ? (double) NFVUtil.MAXValue : plan.readyAt);
        return info;
    }

    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU cpu) {
        DownloadPlan plan = this.planDownload(vnf, this.findVM(cpu));
        return plan == null ? NFVUtil.MAXValue : plan.readyAt - plan.start;
    }

    private void commitPlacement(VNF vnf, VCPU cpu, DownloadPlan plan, double start, double finish) {
        boolean downloads = plan.rate > 0L;
        if (downloads) {
            String id = LABEL + "^" + this.buildVnfKey(vnf);
            if (plan.dcLink != null && !plan.dcLink.reserveBW(plan.start, plan.readyAt, plan.rate, id)) {
                throw new IllegalStateException("Repository DC reservation rejected: " + id);
            }
            if (!plan.hostLink.reserveBW(plan.start, plan.readyAt, plan.rate, id)) {
                if (plan.dcLink != null) plan.dcLink.releaseBWExact(id);
                throw new IllegalStateException("Repository host reservation rejected: " + id);
            }
        }
        vnf.setDlStartTime(plan.start);
        vnf.setDlFinishTime(plan.readyAt);
        this.recordImageDownloadSource(vnf, downloads, downloads, null);
        if (downloads) {
            this.findVM(cpu).addDLQueue(vnf);
            this.recordImageDownload(true, plan.start, plan.readyAt);
        }
        vnf.setStartTime(start);
        vnf.setFinishTime(finish);
        vnf.setEST(start);
        vnf.setvCPUID(cpu.getPrefix());
        this.addVNFQueue(cpu, vnf);
        this.markImageTypeForVM(vnf, cpu);
        cpu.setFinishTimeAtClusteringPhase(this.calcCT(cpu));
        this.assignedVCPUMap.put(cpu.getPrefix(), cpu);
        ComputeHost host = this.findHost(cpu.getPrefix());
        this.hostSet.put(host.getPrefix(), host);
        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));
        this.updateFreeList(vnf);
    }

    @Override
    public void validateSchedule() {
        super.validateSchedule();
        for (BandwidthTimeSlot link : this.repositoryLinks.values()) link.validateOccupancy();
        for (VM vm : this.env.getGlobal_vmMap().values()) {
            if (vm == null || vm.getDlQueue() == null) continue;
            double tail = 0.0d;
            for (VNF transfer : vm.getDlQueue()) {
                if (transfer.getDlStartTime() < tail || transfer.getDlFinishTime() <= transfer.getDlStartTime()) {
                    throw new IllegalStateException("Invalid sequential download timeline on " + vm.getVMID());
                }
                tail = transfer.getDlFinishTime();
            }
        }
    }

    private static final class DownloadPlan {
        private final double start;
        private final double readyAt;
        private final long rate;
        private final BandwidthTimeSlot hostLink;
        private final BandwidthTimeSlot dcLink;

        private DownloadPlan(double start, double readyAt, long rate, BandwidthTimeSlot hostLink,
                             BandwidthTimeSlot dcLink) {
            this.start = start;
            this.readyAt = readyAt;
            this.rate = rate;
            this.hostLink = hostLink;
            this.dcLink = dcLink;
        }

        private static DownloadPlan none() {
            return new DownloadPlan(0.0d, 0.0d, 0L, null, null);
        }

        private static DownloadPlan reuse(double readyAt) {
            return new DownloadPlan(readyAt, readyAt, 0L, null, null);
        }
    }
}
