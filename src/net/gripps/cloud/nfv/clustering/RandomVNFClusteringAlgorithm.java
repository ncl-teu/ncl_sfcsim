package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.nfv.sfc.VNFCluster;
import net.gripps.clustering.common.aplmodel.CustomIDSet;

import java.util.HashMap;
import java.util.Iterator;

/**
 * ランダムにVNFを選択して，VNFクラスタを生成します．
 * Created by Hidehiro Kanemitsu on 2018/12/18
 */
public class RandomVNFClusteringAlgorithm extends AbstractVNFClusteringAlgorithm {

    private int clusterNum;

    public RandomVNFClusteringAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.clusterNum = 15;


    }



    @Override
    public VNFCluster processVNFCluster(VNFCluster cluster) {
        long len = this.sfc.getVNFClusterMap().size();
        long idx = NFVUtil.genLong(0, len-1);
        int i = 0;
        VNFCluster sucCluster = null;
        //sfcの中から対象をランダムに選択する．
        Iterator<VNFCluster> cIte = this.sfc.getVNFClusterMap().values().iterator();
        while(cIte.hasNext()){
            VNFCluster c = cIte.next();
            if(i == idx){
                sucCluster = c;
                break;
            }
            i++;
        }
        //実際のVNFクラスタのクラスタリング処理．この処理が最も重要．
        cluster =  this.clustering(cluster, sucCluster);
        this.UEXClusterSet.remove(cluster.getClusterID());

        //clusterをランダムで選ばれたvcpuへ割り当てる．
        long vlen = this.env.getGlobal_vcpuMap().size();
        long loc = NFVUtil.genLong(0, vlen - 1);
        Iterator<VCPU> vIte = this.env.getGlobal_vcpuMap().values().iterator();
        long idx2 = 0;
        VCPU retvcpu = null;
        while(vIte.hasNext()){
            VCPU v = vIte.next();

            if(idx2 == loc){
                retvcpu = v;
            }else{

            }
            idx2++;
        }
        this.assignVCPU(cluster, retvcpu);


        return cluster;
    }



    /**
     *
     * @return
     */
    @Override
    public VNFCluster selectVNFCluster() {
        //Freeリストから選択する．あくまでfreeリスト（VNF単位）を参照する．
        long size = this.UEXClusterSet.getList().size();

        //long size = this.freeVNFSet.getList().size();
        //乱数により，VNFを選択する．
        long idx = 0;
        Iterator<Long> idIte = this.UEXClusterSet.iterator();
        //乱数を決定させる．
        long location = NFVUtil.genLong(0, size - 1);
        Long retID = -1L;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            if (idx == location) {
                retID = id;
            }
            idx++;
        }
        //SFCから，指定IDのVNFを取得する．
        VNFCluster cls = this.sfc.findVNFCluster(retID);
        //クラスタリングをして，vcpuへ割り当てる．


        return cls;
    }



    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        //乱数により，VNFを選択する．
        long idx = 0;
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        //乱数を決定させる．
        long location = NFVUtil.genLong(0, size - 1);
        Long retID = -1L;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            if (idx == location) {
                retID = id;
            }
            idx++;
        }
        //SFCから，指定IDのVNFを取得する．
        VNF selectedVNF = this.sfc.findVNFByLastID(retID);
        //VNFをスケジュールする．これは，親クラスであるAbstractFairSchedulingAlgorithmのscheduleVNFメソッド
        //をcallしており，fairnessに基づいて割り当てている．
        //this.scheduleVNF(selectedVNF, this.env.getGlobal_vcpuMap());

        return selectedVNF;
    }
    /**
     *
     */
    public void mainProcess() {
        //未スケジュールなVNFが残っている間，行うループ
        while (!this.UEXClusterSet.isEmpty()) {
            VNFCluster  cls = this.selectVNFCluster();
            //クラスタリングの対象となるクラスタを選び，そして
            //指定のvcpuへと割り当てる処理をよぶ．
            this.processVNFCluster(cls);

        }

        CustomIDSet startSet = sfc.getStartVNFSet();
        Iterator<Long> startIte = startSet.iterator();

        //各VNFの優先度(tlevel, blevel)を最新のものへと更新する．
        while(startIte.hasNext()){
            Long startID = startIte.next();
            //Free集合へ追加しておく．
            this.freeVNFSet.add(startID);
            VNF startVNF = this.sfc.findVNFByLastID(startID);
            this.calcActualBlevel(startVNF, new CustomIDSet());
        }
        CustomIDSet endSet = sfc.getEndVNFSet();
        Iterator<Long> endIte = endSet.iterator();
        while(endIte.hasNext()){
            Long endID = endIte.next();
            VNF endVNF = this.sfc.findVNFByLastID(endID);
            this.calcActualTlevel(endVNF, new CustomIDSet());
        }

        //次に，スケジュール処理．
        while(!this.unScheduledVNFSet.isEmpty()){
            VNF vnf = this.selectVNF();
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
            HashMap<String, VCPU> map = new HashMap<String, VCPU>();
            map.put(vnf.getvCPUID(), vcpu);
            //クラスタリングの時点ですでに各クラスタの割当先vcpuは決まっているので，
            //単一vcpuから構成されるhashMapを第二引数とする．
            this.scheduleVNF(vnf, map);
        }

        //応答時間 = makspan = END VNFの終了時刻．
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
