package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/11.
 * Modified 2026/05/13: HEFT now properly tracks download queue times
 * to ensure fair comparison with DHEFT when cloud_container_dl_mode=1
 */
public class HEFT_VNFAlgorithm extends BaseVNFSchedulingAlgorithm {

    public HEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxBlevel = -1d;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            if(vnf.getBlevel() >= maxBlevel){
                maxBlevel = vnf.getBlevel();
                selectedVNF = vnf;
            }

        }
        //SFCから，指定IDのVNFを取得する．
         //selectedVNF = this.sfc.findVNFByLastID(retID);
        //VNFをスケジュールする．これは，親クラスであるAbstractFairSchedulingAlgorithmのscheduleVNFメソッド
        //をcallしており，fairnessに基づいて割り当てている．
        //this.scheduleVNF(selectedVNF, this.env.getGlobal_vcpuMap());

        return selectedVNF;
    }

    @Override
    public double calcImageComTime(VNF vnf, VCPU vcpu) {
        return this.calcImageComTimeDefault(vnf, vcpu);
    }

    /**
     * HEFT repository model:
     * all image pulls from repo are serialized by one global repo dlQueue.
     */
    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu) {
        HashMap<String, Double> map = new HashMap<String, Double>();
        map.put("start", 0.0d);
        map.put("finish", 0.0d);

        if (NFVUtil.cloud_container_dl_mode != 1) {
            return map;
        }

        NFVEnvironment nEnv = this.asNFVEnvironment();
        if (nEnv == null || nEnv.getDockerRepository() == null) {
            return super.getDLInfo(vnf, vcpu);
        }

        double duration = this.calcDownloadImageTime(vnf, vcpu);
        if (duration >= NFVUtil.MAXValue) {
            map.put("start", (double) NFVUtil.MAXValue);
            map.put("finish", (double) NFVUtil.MAXValue);
            return map;
        }
        if (duration <= 0.000001d) {
            return map;
        }

        double queueTail = this.getRepoQueueTail(nEnv.getDockerRepository());
        map.put("start", queueTail);
        map.put("finish", queueTail + duration);
        return map;
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        super.scheduleVNF(vnf, map);

        // HEFT repository queue model:
        // commit selected image pull into the single repo queue.
        if (NFVUtil.cloud_container_dl_mode == 1) {
            VCPU assignedVcpu = this.assignedVCPUMap.get(vnf.getvCPUID());
            if (assignedVcpu != null) {
                double dlTime = this.calcDownloadImageTime(vnf, assignedVcpu);

                if (dlTime > 0.000001d) {
                    NFVEnvironment nEnv = this.asNFVEnvironment();
                    ComputeHost repoHost = (nEnv == null) ? null : nEnv.getDockerRepository();
                    if (repoHost == null) {
                        return;
                    }

                    double dlStart = this.getRepoQueueTail(repoHost);
                    double dlFinish = dlStart + dlTime;

                    vnf.setDlStartTime(dlStart);
                    vnf.setDlFinishTime(dlFinish);

                    VM assignedVm = this.env.getGlobal_vmMap().get(assignedVcpu.getVMID());
                    if (assignedVm != null) {
                        assignedVm.registerImageReadyTime(vnf.getType(), dlFinish);
                    }

                    repoHost.addDLQueue(vnf);
                }
            }
        }
    }

    private NFVEnvironment asNFVEnvironment() {
        if (this.env instanceof NFVEnvironment) {
            return (NFVEnvironment) this.env;
        }
        return null;
    }

    private double getRepoQueueTail(ComputeHost repoHost) {
        if (repoHost == null || repoHost.getDlQueue() == null || repoHost.getDlQueue().isEmpty()) {
            return 0.0d;
        }
        return repoHost.getDlQueue().getLast().getDlFinishTime();
    }

    public void mainProcess() {
        //未スケジュールなVNFが残っている間，行うループ
        while (!this.getUnScheduledVNFSet().isEmpty()) {
            VNF vnf = this.selectVNF();
            if(vnf == null){
                System.out.println("test");
            }
            //vcpu全体から，vnfの割当先を選択する．
            this.scheduleVNF(vnf, this.vcpuMap);
        }
        double val = -1;
        Iterator<Long> endITe = this.getSfc().getEndVNFSet().iterator();
        while (endITe.hasNext()) {
            Long eID = endITe.next();
            VNF endVNF = this.sfc.findVNFByLastID(eID);
            if (endVNF.getFinishTime() >= val) {
                val = endVNF.getFinishTime();
            }
        }
        //応答時間を決める．
        this.makeSpan = val;
    }
}
