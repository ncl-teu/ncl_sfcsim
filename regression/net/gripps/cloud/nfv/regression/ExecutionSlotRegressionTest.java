package net.gripps.cloud.nfv.regression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.Core;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.DHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.listscheduling.NHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

/** Standalone regression checks; run with Java 8 via ant regression_tests. */
public final class ExecutionSlotRegressionTest {
    private static int checks;
    private static final String CORE = "0^0^0^0";

    private static class EmptyEnvironment extends CloudEnvironment {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            return new HashMap<Long, Cloud>();
        }
    }

    // Only readiness inputs are stubbed; production calcEST/queue arithmetic runs.
    private static class Probe extends NHEFT_VNFAlgorithm {
        final double drt;
        final double irt;

        Probe(CloudEnvironment env, int mode, double drt, double irt) {
            super(env, null);
            this.constrainedMode = mode;
            this.drt = drt;
            this.irt = irt;
        }

        @Override public void initialize() { }

        @Override protected HashMap<String, Double> getDLInfo(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("start", 0.0);
            result.put("finish", irt);
            return result;
        }

        @Override protected HashMap<String, Double> calcDeadLine(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("arrival_time", drt);
            result.put("dead_line", drt);
            return result;
        }

        double est(VNF task, VCPU cpu) { return super.calcEST(task, cpu); }
    }

    // Record the interval passed to the core checker independently of its policy.
    private static class RecordingProbe extends Probe {
        final List<double[]> intervals = new ArrayList<double[]>();
        int rejectFirst;

        RecordingProbe(CloudEnvironment env, double drt, double irt, int rejectFirst) {
            super(env, 1, drt, irt);
            this.rejectFirst = rejectFirst;
        }

        @Override public boolean isAssignedInDuration(
                double start, double end, Core core, VCPU cpu, VNF task) {
            intervals.add(new double[] {start, end});
            return intervals.size() > rejectFirst;
        }
    }

    private static class Fixture {
        final CloudEnvironment env = new EmptyEnvironment();
        final VCPU cpu = cpu(0);
        final HashMap<Long, VCPU> cpus = new HashMap<Long, VCPU>();

        Fixture(double... starts) {
            cpus.put(0L, cpu);
            env.getGlobal_coreMap().put(CORE, new Core(CORE, 1, 1L, 0L, cpus, 75));
            for (int i = 0; i < starts.length; i++) {
                cpu.getVnfQueue().add(task(i + 1, 10, starts[i]));
            }
        }

        void sibling(double start) {
            VCPU sibling = cpu(1);
            sibling.getVnfQueue().add(task(100, 10, start));
            cpus.put(1L, sibling);
        }
    }

    private static VCPU cpu(long id) {
        return new VCPU(CORE + "^" + id, CORE, new HashMap<String, Long>(), "vm", 1L, 0L);
    }

    private static VNF task(long id, long workload, double start) {
        VNF task = new VNF(1, workload, 0, 0, 0, null, 20);
        Vector<Long> ids = new Vector<Long>();
        ids.add(1L);
        ids.add(id);
        task.setIDVector(ids);
        task.setStartTime(start);
        task.setFinishTime(start + workload);
        return task;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1e-9,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static double checkSlot(Probe probe, Fixture fixture, String name) {
        VNF incoming = task(200, 5, -1);
        int size = fixture.cpu.getVnfQueue().size();
        double start = probe.est(incoming, fixture.cpu);
        double finish = start + probe.calcExecTime(incoming.getWorkLoad(), fixture.cpu);
        require(start >= Math.max(probe.drt, probe.irt), name + ": readiness violated");
        for (VNF existing : fixture.cpu.getVnfQueue()) {
            require(Math.min(finish, existing.getFinishTime())
                            - Math.max(start, existing.getStartTime()) <= 1e-9,
                    name + ": execution overlaps task " + existing.getIDVector().get(1));
        }
        require(size == fixture.cpu.getVnfQueue().size(), name + ": query mutated queue");
        equal(-1, incoming.getStartTime(), name + ": query mutated incoming task");
        equal(start, probe.est(incoming, fixture.cpu), name + ": repeated query changed EST");
        return start;
    }

    private static void expect(String name, int mode, double drt, double irt,
                               double expected, double... starts) {
        Fixture fixture = new Fixture(starts);
        equal(expected, checkSlot(new Probe(fixture.env, mode, drt, irt), fixture, name), name);
    }

    private static void unitChecks() {
        expect("internal gap still fits", 1, 10, 12, 12, 0, 20);
        expect("IRT invalidates internal gap", 1, 10, 18, 30, 0, 20);
        expect("IRT invalidates front gap", 1, 10, 18, 30, 20);
        expect("exact internal boundary", 1, 10, 15, 15, 0, 20);
        expect("exact front boundary", 1, 10, 15, 15, 20);
        expect("late image after queue", 1, 10, 35, 35, 0, 20);
        expect("late data after queue", 1, 35, 12, 35, 0, 20);
        expect("no image delay", 1, 10, 0, 10, 0, 20);
        expect("late data rejects front gap", 1, 18, 0, 30, 20);
        expect("empty constrained queue", 1, 10, 18, 18);
        expect("empty unconstrained queue", 0, 18, 10, 18);
        expect("unconstrained front gap rejected", 0, 10, 18, 30, 20);
        expect("unconstrained front gap fits", 0, 10, 12, 12, 20);
        expect("unconstrained internal insertion", 0, 10, 12, 12, 0, 20);
        expect("multi-task front insertion", 1, 0, 0, 0, 20, 40);
        expect("search later gap", 1, 10, 18, 30, 0, 20, 50);
        expect("search later gap exact boundary", 1, 10, 45, 45, 0, 20, 50);

        Fixture gap = new Fixture(0, 20);
        RecordingProbe gapProbe = new RecordingProbe(gap.env, 10, 12, 0);
        equal(12, gapProbe.est(task(200, 5, -1), gap.cpu), "record gap EST");
        equal(12, gapProbe.intervals.get(0)[0], "core checked IRT-aware gap start");
        equal(17, gapProbe.intervals.get(0)[1], "core checked IRT-aware gap end");

        Fixture front = new Fixture(20);
        RecordingProbe frontProbe = new RecordingProbe(front.env, 10, 12, 0);
        equal(12, frontProbe.est(task(200, 5, -1), front.cpu), "record front EST");
        equal(12, frontProbe.intervals.get(0)[0], "core checked IRT-aware front start");

        Fixture tail = new Fixture(0, 20);
        RecordingProbe tailProbe = new RecordingProbe(tail.env, 40, 50, 0);
        equal(50, tailProbe.est(task(200, 5, -1), tail.cpu), "tail keeps readiness");
        equal(50, tailProbe.intervals.get(0)[0], "core checked IRT-aware tail start");
        equal(55, tailProbe.intervals.get(0)[1], "core checked IRT-aware tail end");

        RecordingProbe dataProbe = new RecordingProbe(tail.env, 50, 0, 0);
        equal(50, dataProbe.est(task(200, 5, -1), tail.cpu), "tail keeps DRT");
        RecordingProbe impossible = new RecordingProbe(tail.env, 50, 0, 1);
        require(Double.isInfinite(impossible.est(task(200, 5, -1), tail.cpu)), "rejected core must not fall back to an infeasible slot");
        tail.sibling(70);
        RecordingProbe siblingProbe = new RecordingProbe(tail.env, 10, 18, 1);
        equal(80, siblingProbe.est(task(200, 5, -1), tail.cpu), "tail fallback waits for core");
        front.sibling(70);
        RecordingProbe frontReject = new RecordingProbe(front.env, 10, 12, 1);
        equal(30, frontReject.est(task(200, 5, -1), front.cpu), "retry tail without waiting for unrelated sibling");

        for (int mode = 0; mode <= 1; mode++) {
            for (int count = 0; count <= 3; count++) {
                for (int drt = 0; drt <= 70; drt += 7) {
                    for (int irt = 0; irt <= 70; irt += 7) {
                        double[] starts = new double[count];
                        for (int i = 0; i < count; i++) starts[i] = i * 20;
                        Fixture fixture = new Fixture(starts);
                        double selected = checkSlot(new Probe(fixture.env, mode, drt, irt), fixture,
                                "sweep mode=" + mode + " count=" + count + " DRT=" + drt + " IRT=" + irt);
                        // Exhaustive integer-time oracle: these fixtures have integer boundaries.
                        for (int time = Math.max(drt, irt); time <= 100; time++) {
                            boolean free = true;
                            for (VNF existing : fixture.cpu.getVnfQueue()) {
                                if (time < existing.getFinishTime() && time + 5 > existing.getStartTime()) free = false;
                            }
                            if (free) {
                                equal(time, selected, "earliest feasible insertion oracle");
                                break;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("PASS execution-slot regression: " + checks + " assertions");
    }

    private static void audit(String label, DHEFT_VNFAlgorithm algorithm) {
        algorithm.mainProcess();
        algorithm.validateSchedule();
        int pairs = 0;
        int scheduled = 0;
        for (VCPU cpu : algorithm.getAssignedVCPUMap().values()) {
            List<VNF> tasks = new ArrayList<VNF>(cpu.getVnfQueue());
            for (int i = 0; i < tasks.size(); i++) {
                VNF a = tasks.get(i);
                scheduled++;
                require(a.getStartTime() + 1e-6 >= a.getDlFinishTime(), label + ": starts before IRT");
                equal(algorithm.calcExecTime(a.getWorkLoad(), cpu),
                        a.getFinishTime() - a.getStartTime(), label + ": execution duration changed");
                for (DataDependence edge : a.getDpredList()) {
                    VNF predecessor = algorithm.getSfc().findVNFByLastID(edge.getFromID().get(1));
                    VCPU predecessorCPU = algorithm.getVcpuMap().get(predecessor.getvCPUID());
                    require(predecessorCPU != null, label + ": predecessor was not scheduled");
                    double arrival = predecessor.getStartTime()
                            + algorithm.calcExecTime(predecessor.getWorkLoad(), predecessorCPU)
                            + algorithm.calcComTime(edge.getMaxDataSize(), predecessorCPU, cpu);
                    require(a.getStartTime() + 1e-6 >= arrival, label + ": starts before DRT");
                }
                if (a.getFinishTime() - a.getStartTime() <= 1e-6) continue;
                for (int j = i + 1; j < tasks.size(); j++) {
                    VNF b = tasks.get(j);
                    if (b.getFinishTime() - b.getStartTime() <= 1e-6) continue;
                    if (Math.min(a.getFinishTime(), b.getFinishTime())
                            - Math.max(a.getStartTime(), b.getStartTime()) > 1e-6) pairs++;
                }
            }
        }
        System.out.println(label + " makespan=" + algorithm.getMakeSpan()
                + " vCPUs=" + algorithm.getAssignedVCPUMap().size() + " overlapPairs=" + pairs);
        require(pairs == 0, label + ": same-vCPU execution overlap");
        long actualTasks = algorithm.getSfc().getVnfMap().values().stream()
                .filter(task -> !net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm.isVirtualBoundary(task)).count();
        require(scheduled == actualTasks, label + ": missing scheduled tasks");
        auditCoreUsage(label, algorithm);
    }

    private static void auditCoreUsage(String label, DHEFT_VNFAlgorithm algorithm) {
        int violations = 0;
        double maximum = 0;
        for (Core core : algorithm.getEnv().getGlobal_coreMap().values()) {
            TreeMap<Double, Long> events = new TreeMap<Double, Long>();
            for (VCPU cpu : core.getvCPUMap().values()) {
                for (VNF task : cpu.getVnfQueue()) {
                    if (task.getFinishTime() <= task.getStartTime()) continue;
                    events.merge(task.getStartTime(), (long) task.getUsage(), Long::sum);
                    events.merge(task.getFinishTime(), -(long) task.getUsage(), Long::sum);
                }
            }
            long activeUsage = 0;
            boolean overloaded = false;
            for (Map.Entry<Double, Long> event : events.entrySet()) {
                // Merge starts/finishes at equal timestamps to respect [start,end).
                activeUsage += event.getValue();
                double average = NFVUtil.getRoundedValue(
                        (double) activeUsage / core.getvCPUMap().size());
                maximum = Math.max(maximum, average);
                if (average > core.getMaxUsage()) overloaded = true;
            }
            if (overloaded) violations++;
        }
        System.out.println(label + " overloadedCores=" + violations + " peakCoreAverage=" + maximum);
        require(violations == 0, label + ": actual concurrent core usage exceeds limit");
    }

    private static void realChecks(String properties) {
        // Match NFVSchedulingTest's workflow-generation/platform-reseed order.
        NFVUtil.getIns().initialize(properties);
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        CloudUtil.getInstance().initialize(properties);
        NFVEnvironment env = new NFVEnvironment();
        NFVUtil.nheft_vcpu_eft_tolerance = 0;
        NFVUtil.nheft_vcpu_open_requires_comp_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_drt_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = 0;
        System.out.println("AUDIT seed=" + CloudUtil.random_seed + " tasks=" + sfc.getVnfMap().size());
        audit("DHEFT", new DHEFT_VNFAlgorithm((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
        audit("NHEFT", new NHEFT_VNFAlgorithm((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = 1;
        NFVUtil.nheft_vcpu_open_gate_logic = NFVUtil.NHEFT_VCPU_OPEN_GATE_LOGIC_ALL;
        audit("G-NHEFT", new NHEFT_VNFAlgorithm((NFVEnvironment) env.deepCopy(), (SFC) sfc.deepCopy()));
    }

    public static void main(String[] args) {
        unitChecks();
        if (args.length == 1) realChecks(args[0]);
    }
}
