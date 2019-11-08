package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/8/08
 * PEFTアルゴリズムです．
 * - ReadyListから，rank_oct = OCTの合計＠前プロセッサ / プロセッサ数を求める．
 * - rank_octが最大のものを選択して，insertion-policyの割当EFT＋OCT(プロセッサ）with insertion
 * が最小になるところへ割り当てる．
 *
 */
public class PEFT_VNFAlgorithm extends BaseVNFSchedulingAlgorithm {

    /**
     * OCT値
     * (VNF_ID, (vCPU_ID, OCT値))
     */
    protected HashMap<Long, HashMap<String, Double>> OCT;

    /**
     *
     * @param env
     * @param sfc
     */
    public PEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.OCT = new HashMap<Long, HashMap<String, Double>>();

    }

    /**
     * OCTのセット処理です．
     * OCTの構造:
     * タスクとプロセッサで決まる値(t_i, p_k)とする．
     * 全後続タスクの最大値{
     *     全プロセッサの最小値{
     *         OCT(後続タスクt_k, p_w)+w(t_k, p_w) + 通信時間の平均
     *      }
     * }
     * つまり，各タスクは，全プロセッサのOCT値を持つ．
     */
    public void constructOCT(){
        Iterator<Long> startIte = this.sfc.getStartVNFSet().iterator();

        Iterator<Long> endIte = this.sfc.getEndVNFSet().iterator();
        while(endIte.hasNext()){
            Long endID = endIte.next();
            VNF endVNF = this.sfc.findVNFByLastID(endID);
            Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
            double minVal = NFVUtil.MAXValue;
            while(vIte.hasNext()){
                VCPU vcpu = vIte.next();
                double val = this.calcExecTime(endVNF.getWorkLoad(), vcpu);
               this.putOCTValue(endID, vcpu.getPrefix(), val);
            }
        }


        //START VNFに対するループ
        while(startIte.hasNext()){
            Long id = startIte.next();
            VNF startVNF = this.sfc.findVNFByLastID(id);

            Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
            while(vIte.hasNext()){
                VCPU vcpu = vIte.next();
                //Long sucID = dsucIte.next().getToID().get(1);
                double val = this.calcOCT(startVNF,vcpu);
                this.putOCTValue(id, vcpu.getPrefix(), val);
            }
        }
    }

    /**
     * 各プロセッサについて，
     * OCTの構造:
     * タスクとプロセッサで決まる値(t_i, p_k)とする．
     * 全後続タスクの最大値{
     *     全プロセッサの最小値{
     *         OCT(後続タスクt_k, p_w)+w(t_k, p_w) + 通信時間の平均
     *      }
     * }
     * つまり，各タスクは，全プロセッサのOCT値を持つ．
     * @return
     */
    public double calcOCT(VNF vnf, VCPU orgVCPU) {

        double maxValue = -1d;

        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        double val = this.getOCTValue(vnf.getIDVector().get(1), orgVCPU.getPrefix());

        if(val >= 0){
            return val;
        }
        //以降は，登録されてない場合の話．
        while (dsucIte.hasNext()) {
            //vCPUのイテレータ
            Iterator<VCPU> vCPUIte = this.vcpuMap.values().iterator();
            double minValue = NFVUtil.MAXValue;
            DataDependence dsuc = dsucIte.next();
            val = -1d;
            //vCPU単位のループで，各後続タスク
            while (vCPUIte.hasNext()) {
                VCPU vcpu = vCPUIte.next();

                //無ければ，再帰呼び出し
                if (vnf.getDsucList().isEmpty()) {
                    val = this.calcExecTime(vnf.getWorkLoad(), vcpu);
                    //this.putOCTValue(vnf.getIDVector().get(1), orgVCPU.getPrefix(), val);

                } else {
                    VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
                    double comTime = -1d;
                    if (orgVCPU.getPrefix().equals(vcpu.getPrefix())) {
                        comTime = 0;
                    } else {
                        comTime = this.calcComTime(dsuc.getMaxDataSize(), this.aveBW);
                    }
                    val = this.calcOCT(sucVNF, vcpu) + this.calcExecTime(sucVNF.getWorkLoad(), vcpu) + comTime;
                }
                if(val <= minValue){
                    minValue = val;
                }
            }
            if(minValue >= maxValue){
                maxValue = minValue;
            }
        }
        //System.out.println("PUT:"+vnf.getIDVector().get(1) +" / "+ orgVCPU.getPrefix()+ "/val:"+maxValue);
        this.putOCTValue(vnf.getIDVector().get(1), orgVCPU.getPrefix(), maxValue);
        return maxValue;

    }

    public boolean containsOCT(Long taskID, String vCPUID){
        if(this.OCT.containsKey(taskID)){
            HashMap<String, Double> v = this.OCT.get(taskID);
            if(v.containsKey(vCPUID)){
                return true;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }

    public void putOCTValue(Long taskID, String VCPUID, double value){
        //System.out.println("putVal:"+value);
        if(this.OCT.containsKey(taskID)){
            HashMap<String, Double> v = this.OCT.get(taskID);
            v.put(VCPUID, value);
        }else{
            HashMap<String, Double> v = new HashMap<String, Double>();
            v.put(VCPUID, value);
            this.OCT.put(taskID, v);
        }
    }

    public double getOCTValue(Long taskID, String vCPUID){
        if(this.containsOCT(taskID, vCPUID)){
            HashMap<String, Double> v = this.OCT.get(taskID);
            double value = v.get(vCPUID);
            return value;
        }else{
            return -1.0d;
        }
    }

    public double calcAveOCT(VNF vnf){
        if(vnf.getAveOCT() >= 0){
            return vnf.getAveOCT();
        }
        HashMap<String, Double> v = this.OCT.get(vnf.getIDVector().get(1));
        double maxValue = -1d;
        if(v == null){
            System.out.println("test");
        }
        long num = v.size();
        double val  = 0;
        Iterator<Double> dIte = v.values().iterator();
        while(dIte.hasNext()){
            Double dv = dIte.next();
            val += dv.doubleValue();
        }
        double ave = NFVUtil.getRoundedValue((double)val / (double) num);
        vnf.setAveOCT(ave);
        return ave;

    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxAve  = -1d;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            if(this.calcAveOCT(vnf) >= maxAve){
                maxAve = vnf.getAveOCT();
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

    /**
     * 選択されたVNFをスケジュールするためのメソッドです．
     * 具体的には，指定範囲のvCPUに対して，どのvCPUのどの時間スロットへ
     * 割り当てるのかを決めて，そのスロットへ割り当てます．
     *
     * @param vnf 割当対象のVNF
     * @param map 割当先候補となるvCPUの集合．
     */
    @Override
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
            double ftime = est + this.calcExecTime(vnf.getWorkLoad(), cpu) + this.getOCTValue(vnf.getIDVector().get(1), cpu.getPrefix());
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



    public void mainProcess(){
        this.constructOCT();
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

    public HashMap<Long, HashMap<String, Double>> getOCT() {
        return OCT;
    }

    public void setOCT(HashMap<Long, HashMap<String, Double>> OCT) {
        this.OCT = OCT;
    }
}
