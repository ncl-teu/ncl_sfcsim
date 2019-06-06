package net.gripps.cloud.nfv.fairscheduling;

import net.gripps.cloud.CloudUtil;
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
import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 */
public abstract class AbstractFairSchedulingAlgorithm extends BaseVNFSchedulingAlgorithm {

    /**
     * 割り当てられたVNF
     */
    protected HashMap<String, FairnessInfo> fairnessMap;

    /**
     * 分母
     */
    protected double sum_of_a_s_power;

    /**
     * 分子(単なる合計値）
     */
    protected double sum_a_s;


    /**
     * 分母
     */
    protected double sum_of_a_r_power;

    /**
     * 分子
     */
    protected double sum_a_r;

    protected long maxHostNum;

    /**
     * 固定されたホスト集合から得られるvCPU集合全体
     */
    protected HashMap<String, VCPU> finalVCPUMap;



    public AbstractFairSchedulingAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.fairnessMap = new HashMap<String, FairnessInfo>();
        this.sum_of_a_s_power = 0.0d;
        this.sum_a_s = 0.0d;

        this.sum_of_a_r_power = 0.0d;
        this.sum_a_r = 0.0d;
        this.maxHostNum = 0L;
        this.finalVCPUMap = new HashMap<String, VCPU>();

    }



    /**
     * スケジュールのためにVNFを選択する処理です．
     * 継承先で実装してください．
     */
    public abstract VNF selectVNF();

    /**
     * 実際のメイン処理になります．
     * あくまで一例です．
     */
    public void mainProcess(){
        //未スケジュールなVNFが残っている間，行うループ
        while(!this.getUnScheduledVNFSet().isEmpty()){
            VNF vnf = this.selectVNF();
            //vcpu全体から，vnfの割当先を選択する．
            this.scheduleVNF(vnf, this.vcpuMap);
        }
    }

    public void deriveFinalVCPUMap(){
        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        while(vIte.hasNext()){
            VCPU vcpu = vIte.next();
            Long DCID =  NFVUtil.getIns().getDCID(vcpu.getPrefix());
            Cloud cloud = this.env.getDcMap().get(DCID);
            Long HostID = NFVUtil.getIns().getHostID(vcpu.getPrefix());
            ComputeHost host = cloud.getComputeHostMap().get(HostID);
            if(this.hostSet.containsKey(host.getPrefix())){
                this.finalVCPUMap.put(vcpu.getPrefix(), vcpu);
            }else{
                continue;
            }
            //this.hostSet.put(DCID+NFVUtil.DELIMITER+HostID, host);
        }

    }
    /**
     * 選択されたVNFをスケジュールする処理です．オーバイライドメソッドです．
     * まず，VNFの割当先として各VCPUへ割り当てた場合，VCPU同士の公平性（F_s: 重なり具合，F_r: 応答時間）
     * の計算をする．F_sのa=sum(全VCPUの期間の合計) -vnfを割り当てることによる当該 VCPUの完了時刻,
     * F_rのa: vnfを割り当てることによるVCPUの完了時刻
     * として計算する．
     * 1. 各VCPUの
     *
     * @param vnf 割当対象のVNF
     * @param map 割当先候補となるvCPUの集合．
     */
    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map){



        if(this.maxHostNum == 0L){
            map = this.vcpuMap;
            //ホスト数の最大数を超えそうならば，その時点でのvCPU集合とする．
        }else  if(this.hostSet.size() >= this.maxHostNum){
            if(this.finalVCPUMap.isEmpty()){
                //空なら新規生成
                this.deriveFinalVCPUMap();
            }else{
                //map = this.finalVCPUMap;
            }
            map = this.finalVCPUMap;

        }else{
            map = this.vcpuMap;
        }
        //VCPUのイテレータを取得する．
        Iterator<VCPU> vIte = map.values().iterator();
        //それぞれのa値を計算するための処理．
        //host間での公平性を実現するための割当先選択．
        //a_s, a_rはホスト単位で決まる値．
        //まずは，現状のa_s, a_rを，各ホストについて求める．
        //これは，グローバル領域として保持しておく．
        //例えば各vcpuに対して，そのvcpuが属するホストについてのa'_s, a'_rを求める．
        //その(a'_r-a_r), (a'_s-a_s)の分だけ，FIの値は変動する．
        //例えばa_sの値は，「処理の重なり具合の公平性」の基準なので，
        //(処理時間の合計@vcpu)の合計@ホスト - (start@最早VCPU ~ end@最後VCPU)@ホスト となる．
        //また，a_rの値は，(end時刻@最後VCPU)@ホスト となる．
        //割当先はあくまでvcpuなので，vcpuに対して，属するホストを求めて，これらのa値の差分を求める
        //ことにより，FI-1を各VCPUについて求める．この値を最小にする時のVCPUを決める．そして，その
        //ホストのa_s, a_r値を更新する．
        double dif_min = NFVUtil.MAXValue;
        VCPU vcpu_opt = null;
        FairnessInfo info_opt = null;
        HashMap<String, FairnessInfo> retInfo = new HashMap<String, FairnessInfo>();
        double minCT = NFVUtil.MAXValue;
        double retEST = 0.0d;
        double retCT = 0.0d;

        while(vIte.hasNext()){
            double RT = -1;
            VCPU vcpu = vIte.next();

            double est = this.calcEST(vnf, vcpu);
            //vnfを当該vcpuへ割あることによる，vcpuの完了時刻を調べる．
            //double ct = this.calcCT(vcpu);
            double ct = est+this.calcExecTime(vnf.getWorkLoad(), vcpu);
            double currentCT = this.calcCT(vcpu);
            //System.out.println("ct:"+ct);
            ComputeHost host = this.findHost(vcpu.getPrefix());
            //割り当て済みのホスト数を算出する．
            long N = this.fairnessMap.size();
            Iterator<FairnessInfo> infoIte = this.fairnessMap.values().iterator();

            if(this.fairnessMap.containsKey(host.getPrefix())){
                FairnessInfo info = this.fairnessMap.get(host.getPrefix());
                //まずは，重なり具合の公平性を求める（F_s）．
                //そのためにはまず，分母の値を計算する．該当するホスト分を減らして，あとで追加．
                double current_sum_of_power_a_s = this.sum_of_a_s_power  - Math.pow( info.getVal_ovarlap(),2);
                //トータルの実行時間（この場合は，VCPUへ割り当てた分だけ増加する）
                double totalExecTime = info.getTotal_execTime() + this.calcExecTime(vnf.getWorkLoad(), vcpu);

                double duration = Math.max(ct, currentCT) - Math.min(est, info.getCurrentST());
                double a_s = totalExecTime - duration;
                //追加．分母の方．
                current_sum_of_power_a_s += Math.pow(a_s, 2);
                //次に，分子の値を計算する．該当するホスト分を減らして，あとで追加．
                double current_sum_a_s = this.sum_a_s - info.getVal_ovarlap();
                current_sum_a_s += a_s;
                double current_power_of_sum_as = NFVUtil.getRoundedValue(Math.pow(current_sum_a_s,2));
                double Fairness_overlap = 0.0d;
               // Fairness_overlap = NFVUtil.getRoundedValue((double)current_power_of_sum_as/(double)(N*current_sum_of_power_a_s));

                if(current_sum_of_power_a_s == 0){
                     Fairness_overlap = 1.0;
                }else{
                    //vnfをvcpuへ割り当てた場合のF_sの値
                     Fairness_overlap = NFVUtil.getRoundedValue((double)current_power_of_sum_as/(double)(N*current_sum_of_power_a_s));
                }


//System.out.println("Fairness_overlap:"+Fairness_overlap);
                //次に，応答時間に関する公平性の値
                //分母の値．該当するホスト分をへらす．あとで追加する．
                double current_sum_of_a_r_power = this.sum_of_a_r_power - Math.pow(info.getVal_rt(),2);
                double val_rt = Math.max(ct, currentCT);
                current_sum_of_a_r_power += Math.pow(Math.max(ct, info.getCurrentCT()), 2);
                //分子を求める．
                double current_sum_a_r = this.sum_a_r - info.getVal_rt();
                //そして追加．
                current_sum_a_r += val_rt;
                double current_power_of_sum_a_r = Math.pow(current_sum_a_r, 2);
                //vnfをvcpuへ割り当てた場合のF_rの値
                double Fairness_rt = NFVUtil.getRoundedValue((double)current_power_of_sum_a_r/(double)(N*current_sum_of_a_r_power));
                //F値を計算
                double F = NFVUtil.nfv_fairness_weight_overlap* Fairness_overlap + (1-NFVUtil.nfv_fairness_weight_overlap)* Fairness_rt;

                double dif_tmp = Math.abs(1-F);
                //double dif_tmp = (double)(1-F);
               /* if(dif_tmp < 0){
                    continue;
                }*/
               if(  val_rt < minCT) {
                   //   if(dif_tmp < dif_min){
                   // dif_min = dif_tmp;
                   minCT = val_rt;
                   vcpu_opt = vcpu;
                   info_opt = new FairnessInfo(host.getPrefix());
                   info_opt.setCurrentCT(val_rt);
                   info_opt.setCurrentST(Math.min(est, info.getCurrentST()));
                   info_opt.setTotal_execTime(totalExecTime);
                   info_opt.setVal_ovarlap(a_s);
                   info_opt.setVal_rt(val_rt);
                   retEST = est;
                   retCT = ct;
                   //同じ差分のものがあった
                   //     }else if(dif_tmp == dif_min){
               }else if(val_rt == minCT){
                       // double tmpCT = Math.max(ct, currentCT);
                        //完了時刻で勝負．
            //        if(  tmpCT < minCT){
                   if(dif_tmp <= dif_min){
                       dif_min = dif_tmp;
                       minCT = val_rt;
                       //  minCT = tmpCT;
                        dif_min = dif_tmp;
                        vcpu_opt = vcpu;
                        info_opt = new FairnessInfo(host.getPrefix());
                        info_opt.setCurrentCT(val_rt);
                        info_opt.setCurrentST(Math.min(est, info.getCurrentST()));
                        info_opt.setTotal_execTime(totalExecTime);
                        info_opt.setVal_ovarlap(a_s);
                        info_opt.setVal_rt(val_rt);
                        retEST = est;
                        retCT = ct;
                    }else{
                       continue;
                   }
                }

            }else{
                //初めてのホストの場合．
                FairnessInfo info = new FairnessInfo(null);
                info.setVal_rt(0);
                info.setVal_ovarlap(0);
                info.setTotal_execTime(0);
                info.setCurrentST(NFVUtil.MAXValue);
                info.setCurrentCT(-1);

                //ホストが未割り当てであれば，NをN+1として計算する．
                double current_sum_of_power_a_s = this.sum_of_a_s_power;
                //トータルの実行時間（この場合は，VCPUへ割り当てた分だけ増加する）
                double totalExecTime =this.calcExecTime(vnf.getWorkLoad(), vcpu);
                double val_rt = Math.max(ct, currentCT);
                double duration = val_rt - est;
                double a_s = totalExecTime - duration;
                //追加．
                current_sum_of_power_a_s += Math.pow(a_s, 2);
                //次に，分子の値を計算する．該当するホスト分を減らして，あとで追加．
                double current_sum_a_s = this.sum_a_s;
                current_sum_a_s += a_s;
                double current_power_of_sum_as = Math.pow(current_sum_a_s,2);
                double Fairness_overlap = -1d;

                if(current_sum_of_power_a_s == 0){
                    Fairness_overlap = 0;
                }else{
                    //vnfをvcpuへ割り当てた場合のF_sの値
                    Fairness_overlap = NFVUtil.getRoundedValue((double)current_power_of_sum_as/(double)((N+1)*current_sum_of_power_a_s));
                }


                //次に，応答時間に関する公平性の値
                //分母の値．該当するホスト分をへらす．あとで追加する．
                double current_sum_of_a_r_power = this.sum_of_a_r_power;

                current_sum_of_a_r_power += Math.pow(ct,2);

                //分子を求める．
                double current_sum_a_r = this.sum_a_r;
                //そして追加．
                current_sum_a_r += val_rt;
                double current_power_of_sum_a_r = Math.pow(current_sum_a_r, 2);
                double Fairness_rt = -1d;
                if(current_sum_of_a_r_power == 0){
                    Fairness_rt = 0;
                }else{
                    //vnfをvcpuへ割り当てた場合のF_rの値
                    Fairness_rt = NFVUtil.getRoundedValue((double)current_power_of_sum_a_r/(double)((N+1)*current_sum_of_a_r_power));
                }


                //F値を計算
                double F = NFVUtil.nfv_fairness_weight_overlap* Fairness_overlap + (1-NFVUtil.nfv_fairness_weight_overlap)* Fairness_rt;
                //System.out.println("F:"+F);
                double dif_tmp = Math.abs(1-F);
            //    if(dif_tmp < dif_min){
                if(val_rt < minCT) {
                    // dif_min = dif_tmp;
                    minCT = val_rt;
                    vcpu_opt = vcpu;
                    info_opt = new FairnessInfo(host.getPrefix());
                    info_opt.setCurrentCT(val_rt);
                    info_opt.setCurrentST(est);
                    info_opt.setTotal_execTime(totalExecTime);
                    info_opt.setVal_ovarlap(a_s);
                    info_opt.setVal_rt(val_rt);
                    retEST = est;
                    retCT = ct;
                    //       }else if(dif_tmp == dif_min){
                }else if(val_rt == minCT){
                  //  if(ct < minCT){
                    if(dif_tmp <= dif_min){
                        minCT = val_rt;
                        //minCT = ct;
                        dif_min = dif_tmp;
                        vcpu_opt = vcpu;
                        info_opt = new FairnessInfo(host.getPrefix());
                        info_opt.setCurrentCT(val_rt);
                        info_opt.setCurrentST(est);
                        info_opt.setTotal_execTime(totalExecTime);
                        info_opt.setVal_ovarlap(a_s);
                        info_opt.setVal_rt(val_rt);
                        retEST = est;
                        retCT = ct;
                    }
                }
            }
        }
        this.fairnessMap.put(info_opt.getHostPrefix(), info_opt);
        this.sum_of_a_r_power += Math.pow(info_opt.getVal_rt(),2);
        this.sum_of_a_s_power += Math.pow(info_opt.getVal_ovarlap(),2);
        this.sum_a_r += info_opt.getVal_rt();
        this.sum_a_s += info_opt.getVal_ovarlap();

        //vnfの時刻を更新する．
     /*   vnf.setStartTime(info_opt.getCurrentST());
        vnf.setFinishTime(info_opt.getCurrentCT());
        vnf.setEST(info_opt.getCurrentST());
     */
        vnf.setStartTime(retEST);
        vnf.setFinishTime(retCT);
        vnf.setEST(retEST);

        vnf.setvCPUID(vcpu_opt.getPrefix());
        //retCPUにおいて，vnfを追加する
        //vcpu_opt.getVnfQueue().add(vnf);
        this.addVNFQueue(vcpu_opt, vnf);
//System.out.println("VID:"+vcpu_opt.getPrefix()+"NumVNF:"+vcpu_opt.getVnfQueue().size()+"CT:"+this.calcCT(vcpu_opt));
        this.assignedVCPUMap.put(vcpu_opt.getPrefix(), vcpu_opt);
        //未スケジュール集合から削除する．
        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));
        Long DCID =  NFVUtil.getIns().getDCID(vcpu_opt.getPrefix());
        Cloud cloud = this.env.getDcMap().get(DCID);
        Long HostID = NFVUtil.getIns().getHostID(vcpu_opt.getPrefix());
        ComputeHost host = cloud.getComputeHostMap().get(HostID);
        this.hostSet.put(DCID+NFVUtil.DELIMITER+HostID, host);

        //freeリスト更新
        this.updateFreeList(vnf);

    }

    /**
     * 現状，使われていないメソッドです．
     * @param info
     * @param vnf
     * @param vcpu
     * @param est
     * @param ct
     * @param num
     * @return
     */
    public double calcDiffofFairness(FairnessInfo info, VNF vnf, VCPU vcpu, double est, double ct, long num){
        double current_sum_of_power_a_s = this.sum_of_a_s_power - Math.pow( info.getVal_ovarlap(),2);
        //トータルの実行時間（この場合は，VCPUへ割り当てた分だけ増加する）
        double totalExecTime = info.getTotal_execTime() + this.calcExecTime(vnf.getWorkLoad(), vcpu);
        double duration = Math.max(ct, info.getCurrentCT()) - Math.min(est, info.getCurrentST());
        double a_s = totalExecTime - duration;
        //追加．
        current_sum_of_power_a_s += Math.pow(a_s, 2);
        //次に，分子の値を計算する．該当するホスト分を減らして，あとで追加．
        double current_sum_a_s = this.sum_a_s - info.getVal_ovarlap();
        current_sum_a_s += a_s;
        double current_power_of_sum_as = Math.pow(current_sum_a_s,2);
        //vnfをvcpuへ割り当てた場合のF_sの値
        double Fairness_overlap = NFVUtil.getRoundedValue((double)current_power_of_sum_as/(double)(num*current_sum_of_power_a_s));

        //次に，応答時間に関する公平性の値
        //分母の値．該当するホスト分をへらす．あとで追加する．
        double current_sum_of_a_r_power = this.sum_of_a_r_power - Math.pow(info.getVal_rt(),2);
        double val_rt = Math.max(ct, info.getCurrentCT());
        current_sum_of_a_r_power += Math.pow(Math.max(ct, info.getCurrentCT()), 2);
        //分子を求める．
        double current_sum_a_r = this.sum_a_r - info.getVal_rt();
        //そして追加．
        current_sum_a_r += val_rt;
        double current_power_of_sum_a_r = Math.pow(current_sum_a_r, 2);
        //vnfをvcpuへ割り当てた場合のF_rの値
        double Fairness_rt = NFVUtil.getRoundedValue((double)current_power_of_sum_a_r/(double)(num*current_sum_of_a_r_power));
        //F値を計算
        double F = NFVUtil.nfv_fairness_weight_overlap* Fairness_overlap + (1-NFVUtil.nfv_fairness_weight_overlap)* Fairness_rt;
        double dif_tmp = Math.abs(1-F);

        return dif_tmp;
    }


    public long getMaxHostNum() {
        return maxHostNum;
    }

    public void setMaxHostNum(long maxHostNum) {
        this.maxHostNum = maxHostNum;
    }

    public HashMap<String, VCPU> getFinalVCPUMap() {
        return finalVCPUMap;
    }

    public void setFinalVCPUMap(HashMap<String, VCPU> finalVCPUMap) {
        this.finalVCPUMap = finalVCPUMap;
    }
}
