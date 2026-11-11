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
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * PBTLA-w/o-RLS from Zhang, Quek and Zhao, "Dependency-Driven Task Scheduling and Layer
 * Management for Device-Edge-Cloud Systems" (IEEE TMC, doi:10.1109/TMC.2026.3709176),
 * adapted to whole images on this simulator. w/o-RLS is the authors' own ablation (Sec. VI-A):
 * no server downloads an image that none of its tasks needs.
 *
 * <p>Kept from the paper:</p>
 * <ul>
 * <li>Dynamic priority (Eqs. 23-27): {@code p = a1*tailRank - min_s(a2*dataArrival_s + a3*downloadEstimate_s)},
 * recomputed for every batch over the candidates whose predecessors are all scheduled.</li>
 * <li>Batches (Eqs. 29-32): the top candidates by priority. A task runs after every earlier-batch task
 * on its core (no insertion into earlier gaps), and a core takes at most one task per batch (Eq. 31e).</li>
 * <li>GBS (Algorithm 2): for each task in priority order, every server offers its free core with the
 * earliest available time; the estimated finish is {@code max(data arrival, layer estimate, core time)
 * + execution}, with the layer estimate of Eq. 33 (read literally: with no missing image it is the
 * server's estimated download tail); the server with the earliest estimate wins and its estimated
 * download tail and image set are updated.</li>
 * <li>LayerLoad without RLS (Algorithm 3, Sec. VI-A): subbatches take each server's next task in priority
 * order; images that already have a holder are processed first, then images without one, each by its
 * frequency in the whole batch (number of servers missing it, counted before the subbatches). An image
 * without a holder is first downloaded from the repository by the needing server with the earliest
 * download tail; every other needing server takes the earlier of the repository and a holder. Each server
 * downloads one image at a time, and a transfer from a holder waits until the holder has the image
 * (Eqs. 9-11).</li>
 * <li>Tab, single mode (default): each image has one designated source, {@code Tab_l = s'} as in
 * Sec. IV-C 4 and the RLS step. It is the server that the w/o-RLS rule first assigns to fetch the image
 * from the repository. A needing server takes the earlier of the repository and that source; a server
 * that receives the image caches it but does not become a source (normal loading updates only Q_s).
 * The designation lasts for the whole workflow (there is no eviction).</li>
 * <li>Tab, all mode (sensitivity reading): every server that holds or is loading the image is a candidate
 * source and the fastest is used, a broader reading of "candidate MEC servers" in Sec. IV-C 2. Report it as
 * "PBTLA-w/o-RLS (adapted, all holders)".</li>
 * <li>Needing servers of one image are processed in VM-id order (the paper gives no order).</li>
 * </ul>
 *
 * <p>Adapted to this platform:</p>
 * <ul>
 * <li>A VM is a server: its candidate vCPUs are the virtual cores, it has one download queue and its
 * own image set. Images are whole (one layer per image type).</li>
 * <li>No user devices and no cloud migration; no layer deletion (the simulator has no storage limit
 * for any algorithm); no RLS helpers.</li>
 * <li>Data arrival uses the simulator's static communication time, as for every algorithm.</li>
 * <li>Estimates use the repository path rate of each server for {@code w_cs} and the platform average
 * bandwidth (as in b-level) for {@code w_s}; tail ranks use the average speed and bandwidth.</li>
 * <li>Actual transfers use one fixed rate (the path bottleneck) on this algorithm's own copy of the
 * host-pair and DC-pair links, and wait for a full interval at that rate. A transfer between VMs on
 * the same host takes no time, as in the platform rule used by DHEFT and NHEFT.</li>
 * <li>Batch size is {@code max(1, floor(batchCoreRatio * cores))}, default ratio 0.5. The paper sets it to
 * twice the number of servers with four cores each (8 tasks for 16 cores, Sec. VI-A); our VMs have one to
 * three vCPUs, so the same formula would exceed the vCPU count. The ratio keeps the paper's proportion.</li>
 * <li>A task that finds no free core in its batch waits for the next batch.</li>
 * <li>GBS estimates core availability from each vCPU's own queue; the platform's shared physical-core
 * utilization limit is enforced only when a task is committed.</li>
 * <li>Weights a1-a3 and lambda are not given in the main text; they are configuration values.</li>
 * </ul>
 *
 * <p>Enable with {@code run_pbtla=1}; disabled by default.</p>
 */
public final class PBTLA_VNFAlgorithm extends DHEFT_VNFAlgorithm {
    public static final String LABEL = "PBTLA";
    public static final String MODEL_REVISION_SINGLE = "wo-rls-vm-core-ratio-tab-single-v3";
    public static final String MODEL_REVISION_ALL = "wo-rls-vm-core-ratio-tab-all-v2";
    public static final String TAB_SINGLE = "single";
    public static final String TAB_ALL = "all";
    private static final double EPS = 1.0e-9;
    private static final double TOL = 1.0e-6;

    private final double alpha1;
    private final double alpha2;
    private final double alpha3;
    private final double lambda;
    private final double batchCoreRatio;
    private final boolean singleSourceTab;
    private final Map<Integer, Server> designatedSource = new HashMap<Integer, Server>();
    private final int batchSize;

    private final Map<String, Server> servers = new TreeMap<String, Server>();
    private final Map<String, BandwidthTimeSlot> links = new HashMap<String, BandwidthTimeSlot>();
    private final Map<Long, Double> tailRank = new HashMap<Long, Double>();
    private final Map<Long, Double> imageStartByVnf = new HashMap<Long, Double>();
    private final Map<Long, Double> imageReadyByVnf = new HashMap<Long, Double>();
    private long batchCount;
    private long deferredCount;
    private long sameHostFetchCount;

    public PBTLA_VNFAlgorithm(CloudEnvironment env, SFC sfc,
                              double alpha1, double alpha2, double alpha3, double lambda, double batchCoreRatio,
                              String tabMode) {
        super(env, sfc);
        if (!(isFiniteNonNegative(alpha1) && isFiniteNonNegative(alpha2) && isFiniteNonNegative(alpha3))
                || !(lambda >= 0.0d && lambda <= 1.0d) || !(batchCoreRatio > 0.0d && batchCoreRatio <= 1.0d)
                || !(TAB_SINGLE.equals(tabMode) || TAB_ALL.equals(tabMode))) {
            throw new IllegalArgumentException("Invalid PBTLA parameters");
        }
        this.singleSourceTab = TAB_SINGLE.equals(tabMode);
        this.alpha1 = alpha1;
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        this.lambda = lambda;
        this.batchCoreRatio = batchCoreRatio;
        this.buildServers();
        int cores = 0;
        for (Server server : this.servers.values()) cores += server.cores.size();
        this.batchSize = Math.max(1, (int) Math.floor(batchCoreRatio * cores));
        Map<Long, double[]> memo = new HashMap<Long, double[]>();
        for (VNF vnf : this.sfc.getVnfMap().values()) {
            double[] parts = this.tailParts(vnf, memo);
            this.tailRank.put(vnf.getIDVector().get(1), parts[0] + parts[1]);
        }
    }

    private static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0d;
    }

    // ------------------------------------------------------------------ setup

    private void buildServers() {
        ComputeHost repo = ((NFVEnvironment) this.env).getDockerRepository();
        for (VCPU cpu : this.vcpuMap.values()) {
            VM vm = this.findVM(cpu);
            if (vm == null || cpu.getMips() <= 0L) continue;
            Server server = this.servers.get(vm.getVMID());
            if (server == null) {
                ComputeHost host = this.env.getGlobal_hostMap().get(vm.getHostID());
                if (host == null) throw new IllegalStateException("Missing host for VM " + vm.getVMID());
                server = new Server(vm, host, this.path(repo, host));
                this.servers.put(vm.getVMID(), server);
            }
            server.cores.add(cpu);
        }
        if (this.servers.isEmpty()) throw new IllegalStateException("No PBTLA server");
        for (Server server : this.servers.values()) {
            Collections.sort(server.cores, new Comparator<VCPU>() {
                public int compare(VCPU a, VCPU b) {
                    return a.getPrefix().compareTo(b.getPrefix());
                }
            });
        }
    }

    /**
     * Eqs. 23-25: the successor computation tail and the successor transfer tail, each a longest path
     * on its own (they may follow different paths), using the average speed and bandwidth.
     */
    private double[] tailParts(VNF vnf, Map<Long, double[]> memo) {
        double[] known = memo.get(vnf.getIDVector().get(1));
        if (known != null) return known;
        double computation = 0.0d;
        double transfer = 0.0d;
        for (DataDependence edge : vnf.getDsucList()) {
            VNF successor = this.sfc.findVNFByLastID(edge.getToID().get(1));
            double[] next = this.tailParts(successor, memo);
            computation = Math.max(computation, next[0] + this.calcExecTime(successor.getWorkLoad(), this.usedSpeed));
            transfer = Math.max(transfer, next[1] + this.calcComTime(edge.getMaxDataSize(), this.usedBW));
        }
        double[] value = new double[]{computation, transfer};
        memo.put(vnf.getIDVector().get(1), value);
        return value;
    }

    // ------------------------------------------------------------------ main loop

    @Override
    public void mainProcess() {
        while (!this.unScheduledVNFSet.isEmpty()) {
            this.scheduleFreeVirtualBoundaries();
            if (this.unScheduledVNFSet.isEmpty()) break;
            List<VNF> candidates = new ArrayList<VNF>();
            for (Long id : this.snapshot(this.freeVNFSet)) {
                VNF vnf = this.sfc.findVNFByLastID(id);
                if (this.unScheduledVNFSet.contains(id) && !isVirtualBoundary(vnf)) candidates.add(vnf);
            }
            if (candidates.isEmpty()) throw new IllegalStateException("PBTLA found no ready task");
            this.runBatch(candidates);
        }
        double makespan = -1.0d;
        for (Long id : this.snapshot(this.sfc.getEndVNFSet())) {
            makespan = Math.max(makespan, this.sfc.findVNFByLastID(id).getFinishTime());
        }
        this.makeSpan = makespan;
    }

    private List<Long> snapshot(CustomIDSet ids) {
        List<Long> list = new ArrayList<Long>();
        for (java.util.Iterator<Long> it = ids.iterator(); it.hasNext(); ) list.add(it.next());
        Collections.sort(list);
        return list;
    }

    private void scheduleFreeVirtualBoundaries() {
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Long id : this.snapshot(this.freeVNFSet)) {
                VNF vnf = this.sfc.findVNFByLastID(id);
                if (this.unScheduledVNFSet.contains(id) && isVirtualBoundary(vnf)) {
                    this.scheduleVirtualBoundary(vnf, this.vcpuMap);
                    progressed = true;
                }
            }
        }
    }

    private void runBatch(List<VNF> candidates) {
        this.batchCount++;
        final Map<Long, Double> priority = new HashMap<Long, Double>();
        for (VNF vnf : candidates) priority.put(vnf.getIDVector().get(1), this.priorityOf(vnf));
        Collections.sort(candidates, new Comparator<VNF>() {
            public int compare(VNF a, VNF b) {
                int byPriority = Double.compare(priority.get(b.getIDVector().get(1)), priority.get(a.getIDVector().get(1)));
                return byPriority != 0 ? byPriority : a.getIDVector().get(1).compareTo(b.getIDVector().get(1));
            }
        });
        int size = Math.min(candidates.size(), this.batchSize);
        List<Assignment> assigned = this.greedyBatchSchedule(candidates.subList(0, size));
        if (assigned.isEmpty()) throw new IllegalStateException("PBTLA batch placed no task");
        this.loadImages(assigned);
        for (Assignment assignment : assigned) this.commit(assignment);
    }

    // ------------------------------------------------------------------ priority and GBS

    /** Eqs. 26-27 and the dynamic priority of Sec. IV-A. */
    private double priorityOf(VNF vnf) {
        double best = Double.POSITIVE_INFINITY;
        for (Server server : this.servers.values()) {
            double arrival = this.dataArrival(vnf, server.cores.get(0));
            double download = server.downloadTail + this.missingImageEstimate(vnf, server, this.hasImage(server, vnf.getType()));
            best = Math.min(best, this.alpha2 * arrival + this.alpha3 * download);
        }
        return this.alpha1 * this.tailRank.get(vnf.getIDVector().get(1)) - best;
    }

    /** Eq. 33 without the sum over several layers: one whole image. */
    private double missingImageEstimate(VNF vnf, Server server, boolean present) {
        if (present || !this.downloadsImages(vnf)) return 0.0d;
        double size = (double) vnf.getImageSize();
        return this.lambda * size / server.repoPath.rate + (1.0d - this.lambda) * size / this.usedBW;
    }

    private List<Assignment> greedyBatchSchedule(List<VNF> batch) {
        Map<String, Double> estimatedTail = new HashMap<String, Double>();
        Map<String, Set<Integer>> estimatedImages = new HashMap<String, Set<Integer>>();
        for (Server server : this.servers.values()) {
            estimatedTail.put(server.id, server.downloadTail);
            estimatedImages.put(server.id, new HashSet<Integer>());
        }
        Set<String> usedCores = new HashSet<String>();
        List<Assignment> assigned = new ArrayList<Assignment>();
        for (VNF vnf : batch) {
            Server bestServer = null;
            VCPU bestCore = null;
            double bestFinish = Double.POSITIVE_INFINITY;
            double bestLayer = 0.0d;
            boolean bestPresent = false;
            for (Server server : this.servers.values()) {
                VCPU core = null;
                double coreTime = Double.POSITIVE_INFINITY;
                for (VCPU cpu : server.cores) {
                    if (usedCores.contains(cpu.getPrefix())) continue;
                    double available = this.calcCT(cpu);
                    if (available < coreTime) {
                        coreTime = available;
                        core = cpu;
                    }
                }
                if (core == null) continue;
                boolean present = this.hasImage(server, vnf.getType())
                        || estimatedImages.get(server.id).contains(vnf.getType());
                double layer = estimatedTail.get(server.id) + this.missingImageEstimate(vnf, server, present);
                double finish = Math.max(Math.max(this.dataArrival(vnf, core), layer), coreTime)
                        + this.calcExecTime(vnf.getWorkLoad(), core);
                if (!Double.isFinite(finish) || finish >= NFVUtil.MAXValue) continue;
                if (finish < bestFinish - EPS
                        || (Math.abs(finish - bestFinish) <= EPS && core.getPrefix().compareTo(bestCore.getPrefix()) < 0)) {
                    bestServer = server;
                    bestCore = core;
                    bestFinish = finish;
                    bestLayer = layer;
                    bestPresent = present;
                }
            }
            if (bestServer == null) {
                this.deferredCount++;
                continue;
            }
            usedCores.add(bestCore.getPrefix());
            estimatedTail.put(bestServer.id, bestLayer);
            if (!bestPresent) estimatedImages.get(bestServer.id).add(vnf.getType());
            assigned.add(new Assignment(vnf, bestServer, bestCore));
        }
        return assigned;
    }

    // ------------------------------------------------------------------ LayerLoad (w/o RLS)

    private void loadImages(List<Assignment> assigned) {
        // Algorithm 3 line 2: frequencies over the whole batch (servers that miss the image), fixed before subbatches.
        Map<Integer, Set<String>> missingBy = new HashMap<Integer, Set<String>>();
        for (Assignment assignment : assigned) {
            if (!this.downloadsImages(assignment.vnf) || this.hasImage(assignment.server, assignment.vnf.getType())) {
                continue;
            }
            Set<String> serversMissing = missingBy.get(assignment.vnf.getType());
            if (serversMissing == null) {
                serversMissing = new HashSet<String>();
                missingBy.put(assignment.vnf.getType(), serversMissing);
            }
            serversMissing.add(assignment.server.id);
        }
        final Map<Integer, Integer> batchFrequency = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, Set<String>> entry : missingBy.entrySet()) {
            batchFrequency.put(entry.getKey(), entry.getValue().size());
        }
        Map<String, Deque<Assignment>> pending = new TreeMap<String, Deque<Assignment>>();
        for (Assignment assignment : assigned) {
            Deque<Assignment> queue = pending.get(assignment.server.id);
            if (queue == null) {
                queue = new ArrayDeque<Assignment>();
                pending.put(assignment.server.id, queue);
            }
            queue.addLast(assignment);
        }
        while (!pending.isEmpty()) {
            List<Assignment> subbatch = new ArrayList<Assignment>();
            for (Deque<Assignment> queue : pending.values()) subbatch.add(queue.pollFirst());
            for (java.util.Iterator<Map.Entry<String, Deque<Assignment>>> it = pending.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue().isEmpty()) it.remove();
            }
            final Map<Integer, List<Assignment>> needs = new TreeMap<Integer, List<Assignment>>();
            for (Assignment assignment : subbatch) {
                if (!this.downloadsImages(assignment.vnf) || this.hasImage(assignment.server, assignment.vnf.getType())) {
                    continue;
                }
                List<Assignment> list = needs.get(assignment.vnf.getType());
                if (list == null) {
                    list = new ArrayList<Assignment>();
                    needs.put(assignment.vnf.getType(), list);
                }
                list.add(assignment);
            }
            List<Integer> mapped = new ArrayList<Integer>();
            List<Integer> unmapped = new ArrayList<Integer>();
            for (Integer type : needs.keySet()) {
                if (this.isMapped(type)) mapped.add(type); else unmapped.add(type);
            }
            Comparator<Integer> byFrequency = new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    int byCount = Integer.compare(batchFrequency.get(b), batchFrequency.get(a));
                    return byCount != 0 ? byCount : a.compareTo(b);
                }
            };
            Collections.sort(mapped, byFrequency);
            Collections.sort(unmapped, byFrequency);
            for (Integer type : mapped) {
                for (Assignment assignment : needs.get(type)) this.loadFromEarliestSource(assignment);
            }
            for (Integer type : unmapped) {
                List<Assignment> list = needs.get(type);
                Assignment first = list.get(0);
                for (Assignment assignment : list) {
                    if (assignment.server.downloadTail < first.server.downloadTail) first = assignment;
                }
                this.transfer(first, null, this.planTransfer(first.server.repoPath, first.vnf.getImageSize(),
                        first.server.downloadTail));
                if (this.singleSourceTab) this.designatedSource.put(type, first.server);
                for (Assignment assignment : list) {
                    if (assignment != first) this.loadFromEarliestSource(assignment);
                }
            }
        }
    }

    /** Whether Tab already maps the image: its designated source (single mode) or any holder (all mode). */
    private boolean isMapped(int type) {
        return this.singleSourceTab ? this.designatedSource.containsKey(type) : this.hasHolder(type);
    }

    private boolean hasHolder(int type) {
        for (Server server : this.servers.values()) {
            if (this.hasImage(server, type)) return true;
        }
        return false;
    }

    /** The earlier of the repository and every server that holds or is loading the image. */
    private void loadFromEarliestSource(Assignment assignment) {
        Server target = assignment.server;
        long size = assignment.vnf.getImageSize();
        Server bestSource = null;
        double[] best = this.planTransfer(target.repoPath, size, target.downloadTail);
        for (Server source : this.servers.values()) {
            if (source == target) continue;
            if (this.singleSourceTab && source != this.designatedSource.get(assignment.vnf.getType())) continue;
            double ready = this.imageReady(source, assignment.vnf.getType());
            if (ready >= NFVUtil.MAXValue) continue;
            double[] plan = this.planTransfer(this.path(source.host, target.host), size,
                    Math.max(target.downloadTail, ready));
            if (plan != null && (best == null || plan[1] < best[1] - EPS)) {
                best = plan;
                bestSource = source;
            }
        }
        this.transfer(assignment, bestSource, best);
    }

    private void transfer(Assignment assignment, Server source, double[] plan) {
        if (plan == null) {
            throw new IllegalStateException("No feasible image transfer for VNF " + assignment.vnf.getIDVector().get(1));
        }
        Server target = assignment.server;
        Path path = source == null ? target.repoPath : this.path(source.host, target.host);
        if (!path.local) this.reserve(path, plan, assignment.vnf);
        if (path.local) this.sameHostFetchCount++;
        target.downloadTail = plan[1];
        target.downloads.add(new double[]{plan[0], plan[1]});
        target.images.put(assignment.vnf.getType(), plan[1]);
        long id = assignment.vnf.getIDVector().get(1);
        this.imageStartByVnf.put(id, plan[0]);
        this.imageReadyByVnf.put(id, plan[1]);
        boolean hasDownload = plan[1] > plan[0] + EPS;
        this.recordImageDownloadSource(assignment.vnf, hasDownload, source == null, source == null ? null : source.vm);
        if (hasDownload) this.recordImageDownload(source == null, plan[0], plan[1]);
    }

    // ------------------------------------------------------------------ network

    private Path path(ComputeHost source, ComputeHost target) {
        if (source.getDcID().longValue() == target.getDcID().longValue()
                && source.getMachineID() == target.getMachineID()) {
            return Path.local();
        }
        long hostCapacity = Math.min(source.getBw(), target.getBw());
        String hostKey = "host:" + pairKey(source.getPrefix(), target.getPrefix());
        long rate = hostCapacity;
        String dcKey = null;
        long dcCapacity = 0L;
        if (source.getDcID().longValue() != target.getDcID().longValue()) {
            Cloud a = this.env.getDcMap().get(source.getDcID());
            Cloud b = this.env.getDcMap().get(target.getDcID());
            if (a == null || b == null) throw new IllegalStateException("Missing DC on transfer path");
            dcCapacity = Math.min(a.getBw(), b.getBw());
            dcKey = "dc:" + pairKey(String.valueOf(source.getDcID()), String.valueOf(target.getDcID()));
            rate = Math.min(rate, dcCapacity);
        }
        if (rate <= 0L) throw new IllegalStateException("Non-positive transfer rate");
        return new Path(hostKey, hostCapacity, dcKey, dcCapacity, rate);
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private BandwidthTimeSlot link(String key, long capacity) {
        BandwidthTimeSlot slot = this.links.get(key);
        return slot == null ? new BandwidthTimeSlot(key, capacity) : slot;
    }

    /** Earliest full-rate interval starting no earlier than {@code earliest}; never splits a transfer. */
    private double[] planTransfer(Path path, long size, double earliest) {
        if (path.local || size <= 0L) return new double[]{earliest, earliest};
        double duration = (double) size / (double) path.rate;
        BandwidthTimeSlot host = this.link(path.hostKey, path.hostCapacity);
        BandwidthTimeSlot dc = path.dcKey == null ? null : this.link(path.dcKey, path.dcCapacity);
        double start = earliest;
        while (Double.isFinite(start) && start < NFVUtil.MAXValue) {
            double end = start + duration;
            if (!(end > start) || end >= NFVUtil.MAXValue) return null;
            if (host.getAvailableBW(start, end) >= path.rate
                    && (dc == null || dc.getAvailableBW(start, end) >= path.rate)) {
                return new double[]{start, end};
            }
            double next = host.getNextChangeTime(start);
            if (dc != null) next = Math.min(next, dc.getNextChangeTime(start));
            if (!(next > start)) return null;
            start = next;
        }
        return null;
    }

    private void reserve(Path path, double[] plan, VNF vnf) {
        String id = LABEL + "^" + this.buildVnfKey(vnf);
        BandwidthTimeSlot host = this.link(path.hostKey, path.hostCapacity);
        BandwidthTimeSlot dc = path.dcKey == null ? null : this.link(path.dcKey, path.dcCapacity);
        if (dc != null && !dc.reserveBW(plan[0], plan[1], path.rate, id)) {
            throw new IllegalStateException("PBTLA DC reservation rejected: " + id);
        }
        if (!host.reserveBW(plan[0], plan[1], path.rate, id)) {
            if (dc != null) dc.releaseBWExact(id);
            throw new IllegalStateException("PBTLA host reservation rejected: " + id);
        }
        this.links.put(path.hostKey, host);
        if (dc != null) this.links.put(path.dcKey, dc);
    }

    // ------------------------------------------------------------------ commit

    private void commit(Assignment assignment) {
        VNF vnf = assignment.vnf;
        VCPU cpu = assignment.core;
        long id = vnf.getIDVector().get(1);
        double imageStart;
        double imageReady;
        if (this.imageReadyByVnf.containsKey(id)) {
            imageStart = this.imageStartByVnf.get(id);
            imageReady = this.imageReadyByVnf.get(id);
        } else {
            imageReady = this.downloadsImages(vnf) ? this.imageReady(assignment.server, vnf.getType()) : 0.0d;
            if (imageReady >= NFVUtil.MAXValue) throw new IllegalStateException("Image never loaded for VNF " + id);
            imageStart = imageReady;
            this.recordImageDownloadSource(vnf, false, false, null);
        }
        double execution = this.calcExecTime(vnf.getWorkLoad(), cpu);
        double ready = Math.max(Math.max(this.dataArrival(vnf, cpu), imageReady), this.calcCT(cpu));
        double start = this.findEarliestExecutionStart(vnf, cpu, ready, execution);
        double finish = start + execution;
        if (!Double.isFinite(finish) || finish >= NFVUtil.MAXValue) {
            throw new IllegalStateException("No feasible execution slot for VNF " + id);
        }
        vnf.setDlStartTime(imageStart);
        vnf.setDlFinishTime(imageReady);
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
        this.unScheduledVNFSet.remove(id);
        this.updateFreeList(vnf);
    }

    // ------------------------------------------------------------------ helpers

    private boolean downloadsImages(VNF vnf) {
        return NFVUtil.cloud_container_dl_mode == 1 && vnf.getImageSize() > 0L;
    }

    private boolean hasImage(Server server, int type) {
        return this.imageReady(server, type) < NFVUtil.MAXValue;
    }

    private double imageReady(Server server, int type) {
        Double planned = server.images.get(type);
        if (planned != null) return planned.doubleValue();
        double registered = server.vm.getImageReadyTime(type);
        if (registered < NFVUtil.MAXValue) return registered;
        return server.vm.containsType(type) ? 0.0d : NFVUtil.MAXValue;
    }

    /** Latest predecessor finish plus the platform's static transfer time to {@code cpu}. */
    private double dataArrival(VNF vnf, VCPU cpu) {
        double arrival = 0.0d;
        for (DataDependence edge : vnf.getDpredList()) {
            VNF predecessor = this.sfc.findVNFByLastID(edge.getFromID().get(1));
            VCPU source = this.findVCPU(predecessor.getvCPUID());
            if (source == null) throw new IllegalStateException("Unscheduled predecessor for VNF " + vnf.getIDVector().get(1));
            arrival = Math.max(arrival, predecessor.getFinishTime() + this.calcComTime(edge.getMaxDataSize(), source, cpu));
        }
        return arrival;
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        throw new UnsupportedOperationException("PBTLA schedules whole batches in mainProcess()");
    }

    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU cpu) {
        HashMap<String, Double> info = new HashMap<String, Double>();
        boolean scheduled = vnf.getvCPUID() != null && !this.unScheduledVNFSet.contains(vnf.getIDVector().get(1));
        info.put("start", scheduled ? vnf.getDlStartTime() : (double) NFVUtil.MAXValue);
        info.put("finish", scheduled ? vnf.getDlFinishTime() : (double) NFVUtil.MAXValue);
        return info;
    }

    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU cpu) {
        HashMap<String, Double> info = this.getDLInfo(vnf, cpu);
        double start = info.get("start");
        double finish = info.get("finish");
        return finish >= NFVUtil.MAXValue ? NFVUtil.MAXValue : finish - start;
    }

    @Override
    public void validateSchedule() {
        super.validateSchedule();
        for (BandwidthTimeSlot link : this.links.values()) link.validateOccupancy();
        for (Server server : this.servers.values()) {
            List<double[]> downloads = new ArrayList<double[]>(server.downloads);
            Collections.sort(downloads, new Comparator<double[]>() {
                public int compare(double[] a, double[] b) {
                    int byStart = Double.compare(a[0], b[0]);
                    return byStart != 0 ? byStart : Double.compare(a[1], b[1]);
                }
            });
            double tail = 0.0d;
            for (double[] download : downloads) {
                if (download[1] < download[0] || download[0] + TOL < tail) {
                    throw new IllegalStateException("Overlapping downloads on PBTLA server " + server.id);
                }
                tail = Math.max(tail, download[1]);
            }
        }
    }

    public long getBatchCount() {
        return this.batchCount;
    }

    public long getDeferredCount() {
        return this.deferredCount;
    }

    public long getSameHostFetchCount() {
        return this.sameHostFetchCount;
    }

    public String getModelRevision() {
        return this.singleSourceTab ? MODEL_REVISION_SINGLE : MODEL_REVISION_ALL;
    }

    public String describeParameters() {
        return "alpha1=" + this.alpha1 + " alpha2=" + this.alpha2 + " alpha3=" + this.alpha3
                + " lambda=" + this.lambda + " batch_core_ratio=" + this.batchCoreRatio
                + " batch_size=" + this.batchSize + " tab=" + (this.singleSourceTab ? TAB_SINGLE : TAB_ALL);
    }

    // ------------------------------------------------------------------ types

    private static final class Server {
        private final String id;
        private final VM vm;
        private final ComputeHost host;
        private final Path repoPath;
        private final List<VCPU> cores = new ArrayList<VCPU>();
        private final Map<Integer, Double> images = new HashMap<Integer, Double>();
        private final List<double[]> downloads = new ArrayList<double[]>();
        private double downloadTail;

        private Server(VM vm, ComputeHost host, Path repoPath) {
            this.id = vm.getVMID();
            this.vm = vm;
            this.host = host;
            this.repoPath = repoPath;
        }
    }

    private static final class Path {
        private final boolean local;
        private final String hostKey;
        private final long hostCapacity;
        private final String dcKey;
        private final long dcCapacity;
        private final long rate;

        private Path(String hostKey, long hostCapacity, String dcKey, long dcCapacity, long rate) {
            this.local = hostKey == null;
            this.hostKey = hostKey;
            this.hostCapacity = hostCapacity;
            this.dcKey = dcKey;
            this.dcCapacity = dcCapacity;
            this.rate = rate;
        }

        private static Path local() {
            return new Path(null, 0L, null, 0L, Long.MAX_VALUE);
        }
    }

    private static final class Assignment {
        private final VNF vnf;
        private final Server server;
        private final VCPU core;

        private Assignment(VNF vnf, Server server, VCPU core) {
            this.vnf = vnf;
            this.server = server;
            this.core = core;
        }
    }
}
