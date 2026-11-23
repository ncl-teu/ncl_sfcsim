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
 * BPPTS (Wang, Chen, Wang, Gao, Bian, Qiao, "An enhanced list scheduling algorithm for
 * heterogeneous computing using an optimized Predictive Cost Matrix", FGCS 166, 2025),
 * container-adapted: the algorithm does not know container images; the platform makes each
 * task's image available on its VM before it runs.
 *
 * <p>Decision, exactly as in the paper (verified against its Tables 1-3 and Fig. 3a):</p>
 * <ul>
 * <li>Processors = the candidate vCPUs; w(v,p) = workload / speed; w̄ = average over processors.
 * Communication cost of an edge c = data / B̄, with B̄ the average bandwidth of the network
 * paths between processors on different hosts (Eq. 2, startup L = 0); c = 0 on the same
 * processor.</li>
 * <li>BPCM (Eq. 17): BPCM(v,p) = w̄(v) + max over successors s of min over q of
 * (BPCM(s,q) + w(s,q) + c(v,s)·[q != p]); exit: BPCM(v,p) = w(v,p).</li>
 * <li>rank (Eq. 18) = mean_p BPCM(v,p) · (1 + w̄(v) / c̄(v)), c̄(v) = mean cost of v's outgoing
 * edges with data (the reading that reproduces Table 2 best); factor 1 when there is none.
 * The ready task with the highest rank goes first; ties go to the smaller task ID.</li>
 * <li>Processor (Eq. 19, Alg. 1): EST = max(avail(p), max over predecessors (AFT + c)), with
 * avail(p) the finish of p's last task (no insertion: the paper defines no insertion step);
 * EFT = EST + w(v,p). If out-degree &gt; number of processors, minimum EFT; otherwise minimum
 * BEFT = EFT + BPCM(v,p). Ties go to the smaller EFT (reproduces Table 3), then the smaller
 * vCPU prefix.</li>
 * </ul>
 *
 * <p>Execution on the chosen vCPU (platform rules, as for Dep-Comp): a container runtime keeps
 * the images it has pulled, so an image already on the VM is used as is and one still
 * downloading to it is waited for; there is no image transfer between VMs. A missing image is
 * downloaded from the repository into the VM's FIFO queue, starting at max(latest predecessor
 * finish, queue tail) and at the first time the fixed bottleneck rate fits on every shared link. The task starts at the first feasible time not before its image,
 * its actual input data (simulator transfer times) and the vCPU's last finish. The decision
 * never sees image times.</p>
 *
 * <p>Enable with {@code run_bppts=1}; disabled by default.</p>
 */
public final class BPPTS_VNFAlgorithm extends DHEFT_VNFAlgorithm {
    public static final String LABEL = "BPPTS";
    public static final String MODEL_REVISION = "container-adapted-vm-reuse-v2";
    private static final long UNIT_DATA = 1000000L;

    private final Map<String, BandwidthTimeSlot> repositoryLinks = new HashMap<String, BandwidthTimeSlot>();
    private final List<VCPU> processors = new ArrayList<VCPU>();
    private final Map<String, Integer> processorIndex = new HashMap<String, Integer>();
    private final Map<Long, Integer> taskIndex = new HashMap<Long, Integer>();
    private double averageBandwidth;
    private double[][] bpcm;
    private double[] rank;

