package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/11.
 */
public class Ideal_HEFT_VNFAlgorithm extends BaseVNFSchedulingAlgorithm {

    public Ideal_HEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;

        VCPU retCPU = null;
        //VCPUのイテレータを取得
        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            //ESTを計算する．
            double est = this.calcEST(vnf, cpu);
            //完了時刻を計算する．
            double ftime = est + this.calcExecTime(vnf.getWorkLoad(), cpu);
            //VNFの完了時刻を最小にするVCPUを探す．
            if (ftime <= ret_finishtime) {
                ret_finishtime = ftime;
                ret_starttime = est;
                retCPU = cpu;
            }
        }

        //vnfの時刻を更新する．
        vnf.setStartTime(ret_starttime);
        vnf.setFinishTime(ret_finishtime);
        vnf.setEST(ret_starttime);
        vnf.setvCPUID(retCPU.getPrefix());

        //retCPUにおいて，vnfを追加する
        // retCPU.getVnfQueue().add(vnf);
        this.addVNFQueue(retCPU, vnf);

        double ct = this.calcCT(retCPU);

        retCPU.setFinishTimeAtClusteringPhase(ct);
        //retCPUの時刻更新

        this.assignedVCPUMap.put(retCPU.getPrefix(), retCPU);
        Long DCID = NFVUtil.getIns().getDCID(retCPU.getPrefix());
        Cloud cloud = this.env.getDcMap().get(DCID);
        Long HostID = NFVUtil.getIns().getHostID(retCPU.getPrefix());
        ComputeHost host = cloud.getComputeHostMap().get(HostID);
        this.hostSet.put(DCID + NFVUtil.DELIMITER + HostID, host);

        //未スケジュール集合から削除する．
        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));

        //Freeリスト更新
        this.updateFreeList(vnf);


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
