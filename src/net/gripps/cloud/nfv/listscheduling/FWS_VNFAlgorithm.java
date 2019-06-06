package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/24.
 * Multi-Objective Scheduling of Micro-Services for Optimal Service
 * Function Chains の論文での提案アルゴリズム実装です．
 *
 * tlevel+実行時間の小さなものから大きな値を付与する．(L)
 * Freeリストから，Lの大きなものを優先的にスケジュールする．(
 * このとき，Lが同じものがあれば，w(=待ち時間*当該SFCのVNF数）の大きなものを優先する．
 * 割当先は，残余能力の最大のvCPU．
 * つまり，余裕があって早いvCPUへ優先的に割り当てるので，結果的に割り当てるvCPUの数は
 * 多くなりがち，ということになる．つまり，資源の有効活用にはならないかと思われる．
 */
public class FWS_VNFAlgorithm extends BaseVNFSchedulingAlgorithm {


    public FWS_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxL = -1d;
        long retNum = -1L;
        double retTlevel = -1d;

        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            double L = vnf.getTlevel() + this.calcExecTime(vnf.getWorkLoad(), this.usedSpeed);
            if (L > maxL) {
                maxL = L;
                selectedVNF = vnf;
                retNum = this.getVNFNum(id);
                retTlevel = vnf.getTlevel();

            }else if(L == maxL){
                //同じなら，wの比較．
                long tmpNum = this.getVNFNum(id);
                double tmpTlevel = vnf.getTlevel();
                double tmp_w = tmpNum * tmpTlevel;
                double ret_w = retNum * retTlevel;
                if(tmp_w >= ret_w){
                    maxL = L;
                    selectedVNF = vnf;
                    retNum = tmpNum;
                    retTlevel = tmpTlevel;
                }
            }

        }
        return selectedVNF;
    }

    public VCPU selectVCPU(){
        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        long maxC = -1* NFVUtil.MAXValue;
        VCPU retVCPU = null;
        while(vIte.hasNext()){
            VCPU vcpu = vIte.next();
            if(vcpu.getRemainedCapacity() >= maxC){
                maxC = vcpu.getRemainedCapacity();
                retVCPU = vcpu;
            }
        }
        return retVCPU;

    }

    public void mainProcess() {
        //未スケジュールなVNFが残っている間，行うループ
        while (!this.getUnScheduledVNFSet().isEmpty()) {
            VNF vnf = this.selectVNF();

            //vCPUを選択する．
            VCPU vcpu = this.selectVCPU();
            HashMap<String, VCPU> map = new HashMap<String, VCPU>();
            map.put(vcpu.getPrefix(), vcpu);
            //vcpu全体から，vnfの割当先を選択する．
            this.scheduleVNF(vnf, map);
            long rCapacity = vcpu.getRemainedCapacity();
            vcpu.setRemainedCapacity(rCapacity - vnf.getWorkLoad());
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

    /**
     * 指定したVNFのIDが属するSFCの，トータルVNF数を計算します．
     * @param id
     * @return
     */
    public long getVNFNum(Long id){
        LinkedList<SFC> sfcList = SFCGenerator.getIns().getSfcList();
        Iterator<SFC> sIte = sfcList.iterator();
        long retNum = -1L;

        while(sIte.hasNext()){
            SFC sfc = sIte.next();
            VNF retVNF = sfc.findVNFByLastID(id);
            if(retVNF != null){
                retNum = sfc.getVnfMap().size();
                break;
            }
        }
        return retNum;

    }


}