    public BPPTS_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.prepare();
    }

    // ---------------------------------------------------------------- pure BPPTS formulas

    /**
     * Eq. (17). succ[v] lists successor indices, edgeCost[v][k] the cost of edge v -> succ[v][k],
     * exec[v][p] the execution time. Tasks must be indexed in topological order.
     */
    public static double[][] computeBPCM(int[][] succ, double[][] edgeCost, double[][] exec) {
        int n = exec.length;
        int p = n == 0 ? 0 : exec[0].length;
        double[][] bpcm = new double[n][p];
        for (int v = n - 1; v >= 0; v--) {
            if (succ[v].length == 0) {
                bpcm[v] = exec[v].clone();
                continue;
            }
            double average = mean(exec[v]);
            double[] tail = new double[p];
            Arrays.fill(tail, Double.NEGATIVE_INFINITY);
            for (int k = 0; k < succ[v].length; k++) {
                int s = succ[v][k];
                double cost = edgeCost[v][k];
                // min over q of (BPCM(s,q) + w(s,q) + c·[q != p]) via the best and second best q.
                int best = -1;
                double bestValue = Double.POSITIVE_INFINITY;
                double secondValue = Double.POSITIVE_INFINITY;
                for (int q = 0; q < p; q++) {
                    double value = bpcm[s][q] + exec[s][q];
                    if (value < bestValue) {
                        secondValue = bestValue;
                        bestValue = value;
                        best = q;
                    } else if (value < secondValue) {
                        secondValue = value;
                    }
                }
                for (int proc = 0; proc < p; proc++) {
                    double other = (proc == best ? secondValue : bestValue) + cost;
                    double same = bpcm[s][proc] + exec[s][proc];
                    tail[proc] = Math.max(tail[proc], Math.min(same, other));
                }
            }
            for (int proc = 0; proc < p; proc++) bpcm[v][proc] = average + tail[proc];
        }
        return bpcm;
    }

    /** Eq. (18): mean BPCM times (1 + mean execution / mean outgoing edge cost with data). */
    public static double[] computeRank(double[][] bpcm, double[][] edgeCost, double[][] exec) {
        double[] rank = new double[bpcm.length];
        for (int v = 0; v < bpcm.length; v++) {
            double sum = 0.0d;
            int count = 0;
            for (double cost : edgeCost[v]) {
                if (cost > 0.0d) {
                    sum += cost;
                    count++;
                }
            }
            double factor = count == 0 ? 1.0d : 1.0d + mean(exec[v]) / (sum / count);
            rank[v] = mean(bpcm[v]) * factor;
        }
        return rank;
    }

    private static double mean(double[] values) {
        double sum = 0.0d;
        for (double value : values) sum += value;
        return values.length == 0 ? 0.0d : sum / values.length;
    }

    // ---------------------------------------------------------------- preparation

    private void prepare() {
        for (VCPU cpu : new TreeMap<String, VCPU>(this.vcpuMap).values()) {
            if (cpu.getVMID() == null || this.findVM(cpu) == null || cpu.getMips() <= 0L) continue;
            this.processorIndex.put(cpu.getPrefix(), this.processors.size());
            this.processors.add(cpu);
        }
        if (this.processors.isEmpty()) throw new IllegalStateException("BPPTS: no VM-bound vCPU available");

        // Average bandwidth over ordered pairs of processors on different hosts (Eq. 2).
        double bandwidthSum = 0.0d;
        long pairs = 0L;
        for (VCPU from : this.processors) {
            for (VCPU to : this.processors) {
                if (from == to) continue;
                double time = this.calcComTime(UNIT_DATA, from, to);
                if (time <= 0.0d || time >= NFVUtil.MAXValue) continue;
                bandwidthSum += UNIT_DATA / time;
                pairs++;
            }
        }
        this.averageBandwidth = pairs == 0L ? Double.POSITIVE_INFINITY : bandwidthSum / pairs;

        List<VNF> order = this.topologicalOrder();
        for (int i = 0; i < order.size(); i++) this.taskIndex.put(order.get(i).getIDVector().get(1), i);
        int n = order.size();
        int[][] succ = new int[n][];
        double[][] edgeCost = new double[n][];
        double[][] exec = new double[n][this.processors.size()];
        for (int i = 0; i < n; i++) {
            VNF task = order.get(i);
            List<DataDependence> edges = new ArrayList<DataDependence>(task.getDsucList());
            succ[i] = new int[edges.size()];
            edgeCost[i] = new double[edges.size()];
            for (int k = 0; k < edges.size(); k++) {
                succ[i][k] = this.taskIndex.get(edges.get(k).getToID().get(1));
                edgeCost[i][k] = this.modelCost(edges.get(k).getMaxDataSize());
            }
            for (int p = 0; p < this.processors.size(); p++) {
                exec[i][p] = this.calcExecTime(task.getWorkLoad(), this.processors.get(p));
            }
        }
        this.bpcm = computeBPCM(succ, edgeCost, exec);
        this.rank = computeRank(this.bpcm, edgeCost, exec);
    }

    private double modelCost(long data) {
        if (data <= 0L || Double.isInfinite(this.averageBandwidth)) return 0.0d;
        return data / this.averageBandwidth;
    }

    private List<VNF> topologicalOrder() {
        Map<Long, Integer> remaining = new HashMap<Long, Integer>();
        TreeMap<Long, VNF> ready = new TreeMap<Long, VNF>();
        for (VNF task : this.sfc.getVnfMap().values()) {
            remaining.put(task.getIDVector().get(1), task.getDpredList().size());
            if (task.getDpredList().isEmpty()) ready.put(task.getIDVector().get(1), task);
        }
        List<VNF> order = new ArrayList<VNF>();
        while (!ready.isEmpty()) {
            VNF task = ready.pollFirstEntry().getValue();
            order.add(task);
            for (DataDependence edge : task.getDsucList()) {
                Long id = edge.getToID().get(1);
                int left = remaining.get(id) - 1;
                remaining.put(id, left);
                if (left == 0) ready.put(id, this.sfc.findVNFByLastID(id));
            }
        }
        if (order.size() != this.sfc.getVnfMap().size()) throw new IllegalStateException("BPPTS: cyclic task graph");
        return order;
    }

    public double getRank(VNF task) {
        return this.rank[this.taskIndex.get(task.getIDVector().get(1))];
    }

    @Override
    public VNF selectVNF() {
        VNF selected = null;
        double best = Double.NEGATIVE_INFINITY;
        Iterator<Long> ids = this.freeVNFSet.iterator();
        while (ids.hasNext()) {
            Long id = ids.next();
            double value = this.rank[this.taskIndex.get(id)];
            if (selected == null || value > best
                    || (value == best && id < selected.getIDVector().get(1))) {
                selected = this.sfc.findVNFByLastID(id);
                best = value;
            }
        }
        return selected;
    }

    // ---------------------------------------------------------------- processor selection

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> candidates) {
        if (this.scheduleVirtualBoundary(vnf, candidates)) return;

        int v = this.taskIndex.get(vnf.getIDVector().get(1));
        boolean byEFT = vnf.getDsucList().size() > this.processors.size();
        VCPU chosen = null;
        double chosenKey = Double.POSITIVE_INFINITY;
        double chosenEFT = Double.POSITIVE_INFINITY;
        for (int p = 0; p < this.processors.size(); p++) {
            VCPU cpu = this.processors.get(p);
            if (!candidates.containsKey(cpu.getPrefix())) continue;
            double est = Math.max(this.lastFinish(cpu), this.modelDataReady(vnf, cpu));
            double eft = est + this.calcExecTime(vnf.getWorkLoad(), cpu);
            double key = byEFT ? eft : eft + this.bpcm[v][p];
            if (key < chosenKey || (key == chosenKey && (eft < chosenEFT
                    || (eft == chosenEFT && cpu.getPrefix().compareTo(chosen.getPrefix()) < 0)))) {
                chosen = cpu;
                chosenKey = key;
                chosenEFT = eft;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException("No BPPTS processor for VNF " + vnf.getIDVector().get(1));
        }
        this.execute(vnf, chosen);
    }

    /** avail(p): the finish time of the last task on p. */
    private double lastFinish(VCPU cpu) {
        double last = 0.0d;
        for (VNF task : cpu.getVnfQueue()) last = Math.max(last, task.getFinishTime());
        return last;
    }

    /** BPPTS's own estimate: max over predecessors of AFT + c, with c = 0 on the same processor. */
    private double modelDataReady(VNF vnf, VCPU cpu) {
        double ready = 0.0d;
        for (DataDependence edge : vnf.getDpredList()) {
            VNF predecessor = this.sfc.findVNFByLastID(edge.getFromID().get(1));
            double cost = cpu.getPrefix().equals(predecessor.getvCPUID()) ? 0.0d
                    : this.modelCost(edge.getMaxDataSize());
            ready = Math.max(ready, predecessor.getFinishTime() + cost);
        }
        return ready;
    }

    private double predecessorFinishTime(VNF vnf) {
        double release = 0.0d;
        for (DataDependence edge : vnf.getDpredList()) {
            VNF predecessor = this.sfc.findVNFByLastID(edge.getFromID().get(1));
            release = Math.max(release, predecessor.getFinishTime());
        }
        return release;
    }

    // ---------------------------------------------------------------- platform execution

    private void execute(VNF vnf, VCPU cpu) {
        VM target = this.findVM(cpu);
        ImagePlan image = this.planImage(vnf, target, this.predecessorFinishTime(vnf));
        if (image == null) throw new IllegalStateException("No image plan for VNF " + vnf.getIDVector().get(1));
        double execution = this.calcExecTime(vnf.getWorkLoad(), cpu);
        double ready = Math.max(Math.max(image.readyAt, this.calcDeadLine(vnf, cpu).get("arrival_time")),
                this.lastFinish(cpu));
        double start = this.findEarliestExecutionStart(vnf, cpu, ready, execution);
        double finish = start + execution;
        if (!Double.isFinite(finish) || finish >= NFVUtil.MAXValue) {
            throw new IllegalStateException("No feasible execution slot for VNF " + vnf.getIDVector().get(1));
        }

        boolean downloads = image.rate > 0L;
        if (downloads) {
            String id = LABEL + "^" + this.buildVnfKey(vnf);
            if (image.dcLink != null && !image.dcLink.reserveBW(image.start, image.readyAt, image.rate, id)) {
                throw new IllegalStateException("Repository DC reservation rejected: " + id);
            }
            if (!image.hostLink.reserveBW(image.start, image.readyAt, image.rate, id)) {
                if (image.dcLink != null) image.dcLink.releaseBWExact(id);
                throw new IllegalStateException("Repository host reservation rejected: " + id);
            }
        }
        vnf.setDlStartTime(image.start);
        vnf.setDlFinishTime(image.readyAt);
        this.recordImageDownloadSource(vnf, downloads, downloads, null);
        if (downloads) {
            target.addDLQueue(vnf);
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

    /** Same-VM reuse, otherwise a repository download; Dep-Comp's start, rate and shared-link rule. */
    private ImagePlan planImage(VNF vnf, VM target, double release) {
        if (target == null) return null;
        if (NFVUtil.cloud_container_dl_mode != 1 || vnf.getImageSize() <= 0L) return ImagePlan.none();
        double localReady = target.getImageReadyTime(vnf.getType());
        if (localReady < NFVUtil.MAXValue) return ImagePlan.reuse(localReady);
        if (target.containsType(vnf.getType())) return ImagePlan.reuse(0.0d);

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
        double start = Math.max(release, target.getDlQueueFinishTime());
        while (Double.isFinite(start) && start < NFVUtil.MAXValue) {
            double end = start + duration;
            if (end <= start || end >= NFVUtil.MAXValue) return null;
            if (hostLink.getAvailableBW(start, end) >= rate
                    && (dcLink == null || dcLink.getAvailableBW(start, end) >= rate)) {
                return new ImagePlan(start, end, rate, hostLink, dcLink);
            }
            double next = hostLink.getNextChangeTime(start);
            if (dcLink != null) next = Math.min(next, dcLink.getNextChangeTime(start));
            start = next;
        }
        return null;
    }

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
        private final BandwidthTimeSlot hostLink;
        private final BandwidthTimeSlot dcLink;

        private ImagePlan(double start, double readyAt, long rate, BandwidthTimeSlot hostLink,
                          BandwidthTimeSlot dcLink) {
            this.start = start;
            this.readyAt = readyAt;
            this.rate = rate;
            this.hostLink = hostLink;
            this.dcLink = dcLink;
        }

        private static ImagePlan none() {
            return new ImagePlan(0.0d, 0.0d, 0L, null, null);
        }

        private static ImagePlan reuse(double readyAt) {
            return new ImagePlan(readyAt, readyAt, 0L, null, null);
        }
    }
}
