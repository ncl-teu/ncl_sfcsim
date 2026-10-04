package net.gripps.cloud.nfv.sfc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Core;
import net.gripps.cloud.core.VCPU;
import net.gripps.clustering.common.aplmodel.DataDependence;

/** Fail closed before publishing metrics from a completed schedule. */
public final class SchedulingValidator {
    private static final double TOL = 1.0e-6;
    private SchedulingValidator() { }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Schedule validation: " + message);
    }

    private static boolean time(double value) {
        return Double.isFinite(value) && value >= 0.0d;
    }

    public static void validate(BaseVNFSchedulingAlgorithm algorithm) {
        Set<Long> queued = new HashSet<Long>();
        Set<String> used = new HashSet<String>();
        for (VCPU cpu : algorithm.getVcpuMap().values()) {
            ArrayList<VNF> tasks = new ArrayList<VNF>(cpu.getVnfQueue());
            tasks.sort(Comparator.comparingDouble(VNF::getStartTime));
            double previousFinish = 0.0d;
            for (VNF task : tasks) {
                long id = task.getIDVector().get(1);
                require(algorithm.getSfc().findVNFByLastID(id) == task, "foreign task " + id);
                require(!BaseVNFSchedulingAlgorithm.isVirtualBoundary(task), "virtual task occupies a vCPU");
                require(queued.add(id), "duplicate task " + id);
                require(cpu.getPrefix().equals(task.getvCPUID()), "wrong queue for task " + id);
                if (task.getFinishTime() > task.getStartTime()) {
                    require(task.getStartTime() + TOL >= previousFinish, "execution overlap on " + cpu.getPrefix());
                    previousFinish = task.getFinishTime();
                }
                used.add(cpu.getPrefix());
            }
        }
        require(used.equals(algorithm.getAssignedVCPUMap().keySet()), "activated-resource count mismatch");
        double finish = 0.0d;
        for (VNF task : algorithm.getSfc().getVnfMap().values()) {
            long id = task.getIDVector().get(1);
            VCPU cpu = algorithm.getVcpuMap().get(task.getvCPUID());
            require(cpu != null, "unscheduled task " + id);
            require(time(task.getStartTime()) && time(task.getFinishTime())
                    && time(task.getDlStartTime()) && time(task.getDlFinishTime()), "invalid time for task " + id);
            require(task.getDlFinishTime() >= task.getDlStartTime(), "negative transfer duration for task " + id);
            require(task.getStartTime() + TOL >= task.getDlFinishTime(), "image not ready for task " + id);
            require(Math.abs(task.getFinishTime() - task.getStartTime()
                    - algorithm.calcExecTime(task.getWorkLoad(), cpu)) <= TOL, "execution duration mismatch for task " + id);
            require(BaseVNFSchedulingAlgorithm.isVirtualBoundary(task) || queued.contains(id), "missing execution queue entry " + id);
            for (DataDependence edge : task.getDpredList()) {
                VNF predecessor = algorithm.getSfc().findVNFByLastID(edge.getFromID().get(1));
                VCPU source = algorithm.getVcpuMap().get(predecessor.getvCPUID());
                require(source != null, "unscheduled predecessor of " + id);
                double arrival = predecessor.getFinishTime() + algorithm.calcComTime(edge.getMaxDataSize(), source, cpu);
                require(task.getStartTime() + TOL >= arrival, "dependency data not ready for task " + id);
            }
            finish = Math.max(finish, task.getFinishTime());
        }
        require(time(algorithm.getMakeSpan()) && Math.abs(algorithm.getMakeSpan() - finish) <= TOL, "makespan mismatch");
        if (algorithm.getConstrainedMode() != 1) return;
        for (Core core : algorithm.getEnv().getGlobal_coreMap().values()) {
            TreeMap<Double, Long> events = new TreeMap<Double, Long>();
            for (VCPU cpu : core.getvCPUMap().values()) {
                for (VNF task : cpu.getVnfQueue()) {
                    if (task.getFinishTime() <= task.getStartTime()) continue;
                    events.merge(task.getStartTime(), (long) task.getUsage(), Long::sum);
                    events.merge(task.getFinishTime(), -(long) task.getUsage(), Long::sum);
                }
            }
            long usage = 0L;
            for (long change : events.values()) {
                usage += change;
                double average = CloudUtil.getRoundedValue((double) usage / core.getvCPUMap().size());
                require(usage >= 0 && average <= core.getMaxUsage(), "core capacity exceeded on " + core.getPrefix());
            }
        }
    }
}
