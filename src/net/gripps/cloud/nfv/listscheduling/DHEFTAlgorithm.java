package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;

public class DHEFTAlgorithm extends HEFT_VNFAlgorithm{

    public DHEFTAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        //super.scheduleVNF(vnf, map);

        //
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;

        VCPU retCPU = null;
        //VCPUのイテレータを取得
        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            //ESTを計算する．
            double est = this.calcEST(vnf, cpu);
            double dTime = this.calcDownloadImageTime(vnf,cpu);
            if(dTime == -1){
                continue;
            }
           // System.out.println("time:"+dTime);
            //完了時刻を計算する．
            double ftime = est + dTime + this.calcExecTime(vnf.getWorkLoad(), cpu);
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
        //VMを取得する。
        this.env.getGlobal_vmMap().get(retCPU.getVMID()).getTypeSet().add(vnf.getType());


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

}


//