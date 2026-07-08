package net.gripps.cloud.nfv.optimization;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/24.
 */
public class JoraNFVAlgorithm extends BaseVNFSchedulingAlgorithm {
    public JoraNFVAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public double calcImageComTime(VNF vnf, VCPU vcpu) {
        return this.calcImageComTimeDefault(vnf, vcpu);
    }
}
