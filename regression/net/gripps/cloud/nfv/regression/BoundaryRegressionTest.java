package net.gripps.cloud.nfv.regression;

import java.util.HashMap;
import java.util.Random;
import java.util.Vector;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.NHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.nfv.sfc.VNFCluster;

/** Fail-closed image planning, repository topology, and bounded random inputs. */
public final class BoundaryRegressionTest {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void fails(Class<? extends RuntimeException> type, Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            require(type.isInstance(expected), message + ": wrong failure " + expected);
            return;
        }
        throw new AssertionError(message + ": did not fail");
    }

    private static class Probe extends NHEFT_VNFAlgorithm {
        Probe(NFVEnvironment env, SFC sfc) { super(env, sfc); }
        @Override public void initialize() { vcpuMap.putAll(env.getGlobal_vcpuMap()); }
        double ready(VNF task, VCPU cpu) { return getDLInfo(task, cpu).get("finish"); }
    }

    private static final class CommitProbe extends Probe {
        CommitProbe(NFVEnvironment env, SFC sfc) { super(env, sfc); }
        @Override protected HashMap<String, Double> getDLInfo(VNF task, VCPU cpu) {
            // Deliberately make evaluation succeed while the actual commit has no source.
            HashMap<String, Double> result = new HashMap<String, Double>();
            result.put("start", 0.0d); result.put("finish", 0.0d);
            return result;
        }
    }

    private static final class SingleDC extends SchedulingStateRegressionTest.Fixture {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            HashMap<Long, Cloud> result = super.buildDCMap();
            result.remove(1L);
            return result;
        }
    }

    private static final class MissingDCZero extends SchedulingStateRegressionTest.Fixture {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            HashMap<Long, Cloud> result = super.buildDCMap();
            result.remove(0L);
            return result;
        }
    }

    private static SFC workflow(long imageSize) {
        SFC sfc = new SFC(0, 0, 0, 0, 0, 0, 0, null, new HashMap<Long, VNF>(),
                new HashMap<Long, VNFCluster>(), 1L, 0, 0);
        VNF task = new VNF(8, 100, 0, 0, 0, null, 20);
        Vector<Long> ids = new Vector<Long>();
        ids.add(1L); ids.add(1L); task.setIDVector(ids);
        task.setImageSize(imageSize); task.setDlStartTime(0); task.setDlFinishTime(0);
        sfc.getVnfMap().put(1L, task);
        return sfc;
    }

    private static void unchangedAfterFailure(Probe probe, VNF task) {
        require(probe.getAssignedVCPUMap().isEmpty(), "failed task activated a vCPU");
        require(probe.getImageDownloadTotalCount() == 0, "failed task counted a transfer");
        for (VCPU cpu : probe.getVcpuMap().values()) {
            require(cpu.getVnfQueue().isEmpty(), "failed task entered an execution queue");
            require(probe.getEnv().getGlobal_vmMap().get(cpu.getVMID()).getImageReadyTime(task.getType())
                    == NFVUtil.MAXValue, "failed task populated image cache");
        }
    }

    private static void imageChecks() {
        SchedulingStateRegressionTest.Fixture env = new SchedulingStateRegressionTest.Fixture();
        env.setDockerRepository(null);
        SFC sfc = workflow(1000);
        VNF task = sfc.findVNFByLastID(1L);
        Probe probe = new Probe(env, sfc);
        for (VCPU cpu : probe.getVcpuMap().values()) {
            require(probe.ready(task, cpu) == NFVUtil.MAXValue, "missing source treated as ready");
            require(probe.calcDownloadImageTime(task, cpu) == NFVUtil.MAXValue, "static fallback on missing source");
        }
        fails(IllegalStateException.class, () -> probe.scheduleVNF(task, probe.getVcpuMap()), "all sources missing");
        unchangedAfterFailure(probe, task);

        CommitProbe commit = new CommitProbe(env, sfc);
        fails(IllegalStateException.class, () -> commit.scheduleVNF(task, commit.getVcpuMap()), "missing commit plan");
        unchangedAfterFailure(commit, task);

        // A missing repository does not prevent an actual cache source from being used.
        env.getGlobal_vmMap().get("vm1").registerImageReadyTime(task.getType(), 5.0d);
        env.getGlobal_hostMap().get("0^0").setBw(0L);
        VCPU blocked = probe.getVcpuMap().get("0^0^0^0^0");
        blocked.setMips(1000L);
        require(probe.ready(task, blocked) == NFVUtil.MAXValue, "unusable target path treated as ready");
        probe.scheduleVNF(task, probe.getVcpuMap());
        probe.validateSchedule();
        require(task.getvCPUID().equals("0^1^0^0^0"), "did not skip infeasible faster vCPU");
        require(task.getStartTime() == 5.0d, "cache ready time lost");

        SchedulingStateRegressionTest.Fixture noRepo = new SchedulingStateRegressionTest.Fixture();
        noRepo.setDockerRepository(null);
        SFC zero = workflow(0);
        Probe zeroProbe = new Probe(noRepo, zero);
        zeroProbe.scheduleVNF(zero.findVNFByLastID(1L), zeroProbe.getVcpuMap());
        zeroProbe.validateSchedule();
        require(zero.findVNFByLastID(1L).getStartTime() == 0.0d, "zero image requires a source");
        NFVUtil.cloud_container_dl_mode = 0;
        SFC disabled = workflow(1000);
        Probe disabledProbe = new Probe(new SchedulingStateRegressionTest.Fixture(), disabled);
        ((NFVEnvironment) disabledProbe.getEnv()).setDockerRepository(null);
        disabledProbe.scheduleVNF(disabled.findVNFByLastID(1L), disabledProbe.getVcpuMap());
        disabledProbe.validateSchedule();
        require(disabled.findVNFByLastID(1L).getStartTime() == 0.0d, "disabled image model requires a source");
        NFVUtil.cloud_container_dl_mode = 1;

        SchedulingStateRegressionTest.Fixture broken = new SchedulingStateRegressionTest.Fixture();
        broken.getDcMap().remove(1L);
        SFC brokenSfc = workflow(1000);
        Probe brokenProbe = new Probe(broken, brokenSfc);
        VNF brokenTask = brokenSfc.findVNFByLastID(1L);
        require(brokenProbe.ready(brokenTask, brokenProbe.getVcpuMap().values().iterator().next())
                == NFVUtil.MAXValue, "missing source DC treated as ready");
        fails(IllegalStateException.class, () -> brokenProbe.scheduleVNF(brokenTask, brokenProbe.getVcpuMap()),
                "invalid source DC must not schedule");
        unchangedAfterFailure(brokenProbe, brokenTask);
    }

    private static void randomChecks() {
        CloudUtil.uniformRand = new Random(151);
        for (int i = 0; i < 10000; i++) {
            double a = CloudUtil.genDouble(0.5d, 0.7d);
            double b = CloudUtil.genDouble2(-2.0d, -1.5d, 0, 0.5d);
            double c = CloudUtil.genDouble(0.12345d, 0.12346d);
            require(a >= 0.5d && a <= 0.7d, "genDouble outside range");
            require(b >= -2.0d && b <= -1.5d, "genDouble2 outside range");
            require(c >= 0.12345d && c <= 0.12346d, "rounded sample outside narrow range");
        }
        Random control = new Random(151);
        CloudUtil.uniformRand = new Random(151);
        for (int i = 0; i < 100; i++) {
            require(CloudUtil.genDouble(1.0d, 1.0d) == 1.0d, "fixed range changed");
            control.nextDouble();
        }
        require(control.nextDouble() == CloudUtil.uniformRand.nextDouble(), "fixed range changed RNG consumption");
        CloudUtil.uniformRand = new Random(151);
        require(CloudUtil.genDouble(0.5d, 0.5d) == 0.5d, "fixed subunit rate changed");
        control = new Random(151);
        CloudUtil.uniformRand = new Random(151);
        require(CloudUtil.genDouble2(0.5d, 0.5d, 0, 0.5d) == 0.5d, "genDouble2 fixed range changed");
        require(control.nextDouble() == CloudUtil.uniformRand.nextDouble(), "genDouble2 fixed range consumed a new draw");
        fails(IllegalArgumentException.class, () -> CloudUtil.genDouble(1, 0), "reversed range");
        fails(IllegalArgumentException.class, () -> CloudUtil.genDouble(Double.NaN, 1), "NaN range");
        fails(IllegalArgumentException.class, () -> CloudUtil.genDouble2(0, Double.POSITIVE_INFINITY, 0, 0.5),
                "infinite range");
        fails(IllegalArgumentException.class, () -> CloudUtil.genDouble(-Double.MAX_VALUE, Double.MAX_VALUE),
                "unrepresentable width");
    }

    public static void main(String[] args) {
        NFVUtil.cloud_container_dl_mode = 1; NFVUtil.repository_bw = 100;
        NFVUtil.cloud_constrained_mode = 1; NFVUtil.nheft_vcpu_eft_tolerance = 0;
        NFVUtil.nheft_vcpu_open_requires_comp_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_drt_advantage = 0;
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = 0;
        fails(IllegalStateException.class, () -> new SingleDC(), "repository DC missing");
        fails(IllegalStateException.class, () -> new MissingDCZero(), "DC zero missing");
        SchedulingStateRegressionTest.Fixture valid = new SchedulingStateRegressionTest.Fixture();
        require(valid.getDockerRepository().getDcID() == 1L, "repository was relocated");
        imageChecks();
        randomChecks();
        System.out.println("PASS boundary regression: " + checks + " assertions");
    }
}
