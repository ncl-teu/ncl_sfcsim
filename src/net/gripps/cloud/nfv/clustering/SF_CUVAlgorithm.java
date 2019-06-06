package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.nfv.sfc.VNFCluster;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;
import net.gripps.environment.CPU;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Service Function Clustering for Utilizing vCPU and Functions(SF-CUV)
 * アルゴリズムの実装です．
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 */
public class SF_CUVAlgorithm extends AbstractVNFClusteringAlgorithm {

    private int updateMode = 0;
    private HashMap<String, VCPU> unAssignedVCPUMap;

    private double maxFinishTime;

    public SF_CUVAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.maxFinishTime = NFVUtil.MAXValue;
        this.unAssignedVCPUMap = new HashMap<String, VCPU>();
        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        while (vIte.hasNext()) {
            VCPU vcpu = vIte.next();
            this.unAssignedVCPUMap.put(vcpu.getPrefix(), vcpu);

        }

    }

    public HashMap<String, VCPU> getVCPUSetAsType(VNF vnf, VCPU vcpu) {
        int type = vnf.getType();
        HashMap<String, VCPU> retMap = new HashMap<String, VCPU>();

        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        //vCPUに対するループ
        while (vIte.hasNext()) {
            VCPU v = vIte.next();
            if (v.getVCPUID().equals(vcpu.getVCPUID())) {
                continue;
            }

            Iterator<VNF> qIte = v.getVnfQueue().iterator();
            while (qIte.hasNext()) {
                VNF q = qIte.next();
                //同じものがあった時点で抜ける．
                if (q.getType() == type) {
                    retMap.put(q.getvCPUID(), v);
                    break;
                }
            }
        }
        return retMap;

    }

    public FTInfo calcFinishTime(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;
        FTInfo info = new FTInfo();

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
        info.setFinishTime(ret_finishtime);
        info.setPrefix(retCPU.getPrefix());
        return info;
    }


    /**
     * 選択されたVNFをスケジュールするためのメソッドです．
     * 具体的には，指定範囲のvCPUに対して，どのvCPUのどの時間スロットへ
     * 割り当てるのかを決めて，そのスロットへ割り当てます．
     * <p>
     * FreeVNFについて，そのVNFと同一タイプのVNFが割り当てられているvCPU集合をみる．
     * その集合内での割り当ての完了時刻をT1とする．
     * 次に，VNFが割り当てられているvCPUと同一ホスト内の別vCPU集合を見る．
     * その集合内での割り当ての完了時刻をT2とする．
     * もしT1 < T2であれば，T1での割り当て，逆ならT2での割り当てとする．
     * つまり，ローカリティによる（ホスト数削減）と，同一タイプのVNFの使い回しによる（ファンクション数削減）
     * の双方を実現するorderingアルゴリズムの完成である．
     * VNF Clustering and Ordering for Utilizing Hosts and Functions (VNF-COUHF)
     *
     * @param vnf 割当対象のVNF
     */
    public void scheduleVNF_CMWSL(VNF vnf) {

        VCPU assignedVCPU = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
        //同じタイプのVNFが割り当てられているvcpu集合を取得する．
        HashMap<String, VCPU> sameVCPUMap = this.getVCPUSetAsType(vnf, assignedVCPU);
        //startなら変更なしとする
        if(vnf.getTlevel() == 0){
            VCPU retVCPU = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
            HashMap<String, VCPU> retMap = new HashMap<String, VCPU>();
            retMap.put(retVCPU.getPrefix(), retVCPU);
            this.scheduleVNF(vnf, retMap);
            return;

        }

        if (!sameVCPUMap.isEmpty()) {
            ///同一ホストのやつと比較する．
            VCPU sameVCPU = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
            HashMap<String, VCPU> sameHostMap = this.getSameHostVCPUMap(sameVCPU);
            //同一ホスト
            FTInfo info = this.calcFinishTime(vnf, sameHostMap);
            //タイプが同じものの集合
            FTInfo info2 = this.calcFinishTime(vnf, sameVCPUMap);
            //全て
            FTInfo info3 = this.calcFinishTime(vnf, this.vcpuMap);
            if (info.getFinishTime() >= info2.getFinishTime()) {
                //        if(info3.getFinishTime() >= info2.getFinishTime()){
                //バラけたほうが良い場合
                VCPU retVCPU = this.env.getGlobal_vcpuMap().get(info2.getPrefix());
                HashMap<String, VCPU> retMap = new HashMap<String, VCPU>();
                retMap.put(retVCPU.getPrefix(), retVCPU);
                this.scheduleVNF(vnf, retMap);
  /*              }else{
                    this.scheduleVNF(vnf, this.vcpuMap);
                }
*/
            } else {
                //   if(info3.getFinishTime()>=info.getFinishTime()){
                //ローカリティが良い場合．
                VCPU retVCPU = this.env.getGlobal_vcpuMap().get(info.getPrefix());
                HashMap<String, VCPU> retMap = new HashMap<String, VCPU>();
                retMap.put(retVCPU.getPrefix(), retVCPU);
                this.scheduleVNF(vnf, retMap);
                //           }else{
                //            this.scheduleVNF(vnf, this.vcpuMap);
                //       }

            }
        } else {
            //空なら，全体から取得する．
            this.scheduleVNF(vnf, this.vcpuMap);
        }

    }

    /**
     * @param cluster
     * @return
     */
    public double calcLevel(VNFCluster cluster) {
        long speed = 0L;
        if (cluster.getVcpuID() == null) {
            speed = this.usedSpeed;
        } else {
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(cluster.getVcpuID());
            speed = vcpu.getSpeed();
        }
        VNF btmVNF = this.sfc.findVNFByLastID(cluster.getBlevelDominantVNF());
        double level = cluster.getTlevel() /*+ this.calcExecTime(cluster.getClusterSize(), speed)*/ + cluster.getBlevel();
        //  - this.calcExecTime(btmVNF.getWorkLoad(), speed);
        return level;

    }


    /**
     * vnfのtlevelを計算します．
     *
     * @param vnf
     * @return
     */
    public double calcWSLTlevel(VNF vnf) {
        //まずは子孫たちを取得する．
        //全体 - 子孫で，先に実行される部分となる．
        VNFCluster cluster = this.sfc.findVNFCluster(vnf.getClusterID());
        CustomIDSet descendantSet = this.getDescendantsInCluster(new CustomIDSet(), vnf);
        CustomIDSet allSet = cluster.getVnfSet();
        CustomIDSet retSet = this.getSubSet(allSet, descendantSet);
        long totalSize = this.calcWorkLoad(retSet) - vnf.getWorkLoad();
        long speed = -1;
        if (cluster.getVcpuID() == null) {
            speed = this.usedSpeed;
        } else {
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(cluster.getVcpuID());
            speed = vcpu.getSpeed();
        }
        double tlevel = cluster.getTlevel() + this.calcExecTime(totalSize, speed);
        return tlevel;

    }



    public void mainProcess() {

        //未スケジュールなVNFが残っている間，行うループ
        while (!this.UEXClusterSet.isEmpty()) {
            VNFCluster cls = this.selectVNFCluster();
            //クラスタリングの対象となるクラスタを選び，そして
            //指定のvcpuへと割り当てる処理をよぶ．
            this.processVNFCluster(cls);

        }
        //残っているVNFクラスタに対してのvCPU割り当てをする．
        Iterator<Long> uIte = this.UEXClusterSet.iterator();
        while (uIte.hasNext()) {
            Long id = uIte.next();
            VNFCluster cls = this.sfc.findVNFCluster(id);
            VCPU vcpu = this.selectVCPU(cls);
            this.assignVCPU(cls, vcpu);
        }

        CustomIDSet startSet = sfc.getStartVNFSet();
        Iterator<Long> startIte = startSet.iterator();


        // Iterator<VNFCluster> cIte = this.sfc.getVNFClusterMap().values().iterator();
        Iterator<VNF> vIte = this.sfc.getVnfMap().values().iterator();
        //各VNFに対するループ
        //各VNFに対し，vCPUIDのセット（＋クラスタに対するvCPUのセット）
        while (vIte.hasNext()) {
            VNF v = vIte.next();
            VNFCluster cls = this.sfc.findVNFCluster(v.getClusterID());
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(cls.getVcpuID());
            if (cls.getVcpuID() == null) {
                VCPU newVCPU = this.selectVCPU(cls);
                cls.setVcpuID(newVCPU.getPrefix());
                v.setvCPUID(newVCPU.getPrefix());

            } else {
                if (v.getvCPUID() == null) {
                    v.setvCPUID(cls.getVcpuID());
                }
            }
            v.setvCPUID(cls.getVcpuID());
        }

            //各VNFの優先度(tlevel, blevel)を最新のものへと更新する．
            while (startIte.hasNext()) {
                Long startID = startIte.next();
                //Free集合へ追加しておく．
                this.freeVNFSet.add(startID);
                VNF startVNF = this.sfc.findVNFByLastID(startID);
                this.calcActualBlevel(startVNF, new CustomIDSet());
            }
            CustomIDSet endSet = sfc.getEndVNFSet();
            Iterator<Long> endIte = endSet.iterator();
            while (endIte.hasNext()) {
                Long endID = endIte.next();
                VNF endVNF = this.sfc.findVNFByLastID(endID);
                this.calcActualTlevel(endVNF, new CustomIDSet());
            }

/*
        //各VNFの優先度(tlevel, blevel)を最新のものへと更新する．
        while (startIte.hasNext()) {
            Long startID = startIte.next();
            //Free集合へ追加しておく．
            this.freeVNFSet.add(startID);
            VNF startVNF = this.sfc.findVNFByLastID(startID);
            this.calcActualBlevel(startVNF, new CustomIDSet());
        }
        CustomIDSet endSet = sfc.getEndVNFSet();
        Iterator<Long> endIte = endSet.iterator();
        while (endIte.hasNext()) {
            Long endID = endIte.next();
            VNF endVNF = this.sfc.findVNFByLastID(endID);
            this.calcActualTlevel(endVNF, new CustomIDSet());
        }
*/
        //次に，スケジュール処理 ．
        while (!this.unScheduledVNFSet.isEmpty()) {
            VNF vnf = this.selectVNF();
            HashMap<String, VCPU> map = new HashMap<String, VCPU>();

            VCPU vcpu = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());

            switch (NFVUtil.cmwsl_sched_area) {
                case 0:
                    map.put(vnf.getvCPUID(), vcpu);
                    break;
                case 1:
                    map = this.getSameHostVCPUMap(vcpu);

                    break;
                case 2:
                    map = this.vcpuMap;
                    break;
                default:
                    //  map = this.vcpuMap;
                    break;
            }

            if (NFVUtil.cmwsl_sched_area >= 3) {
                scheduleVNF_CMWSL(vnf);
                updateTlevel(vnf);

            } else {
                //スケジューリング
                this.scheduleVNF(vnf, map);
                updateTlevel(vnf);

            }
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
        long size = this.sfc.getVnfMap().size();
        if (val == 0.0) {
            VNF endVNF = this.sfc.findVNFByLastID(size);
            Iterator<DataDependence> dpredIte = endVNF.getDpredList().iterator();
            VCPU eVCPU = this.env.getGlobal_vcpuMap().get(endVNF.getvCPUID());

            double maxF = -1d;
            while (dpredIte.hasNext()) {
                DataDependence dpred = dpredIte.next();
                VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                VCPU predVCPU = this.env.getGlobal_vcpuMap().get(predVNF.getvCPUID());
                double tmpVal = predVNF.getFinishTime() + this.calcComTime(dpred.getMaxDataSize(), predVCPU, eVCPU);
                if (tmpVal >= maxF) {
                    maxF = tmpVal;
                }

            }
            this.makeSpan = maxF;
        }
    }

    public void updateTlevel(VNF vnf) {
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
            if (this.freeVNFSet.contains(sucVNF.getIDVector().get(1))) {
                //freeに入っているときだけ，考慮する．
                this.configureVNFTlevel(sucVNF);
            } else {
                continue;
            }
        }

    }

    public void configureVNFTlevel(VNF vnf) {
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        double maxTlevel = -1d;
        VCPU toVCPU = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());

        while (dpredIte.hasNext()) {
            DataDependence dpred = dpredIte.next();
            VNF fromVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
            VCPU fromVCPU = this.env.getGlobal_vcpuMap().get(fromVNF.getvCPUID());
            double tmpValue = fromVNF.getFinishTime() + this.calcComTime(dpred.getMaxDataSize(), fromVCPU, toVCPU);
            if (maxTlevel <= tmpValue) {
                maxTlevel = tmpValue;
                vnf.setDominantPredID(fromVNF.getIDVector().get(1));
                vnf.setTlevel(maxTlevel);
            }
        }
    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxLevel = -1d;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            if (vnf.getTlevel() + vnf.getBlevel() >= maxLevel) {
                maxLevel = vnf.getTlevel() + vnf.getBlevel();
                selectedVNF = vnf;
            }

        }

        return selectedVNF;
    }


    @Override
    public VNFCluster selectVNFCluster() {
        //Freeクラスタリストから，levelが最大のものを選択する．
        Iterator<Long> freeClusterIte = this.freeClusterSet.iterator();
        double maxLevel = -1.0d;
        VNFCluster retCluster = null;
        while (freeClusterIte.hasNext()) {
            Long id = freeClusterIte.next();
            VNFCluster cluster = this.sfc.findVNFCluster(id);
            double tmpLevel = this.calcLevel(cluster);
            if (maxLevel <= tmpLevel) {
                maxLevel = tmpLevel;
                retCluster = cluster;
            }
        }
        return retCluster;

    }

    /**
     * UEX, FREE双方から，clusterを削除する．
     * そして，vCPUの割当てを行う．
     * そして，レベルの更新をして，Freeリストの更新を行う．
     *
     * @param cluster
     */
    public void postProcess(VNFCluster cluster) {
        //すでに割り当て済みである場合に限って消す．
        if(cluster.getVcpuID() != null){
            //System.out.println("num:"+cluster.getVnfSet().getList().size()+"cID:"+cluster.getClusterID());
            //上方向もダメなら，これで終了．
            this.freeClusterSet.remove(cluster.getClusterID());
            this.UEXClusterSet.remove(cluster.getClusterID());
            configureLevel(cluster);
            this.updateFreeClusterSet(cluster);
        }else{
            VCPU targetVCPU = this.selectVCPU(cluster);
            this.assignVCPU(cluster, targetVCPU);
            configureLevel(cluster);
           // this.updateFreeClusterSet(cluster);
        }


/*
        VCPU targetVCPU = this.selectVCPU(cluster);
        if (cluster.getVcpuID() == null) {
            this.assignVCPU(cluster, targetVCPU);

        }
        */

    }


    @Override
    public VNFCluster processVNFCluster(VNFCluster cluster) {
        long speed = -1L;
        if (cluster.getVcpuID() != null) {
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(cluster.getVcpuID());
            speed = vcpu.getMips();
        } else {
            speed = this.usedSpeed;
        }

        //btmのblevelを支配するVNF(btmVNF@cls_a)について，その後続VNFが属するクラスタ(cls_b)とまとめる．
        //このとき，level(cls_a)が，
        // tlevel(cls_a) + exec_time(cls_a)+exec_time(cls_b)+blevel(cls_b) < level(cls_a)かつ最小となる
        // ものとクラスタリングする．
        // もしなければ，上を見る．topのtlevelを支配するVNF(topVNF@cls_a)について，その先行VNFが属するクラスタ(cls_c)と
        //まとめる．このとき，level(cls_a)が，
        //tlevel(cls_c) + exec_time(cls_a+cls_c) + blevel(cls_a) < level(cls_a)かつ最小となるものとクラスタリングする．
        //これでも小さくなれなければ，クラスタリングしない．
        //btmのbsucのクラスタとまとめて，levelが減ればOK
        VNF bVNF = this.sfc.findVNFByLastID(cluster.getBlevelDominantVNF());
        //bVNFの後続タスクを調べる．
        if (bVNF.getDsucList().isEmpty()) {
            //btmがEND VNFであれば，上向きにクラスタリングする．
            VNFCluster ret = this.clusteringUpProcess(cluster);
            if (ret == null) {
                //上向きにもできなければ，後始末．
                this.postProcess(cluster);
            }
        } else {
            //btmがEND VNFではない場合
            VNF dsucVNF = this.sfc.findVNFByLastID(bVNF.getDominantSucID());

            //  Iterator<DataDependence> dsucIte = bVNF.getDsucList().iterator();
            double orgLevel = this.calcLevel(cluster);
            boolean isCluserableDown = false;
            VNFCluster target = null;

            VNFCluster dsucCluster = this.sfc.findVNFCluster(dsucVNF.getClusterID());
            long speed2 = -1L;
            if (dsucCluster.getVcpuID() != null) {
                VCPU vcpu2 = this.env.getGlobal_vcpuMap().get(dsucCluster.getVcpuID());
                speed2 = vcpu2.getMips();
            } else {
                speed2 = this.usedSpeed;
            }

            VNF dsucbtmVNF = this.sfc.findVNFByLastID(dsucCluster.getBlevelDominantVNF());

            CustomIDSet descendantSet2 = this.getDescendantsInCluster(new CustomIDSet(), dsucVNF);
            CustomIDSet allSet2 = dsucCluster.getVnfSet();
            CustomIDSet retSet2 = this.getSubSet(allSet2, descendantSet2);
            long totalSize2 = this.calcWorkLoad(retSet2) - dsucVNF.getWorkLoad();

            double tmpLevel = cluster.getTlevel() + this.calcExecTime(cluster.getClusterSize(), speed) + this.calcExecTime(totalSize2, speed) + dsucbtmVNF.getBlevel();

            if (tmpLevel <= orgLevel) {
                target = dsucCluster;
                //System.out.println("OK!!");
            }


            //orgLevelより小さくなるか
            if (target != null) {
                isCluserableDown = true;
                //targetとクラスタリングする．
                cluster = this.clustering(cluster, target);

            } else {
                //下向きにはクラスタリングできなければ，上向きに行う．
                VNFCluster ret = this.clusteringUpProcess(cluster);
                if (ret == null) {
                    this.postProcess(cluster);
                }
            }
        }
        //それでもなお，単一クラスタであれば，これで終わりとする．
        if (cluster.getVnfSet().getList().size() == 1) {
            this.postProcess(cluster);
        }

        return cluster;
    }

    /**
     * clusterのレベルを最小にするものを選びます．
     *
     * @param cluster
     * @return
     */
    public VCPU selectVCPU(VNFCluster cluster) {
        VNF tVNF = this.sfc.findVNFByLastID(cluster.getTlevelDominantVNF());
        VCPU retVCPU = null;
        VNF bVNF = this.sfc.findVNFByLastID(cluster.getBlevelDominantVNF());
        CustomIDSet descendantSet = this.getDescendantsInCluster(new CustomIDSet(), bVNF);
        CustomIDSet allSet = cluster.getVnfSet();
        CustomIDSet retSet = this.getSubSet(allSet, descendantSet);
        long totalSize = this.calcWorkLoad(retSet);

        //tVNFがstartでない場合
        if (!tVNF.getDpredList().isEmpty()) {
            //もしstart出ない場合は，計算する．
            VNF ttVNF = this.sfc.findVNFByLastID(tVNF.getDominantPredID());
            VCPU ttVCPU = this.env.getGlobal_vcpuMap().get(ttVNF.getvCPUID());
            // Iterator<VCPU> uIte = this.unAssignedVCPUMap.values().iterator();
            Iterator<VCPU> uIte = this.vcpuMap.values().iterator();
            double minLevel = CloudUtil.MAXValue;
            DataDependence dd = ttVNF.findDDFromDsucList(ttVNF.getIDVector(), tVNF.getIDVector());
            //btmタスクがENDでない場合
            if (!bVNF.getDsucList().isEmpty()) {

                VNF bbVNF = this.sfc.findVNFByLastID(bVNF.getDominantSucID());
                DataDependence dsuc = bbVNF.findDDFromDpredList(bVNF.getIDVector(), bbVNF.getIDVector());
                //bbVNFにvcpuが割り当てられている場合
                if (bbVNF.getvCPUID() != null) {

                    VCPU bbVCPU = this.env.getGlobal_vcpuMap().get(bbVNF.getvCPUID());
                    while (uIte.hasNext()) {
                        VCPU u = uIte.next();
                        double tmpLevel = 0;
                        if ((ttVCPU == null) || bbVCPU == null) {
                            long u_bw = this.getBW(u);

                            tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), u_bw)
                                    + this.calcExecTime(totalSize, u)/*this.calcExecTime(cluster.getClusterSize(), u)*/ + this.calcComTime(dsuc.getMaxDataSize(), u_bw);
                        } else {
                            tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), ttVCPU, u)
                                    +  this.calcExecTime(totalSize, u)/*this.calcExecTime(cluster.getClusterSize(), u) */+ this.calcComTime(dsuc.getMaxDataSize(), u, bbVCPU);
                        }

                        if (minLevel >= tmpLevel) {
                            minLevel = tmpLevel;
                            retVCPU = u;
                        }
                    }

                } else {

                    while (uIte.hasNext()) {
                        VCPU u = uIte.next();

                        Long DCID = CloudUtil.getInstance().getDCID(u.getPrefix());
                        Long hostID = CloudUtil.getInstance().getHostID(u.getPrefix());

                        long dcBW = NFVUtil.MAXValue;
                        Cloud cloud = this.env.getDcMap().get(DCID);

                        ComputeHost host = cloud.getComputeHostMap().get(hostID);
                        long hostBW = host.getBw();
                        long bw = Math.min(hostBW, this.usedBW);
                        double tmpLevel = 0;
                        if (ttVCPU == null) {
                            tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), hostBW)
                                    + this.calcExecTime(totalSize, u)/*+ this.calcExecTime(cluster.getClusterSize(), u) */+ this.calcComTime(dsuc.getMaxDataSize(), hostBW);
                        } else {
                            tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), ttVCPU, u)
                                    + this.calcExecTime(totalSize, u)/*+ this.calcExecTime(cluster.getClusterSize(), u) */+ this.calcComTime(dsuc.getMaxDataSize(), bw);
                        }

                        if (minLevel >= tmpLevel) {
                            minLevel = tmpLevel;
                            retVCPU = u;
                        }
                    }
                }

            } else {
                minLevel = CloudUtil.MAXValue;
                //BtmタスクがENDな場合．
                while (uIte.hasNext()) {
                    VCPU u = uIte.next();
                    Long hostID = CloudUtil.getInstance().getHostID(u.getPrefix());
                    long bw = this.getBW(u);
                    double tmpLevel = 0;
                    if (ttVCPU == null) {
                        tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), bw)
                                + this.calcExecTime(cluster.getClusterSize(), u);
                    } else {

                        tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcComTime(dd.getMaxDataSize(), ttVCPU, u)
                                + this.calcExecTime(cluster.getClusterSize(), u);
                    }

                    if (minLevel >= tmpLevel) {
                        minLevel = tmpLevel;
                        retVCPU = u;
                    }
                }
                // System.out.println(422);
            }

        } else {
            //tVNFがstartな場合
//System.out.println(422);
            Iterator<VCPU> uIte = this.unAssignedVCPUMap.values().iterator();
            double minLevel = CloudUtil.MAXValue;


            //btmタスクがENDでない場合
            if (!bVNF.getDsucList().isEmpty()) {
                VNF bbVNF = this.sfc.findVNFByLastID(bVNF.getDominantSucID());
                DataDependence dsuc = bbVNF.findDDFromDpredList(bVNF.getIDVector(), bbVNF.getIDVector());
                //bbVNFにvcpuが割り当てられている場合
                if (bbVNF.getvCPUID() != null) {
                    VCPU bbVCPU = this.env.getGlobal_vcpuMap().get(bbVNF.getvCPUID());
                    while (uIte.hasNext()) {
                        VCPU u = uIte.next();
                        double tmpLevel = u.getFinishTimeAtClusteringPhase()
                                + this.calcExecTime(totalSize, u)/* + this.calcExecTime(cluster.getClusterSize(), u) */+ this.calcComTime(dsuc.getMaxDataSize(), u, bbVCPU);
                        if (minLevel >= tmpLevel) {
                            minLevel = tmpLevel;
                            retVCPU = u;
                        }
                    }

                } else {
                    while (uIte.hasNext()) {
                        VCPU u = uIte.next();
                        Long hostID = CloudUtil.getInstance().getHostID(u.getPrefix());
                        Long DCID = CloudUtil.getInstance().getDCID(u.getPrefix());
                        Cloud cloud = this.env.getDcMap().get(DCID);

                        ComputeHost host = cloud.getComputeHostMap().get(hostID);
                        long hostBW = host.getBw();
                        long bw = Math.min(hostBW, this.usedBW);
                        double tmpLevel = u.getFinishTimeAtClusteringPhase()
                                + this.calcExecTime(totalSize, u)/*+ this.calcExecTime(cluster.getClusterSize(), u) */+ this.calcComTime(dsuc.getMaxDataSize(), bw);
                        if (minLevel >= tmpLevel) {
                            minLevel = tmpLevel;
                            retVCPU = u;
                        }
                    }
                }
            } else {
//System.out.println(461);
                //BtmタスクがENDな場合．
                while (uIte.hasNext()) {
                    VCPU u = uIte.next();
                    Long hostID = CloudUtil.getInstance().getHostID(u.getPrefix());
                    long bw = this.getBW(u);
                    double tmpLevel = 0;
                    tmpLevel = u.getFinishTimeAtClusteringPhase() + this.calcExecTime(cluster.getClusterSize(), u);

                    if (minLevel >= tmpLevel) {
                        minLevel = tmpLevel;
                        retVCPU = u;
                    }
                }
            }
        }
        if (retVCPU != null) {
            this.unAssignedVCPUMap.remove(retVCPU.getPrefix());
            //System.out.println("NOT NULl");
        } else {
            System.out.println("null!!");
        }

        return retVCPU;
    }


    /**
     * @param cluster
     * @return
     */
    public VNFCluster clusteringUpProcess(VNFCluster cluster) {

        VNF tVNF = this.sfc.findVNFByLastID(cluster.getTlevelDominantVNF());
        //tVNFのtlevel支配の先行VNFを取得．
        VNF tpredVNF = this.sfc.findVNFByLastID(tVNF.getDominantPredID());
        if (tpredVNF == null) {
            return null;
        }
        VNFCluster tpredCluster = this.sfc.findVNFCluster(tpredVNF.getClusterID());
        VNF bVNF = this.sfc.findVNFByLastID(cluster.getBlevelDominantVNF());

        long clusterSize = cluster.getVnfSet().getList().size();
        long tpredSize = tpredCluster.getVnfSet().getList().size();
        if (tpredSize == 1) {

            cluster = this.clustering(cluster, tpredCluster);
        } else {
            long speed = -1L;
            if(tpredCluster.getVcpuID() != null){
                VCPU vcpu  = this.env.getGlobal_vcpuMap().get(tpredCluster.getVcpuID());
                speed = vcpu.getMips();
            }else{
                speed = this.usedSpeed;
            }

            double tmpLevel = tpredCluster.getTlevel() + this.calcExecTime(tpredCluster.getClusterSize(), speed) + cluster.getBlevel();

            // this.calcExecTime(cluster.getClusterSize(), this.usedSpeed) + cluster.getBlevel() - this.calcExecTime(bVNF.getWorkLoad(), this.usedSpeed);
            double orgLevel = this.calcLevel(cluster);
            if (tmpLevel <= orgLevel) {
                //クラスタリングする．
                cluster = this.clustering(cluster, tpredCluster);
                return cluster;
            } else {
                return null;
            }
        }
        return cluster;
    }

    /**
     * vcpuに割り当ててから呼ばれる処理．
     * つまり，クラスタリング終了時に呼ばれる処理．
     * outの後続VNFに対してtlevelを更新する．
     * 1. out自体のtlevelを更新．
     * 2. outの後続VNFを取得し，それらの各先行VNFのtlevel値
     * を見ながら，tlevelを更新する．（その後続vnfが，当該クラスタのtopである場合のみ）
     *
     * @param cluster
     * @return
     */
    @Override
    public VNFCluster configureLevel(VNFCluster cluster) {
        //btm VNFのtlevel = tlevel(cls) + execTime(workload)
        Iterator<Long> outIte = cluster.getOutSet().iterator();
        VCPU vcpu = this.env.getGlobal_vcpuMap().get(cluster.getVcpuID());
        CustomIDSet sucSet = new CustomIDSet();
        CustomIDSet sucClsSet = new CustomIDSet();

        while (outIte.hasNext()) {
            Long id = outIte.next();
            VNF bVNF = this.sfc.findVNFByLastID(id);
            double tlevel = this.calcWSLTlevel(bVNF);
            bVNF.setTlevel(tlevel);
            //     double tlevel = cluster.getTlevel() + this.calcExecTime(cluster.getClusterSize(), vcpu) - this.calcExecTime(bVNF.getWorkLoad(), vcpu);
            //bVNFの後続に対して行う．
            Iterator<DataDependence> dsucIte = bVNF.getDsucList().iterator();
            //後続タスク取得ループ
            while (dsucIte.hasNext()) {
                DataDependence dsuc = dsucIte.next();
                VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
                if (sucVNF.getClusterID().longValue() != cluster.getClusterID().longValue()) {
                    sucSet.add(sucVNF.getIDVector().get(1));
                } else {

                }
            }
            //別クラスタに属する後続VNFに対するループ
            Iterator<Long> sucIte = sucSet.iterator();

            while (sucIte.hasNext()) {
                Long sucID = sucIte.next();
                double maxTlevel = -1d;
                Long dominantTlevelID = -1L;
                //クラスタ取得
                VNF sucVNF = this.sfc.findVNFByLastID(sucID);
                VNFCluster sucCluster = this.sfc.findVNFCluster(sucVNF.getClusterID());
                sucClsSet.add(sucVNF.getClusterID());

                if (sucCluster.getTopSet().contains(sucID)) {
                    //sucVNFがtopであれば，tlevelを更新する．
                    Iterator<DataDependence> dpredIte = sucVNF.getDpredList().iterator();
                    while (dpredIte.hasNext()) {
                        DataDependence dpred = dpredIte.next();
                        VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                        long speed = 0;
                        long bw = 0;
                        double tmpTlevel = -1d;
                        if (sucCluster.getVcpuID() != null) {
                            VCPU sucVCPU = this.env.getGlobal_vcpuMap().get(sucCluster.getVcpuID());
                            speed = sucVCPU.getSpeed();
                            long sucBW = this.getBW(sucVCPU);
                            if (vcpu != null) {
                                tmpTlevel = predVNF.getTlevel() + this.calcExecTime(predVNF.getWorkLoad(), speed) + this.calcComTime(dpred.getMaxDataSize(), vcpu, sucVCPU);

                            } else {
                                long realBW = Math.min(this.usedBW, sucBW);
                                tmpTlevel = predVNF.getTlevel() + this.calcExecTime(predVNF.getWorkLoad(), speed) + this.calcComTime(dpred.getMaxDataSize(), realBW);

                            }
                        } else {
                            speed = this.usedSpeed;

                            //  long hostBW = this.getBW(vcpu);
                            long hostBW = -1;
                            if (vcpu == null) {
                                hostBW = this.usedBW;
                            } else {
                                hostBW = this.getBW(vcpu);
                            }
                            bw = Math.min(hostBW, this.usedBW);
                            tmpTlevel = predVNF.getTlevel() + this.calcExecTime(predVNF.getWorkLoad(), speed) + this.calcComTime(dpred.getMaxDataSize(), bw);
                        }
                        if (tmpTlevel >= maxTlevel) {
                            maxTlevel = tmpTlevel;
                            dominantTlevelID = predVNF.getIDVector().get(1);
                        }
                    }
                    sucVNF.setDominantPredID(dominantTlevelID);
                    sucVNF.setTlevel(maxTlevel);
                    //sucCluster自体のtlevelを更新．
                    this.configureClusterTlevel(sucCluster);
                } else{

                }
            }
        }
        //後続クラスタのclusterTlevelが変わったので，それにともなってoutVNFのtlevelも更新する．
        Iterator<Long> sucClsIte = sucClsSet.iterator();
        while(sucClsIte.hasNext()){
            VNFCluster cls = this.sfc.findVNFCluster(sucClsIte.next());
            Iterator<Long> outIte2 = cls.getOutSet().iterator();
            double maxClusterBlevel = -1d;

            long speed = -1L;
            if(cls.getVcpuID()!= null){
                VCPU vcpu2 = this.env.getGlobal_vcpuMap().get(cls.getVcpuID());
                speed = vcpu2.getMips();
            }else{
                speed = this.usedSpeed;

            }
            while(outIte2.hasNext()){
                Long outID = outIte2.next();
                VNF out = this.sfc.findVNFByLastID(outID);
                CustomIDSet descendantSet = this.getDescendantsInCluster(new CustomIDSet(), out);
                CustomIDSet allSet = cls.getVnfSet();
                CustomIDSet retSet = this.getSubSet(allSet, descendantSet);
                long totalSize = this.calcWorkLoad(retSet) - out.getWorkLoad();
                //tlevel更新
                double newTlevel = cls.getTlevel()+ this.calcExecTime(totalSize, speed);
                out.setTlevel(newTlevel);
                double tmpval = this.calcExecTime(totalSize, speed) + out.getBlevel();
                if(tmpval >= maxClusterBlevel){
                    maxClusterBlevel = tmpval;
                    cls.setBlevelDominantVNF(out.getIDVector().get(1));
                }
            }
        }
        if (vcpu != null) {
            //最後に，vcpuの完了時刻を更新する．
            double currentFT = vcpu.getFinishTimeAtClusteringPhase();
            double startTime = Math.max(currentFT, cluster.getTlevel());
            currentFT = startTime + this.calcExecTime(cluster.getClusterSize(), vcpu);
            vcpu.setFinishTimeAtClusteringPhase(currentFT);
        }

        return cluster;

    }

    public int getUpdateMode() {
        return updateMode;
    }

    public void setUpdateMode(int updateMode) {
        this.updateMode = updateMode;
    }
}
