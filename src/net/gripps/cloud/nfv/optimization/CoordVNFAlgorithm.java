package net.gripps.cloud.nfv.optimization;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/22.
 * Scalable and coordinated allocation of service function chainsでの
 * 論文のCoordVNFアルゴリズム実装です．
 * SFCの先行関係で先に処理されるVNFを選んで，前に割り当てられたVNFの隣接
 * ノードへ割り当ててみる．これを幅優先探索（Breadth First Search)で行い，もし
 * 処理容量に余裕があれば割り当てる．ダメなら，次の候補，というように探す．
 * それでもダメならバックトラッキングする．
 *
 * 観点が違うので，いろいろ変更した．
 * Free VNFから，選択する（選択基準はなしだが，tlevelが最小のものにした）
 *             //帯域幅のみをみて，完了時刻は見てないので，結局はvnfに必要なデータ
 *             //到着時刻が最小のものを選択する，ということになる．つまり，
 *             //calcESTをして，それが最小のものを選択することになる．
 *             完了時刻までは見てない．
 *
 */
public class CoordVNFAlgorithm extends BaseVNFSchedulingAlgorithm {

    public CoordVNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxTlevel = NFVUtil.MAXValue;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            if(vnf.getTlevel()<= maxTlevel){
                maxTlevel = vnf.getTlevel();
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
     * VNFの先行VNFが割り当てられているvcpuのうち，最も
     * @param vnf
     * @return
     */
    public VCPU selectVCPU(VNF vnf){
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();
            VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
            VCPU redVCPU = this.env.getGlobal_vcpuMap().get(predVNF.getvCPUID());
            //先行VPUのうち，
        }
        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        long maxC = -1L;
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
            //Freeリストから，tlevelが最小のものを選択する．
            VNF vnf = this.selectVNF();
            HashMap<String, VCPU> retMap = new HashMap<String, VCPU>();

            if(vnf.getDpredList().isEmpty()){
                    retMap = this.vcpuMap;
            }else{
                Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
                while(dpredIte.hasNext()){
                    DataDependence dpred = dpredIte.next();
                    VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                    if(predVNF.getvCPUID() != null){
                        VCPU predVPU = this.env.getGlobal_vcpuMap().get(predVNF.getvCPUID());
                        HashMap<String, VCPU> map  = this.getSameHostVCPUMap(predVPU);
                        retMap.putAll(map);

                    }
                }
            }
            if(retMap.isEmpty()){
                retMap = this.vcpuMap;
            }
            //vnfの先行vnfが属するvcpuから，最も近いもの（つまり，帯域幅が最大のもの）
            //を選択する．そうなると，自分自身or同一ホスト，ということになるね．
            //帯域幅のみをみて，完了時刻は見てないので，結局はvnfに必要なデータ
            //到着時刻が最小のものを選択する，ということになる．つまり，
            //calcESTをして，それが最小のものを選択することになる．
            //隣のノードに次のVNFを割り当ててみて，ダメなら2ホップ先・・・ということは，
            //隣り合うノードの帯域幅では選んでいないので，結局はランダムで割り当てている
            //ということになる．
            Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
            long len = this.vcpuMap.size();
            double minST = NFVUtil.MAXValue;
            VCPU retVCPU = null;
            long idx = NFVUtil.genLong(0,len-1);
            long i = 0;
            while(vIte.hasNext()){
                VCPU v = vIte.next();
                if(i == idx){
                    retVCPU = v;
                    break;
                }
                i++;
            }
            /*
            while(vIte.hasNext()){
                VCPU v = vIte.next();
                double st = this.calcEST(vnf, v);
                if(st <= minST){
                    minST = st;
                    retVCPU = v;
                }
            }*/
            //スケジュールする．
            HashMap<String, VCPU> map = new HashMap<String, VCPU>();
            map.put(retVCPU.getPrefix(), retVCPU);
            this.scheduleVNF(vnf, map);

            //vcpu全体から，vnfの割当先を選択する．
          //  this.scheduleVNF(vnf, this.vcpuMap);
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
