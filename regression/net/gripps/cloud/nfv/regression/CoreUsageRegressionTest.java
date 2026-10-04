package net.gripps.cloud.nfv.regression;

import java.util.HashMap;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.Core;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.NHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.sfc.VNF;

/** Checks sibling queue accounting without changing the core's averaging policy. */
public final class CoreUsageRegressionTest {
    private static int checks;
    private static final String PREFIX = "0^0^0^0";

    private static class EmptyEnvironment extends CloudEnvironment {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            return new HashMap<Long, Cloud>();
        }
    }

    private static class Probe extends NHEFT_VNFAlgorithm {
        private final double ready;

        Probe(CloudEnvironment env, double ready) {
            super(env, null);
            this.constrainedMode = 1;
            this.ready = ready;
        }

        @Override public void initialize() { }

        @Override protected HashMap<String, Double> getDLInfo(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("start", 0.0);
            result.put("finish", ready);
            return result;
        }

        @Override protected HashMap<String, Double> calcDeadLine(VNF task, VCPU cpu) {
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("arrival_time", ready);
            result.put("dead_line", ready);
            return result;
        }

        double est(VNF task, VCPU cpu) { return super.calcEST(task, cpu); }
    }

    private static class Fixture {
        final CloudEnvironment env = new EmptyEnvironment();
        final HashMap<Long, VCPU> cpus = new HashMap<Long, VCPU>();
        final Core core;
        final Probe probe;

        Fixture(int count, int limit, double ready) {
            for (long i = 0; i < count; i++) {
                cpus.put(i, new VCPU(PREFIX + "^" + i, PREFIX,
                        new HashMap<String, Long>(), "vm", 1L, 0L));
            }
            core = new Core(PREFIX, count, 1L, 0L, cpus, limit);
            env.getGlobal_coreMap().put(PREFIX, core);
            probe = new Probe(env, ready);
        }

        VCPU cpu(long id) { return cpus.get(id); }

        void add(long id, double start, double finish, int usage) {
            cpu(id).getVnfQueue().add(task(start, finish, usage));
        }

        boolean allows(double start, double finish, int usage) {
            return probe.isAssignedInDuration(start, finish, core, cpu(0),
                    task(start, finish, usage));
        }
    }

    private static VNF task(double start, double finish, int usage) {
        VNF task = new VNF(1, Math.round(finish - start), 0, 0, 0, null, usage);
        task.setStartTime(start);
        task.setFinishTime(finish);
        return task;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void namedChecks() {
        Fixture busy = new Fixture(2, 75, 2);
        busy.add(1, 0, 10, 80);
        require(!busy.allows(2, 4, 80), "must read busy sibling queue: (80+80)/2 > 75");
        require(busy.allows(2, 4, 20), "below-threshold sibling load is feasible");
        busy.core.setMaxUsage(80);
        require(busy.allows(2, 4, 80), "exact threshold is allowed");

        Fixture candidateOnly = new Fixture(2, 75, 10);
        candidateOnly.add(0, 0, 10, 80);
        require(candidateOnly.allows(10, 12, 80), "candidate history must not count as sibling load");

        Fixture boundaries = new Fixture(2, 75, 2);
        boundaries.add(1, 0, 2, 80);
        boundaries.add(1, 4, 10, 80);
        boundaries.add(1, 3, 3, 100);
        require(boundaries.allows(2, 4, 80), "touching endpoints and zero-duration tasks do not overlap");
        require(boundaries.allows(3, 3, 100), "zero-duration incoming task occupies no interval");
        require(!boundaries.allows(1, 3, 80), "positive overlap at left boundary is counted");
        require(!boundaries.allows(3, 5, 80), "positive overlap at right boundary is counted");

        Fixture mixed = new Fixture(3, 59, 2);
        mixed.add(1, 0, 10, 20);
        mixed.add(2, 0, 10, 80);
        require(!mixed.allows(2, 4, 80), "all distinct siblings are counted: (80+20+80)/3 > 59");
        mixed.core.setMaxUsage(60);
        require(mixed.allows(2, 4, 80), "three-vCPU threshold equality is allowed");

        Fixture peaks = new Fixture(2, 75, 1);
        peaks.add(1, 0, 3, 20);
        peaks.add(1, 3, 10, 80);
        require(peaks.allows(1, 9, 70), "preserve per-vCPU maximum rather than summing queued tasks");

        Fixture single = new Fixture(1, 75, 2);
        require(!single.allows(2, 4, 80), "single-vCPU incoming usage is still checked");
        require(single.allows(2, 4, 75), "single-vCPU exact threshold is allowed");

        VNF incoming = task(2, 4, 80);
        VNF existing = busy.cpu(1).getVnfQueue().peek();
        boolean first = busy.probe.isAssignedInDuration(2, 4, busy.core, busy.cpu(0), incoming);
        boolean second = busy.probe.isAssignedInDuration(2, 4, busy.core, busy.cpu(0), incoming);
        require(first == second, "repeated check is deterministic");
        require(busy.cpu(0).getVnfQueue().isEmpty() && busy.cpu(1).getVnfQueue().size() == 1,
                "core checks do not modify queues");
        require(existing.getStartTime() == 0 && existing.getFinishTime() == 10
                        && incoming.getStartTime() == 2 && incoming.getFinishTime() == 4,
                "core checks do not modify task times");

        Fixture gap = new Fixture(2, 75, 12);
        gap.add(0, 0, 10, 20);
        gap.add(0, 20, 30, 20);
        gap.add(1, 10, 20, 80);
        require(gap.probe.est(task(-1, 4, 80), gap.cpu(0)) == 30,
                "calcEST rejects core-overloaded gap and finds feasible tail");

        Fixture adjacent = new Fixture(2, 75, 15);
        adjacent.add(0, 0, 10, 20);
        adjacent.add(0, 20, 30, 20);
        adjacent.add(1, 20, 30, 80);
        require(adjacent.probe.est(task(-1, 4, 80), adjacent.cpu(0)) == 15,
                "calcEST accepts gap ending exactly at sibling start");
    }

    private static void sweepChecks() {
        int[] usages = {0, 20, 50, 70, 75, 80, 100};
        int[] limits = {40, 60, 75, 80};
        double[][] intervals = {{-2, 0}, {0, 2}, {2, 3}, {3, 3}, {3, 6}, {6, 8}, {8, 10}};
        for (int count = 1; count <= 3; count++) {
            for (int usage : usages) {
                for (int siblingUsage : usages) {
                    for (int limit : limits) {
                        for (double[] interval : intervals) {
                            Fixture f = new Fixture(count, limit, 2);
                            for (int id = 1; id < count; id++) {
                                f.add(id, interval[0], interval[1], siblingUsage);
                            }
                            // Independent positive-duration intersection for [2,6).
                            boolean overlaps = Math.min(6, interval[1]) > Math.max(2, interval[0]);
                            int total = usage + (overlaps ? (count - 1) * siblingUsage : 0);
                            boolean expected = NFVUtil.getRoundedValue((double) total / count) <= limit;
                            require(f.allows(2, 6, usage) == expected,
                                    "sweep count=" + count + " usage=" + usage
                                            + " sibling=" + siblingUsage + " limit=" + limit
                                            + " interval=" + interval[0] + "," + interval[1]);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        namedChecks();
        sweepChecks();
        System.out.println("PASS core-usage regression: " + checks + " assertions");
    }
}
