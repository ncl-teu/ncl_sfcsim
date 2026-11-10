package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-image, DAG adaptation of the Dep-Comp placement heuristic from Tang et al.
 * Shares the DAG ordering and platform constraints, not NHEFT's placement or
 * image transfer policy.
 *
 * <ul>
 * <li>A VM is the image-cache unit. Check and score only the vCPUs offered by the
 * simulator, including any configured candidate budget.</li>
 * <li>Release is the latest predecessor computation finish, or zero for a root task.
 * Starting at that admission time, require a full feasible execution interval at
 * {@code max(admission, estimatedImageReady)} on each candidate. If none fit,
 * advance admission to the earliest feasible interval and check again. There is
 * no cloud fallback.</li>
 * <li>Rank admitted candidates by {@code max(0, estimatedImageReady - admission) + execution}.
 * Following the download-time definition in LDLS Eq. (1), the image term counts
 * the remaining time until the target VM's image is ready, including its download
 * queue. Additional link waiting comes from this simulator's shared topology.
 * Recompute this score after advancing admission. Data transfer is not a score term.</li>
 * <li>Reuse images only on the target VM, including downloads already in flight.
 * On a miss, append a repository download to that VM's FIFO queue in DAG
 * placement order.</li>
 * <li>Use one fixed bottleneck rate for the entire download. Starting from
 * {@code max(release, vmDownloadTail)}, wait for a continuous interval with enough
 * host/DC link capacity. Never split a transfer, change its rate, or use a peer.</li>
 * <li>Candidate plans do not reserve anything. Commit only the selected transfer,
 * and start computation after image readiness, data arrival, and admission.</li>
 * </ul>
 *
 * <p>Enable with {@code run_dep_comp=1}; disabled by default.</p>
 */
public final class DepComp_VNFAlgorithm extends DHEFT_VNFAlgorithm {
    public static final String LABEL = "DEP-COMP";
    public static final String MODEL_REVISION = "repo-only-vm-fifo-fixed-rate-v1";
    private final Map<String, BandwidthTimeSlot> repositoryLinks = new HashMap<String, BandwidthTimeSlot>();
    private long capacityFallbackCount;

