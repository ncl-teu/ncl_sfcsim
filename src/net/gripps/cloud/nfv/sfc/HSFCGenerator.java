package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.HUtil;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/16.
 */
public class HSFCGenerator extends SFCGenerator {
    protected static HSFCGenerator own;

    protected HSFCGenerator() {
        super();
    }

    public static HSFCGenerator getIns() {
        if (HSFCGenerator.own == null) {
            HSFCGenerator.own = new HSFCGenerator();
        }
        return HSFCGenerator.own;
    }

    @Override
    public void constructFunction() {
        try {
            //最上位レイヤ(第一層)におけるファンクション数
            long tasknum = NFVUtil.sfc_vnf_num;            //APLを生成して，シングルトンにセットする．
            SFC apl = new SFC(-1, -1, -1, -1, -1, -1,
                    -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);
            Vector<Long> id = new Vector<Long>();
            id.add(new Long(1));
            apl.setIDVector(id);
            HSFCGenerator.getIns().setSfc(apl);
            long level = HUtil.hclustering_level_num;            //VNF数分だけ，VNFインスタンスを生成する．
            // for (int i = 0; i < tasknum; i++) {
            long predNum = 0;
            long currentNum = 0;
            long predMaxWorkload = 0;            //各レベルに対するループ
            for (int i = 0; i < level; i++) {
                //apl.getLevelVNFSet().add(new HashMap<Long, VNF>());
                HashMap<Long, VNF> levelMap = new HashMap<Long, VNF>();                //VNF数の比率を決める．
                double numRate = HUtil.genDouble2(HUtil.hclustering_vnf_num_rate_min, HUtil.hclustering_vnf_num_rate_max,
                        HUtil.dist_hclustering_vnf_num_rate, HUtil.dist_hclustering_vnf_num_rate_mu);                //当該レベルのVNF数を決める．
                if (predNum == 0) {
                    currentNum = 1;
                } else {
                    currentNum = (long) Math.ceil(predNum * numRate);
                }
                long currentMaxWorkload = 0;
                //現在のレベル内での処理
                for (int j = 0; j < currentNum; j++) {
                    //最大階層が1であるときは，そのまま実際の値を作る．
                    VNF vnf = this.buildChildVNF(i, predMaxWorkload);
                    if (vnf.getWorkLoad() >= currentMaxWorkload) {
                        currentMaxWorkload = vnf.getWorkLoad();
                    }
                    //このときにVNFにはIDが付与される．
                    SFCGenerator.getIns().getSfc().addVNF(vnf);
                    levelMap.put(vnf.getIDVector().get(1), vnf);
                }
                predMaxWorkload = currentMaxWorkload;
                predNum = currentNum;
                apl.getLevelVNFSet().add(levelMap);
            }
            //ダミーENDVNFを追加する．
            this.virtualENDVNF = new VNF(NFVUtil.VNF_TYPE_VEND, 0, -1,
                    -1, -1, null, 0);
            apl.addVNF(this.virtualENDVNF);
            //END VNF用のクラスタ(ENDCluster)を生成する．
            VNFCluster endCluster = new VNFCluster(this.virtualENDVNF.getIDVector().get(1), null, new CustomIDSet(), 0);
            this.virtualENDVNF.setClusterID(endCluster.getClusterID());
            //EndClusterにEND VNFを追加する．
            endCluster.getVnfSet().add(this.virtualENDVNF.getIDVector().get(1));
            //aplに，END Clusterを追加する．
            apl.getVNFClusterMap().put(endCluster.getClusterID(), endCluster);            //各リストに対し，仮想endVNFとの依存関係を構築する．
            Iterator<VNF> eIte = apl.getLevelVNFSet().get((int) level - 1).values().iterator();            //最下層のVNF -> 仮想 END VNFへの矢印を構築する．
            while (eIte.hasNext()) {
                VNF evnf = eIte.next();
                DataDependence dd = new DataDependence(evnf.getIDVector(), this.virtualENDVNF.getIDVector(), 0, 0, 0);
                this.virtualENDVNF.addDpred(dd);
                this.createCluster(evnf);
                evnf.addDsuc(dd);
            }
            apl.getEndVNFSet().add(this.virtualENDVNF.getIDVector().get(1));
            //END Clusterの設定
            endCluster.configureVNFCluster();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public VNF buildChildVNF(int level, long w) {
        long weight = 0;
        if (level == 0) {
            weight = NFVUtil.vnf_weight_max;
        } else {
            double w_rate = HUtil.genDouble2(HUtil.hclustering_vnf_workload_rate_min, HUtil.hclustering_vnf_workload_rate_max,
                    HUtil.dist_hclustering_vnf_workload_rate, HUtil.dist_hclustering_vnf_workload_rate_mu);
            weight = (long) Math.ceil(w * w_rate);
        }        //使用率を求める．
        int usage = NFVUtil.genInt2(NFVUtil.vnf_usage_min, NFVUtil.vnf_usage_max, NFVUtil.dist_vnf_usage, NFVUtil.dist_vnf_usage_mu);
        int type = NFVUtil.genInt(1, NFVUtil.vnf_type_max);
        VNF newVNF = new VNF(type, weight, -1, -1, -1, null, usage);
        return newVNF;
    }

    @Override
    public SFC assignDependencyProcess() {
        SFC sfc = HSFCGenerator.getIns().getSfc();
        HashMap<Long, VNF> vnfMap = sfc.getVnfMap();
        int vnfnum = sfc.getVnfMap().size();
        //深さを設定
        sfc.setDepth(sfc.getLevelVNFSet().size());        //START/ENDをそれぞれ設定する．
        sfc.getStartVNFSet().add(new Long(1));
        sfc.getEndVNFSet().add(new Long(vnfnum));
        CustomIDSet startSet = new CustomIDSet();        //Startタスクの最大数
        int start_num = 1;
        this.startVNFNum = start_num;
        //深さ
        int depth = sfc.getDepth();
        int level = sfc.getLevelVNFSet().size();
        //各レベルごとのループ．
        //レベル10こあるとすると，0~8までに対して行う．
        long i = 0;
        for (int v = 0; v < level; v++) {
            HashMap<Long, VNF> levelSet = sfc.getLevelVNFSet().get(v);
            HashMap<Long, VNF> nextLevelSet = null;
            if (v < level - 1) {
                nextLevelSet = sfc.getLevelVNFSet().get(v + 1);
            }
            int currentLen = levelSet.size();
            int nextLen = 0;
            if (v < level - 1) {
                nextLen = nextLevelSet.size();
            }
            Iterator<VNF> vIte = levelSet.values().iterator();
            VNF[] arr_next = null;
            if (v < level - 1) {
                arr_next = nextLevelSet.values().toArray(new VNF[nextLen]);
            }
            Iterator<VNF> cvIte = levelSet.values().iterator();
            //現在の階層のVNFごとのループ
            while (cvIte.hasNext()) {
                VNF vnf = cvIte.next();
                if ((vnf.getDpredList().isEmpty()) && (v > 0)) {
                    HashMap<Long, VNF> parentSet = sfc.getLevelVNFSet().get(v - 1);
                    int oyalen = parentSet.size();
                    VNF[] arr_parent = parentSet.values().toArray(new VNF[oyalen]);
                    int idx = HUtil.genInt(0, oyalen - 1);
                    VNF oya = arr_parent[idx];
                    DataDependence dd = new DataDependence(oya.getIDVector(), vnf.getIDVector(), 0, 0, 0);                    //データサイズの決定
                    long datasize = NFVUtil.genLong2(NFVUtil.vnf_datasize_min,
                            NFVUtil.vnf_datasize_max, NFVUtil.dist_vnf_datasize, NFVUtil.dist_vnf_datasize_mu);
                    dd.setMaxDataSize(datasize);
                    dd.setAveDataSize(datasize);
                    dd.setMinDataSize(datasize);
                    oya.addDsuc(dd);
                    vnf.addDpred(dd);
                }
                if (v >= level - 1) {
                    continue;
                }
//出辺数を決める．
                long ddoutnum = NFVUtil.genLong2(NFVUtil.sfc_vnf_outdegree_min, NFVUtil.sfc_vnf_outdegree_max, 1, 0.5);
                long actualNum = Math.min(ddoutnum, nextLen);
                //実際の辺の数だけ，後続VNFを作成．
                for (int j = 0; j < actualNum; j++) {
                    int targetIdx = HUtil.genInt(0, nextLen - 1);
                    VNF dsucTask = arr_next[targetIdx];
                    //依存関係の定義．
                    DataDependence dd = new DataDependence(vnf.getIDVector(), dsucTask.getIDVector(), 0, 0, 0);                    //データサイズの決定
                    long datasize = NFVUtil.genLong2(NFVUtil.vnf_datasize_min,
                            NFVUtil.vnf_datasize_max, NFVUtil.dist_vnf_datasize, NFVUtil.dist_vnf_datasize_mu);
                    dd.setMaxDataSize(datasize);
                    dd.setAveDataSize(datasize);
                    dd.setMinDataSize(datasize);
                    int maxDepth = 0;
                    //当該タスクに，後続タスクをセット
                    if (vnf.addDsuc(dd)) {
                        if (sfc.getMaxData() <= dd.getMaxDataSize()) {
                            sfc.setMaxData(dd.getMaxDataSize());
                        }
                        if (sfc.getMinData() >= dd.getMaxDataSize()) {
                            sfc.setMinData(dd.getMaxDataSize());
                        }
                        //後続タスクに，先行タスクをセット
                        dsucTask.addDpred(dd);
                        //dsucのdepthを更新
                        Iterator<DataDependence> dpredIte = dsucTask.getDpredList().iterator();
                        while (dpredIte.hasNext()) {
                            VNF preTask = sfc.findVNFByLastID(dpredIte.next().getFromID().get(1));
                            int d = preTask.getDepth();
                            if (d >= maxDepth) {
                                maxDepth = d;
                            }
                        }
                        dsucTask.setDepth(maxDepth + 1);
                        //this.dQueue.add(dsucTask);
                    }
                }
                this.createCluster(vnf);
                i++;
            }
        }
        VNF endTask = sfc.findVNFByLastID(new Long(vnfnum));
        CustomIDSet ansSet = new CustomIDSet();
        CustomIDSet vSet = new CustomIDSet();
        vSet.add(endTask.getIDVector().get(1));
        VNFCluster endCluster = new VNFCluster(new Long(endTask.getIDVector().get(1)), null, vSet, endTask.getWorkLoad());
        endCluster.setClusterSize(endTask.getWorkLoad());
        sfc.getVNFClusterMap().put(endCluster.getClusterID(), endCluster);
        ansSet.add(endTask.getIDVector().get(1));
        LinkedList<DataDependence> dpredList = endTask.getDpredList();
        Iterator<DataDependence> dpredIte = dpredList.iterator();
        while (dpredIte.hasNext()) {
            DataDependence dd = dpredIte.next();
            VNF dpredTask = sfc.findVNFByLastID(dd.getFromID().get(1));
            this.updateAncestor(ansSet, dpredTask);
        }
        //全体のPGの命令数を集計する．
        //VNF task = (BBTask)AplOperator.getInstance().calculateInstructions(AplOperator.getInstance().getApl());
        SFCGenerator.getIns().setSfc(sfc);
        return sfc;
    }

    public VNFCluster createCluster(VNF vnf) {        //クラスタを生成しておく．
        //VNFクラスタも作っておく．
        CustomIDSet vnfSet = new CustomIDSet();
        vnfSet.add(vnf.getIDVector().get(1));
        VNFCluster cluster = new VNFCluster(vnf.getIDVector().get(1), null, vnfSet, vnf.getWorkLoad());
        vnf.setClusterID(cluster.getClusterID());
        sfc.getVNFClusterMap().put(cluster.getClusterID(), cluster);
        if (vnf.getDpredList().isEmpty()) {
            sfc.getStartVNFSet().add(vnf.getIDVector().get(1));
        }
        if (vnf.getDsucList().isEmpty()) {
            sfc.getEndVNFSet().add(vnf.getIDVector().get(1));
        }
        cluster.configureVNFCluster();
        return cluster;
    }
}