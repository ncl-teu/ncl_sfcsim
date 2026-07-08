package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

/**
 * NPPHEFT: NHEFT + multi-hole forward probing.
 *
 * Compared with NPHEFT (single contiguous hole), NPPHEFT tries multiple probe
 * starts before baseline and picks the one with the earliest finish, which allows
 * download progress to be accumulated across multiple fragmented windows.
 */
public class NPPHEFT_VNFAlgorithm extends NHEFT_VNFAlgorithm {

    private static final double EPS = 0.000001d;

    public NPPHEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
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

        DynamicResult baseline = this.simulateDynamicDownload(
                baselineStart,
                dataSize,
                maxTransferBW,
                dcSlot,
                hostSlot
        );

        double bestStart = baselineStart;
        double bestFinish = baseline.finishTime;

        // Probe multiple starts before baseline: this effectively exploits
        // fragmented windows because simulation can progress over many segments.
        double probeStart = 0.0d;
        for (int i = 0; i < 4096 && probeStart + EPS < baselineStart; i++) {
            DynamicResult probe = this.simulateDynamicDownload(
                    probeStart,
                    dataSize,
                    maxTransferBW,
                    dcSlot,
                    hostSlot
            );
            if (probe.finishTime + EPS < bestFinish) {
                bestFinish = probe.finishTime;
                bestStart = probeStart;
            }

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(probeStart);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(probeStart);
            double next = Math.min(nextDc, nextHost);
            if (next == Double.MAX_VALUE) {
                break;
            }
            probeStart = next + EPS;
        }

        return bestStart;
    }
}
