package net.gripps.cloud.nfv.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.Core;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.DHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.listscheduling.NHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;

/** Diagnostic audit, not a passing regression suite. Production code is read only. */
public final class PreRerunAudit {
    private static class EmptyEnvironment extends CloudEnvironment {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            return new HashMap<Long, Cloud>();
        }
    }

    private static Object field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class DownloadProbe extends NHEFT_VNFAlgorithm {
        DownloadProbe() { super(new EmptyEnvironment(), null); }
        @Override public void initialize() { }
        Object simulate(long bytes, BandwidthTimeSlot slot) {
            return simulateDynamicDownload(0, bytes, 100, null, slot);
        }
    }

    private static class ExecutionProbe extends DownloadProbe {
        @Override protected HashMap<String, Double> getDLInfo(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("start", 0.0);
            result.put("finish", 0.0);
            return result;
        }
        @Override protected HashMap<String, Double> calcDeadLine(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("arrival_time", 0.0);
            return result;
        }
        double est(VNF task, VCPU cpu) { return calcEST(task, cpu); }
        CloudEnvironment environment() { return env; }
    }

    private static VCPU executionCPU(String core, long id) {
        return new VCPU(core + "^" + id, core, new HashMap<String, Long>(), "vm", 1L, 0L);
    }

    private static VNF executionTask(long id, double start, long length) {
        VNF task = task(id, 1, 0);
        task.setWorkLoad(length);
        task.setStartTime(start);
        task.setFinishTime(start + length);
        return task;
    }

    private static void executionAndGateChecks() throws Exception {
        String coreId = "0^0^0^0";
        ExecutionProbe probe = new ExecutionProbe();
        probe.setConstrainedMode(1);
        VCPU cpu = executionCPU(coreId, 0);
        HashMap<Long, VCPU> cpus = new HashMap<Long, VCPU>();
        cpus.put(0L, cpu);
        Core core = new Core(coreId, 1, 1L, 0L, cpus, 75);
        probe.environment().getGlobal_coreMap().put(coreId, core);
        cpu.getVnfQueue().add(executionTask(1, 20, 10));
        cpu.getVnfQueue().add(executionTask(2, 40, 10));
        System.out.println("FRONT_GAP expectedEarliest=0 actualEST=" + probe.est(executionTask(3, -1, 5), cpu));
        cpu.getVnfQueue().clear();
        VCPU sibling = executionCPU(coreId, 1);
        cpus.put(1L, sibling);
        cpu.getVnfQueue().add(executionTask(1, 0, 10));
        sibling.getVnfQueue().add(executionTask(2, 0, 100));
        System.out.println("SIBLING_WAIT feasibleAt10=" + probe.isAssignedInDuration(10, 15, core, cpu, executionTask(3, -1, 5))
                + " actualEST=" + probe.est(executionTask(3, -1, 5), cpu));

        Method gate = NHEFT_VNFAlgorithm.class.getDeclaredMethod("isNewVCPUOpeningAllowed",
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        gate.setAccessible(true);
        int checks = 0;
        for (int enabled = 0; enabled < 8; enabled++) {
            for (int advantages = 0; advantages < 8; advantages++) {
                for (int any = 0; any < 2; any++) {
                    for (int eft = 0; eft < 3; eft++) {
                        boolean expected = eft == 0 && (enabled == 0
                                || (any == 1 ? (enabled & advantages) != 0 : (enabled & advantages) == enabled));
                        boolean actual = (Boolean) gate.invoke(probe,
                                eft == 0 ? 5.0 : (eft == 1 ? 10.0 : 15.0),
                                (advantages & 1) != 0 ? 5.0 : 15.0,
                                (advantages & 2) != 0 ? 5.0 : 15.0,
                                (advantages & 4) != 0 ? 5.0 : 15.0,
                                10.0, 10.0, 10.0, 10.0,
                                (enabled & 1) != 0, (enabled & 2) != 0, (enabled & 4) != 0, any == 1);
                        if (actual != expected) throw new AssertionError("Gate truth-table mismatch");
                        checks++;
                    }
                }
            }
        }
        System.out.println("PASS gate truth table: " + checks + " combinations");
    }

    private static VNF task(long id, int type, long imageSize) {
        VNF task = new VNF(type, 100, 0, 0, 0, null, 20);
        Vector<Long> ids = new Vector<Long>();
        ids.add(1L);
        ids.add(id);
        task.setIDVector(ids);
        task.setImageSize(imageSize);
        return task;
    }

    private static double bytes(Object result) {
        int count = (Integer) field(result, "segmentCount");
        double[] starts = (double[]) field(result, "startTimes");
        double[] ends = (double[]) field(result, "endTimes");
        long[] rates = (long[]) field(result, "bandwidths");
        double total = 0;
        for (int i = 0; i < count; i++) total += (ends[i] - starts[i]) * rates[i];
        return total;
    }

    private static void smallBandwidthChecks() throws Exception {
        BandwidthTimeSlot adjacent = new BandwidthTimeSlot("adjacent", 100);
        adjacent.reserveBW(0, 1, 30, "a");
        adjacent.reserveBW(1, 2, 30, "b");
        System.out.println("INTERVAL expectedResidual=70 actualResidual="
                + adjacent.getAvailableBW(0, 2));

        BandwidthTimeSlot closeBoundary = new BandwidthTimeSlot("close-boundary", 100);
        closeBoundary.reserveBW(0.0000001, 1, 100, "existing");
        Object result = new DownloadProbe().simulate(100, closeBoundary);
        int count = (Integer) field(result, "segmentCount");
        double[] starts = (double[]) field(result, "startTimes");
        double[] ends = (double[]) field(result, "endTimes");
        long[] rates = (long[]) field(result, "bandwidths");
        boolean feasible = true;
        for (int i = 0; i < count; i++) {
            feasible &= closeBoundary.getAvailableBW(starts[i], ends[i]) >= rates[i];
        }
        Method commit = NHEFT_VNFAlgorithm.class.getDeclaredMethod("commitDynamicReservation",
                String.class, BandwidthTimeSlot.class, BandwidthTimeSlot.class, result.getClass());
        commit.setAccessible(true);
        commit.invoke(new DownloadProbe(), "probe", null, closeBoundary, result);
        double committedBytes = 0;
        for (BandwidthTimeSlot.BandwidthOccupancy occupancy : occupancies(closeBoundary)) {
            if (occupancy.taskId.startsWith("probe#")) {
                committedBytes += (occupancy.endTime - occupancy.startTime) * occupancy.occupiedBW;
            }
        }
        System.out.println("EPS_BOUNDARY feasible=" + feasible + " plannedBytes=" + bytes(result)
                + " committedBytes=" + committedBytes + " expectedBytes=100");

        BandwidthTimeSlot many = new BandwidthTimeSlot("many-events", 100);
        for (int i = 0; i < 2200; i++) many.reserveBW(i + 0.25, i + 0.5, 50, "busy" + i);
        Object truncated = new DownloadProbe().simulate(1000000, many);
        System.out.println("LOOP_LIMIT expectedBytes=1000000 actualBytes=" + bytes(truncated)
                + " segments=" + field(truncated, "segmentCount")
                + " reportedFinish=" + field(truncated, "finishTime"));
    }

    @SuppressWarnings("unchecked")
    private static List<BandwidthTimeSlot.BandwidthOccupancy> occupancies(BandwidthTimeSlot slot) {
        return (List<BandwidthTimeSlot.BandwidthOccupancy>) field(slot, "occupancyList");
    }

    private static class CacheProbe extends DHEFT_VNFAlgorithm {
        CacheProbe(NFVEnvironment env) { super(env, null); }
        @Override public void initialize() { }
        double ready(VNF task, VCPU cpu) { return getDLInfo(task, cpu).get("finish"); }
    }

    private static void cacheChecks(String config) {
        NFVUtil.getIns().initialize(config);
        CloudUtil.getInstance().initialize(config);
        NFVEnvironment env = new NFVEnvironment();
        VCPU cpu = null;
        for (VCPU candidate : env.getGlobal_vcpuMap().values()) {
            if (candidate.getVMID() != null && env.getGlobal_vmMap().containsKey(candidate.getVMID())) {
                cpu = candidate;
                break;
            }
        }
        if (cpu == null) throw new AssertionError("No VM-bound vCPU");
        VM vm = env.getGlobal_vmMap().get(cpu.getVMID());
        ComputeHost host = env.getGlobal_hostMap().get(vm.getHostID());
        vm.registerImageReadyTime(1, 10);
        VNF unrelated = task(100, 2, 1000);
        unrelated.setDlStartTime(10);
        unrelated.setDlFinishTime(100);
        host.addDLQueue(unrelated);
        CacheProbe probe = new CacheProbe(env);
        System.out.println("LOCAL_CACHE expectedReady=10 actualReady=" + probe.ready(task(101, 1, 1000), cpu));
        System.out.println("ZERO_IMAGE expectedReady=0 actualReady=" + probe.ready(task(102, 0, 0), cpu));
        vm.registerImageReadyTime(1, 100);
        System.out.println("CACHE_REGISTER previousReady=10 laterWrite=100 actualReady=" + vm.getImageReadyTime(1));
    }

    private static class ObservedDHEFT extends DHEFT_VNFAlgorithm {
        int localCacheDelays;
        int zeroImageDelays;
        double worstLocalDelay;
        ObservedDHEFT(NFVEnvironment env, SFC sfc) { super(env, sfc); }
        @Override public void scheduleVNF(VNF task, HashMap<String, VCPU> candidates) {
            Map<String, Double> previous = new HashMap<String, Double>();
            for (VM vm : env.getGlobal_vmMap().values()) {
                previous.put(vm.getVMID(), vm.getImageReadyTime(task.getType()));
            }
            super.scheduleVNF(task, candidates);
            VCPU cpu = candidates.get(task.getvCPUID());
            double ready = previous.get(cpu.getVMID());
            if (task.getImageSize() > 0 && ready < NFVUtil.MAXValue
                    && task.getDlFinishTime() > ready + 1e-6) {
                localCacheDelays++;
                worstLocalDelay = Math.max(worstLocalDelay, task.getDlFinishTime() - ready);
            }
            if (task.getImageSize() == 0 && task.getDlFinishTime() > 1e-6) zeroImageDelays++;
        }
    }

    private static class ObservedNHEFT extends NHEFT_VNFAlgorithm {
        int mismatchedTransfers;
        double worstByteError;
        boolean firstRealSeen;
        boolean virtualOnlyBootstrap;
        ObservedNHEFT(NFVEnvironment env, SFC sfc) { super(env, sfc); }
        @SuppressWarnings("unchecked")
        Map<String, BandwidthTimeSlot> slots() {
            try {
                Field field = NHEFT_VNFAlgorithm.class.getDeclaredField("linkSlotMap");
                field.setAccessible(true);
                return (Map<String, BandwidthTimeSlot>) field.get(this);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public void scheduleVNF(VNF task, HashMap<String, VCPU> candidates) {
            if (!firstRealSeen && task.getWorkLoad() > 0) {
                firstRealSeen = true;
                virtualOnlyBootstrap = !assignedVCPUMap.isEmpty();
            }
            super.scheduleVNF(task, candidates);
            if (task.getImageSize() <= 0 || task.getDlFinishTime() <= task.getDlStartTime() + 1e-6) return;
            String prefix = "NHEFT^" + task.getIDVector().get(0) + "^"
                    + task.getIDVector().get(1) + "^" + task.getvCPUID() + "^";
            double committed = 0;
            for (Map.Entry<String, BandwidthTimeSlot> entry : slots().entrySet()) {
                if (!entry.getKey().startsWith("HostLink^")) continue;
                for (BandwidthTimeSlot.BandwidthOccupancy occupancy : occupancies(entry.getValue())) {
                    if (occupancy.taskId.startsWith(prefix)) {
                        committed += (occupancy.endTime - occupancy.startTime) * occupancy.occupiedBW;
                    }
                }
            }
            double error = Math.abs(committed - task.getImageSize());
            worstByteError = Math.max(worstByteError, error);
            if (error > Math.max(1e-7, task.getImageSize() * 1e-9)) mismatchedTransfers++;
        }
    }

    private static void executeAudit(String label, DHEFT_VNFAlgorithm algorithm) throws Exception {
        // Reuse the independent readiness/overlap/core-load oracle without changing production visibility.
        Method audit = ExecutionSlotRegressionTest.class.getDeclaredMethod("audit", String.class, DHEFT_VNFAlgorithm.class);
        audit.setAccessible(true);
        audit.invoke(null, label, algorithm);
        Set<String> realCPUs = new HashSet<String>();
        for (VNF task : algorithm.getSfc().getVnfMap().values()) {
            if (task.getWorkLoad() > 0) realCPUs.add(task.getvCPUID());
        }
        System.out.println(label + " reportedVCPUs=" + algorithm.getAssignedVCPUMap().size()
                + " positiveWorkloadVCPUs=" + realCPUs.size());
        if (algorithm instanceof ObservedDHEFT) {
            ObservedDHEFT observed = (ObservedDHEFT) algorithm;
            System.out.println(label + " localCacheDelays=" + observed.localCacheDelays
                    + " worstLocalDelay=" + observed.worstLocalDelay + " zeroImageDelays=" + observed.zeroImageDelays);
        }
        if (algorithm instanceof ObservedNHEFT) {
            ObservedNHEFT observed = (ObservedNHEFT) algorithm;
            int overloaded = 0;
            int mostOccupancies = 0;
            for (BandwidthTimeSlot slot : observed.slots().values()) {
                List<BandwidthTimeSlot.BandwidthOccupancy> occupancies = occupancies(slot);
                mostOccupancies = Math.max(mostOccupancies, occupancies.size());
                TreeMap<Double, Long> events = new TreeMap<Double, Long>();
                for (BandwidthTimeSlot.BandwidthOccupancy occupancy : occupancies) {
                    if (occupancy.endTime <= occupancy.startTime) throw new AssertionError("Invalid reservation interval");
                    events.merge(occupancy.startTime, occupancy.occupiedBW, Long::sum);
                    events.merge(occupancy.endTime, -occupancy.occupiedBW, Long::sum);
                }
                long active = 0;
                long capacity = (Long) field(slot, "totalBW");
                for (long change : events.values()) {
                    active += change;
                    if (active > capacity || active < 0) { overloaded++; break; }
                }
            }
            System.out.println(label + " mismatchedTransfers=" + observed.mismatchedTransfers
                    + " worstByteError=" + observed.worstByteError + " overloadedLinks=" + overloaded
                    + " maxLinkOccupancies=" + mostOccupancies);
            System.out.println(label + " usedSetNonemptyBeforeFirstRealTask=" + observed.virtualOnlyBootstrap);
            TreeMap<Double, Long> repositoryEvents = new TreeMap<Double, Long>();
            String repositoryPrefix = algorithm.getEnv() instanceof NFVEnvironment
                    ? ((NFVEnvironment) algorithm.getEnv()).getDockerRepository().getPrefix() : "";
            for (Map.Entry<String, BandwidthTimeSlot> entry : observed.slots().entrySet()) {
                String[] ids = entry.getKey().split("\\^");
                if (ids.length != 5 || !"HostLink".equals(ids[0])) continue;
                if (!(ids[1] + "^" + ids[2]).equals(repositoryPrefix)
                        && !(ids[3] + "^" + ids[4]).equals(repositoryPrefix)) continue;
                for (BandwidthTimeSlot.BandwidthOccupancy occupancy : occupancies(entry.getValue())) {
                    repositoryEvents.merge(occupancy.startTime, occupancy.occupiedBW, Long::sum);
                    repositoryEvents.merge(occupancy.endTime, -occupancy.occupiedBW, Long::sum);
                }
            }
            long active = 0;
            long peak = 0;
            for (long change : repositoryEvents.values()) { active += change; peak = Math.max(peak, active); }
            System.out.println(label + " repositoryAggregatePeak=" + peak + " repositoryConfiguredBW="
                    + ((NFVEnvironment) algorithm.getEnv()).getDockerRepository().getBw()
                    + " (endpoint aggregate is not a constraint in the existing pair-slot model)");
        }
    }

    private static void workflow(String config) throws Exception {
        NFVUtil.getIns().initialize(config);
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        CloudUtil.getInstance().initialize(config);
        NFVEnvironment env = new NFVEnvironment();
        NFVUtil.nheft_vcpu_eft_tolerance = 0;
        NFVUtil.nheft_vcpu_open_requires_comp_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_drt_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = 0;
        NFVUtil.nheft_vcpu_open_gate_logic = NFVUtil.NHEFT_VCPU_OPEN_GATE_LOGIC_ALL;
        System.out.println("AUDIT config=" + config + " seed=" + CloudUtil.random_seed
                + " tasks=" + sfc.getVnfMap().size());
        executeAudit("DHEFT", new ObservedDHEFT((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
        executeAudit("NHEFT", new ObservedNHEFT((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = 1;
        executeAudit("G-NHEFT", new ObservedNHEFT((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "workflow".equals(args[0])) {
            workflow(args[1]);
        } else if (args.length == 2 && "small".equals(args[0])) {
            smallBandwidthChecks();
            cacheChecks(args[1]);
            executionAndGateChecks();
        } else {
            throw new IllegalArgumentException("Usage: PreRerunAudit small|workflow config.properties");
        }
    }
}
