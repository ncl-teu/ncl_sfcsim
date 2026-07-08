package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

/**
 * NPHEFT: NHEFT + one tentative contiguous front-insertion probe.
 *
 * If the probe cannot improve finish potential, baseline NHEFT start is kept.
 */
public class NPHEFT_VNFAlgorithm extends NHEFT_VNFAlgorithm {

    private static final double EPS = 0.000001d;

    public NPHEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    protected double adjustDownloadStartForVariant(VNF vnf,
                                                   VCPU targetVCPU,
                                                   BandwidthTimeSlot dcSlot,
                                                   BandwidthTimeSlot hostSlot,
                                                   long maxTransferBW,
                                                   long dataSize,
                                                   double baselineStart) {
        if (maxTransferBW <= 0L) {
            return baselineStart;
        }

        double roughDuration = (double) dataSize / (double) maxTransferBW;
        double probeStart = this.findEarliestWholeSlotBefore(
                dcSlot,
                hostSlot,
                maxTransferBW,
                roughDuration,
                baselineStart
        );

        if (probeStart >= 0.0d && probeStart + EPS < baselineStart) {
            return probeStart;
        }
        return baselineStart;
    }

    private double findEarliestWholeSlotBefore(BandwidthTimeSlot dcSlot,
                                               BandwidthTimeSlot hostSlot,
                                               long requiredBW,
                                               double transferTime,
                                               double upperBoundExclusive) {
        double t = 0.0d;
        for (int i = 0; i < 2048; i++) {
            if (t + transferTime > upperBoundExclusive - EPS) {
                return -1.0d;
            }

            double end = t + transferTime;
            boolean okDc = (dcSlot == null) || (dcSlot.getAvailableBW(t, end) >= requiredBW);
            boolean okHost = (hostSlot == null) || (hostSlot.getAvailableBW(t, end) >= requiredBW);
            if (okDc && okHost) {
                return t;
            }

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(t);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(t);
            double next = Math.min(nextDc, nextHost);
            if (next == Double.MAX_VALUE) {
                return -1.0d;
            }
            t = next + EPS;
        }
        return -1.0d;
    }
}
