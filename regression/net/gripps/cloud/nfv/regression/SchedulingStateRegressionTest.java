package net.gripps.cloud.nfv.regression;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
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
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.nfv.sfc.VNFCluster;

/** Independent fixtures for cache, transfer conservation, rollback and activation. */
public final class SchedulingStateRegressionTest {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void equal(double expected, double actual, String message) {
        require(Math.abs(expected - actual) <= 1.0e-7, message + ": " + actual);
    }
    static class Fixture extends NFVEnvironment {
        @Override public HashMap<Long, Cloud> buildDCMap() {
            HashMap<Long, ComputeHost> hosts = new HashMap<Long, ComputeHost>();
            for (long i = 0; i < 2; i++) {
                String prefix = "0^" + i;
                ComputeHost host = new ComputeHost(i, null, 0, new HashMap<String, VM>(), 0L, prefix, 100);
                hosts.put(i, host);
                global_hostMap.put(prefix, host);
                HashMap<String, Long> ids = new HashMap<String, Long>();
                ids.put(CloudUtil.ID_DC, 0L);
                ids.put(CloudUtil.ID_HOST, i);
                VCPU cpu = new VCPU(prefix + "^0^0^0", prefix + "^0^0", ids, "vm" + i, 100L, 0L);
                global_vcpuMap.put(cpu.getPrefix(), cpu);
                HashMap<String, VCPU> cpus = new HashMap<String, VCPU>();
                cpus.put(cpu.getPrefix(), cpu);
                VM vm = new VM("vm" + i, prefix, cpus, 100L, "vm" + i);
                global_vmMap.put(vm.getVMID(), vm);
                host.getVmMap().put(vm.getVMID(), vm);
                HashMap<Long, VCPU> siblings = new HashMap<Long, VCPU>();
                siblings.put(0L, cpu);
                global_coreMap.put(cpu.getCorePrefix(), new Core(cpu.getCorePrefix(), 1, 100L, 0L, siblings, 75));
            }
            HashMap<Long, Cloud> result = new HashMap<Long, Cloud>();
            result.put(0L, new Cloud(0L, hosts, 100L));
            result.put(1L, new Cloud(1L, new HashMap<Long, ComputeHost>(), 100L));
            return result;
        }
    }
    private static class CacheProbe extends DHEFT_VNFAlgorithm {
        CacheProbe(Fixture fixture, SFC sfc) { super(fixture, sfc); }
        @Override public void initialize() { this.vcpuMap.putAll(env.getGlobal_vcpuMap()); }
        double ready(VNF task, VCPU cpu) { return getDLInfo(task, cpu).get("finish"); }
        void mark(VNF task, VCPU cpu) { markImageTypeForVM(task, cpu); }
    }
    private static class TransferProbe extends NHEFT_VNFAlgorithm {
        TransferProbe(Fixture fixture) { super(fixture, null); }
        @Override public void initialize() { }
        Object simulate(long size, BandwidthTimeSlot slot) { return simulateDynamicDownload(0, size, 100, null, slot); }
    }
    private static VNF task(long id, int type, long size) {
        VNF result = new VNF(type, 100, 0, 0, 0, null, 20);
        Vector<Long> ids = new Vector<Long>();
        ids.add(1L); ids.add(id);
        result.setIDVector(ids);
        result.setImageSize(size);
        result.setDlStartTime(0); result.setDlFinishTime(0);
        return result;
    }
    private static Object field(Object value, String name) throws Exception {
        Field f = value.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(value);
    }
    private static double bytes(Object result) throws Exception {
        double[] starts = (double[]) field(result, "startTimes");
        double[] ends = (double[]) field(result, "endTimes");
        long[] rates = (long[]) field(result, "bandwidths");
        int count = (Integer) field(result, "segmentCount");
        double sum = 0;
        for (int i = 0; i < count; i++) sum += (ends[i] - starts[i]) * rates[i];
        return sum;
    }
    private static Method commit(Object trace) throws Exception {
        Method method = NHEFT_VNFAlgorithm.class.getDeclaredMethod("commitDynamicReservation",
                String.class, BandwidthTimeSlot.class, BandwidthTimeSlot.class, trace.getClass());
        method.setAccessible(true);
        return method;
    }
    private static void bandwidthChecks(Fixture fixture) throws Exception {
        Random random = new Random(151);
        for (int sample = 0; sample < 200; sample++) {
            BandwidthTimeSlot slot = new BandwidthTimeSlot("sweep", 100);
            long[] occupied = new long[20];
            for (int j = 0; j < 10; j++) {
                int start = random.nextInt(19), end = start + 1 + random.nextInt(20 - start);
                long bandwidth = 1 + random.nextInt(10);
                require(slot.reserveBW(start, end, bandwidth, "task" + j), "valid reservation rejected");
                for (int t = start; t < end; t++) occupied[t] += bandwidth;
            }
            for (int start = 0; start < 20; start++) {
                long peak = 0;
                for (int end = start + 1; end <= 20; end++) {
                    peak = Math.max(peak, occupied[end - 1]);
                    equal(100 - peak, slot.getAvailableBW(start, end), "interval peak oracle");
                }
            }
            slot.validateOccupancy();
        }
        BandwidthTimeSlot close = new BandwidthTimeSlot("close-boundary", 100);
        close.reserveBW(1.0e-7, 1, 100, "existing");
        TransferProbe probe = new TransferProbe(fixture);
        Object trace = probe.simulate(100, close);
        equal(100, bytes(trace), "close-boundary bytes");
        commit(trace).invoke(probe, "new", null, close, trace);
        close.validateOccupancy();
        BandwidthTimeSlot many = new BandwidthTimeSlot("many", 100);
        for (int i = 0; i < 2200; i++) many.reserveBW(i + 0.25, i + 0.5, 50, "old" + i);
        Object longTrace = probe.simulate(1000000, many);
        equal(1000000, bytes(longTrace), "all bytes beyond 4096 segments");
        require((Integer) field(longTrace, "segmentCount") > 4096, "segment trace was truncated");
        BandwidthTimeSlot empty = new BandwidthTimeSlot("empty", 100);
        Object atomic = probe.simulate(100, empty);
        BandwidthTimeSlot rejected = new BandwidthTimeSlot("reject", 100) {
            @Override public boolean reserveBW(double s, double e, long bw, String id) { return false; }
        };
        try {
            commit(atomic).invoke(probe, "atomic", empty, rejected, atomic);
            throw new AssertionError("Rejected reservation ignored");
        } catch (InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalStateException, "wrong reservation failure");
        }
        equal(0, empty.getUsedBWAt(0), "partial DC reservation was not rolled back");
        require(Double.isInfinite(empty.getNextAvailableTime(0, 101)), "impossible bandwidth reported feasible");
    }
    private static void cacheAndActivationChecks(Fixture fixture) {
        VCPU cpu = fixture.getGlobal_vcpuMap().get("0^0^0^0^0");
        VM vm = fixture.getGlobal_vmMap().get(cpu.getVMID());
        CacheProbe probe = new CacheProbe(fixture, null);
        vm.registerImageReadyTime(1, 10);
        VNF unrelated = task(10, 2, 1000);
        unrelated.setDlStartTime(10); unrelated.setDlFinishTime(100);
        fixture.getGlobal_hostMap().get("0^0").addDLQueue(unrelated);
        equal(10, probe.ready(task(11, 1, 1000), cpu), "local cache waits for unrelated transfer");
        equal(0, probe.ready(task(12, 2, 0), cpu), "zero image waits for transfer");
        vm.registerImageReadyTime(1, 100);
        equal(10, vm.getImageReadyTime(1), "registry moved ready time later");
        vm.getTypeSet().add(6);
        vm.registerImageReadyTime(6, 100);
        equal(0, vm.getImageReadyTime(6), "preloaded image became unavailable");
        VM sameHost = new VM("same-host", "0^0", new HashMap<String, VCPU>(), 100L, "same-host");
        sameHost.registerImageReadyTime(3, 7);
        fixture.getGlobal_vmMap().put(sameHost.getVMID(), sameHost);
        equal(7, probe.ready(task(13, 3, 1000), cpu), "same-host reuse waits for transfer");
        probe.mark(task(14, 4, 0), cpu);
        require(vm.getImageReadyTime(4) == NFVUtil.MAXValue, "zero image populated cache");
        VNF boundary = task(15, NFVUtil.VNF_TYPE_VSTART, 0);
        boundary.setWorkLoad(0); boundary.setUsage(0);
        probe.scheduleVNF(boundary, fixture.getGlobal_vcpuMap());
        require(probe.getAssignedVCPUMap().isEmpty() && probe.getHostSet().isEmpty(), "virtual task activated resource");
        require(cpu.getVnfQueue().isEmpty(), "virtual task occupied queue");
        require(boundary.getvCPUID() != null && boundary.getFinishTime() == 0, "virtual dependency metadata missing");
        NFVUtil.cloud_container_dl_mode = 0;
        equal(0, probe.ready(task(16, 5, 1000), cpu), "disabled image model still transfers");
        NFVUtil.cloud_container_dl_mode = 1;
    }
    private static void validatorChecks() {
        Fixture fixture = new Fixture();
        SFC sfc = new SFC(0, 0, 0, 0, 0, 0, 0, null, new HashMap<Long, VNF>(),
                new HashMap<Long, VNFCluster>(), 1L, 0, 0);
        CacheProbe probe = new CacheProbe(fixture, sfc);
        VCPU cpu = fixture.getGlobal_vcpuMap().get("0^0^0^0^0");
        VNF first = task(1, 1, 0);
        first.setvCPUID(cpu.getPrefix()); first.setStartTime(0); first.setFinishTime(1);
        sfc.getVnfMap().put(1L, first); cpu.getVnfQueue().add(first);
        probe.getAssignedVCPUMap().put(cpu.getPrefix(), cpu);
        probe.validateSchedule(); checks++;
        VNF second = task(2, 1, 0);
        second.setvCPUID(cpu.getPrefix()); second.setStartTime(0.5); second.setFinishTime(1.5);
        sfc.getVnfMap().put(2L, second); cpu.getVnfQueue().add(second);
        try {
            probe.validateSchedule();
            throw new AssertionError("Validator accepted overlap");
        } catch (IllegalStateException expected) { checks++; }
    }
    private static void gateChecks(Fixture fixture) throws Exception {
        TransferProbe probe = new TransferProbe(fixture);
        Method gate = NHEFT_VNFAlgorithm.class.getDeclaredMethod("isNewVCPUOpeningAllowed",
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        gate.setAccessible(true);
        for (int enabled = 0; enabled < 8; enabled++) {
            for (int advantages = 0; advantages < 8; advantages++) {
                for (int any = 0; any < 2; any++) {
                    for (int eft = 0; eft < 3; eft++) {
                        boolean expected = eft == 0 && (enabled == 0 || (any == 1
                                ? (enabled & advantages) != 0 : (enabled & advantages) == enabled));
                        boolean actual = (Boolean) gate.invoke(probe,
                                eft == 0 ? 5.0 : (eft == 1 ? 10.0 : 15.0),
                                (advantages & 1) != 0 ? 5.0 : 15.0,
                                (advantages & 2) != 0 ? 5.0 : 15.0,
                                (advantages & 4) != 0 ? 5.0 : 15.0,
                                10.0, 10.0, 10.0, 10.0,
                                (enabled & 1) != 0, (enabled & 2) != 0, (enabled & 4) != 0, any == 1);
                        require(expected == actual, "gate decision changed");
                    }
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        NFVUtil.cloud_container_dl_mode = 1; NFVUtil.repository_bw = 100;
        NFVUtil.cloud_constrained_mode = 1;
        Fixture fixture = new Fixture();
        bandwidthChecks(fixture);
        cacheAndActivationChecks(fixture);
        validatorChecks();
        gateChecks(fixture);
        System.out.println("PASS scheduling-state regression: " + checks + " assertions");
    }
}