    public DepComp_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> candidates) {
        if (this.scheduleVirtualBoundary(vnf, candidates)) return;

        double release = this.predecessorFinishTime(vnf);
        Map<String, ImagePlan> plansByVM = new HashMap<String, ImagePlan>();
        List<Candidate> placements = new ArrayList<Candidate>();
        double nextAdmission = Double.POSITIVE_INFINITY;
        Candidate selected = null;
        for (VCPU cpu : candidates.values()) {
            VM target = this.findVM(cpu);
            if (target == null || cpu.getMips() <= 0L) continue;
            if (!plansByVM.containsKey(target.getVMID())) {
                plansByVM.put(target.getVMID(), this.planImage(vnf, target, release));
            }
            ImagePlan image = plansByVM.get(target.getVMID());
            if (image == null) continue;
            double execution = this.calcExecTime(vnf.getWorkLoad(), cpu);
            double estimatedStart = Math.max(release, image.readyAt);
            double available = this.findEarliestExecutionStart(vnf, cpu, estimatedStart, execution);
            if (!Double.isFinite(available + execution) || available + execution >= NFVUtil.MAXValue) continue;
            Candidate placement = new Candidate(cpu, image, execution);
            placements.add(placement);
            nextAdmission = Math.min(nextAdmission, available);
            if (available == estimatedStart && placement.precedes(selected, release)) selected = placement;
        }

        double admission = release;
        if (selected == null && !placements.isEmpty()) {
            // No cloud fallback on this platform. Move only the admission clock;
            // the selected transfer can still be planned from the original release.
            admission = nextAdmission;
            this.capacityFallbackCount++;
            for (Candidate placement : placements) {
                double estimatedStart = Math.max(admission, placement.image.readyAt);
                double available = this.findEarliestExecutionStart(vnf, placement.cpu,
                        estimatedStart, placement.execution);
                if (available == estimatedStart && placement.precedes(selected, admission)) selected = placement;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("No feasible Dep-Comp placement for VNF " + vnf.getIDVector().get(1));
        }

        this.commitPlacement(vnf, selected, admission);
    }

    public long getCapacityFallbackCount() {
        return this.capacityFallbackCount;
    }

    private double predecessorFinishTime(VNF vnf) {
        double release = 0.0d;
        for (DataDependence edge : vnf.getDpredList()) {
            VNF predecessor = this.sfc.findVNFByLastID(edge.getFromID().get(1));
            release = Math.max(release, predecessor.getFinishTime());
        }
        return release;
    }

    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU cpu) {
        ImagePlan plan = this.planImage(vnf, this.findVM(cpu), this.predecessorFinishTime(vnf));
        HashMap<String, Double> info = new HashMap<String, Double>();
        info.put("start", plan == null ? (double) NFVUtil.MAXValue : plan.start);
        info.put("finish", plan == null ? (double) NFVUtil.MAXValue : plan.readyAt);
        return info;
    }

    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU cpu) {
        ImagePlan plan = this.planImage(vnf, this.findVM(cpu), this.predecessorFinishTime(vnf));
        return plan == null ? NFVUtil.MAXValue : plan.readyAt - plan.start;
    }

    private ImagePlan planImage(VNF vnf, VM target, double release) {
        if (target == null) return null;
        if (NFVUtil.cloud_container_dl_mode != 1 || vnf.getImageSize() <= 0L) {
            return ImagePlan.reuse(0.0d);
        }
        double localReady = target.getImageReadyTime(vnf.getType());
        if (localReady < NFVUtil.MAXValue) return ImagePlan.reuse(localReady);
        if (target.containsType(vnf.getType())) return ImagePlan.reuse(0.0d);

        ComputeHost repo = ((NFVEnvironment) this.env).getDockerRepository();
        ComputeHost host = this.env.getGlobal_hostMap().get(target.getHostID());
        if (repo == null || host == null) return null;
        long hostCapacity = Math.min(repo.getBw(), host.getBw());
        if (hostCapacity <= 0L) return null;
        long rate = hostCapacity;
        String hostKey = "repo-host:" + host.getPrefix();
        BandwidthTimeSlot hostLink = this.linkOrEmpty(hostKey, hostCapacity);
        String dcKey = null;
        BandwidthTimeSlot dcLink = null;
        if (!repo.getDcID().equals(host.getDcID())) {
            Cloud source = this.env.getDcMap().get(repo.getDcID());
            Cloud destination = this.env.getDcMap().get(host.getDcID());
            if (source == null || destination == null) return null;
            long dcCapacity = Math.min(source.getBw(), destination.getBw());
            if (dcCapacity <= 0L) return null;
            rate = Math.min(rate, dcCapacity);
            dcKey = "repo-dc:" + host.getDcID();
            dcLink = this.linkOrEmpty(dcKey, dcCapacity);
        }

        double duration = (double) vnf.getImageSize() / rate;
        double start = Math.max(release, target.getDlQueueFinishTime());
        while (Double.isFinite(start) && start < NFVUtil.MAXValue) {
            double end = start + duration;
            if (end <= start || end >= NFVUtil.MAXValue) return null;
            if (hostLink.getAvailableBW(start, end) >= rate
                    && (dcLink == null || dcLink.getAvailableBW(start, end) >= rate)) {
                return new ImagePlan(start, end, rate, hostKey, hostLink, dcKey, dcLink);
            }
            // Advance to a capacity event, not a partial transfer at a reduced rate.
            double next = hostLink.getNextChangeTime(start);
            if (dcLink != null) next = Math.min(next, dcLink.getNextChangeTime(start));
            start = next;
        }
        return null;
    }

    private BandwidthTimeSlot linkOrEmpty(String key, long capacity) {
        BandwidthTimeSlot link = this.repositoryLinks.get(key);
        return link == null ? new BandwidthTimeSlot(key, capacity) : link;
    }

    private void commitPlacement(VNF vnf, Candidate selected, double admission) {
        VCPU cpu = selected.cpu;
        ImagePlan image = selected.image;
        double ready = Math.max(admission, Math.max(image.readyAt,
                this.calcDeadLine(vnf, cpu).get("arrival_time")));
        double start = this.findEarliestExecutionStart(vnf, cpu, ready, selected.execution);
        double finish = start + selected.execution;
        if (!Double.isFinite(finish) || finish >= NFVUtil.MAXValue) {
            throw new IllegalStateException("No feasible execution slot for VNF " + vnf.getIDVector().get(1));
        }

        boolean downloads = image.rate > 0L;
        if (downloads) this.reserveRepositoryPath(vnf, image);
        vnf.setDlStartTime(image.start);
        vnf.setDlFinishTime(image.readyAt);
        this.recordImageDownloadSource(vnf, downloads, downloads, null);
        if (downloads) {
            this.findVM(cpu).addDLQueue(vnf);
            this.recordImageDownload(true, image.start, image.readyAt);
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

    private void reserveRepositoryPath(VNF vnf, ImagePlan plan) {
        String id = LABEL + "^" + this.buildVnfKey(vnf);
        if (plan.dcLink != null && !plan.dcLink.reserveBW(plan.start, plan.readyAt, plan.rate, id)) {
            throw new IllegalStateException("Repository DC reservation rejected: " + id);
        }
        if (!plan.hostLink.reserveBW(plan.start, plan.readyAt, plan.rate, id)) {
            if (plan.dcLink != null) plan.dcLink.releaseBWExact(id);
            throw new IllegalStateException("Repository host reservation rejected: " + id);
        }
        this.repositoryLinks.put(plan.hostKey, plan.hostLink);
        if (plan.dcLink != null) this.repositoryLinks.put(plan.dcKey, plan.dcLink);
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
                    throw new IllegalStateException("Invalid FIFO download timeline on " + vm.getVMID());
                }
                tail = transfer.getDlFinishTime();
            }
        }
    }

    private static final class ImagePlan {
        private final double start;
        private final double readyAt;
        private final long rate;
        private final String hostKey;
        private final BandwidthTimeSlot hostLink;
        private final String dcKey;
        private final BandwidthTimeSlot dcLink;

        private ImagePlan(double start, double readyAt, long rate, String hostKey, BandwidthTimeSlot hostLink,
                          String dcKey, BandwidthTimeSlot dcLink) {
            this.start = start;
            this.readyAt = readyAt;
            this.rate = rate;
            this.hostKey = hostKey;
            this.hostLink = hostLink;
            this.dcKey = dcKey;
            this.dcLink = dcLink;
        }

        private static ImagePlan reuse(double readyAt) {
            return new ImagePlan(readyAt, readyAt, 0L, null, null, null, null);
        }
    }

    private static final class Candidate {
        private final VCPU cpu;
        private final ImagePlan image;
        private final double execution;

        private Candidate(VCPU cpu, ImagePlan image, double execution) {
            this.cpu = cpu;
            this.image = image;
            this.execution = execution;
        }

        private double scoreAt(double admission) {
            return Math.max(0.0d, this.image.readyAt - admission) + this.execution;
        }

        private boolean precedes(Candidate other, double admission) {
            if (other == null) return true;
            int byScore = Double.compare(this.scoreAt(admission), other.scoreAt(admission));
            return byScore < 0 || (byScore == 0 && this.cpu.getPrefix().compareTo(other.cpu.getPrefix()) < 0);
        }
    }
}
