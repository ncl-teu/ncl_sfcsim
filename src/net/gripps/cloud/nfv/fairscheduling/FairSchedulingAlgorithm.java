package net.gripps.cloud.nfv.fairscheduling;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 */
public class FairSchedulingAlgorithm extends AbstractFairSchedulingAlgorithm {

    public FairSchedulingAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public VNF selectVNF() {
        return null;
    }
}
