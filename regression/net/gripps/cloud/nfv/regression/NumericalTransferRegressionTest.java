package net.gripps.cloud.nfv.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.NHEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;

/** Reproduce roundoff at transfer boundaries without relaxing reservation checks. */
public final class NumericalTransferRegressionTest {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class Probe extends NHEFT_VNFAlgorithm {
        Probe() { super(new SchedulingStateRegressionTest.Fixture(), null); }
        @Override public void initialize() { }
        Object simulate(double start, long size, long rate, BandwidthTimeSlot slot) {
            return simulateDynamicDownload(start, size, rate, null, slot);
        }
    }

    private static Object field(Object trace, String name) throws Exception {
        Field result = trace.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result.get(trace);
    }

    private static double bytes(Object trace) throws Exception {
        double[] starts = (double[]) field(trace, "startTimes");
        double[] ends = (double[]) field(trace, "endTimes");
        long[] rates = (long[]) field(trace, "bandwidths");
        int count = (Integer) field(trace, "segmentCount");
        double total = 0;
        for (int i = 0; i < count; i++) {
            require(ends[i] > starts[i], "zero-length transfer segment");
            total += (ends[i] - starts[i]) * rates[i];
        }
        return total;
    }

    private static void commit(Probe probe, BandwidthTimeSlot slot, Object trace) throws Exception {
        Method method = NHEFT_VNFAlgorithm.class.getDeclaredMethod("commitDynamicReservation",
                String.class, BandwidthTimeSlot.class, BandwidthTimeSlot.class, trace.getClass());
        method.setAccessible(true);
        method.invoke(probe, "new", null, slot, trace);
        slot.validateOccupancy();
        checks++;
    }

    private static void boundary(boolean futureEvent) throws Exception {
        Probe probe = new Probe();
        BandwidthTimeSlot slot = new BandwidthTimeSlot("residual", 1000);
        require(slot.reserveBW(0.0d, 3.3d, 950L, "old"), "fixture reservation failed");
        if (futureEvent) require(slot.reserveBW(3.4d, 4.0d, 950L, "future"), "future fixture failed");
        // (3.3-1.3)*50 = 99.99999999999999. The residual cannot advance time.
        Object trace = probe.simulate(1.3d, 100L, 1000L, slot);
        require((Double) field(trace, "finishTime") == 3.3d, "roundoff changed completion boundary");
        require((Integer) field(trace, "segmentCount") == 1, "phantom residual segment added");
        require(Math.abs(bytes(trace) - 100.0d) <= 1.0e-7, "missing transfer bytes");
        commit(probe, slot, trace);
    }

    public static void main(String[] args) throws Exception {
        NFVUtil.repository_bw = 100;
        boundary(false);
        boundary(true);
        Probe probe = new Probe();
        Random random = new Random(610245);
        for (int sample = 0; sample < 2000; sample++) {
            long size = 1 + random.nextInt(5000);
            long available = 1 + random.nextInt(999);
            double start = random.nextDouble() * 1000.0d;
            double event = start + (double) size / available;
            BandwidthTimeSlot slot = new BandwidthTimeSlot("sweep", 1000);
            require(slot.reserveBW(0, event, 1000 - available, "old"), "sweep fixture rejected");
            if ((sample & 1) == 0) {
                require(slot.reserveBW(event + 0.1d, event + 1.0d, 950L, "future"), "future event rejected");
            }
            Object trace = probe.simulate(start, size, 1000L, slot);
            require(Math.abs(bytes(trace) - size) <= Math.max(1.0e-7, size * 1.0e-9), "byte conservation");
            require(Double.isFinite((Double) field(trace, "finishTime")), "nonfinite finish");
            commit(probe, slot, trace);
        }
        try {
            // No actual byte was transmitted: a genuinely unrepresentable duration must fail.
            probe.simulate(1.0e18d, 1L, 1000L, new BandwidthTimeSlot("too-large-time", 1000));
            throw new AssertionError("materially untransmitted image accepted");
        } catch (IllegalStateException expected) { checks++; }
        System.out.println("PASS numerical-transfer regression: " + checks + " assertions");
    }
}
