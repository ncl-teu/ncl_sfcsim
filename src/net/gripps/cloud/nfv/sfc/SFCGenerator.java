package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;
import net.gripps.clustering.tool.Calc;

import java.util.*;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/03.
 */
public class SFCGenerator {

    /**
     * シングルトンインスタンス
     */
    protected static SFCGenerator own;
    /**
     * SFCオブジェクト       assignDependencyProcess
     */
    protected static SFC sfc;
    /**
     * 複数SFCを処理する場合
     */
    protected LinkedList<SFC> sfcList;
    /**
     * 仮想的な開始VNF
     */
    protected VNF virtualStartVNF;
    /**
     * 仮想的な終了VNF
     */
    protected VNF virtualENDVNF;
    /**
     *
     */
    protected TreeSet aplIDSet;

    /**
     *
     */
    protected int startVNFNum;



    protected  SFCGenerator() {
        this.aplIDSet = new TreeSet();
        this.sfcList = new LinkedList<SFC>();

    }

    public static SFCGenerator getIns() {
        if (SFCGenerator.own == null) {
            SFCGenerator.own = new SFCGenerator();
        }

        return SFCGenerator.own;
    }


    /**
     *
     */
    public void constructFunction() {

        try {
            //最上位レイヤ(第一層)におけるファンクション数
            long tasknum = NFVUtil.sfc_vnf_num;

            //APLを生成して，シングルトンにセットする．
            SFC apl = new SFC(-1, -1, -1, -1, -1, -1,
                    -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);
            Vector<Long> id = new Vector<Long>();
            id.add(new Long(1));
            apl.setIDVector(id);
            SFCGenerator.getIns().setSfc(apl);
            //VNF数分だけ，VNFインスタンスを生成する．
            for (int i = 0; i < tasknum; i++) {

                //最大階層が1であるときは，そのまま実際の値を作る．
                VNF vnf = this.buildChildVNF();
                //このときにVNFにはIDが付与される．
                SFCGenerator.getIns().getSfc().addVNF(vnf);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SFC  autoconstructFunction() {

        try {
            //最上位レイヤ(第一層)におけるファンクション数
            long tasknum = NFVUtil.sfc_vnf_num;

            //APLを生成して，シングルトンにセットする．
            SFC apl = new SFC(-1, -1, -1, -1, -1, -1,
                    -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);
            Vector<Long> id = new Vector<Long>();
            id.add(new Long(1));
            apl.setIDVector(id);

            //VNF数分だけ，VNFインスタンスを生成する．
            for (int i = 0; i < tasknum; i++) {

                //最大階層が1であるときは，そのまま実際の値を作る．
                VNF vnf = this.buildChildVNF();
                //このときにVNFにはIDが付与される．
                apl.addVNF(vnf);
                //SFCGenerator.getIns().getSfc().addVNF(vnf);

            }
            return apl;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }




    /**
     * @param num
     */
    public void generateSFCbyMultiCase(long num, SFC orgSFC) {
        try {
            //最上位レイヤ(第一層)におけるファンクション数
            long tasknum = num;

            //APLを生成して，シングルトンにセットする．
            SFC apl = new SFC(-1, -1, -1, -1, -1, -1,
                    -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);
            Vector<Long> id = new Vector<Long>();
            id.add(new Long(1));
            apl.setIDVector(id);
            //SFCGenerator.getIns().setSfc(apl);
            //VNF数分だけ，VNFインスタンスを生成する．
            for (int i = 0; i < tasknum; i++) {

                //最大階層が1であるときは，そのまま実際の値を作る．
                VNF vnf = this.buildChildVNF();
                //このときにVNFにはIDが付与される．
                orgSFC.addVNF(vnf);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public SFC singleSFCProcess() {

        this.constructFunction();
        this.assignDependencyProcess();
        //完成したSFCを取得
        SFC sfc = this.getSfc();
        this.sfcList.add(sfc);
        Iterator<VNFCluster> cIte = sfc.getVNFClusterMap().values().iterator();
        while(cIte.hasNext()){
            VNFCluster c = cIte.next();
            c.configureVNFCluster();
        }
        this.setSfc(sfc);

        return this.sfc;
    }


    public SFC autoSFCProcess() {
        //this.setSfc(null);
        SFC sfc = this.autoconstructFunction();
        sfc = this.autoassignDependencyProcess(sfc);
        //完成したSFCを取得

        //this.sfcList.add(sfc);
        Iterator<VNFCluster> cIte = sfc.getVNFClusterMap().values().iterator();
        while(cIte.hasNext()){
            VNFCluster c = cIte.next();
            c.autoconfigureVNFCluster(sfc);
        }
        //this.setSfc(sfc);

        return sfc;
    }





    /**
     * 複数のSFCを生成して，リストへ確認します．
     * そして，一つのSFCとして生成します．
     *
     * @return
     */
    public SFC multipleSFCProcess() {
        try {
            //仮想start/endを用意する．
            this.virtualStartVNF = new VNF(NFVUtil.VNF_TYPE_VSTART, 0, -1, -1, -1, null, 0);
            //通常，VNFは1から始まるが，0をセットする．
            Vector<Long> startIDVec = new Vector<Long>();
            startIDVec.add(new Long(1));
            startIDVec.add(new Long(0));

            //APLを生成して，シングルトンにセットする．
            SFC orgSFC = new SFC(-1, -1, -1, -1, -1, -1,
                    -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);

            this.setSfc(orgSFC);
            Vector<Long> id = new Vector<Long>();
            id.add(new Long(1));
            orgSFC.setIDVector(id);


            //orgSFCに，仮想startVNFを追加する．
            orgSFC.addVNF(this.virtualStartVNF);
            VNFCluster startCluster = new VNFCluster(this.virtualStartVNF.getIDVector().get(1),  null, new CustomIDSet(),0);
            //startCluster.configureVNFCluster();
            this.virtualStartVNF.setClusterID(startCluster.getClusterID());
         //   startCluster.addVNF(this.virtualStartVNF);
            startCluster.getVnfSet().add(this.virtualStartVNF.getIDVector().get(1));
            orgSFC.getVNFClusterMap().put(startCluster.getClusterID(), startCluster);

            //startCluster.setClusterID(startCluster.getClusterID());

            // this.virtualStartVNF.se
            //SFCたちを作る．
            for (int i = 0; i < NFVUtil.multiple_sfc_num; i++) {
                //VNF数を決定．
                long tasknum = NFVUtil.genLong2(NFVUtil.multiple_sfc_vnf_num_min, NFVUtil.multiple_sfc_vnf_num_max,
                        NFVUtil.dist_multiple_sfc_vnf_num, NFVUtil.dist_multiple_sfc_vnf_num_mu);
                //APLを生成して，シングルトンにセットする．
                SFC childSFC = new SFC(-1, -1, -1, -1, -1, -1,
                        -1, null, new HashMap<Long, VNF>(), new HashMap<Long, VNFCluster>(), new Long(1), -1, -1);
                Vector<Long> childID = new Vector<Long>();
                childID.add(new Long(1));
                childSFC.setIDVector(id);
                long startIDX = -1;
                this.aplIDSet.clear();
                for(int j=0;j<tasknum;j++){
                    if(j==0){
                        startIDX = orgSFC.getVnfMap().size()+1;
                    }
                    //最上位レイヤ(第一層)におけるファンクション数
                    //最大階層が1であるときは，そのまま実際の値を作る．
                    VNF vnf = this.buildChildVNF();
                    //VNFに，image sizeをセットする．
                    //乱数を決める．
                    long imageSize = NFVUtil.genLong(NFVUtil.vnf_image_size_min, NFVUtil.vnf_image_size_max);
                    //乱数で決めたimageSizeを，vnfにセットする．
                    vnf.setImageSize(imageSize);

                    //現在の要素数(1から開始）
                    long assignedID = startIDX+j;

                    //このときにVNFにはIDが付与される．
                    childSFC.addVNF(vnf, assignedID);
                }
                //あとは依存関係．
                childSFC = this.assignDependencyProcessforMulti(childSFC, startIDX);
                this.sfcList.add(childSFC);
                orgSFC.getVnfMap().putAll(childSFC.getVnfMap());
                orgSFC.getVNFClusterMap().putAll(childSFC.getVNFClusterMap());



            }
            //各リストに対し，仮想startVNFとの依存関係を構築する．
            Iterator<SFC> sfcIte = this.sfcList.iterator();
            while(sfcIte.hasNext()){
                SFC sfc = sfcIte.next();
                CustomIDSet startSet = sfc.getStartVNFSet();
                Iterator<Long> sIte = startSet.iterator();
                while(sIte.hasNext()){
                    Long sID = sIte.next();
                    VNF svnf = sfc.findVNFByLastID(sID);
                    DataDependence dd = new DataDependence(this.virtualStartVNF.getIDVector(), svnf.getIDVector(), 0,0,0);
                    this.virtualStartVNF.addDsuc(dd);
                    svnf.addDpred(dd);
                }

            }
            // this.virtualStartVNF.setIDVector();
            this.virtualENDVNF = new VNF(NFVUtil.VNF_TYPE_VEND, 0, -1,
                    -1, -1, null, 0);

            orgSFC.addVNF(this.virtualENDVNF);


            VNFCluster endCluster = new VNFCluster(this.virtualENDVNF.getIDVector().get(1),  null, new CustomIDSet(),0);
            //endCluster.configureVNFCluster();
            this.virtualENDVNF.setClusterID(endCluster.getClusterID());
           // endCluster.addVNF(this.virtualENDVNF);
            endCluster.getVnfSet().add(this.virtualENDVNF.getIDVector().get(1));

            orgSFC.getVNFClusterMap().put(endCluster.getClusterID(), endCluster);

            //各リストに対し，仮想endVNFとの依存関係を構築する．
            Iterator<SFC> sfc_endIte = this.sfcList.iterator();
            while(sfc_endIte.hasNext()){
                SFC sfc = sfc_endIte.next();
                CustomIDSet endSet = sfc.getEndVNFSet();
                Iterator<Long> eIte = endSet.iterator();
                while(eIte.hasNext()){
                    Long eID = eIte.next();
                    VNF evnf = sfc.findVNFByLastID(eID);
                    if(evnf==null){
                        System.out.println("test2");
                    }
                    DataDependence dd = new DataDependence( evnf.getIDVector(), this.virtualENDVNF.getIDVector(),0,0,0);
                    this.virtualENDVNF.addDpred(dd);
                    evnf.addDsuc(dd);
                }

            }

            orgSFC.getStartVNFSet().add(this.virtualStartVNF.getIDVector().get(1));
            orgSFC.getEndVNFSet().add(this.virtualENDVNF.getIDVector().get(1));
            Iterator<VNFCluster> cIte = orgSFC.getVNFClusterMap().values().iterator();
            while(cIte.hasNext()){
                VNFCluster c = cIte.next();
                c.configureVNFCluster();
            }
            this.setSfc(orgSFC);
            //top/in/out/btmを設定する．


        } catch (Exception e) {
            e.printStackTrace();
        }


        return this.getSfc();
    }


    /**
     * VNF作成のためのメソッドです，
     * カスタマイズのために付加的な情報をVNFへ追加する場合は，ここで追記してください．
     *
     * @return
     */
    public VNF buildChildVNF() {
        long weight = 0;
        weight = NFVUtil.genLong2(NFVUtil.vnf_weight_min, NFVUtil.vnf_weight_max, NFVUtil.dist_vnf_weight, NFVUtil.dist_vnf_weight_mu);
        //使用率を求める．
        int usage = NFVUtil.genInt2(NFVUtil.vnf_usage_min, NFVUtil.vnf_usage_max, NFVUtil.dist_vnf_usage, NFVUtil.dist_vnf_usage_mu);
        int type = NFVUtil.genInt(1, NFVUtil.vnf_type_max);

        VNF newVNF = new VNF(type, weight, -1, -1, -1, null, usage);


        return newVNF;
    }

    public TreeSet<Long> createIDSet(HashMap<Long, VNF> vnfMap) {
        Collection<VNF> taskCollection = vnfMap.values();
        Iterator<VNF> ite = taskCollection.iterator();
        TreeSet<Long> retset = new TreeSet<Long>();
        //long startmillis = System.currentTimeMillis();

        while (ite.hasNext()) {

            VNF task = ite.next();
            Vector<Long> idlist = task.getIDVector();

            Long lastid = idlist.lastElement();


            retset.add(lastid);

        }
        //必ず複製する！
        return (TreeSet<Long>) retset.clone();
    }

    /**
     * 対象となるDsucを，自分よりもIDの後のやつのみに絞り込む．
     *
     * @param pTask
     * @param cTask
     * @param isdd
     * @return
     */
    public TreeSet<Long> getCandidateTaskFromOldOnly(SFC pTask, VNF cTask, boolean isdd) {
        TreeSet allSet = new TreeSet<Long>();
        if (pTask.getIDVector().size() == 1) {
            if (this.aplIDSet.isEmpty()) {
                this.aplIDSet = this.createIDSet(SFCGenerator.getIns().getSfc().getVnfMap());
            } else {

            }
            allSet = (TreeSet<Long>) this.aplIDSet.clone();

        } else {
            allSet = this.createIDSet(pTask.getVnfMap());
        }

        //allSetから，start候補を外す．

        for (int i = 1; i <= this.startVNFNum; i++) {
            allSet.remove(new Long(i));
        }
        Long cID = cTask.getIDVector().lastElement();
        TreeSet<Long> retSet = (TreeSet<Long>) allSet.tailSet(cID);
        retSet.remove(cID);
        return retSet;
    }

    public TreeSet<Long> autogetCandidateTaskFromOldOnly(SFC pTask, VNF cTask, boolean isdd) {
        TreeSet allSet = new TreeSet<Long>();
        TreeSet<Long> tmpSet = new TreeSet<Long>();

        if (pTask.getIDVector().size() == 1) {
            if (tmpSet.isEmpty()) {
                tmpSet = this.createIDSet(pTask.getVnfMap());
            } else {

            }
            allSet = (TreeSet<Long>) tmpSet.clone();

        } else {
            allSet = this.createIDSet(pTask.getVnfMap());
        }

        //allSetから，start候補を外す．

        for (int i = 1; i <= this.startVNFNum; i++) {
            allSet.remove(new Long(i));
        }
        Long cID = cTask.getIDVector().lastElement();
        TreeSet<Long> retSet = (TreeSet<Long>) allSet.tailSet(cID);
        retSet.remove(cID);
        return retSet;
    }


    public TreeSet<Long> getCandidateTaskFromOldOnlyMulti(SFC pTask, VNF cTask, boolean isdd) {
        TreeSet allSet = new TreeSet<Long>();
        if(pTask.getIDVector().size() == 1){
            if(this.aplIDSet.isEmpty()){
                this.aplIDSet = this.createIDSet(pTask.getVnfMap());
            }else{

            }
            allSet = (TreeSet<Long>)this.aplIDSet.clone();

        }else{
            allSet = this.createIDSet(pTask.getVnfMap());
        }

        //allSetから，start候補を外す．
        //  int startLen = (int)Math.ceil((this.startNumRate*AplOperator.getInstance().getApl().getTaskClusterList().size()));

        Iterator<Long> startIte = pTask.getStartVNFSet().iterator();
        while(startIte.hasNext()){
            Long sID = startIte.next();
            allSet.remove(sID);
        }

        Long cID = cTask.getIDVector().lastElement();
        TreeSet<Long> retSet = (TreeSet<Long>)allSet.tailSet(cID);

        retSet.remove(cID);
        return retSet;
    }

    /**
     * ファンクション間の依存関係を決めるためのメソッドです．
     *
     * @return
     */
    public SFC autoassignDependencyProcess(SFC sfc) {


        HashMap<Long, VNF> vnfMap = sfc.getVnfMap();
        int vnfnum = sfc.getVnfMap().size();
        //深さを設定
        sfc.setDepth((int) (Math.sqrt(vnfnum) / NFVUtil.depth_alpha));
        //START/ENDを決める．
        VNF startVNF = vnfMap.get(new Long(1));
        VNF endVNF = vnfMap.get(new Long(vnfnum));

        //START/ENDをそれぞれ設定する．
        sfc.getStartVNFSet().add(new Long(1));
        sfc.getEndVNFSet().add(new Long(vnfnum));

        CustomIDSet startSet = new CustomIDSet();

        //Startタスクの最大数
        int start_num = Double.valueOf(vnfnum * NFVUtil.startNumRate).intValue();
        this.startVNFNum = start_num;
        //深さ
        int depth = sfc.getDepth();

        //STARTファンクション設定処理
        Iterator<VNF> vnfIte = vnfMap.values().iterator();
        CustomIDSet tmpStartSet = new CustomIDSet();
        while (vnfIte.hasNext()) {
            VNF t = vnfIte.next();
            if (t.getIDVector().get(1) <= start_num) {
                continue;
            } else {
                tmpStartSet.add(t.getIDVector().get(1));
            }
        }

        //トップレベルにおける依存関係の決定のためのループ
        for (int i = 0; i < vnfnum; i++) {
            //最新のタスクを取得する
            VNF vnf = vnfMap.get(new Long(i + 1));
            //もしStartタスクであれば，StartSetへ追加しておく．
            if (vnf.getDpredList().isEmpty()) {
                startSet.add(vnf.getIDVector().get(1));
            }

            //当該タスクがENDタスクであれば，何もしない
            if (vnf.getIDVector().get(1).equals(new Long(vnfnum))) {
                continue;
            }

            //START VNF以外＋自分より若いIDのVNF以外を，後続VNF候補とする．
            TreeSet<Long> remainSet =
                    //this.getCandidateTaskIDSetFromApl(task, true);
                    this.autogetCandidateTaskFromOldOnly(sfc, vnf, true);

            //データ依存辺の出力辺数を決める．正規分布とする．
            long ddoutnum = NFVUtil.genLong2(NFVUtil.sfc_vnf_outdegree_min, NFVUtil.sfc_vnf_outdegree_max, 1, 0.5);
            int dsucidx;
            int dsize = remainSet.size();
            Long dsucID = new Long(0);
            Long[] dcandidates = remainSet.toArray(new Long[0]);
            // Long[] dcandidates = this.dQueue.toArray(new Long[0]);
            int inverval = (vnfnum / depth);

            SortedSet<Long> tmpSet = null;
            for (int j = 0; j < ddoutnum; j++) {
                if (dsize > 0) {
                    if (j == 0) {
                        long tmpnextID = vnf.getIDVector().get(1) + inverval;
                        dsucID = Math.min(vnfnum, tmpnextID);
                    } else {
                        if (tmpStartSet.isEmpty()) {

                            //全タスクが規定の深さまで行っていれば，通常通りランダム操作にうつる．
                            dsucidx = NFVUtil.genInt(0, dsize - 1);
                            dsucID = dcandidates[dsucidx];

                        } else {
                            tmpSet = tmpStartSet.getObjSet().tailSet(vnf.getIDVector().get(1));
                            tmpSet.remove(vnf.getIDVector().get(1));
                            if (tmpSet.isEmpty()) {
                                dsucidx = NFVUtil.genInt(0, dsize - 1);
                                dsucID = dcandidates[dsucidx];
                            } else {
                                dsucID = tmpSet.first();
                                tmpStartSet.remove(dsucID);
                            }

                        }
                    }

                } else {
                    break;
                }

                VNF dsucTask = sfc.findVNFByLastID(dsucID);
                DataDependence dd = new DataDependence(vnf.getIDVector(), dsucTask.getIDVector(), 0, 0, 0);
                // long datasize = 0;
                long tmp = 0;
               /* if(j == 0){
                    datasize = this.generateLongValueForSize2(this.ddedge_sizemin, this.ddedge_sizemax);
                    tmp = datasize;
                }else{
                    datasize =tmp;
                }*/
                //データサイズの決定
                long datasize = NFVUtil.genLong2(NFVUtil.vnf_datasize_min,
                        NFVUtil.vnf_datasize_max, NFVUtil.dist_vnf_datasize, NFVUtil.dist_vnf_datasize_mu);

                dd.setMaxDataSize(datasize);
                dd.setAveDataSize(datasize);
                dd.setMinDataSize(datasize);
                remainSet.remove(dsucID);
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
            //クラスタを生成しておく．
            //VNFクラスタも作っておく．
            CustomIDSet vnfSet = new CustomIDSet();
            vnfSet.add(vnf.getIDVector().get(1));

            VNFCluster cluster = new VNFCluster(new Long(i + 1), null, vnfSet, vnf.getWorkLoad());
            vnf.setClusterID(cluster.getClusterID());
            //cluster.configureVNFCluster();



            sfc.getVNFClusterMap().put(cluster.getClusterID(), cluster);
            if (vnf.getDpredList().isEmpty()) {
                sfc.getStartVNFSet().add(vnf.getIDVector().get(1));

            }

            if (vnf.getDsucList().isEmpty()) {
                sfc.getEndVNFSet().add(vnf.getIDVector().get(1));
            }


        }
        VNF endTask = sfc.findVNFByLastID(new Long(vnfnum));
        endTask.setClusterID(endTask.getIDVector().get(1));
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
            this.autoupdateAncestor(sfc, ansSet, dpredTask);
        }
        //全体のPGの命令数を集計する．
        //VNF task = (BBTask)AplOperator.getInstance().calculateInstructions(AplOperator.getInstance().getApl());


        return sfc;

    }


    /**
     * ファンクション間の依存関係を決めるためのメソッドです．
     *
     * @return
     */
    public SFC assignDependencyProcess() {

        SFC sfc = SFCGenerator.getIns().getSfc();
        HashMap<Long, VNF> vnfMap = sfc.getVnfMap();
        int vnfnum = sfc.getVnfMap().size();
        //深さを設定
        sfc.setDepth((int) (Math.sqrt(vnfnum) / NFVUtil.depth_alpha));
        //START/ENDを決める．
        VNF startVNF = vnfMap.get(new Long(1));
        VNF endVNF = vnfMap.get(new Long(vnfnum));

        //START/ENDをそれぞれ設定する．
        sfc.getStartVNFSet().add(new Long(1));
        sfc.getEndVNFSet().add(new Long(vnfnum));

        CustomIDSet startSet = new CustomIDSet();

        //Startタスクの最大数
        int start_num = Double.valueOf(vnfnum * NFVUtil.startNumRate).intValue();
        this.startVNFNum = start_num;
        //深さ
        int depth = sfc.getDepth();

        //STARTファンクション設定処理
        Iterator<VNF> vnfIte = vnfMap.values().iterator();
        CustomIDSet tmpStartSet = new CustomIDSet();
        while (vnfIte.hasNext()) {
            VNF t = vnfIte.next();
            if (t.getIDVector().get(1) <= start_num) {
                continue;
            } else {
                tmpStartSet.add(t.getIDVector().get(1));
            }
        }

        //トップレベルにおける依存関係の決定のためのループ
        for (int i = 0; i < vnfnum; i++) {
            //最新のタスクを取得する
            VNF vnf = vnfMap.get(new Long(i + 1));
            //もしStartタスクであれば，StartSetへ追加しておく．
            if (vnf.getDpredList().isEmpty()) {
                startSet.add(vnf.getIDVector().get(1));
            }

            //当該タスクがENDタスクであれば，何もしない
            if (vnf.getIDVector().get(1).equals(new Long(vnfnum))) {
                continue;
            }

            //START VNF以外＋自分より若いIDのVNF以外を，後続VNF候補とする．
            TreeSet<Long> remainSet =
                    //this.getCandidateTaskIDSetFromApl(task, true);
                    this.getCandidateTaskFromOldOnly(sfc, vnf, true);

            //データ依存辺の出力辺数を決める．正規分布とする．
            long ddoutnum = NFVUtil.genLong2(NFVUtil.sfc_vnf_outdegree_min, NFVUtil.sfc_vnf_outdegree_max, 1, 0.5);
            int dsucidx;
            int dsize = remainSet.size();
            Long dsucID = new Long(0);
            Long[] dcandidates = remainSet.toArray(new Long[0]);
            // Long[] dcandidates = this.dQueue.toArray(new Long[0]);
            int inverval = (vnfnum / depth);

            SortedSet<Long> tmpSet = null;
            for (int j = 0; j < ddoutnum; j++) {
                if (dsize > 0) {
                    if (j == 0) {
                        long tmpnextID = vnf.getIDVector().get(1) + inverval;
                        dsucID = Math.min(vnfnum, tmpnextID);
                    } else {
                        if (tmpStartSet.isEmpty()) {

                            //全タスクが規定の深さまで行っていれば，通常通りランダム操作にうつる．
                            dsucidx = NFVUtil.genInt(0, dsize - 1);
                            dsucID = dcandidates[dsucidx];

                        } else {
                            tmpSet = tmpStartSet.getObjSet().tailSet(vnf.getIDVector().get(1));
                            tmpSet.remove(vnf.getIDVector().get(1));
                            if (tmpSet.isEmpty()) {
                                dsucidx = NFVUtil.genInt(0, dsize - 1);
                                dsucID = dcandidates[dsucidx];
                            } else {
                                dsucID = tmpSet.first();
                                tmpStartSet.remove(dsucID);
                            }

                        }
                    }

                } else {
                    break;
                }

                VNF dsucTask = sfc.findVNFByLastID(dsucID);
                DataDependence dd = new DataDependence(vnf.getIDVector(), dsucTask.getIDVector(), 0, 0, 0);
                // long datasize = 0;
                long tmp = 0;
               /* if(j == 0){
                    datasize = this.generateLongValueForSize2(this.ddedge_sizemin, this.ddedge_sizemax);
                    tmp = datasize;
                }else{
                    datasize =tmp;
                }*/
                //データサイズの決定
                long datasize = NFVUtil.genLong2(NFVUtil.vnf_datasize_min,
                        NFVUtil.vnf_datasize_max, NFVUtil.dist_vnf_datasize, NFVUtil.dist_vnf_datasize_mu);

                dd.setMaxDataSize(datasize);
                dd.setAveDataSize(datasize);
                dd.setMinDataSize(datasize);
                remainSet.remove(dsucID);
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
            //クラスタを生成しておく．
            //VNFクラスタも作っておく．
            CustomIDSet vnfSet = new CustomIDSet();
            vnfSet.add(vnf.getIDVector().get(1));

            VNFCluster cluster = new VNFCluster(new Long(i + 1), null, vnfSet, vnf.getWorkLoad());
            vnf.setClusterID(cluster.getClusterID());
            //cluster.configureVNFCluster();



            sfc.getVNFClusterMap().put(cluster.getClusterID(), cluster);
            if (vnf.getDpredList().isEmpty()) {
                sfc.getStartVNFSet().add(vnf.getIDVector().get(1));

            }

            if (vnf.getDsucList().isEmpty()) {
                sfc.getEndVNFSet().add(vnf.getIDVector().get(1));
            }


        }
        VNF endTask = sfc.findVNFByLastID(new Long(vnfnum));
        endTask.setClusterID(endTask.getIDVector().get(1));
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

    public SFC assignDependencyProcessforMulti(SFC childSFC, long startIDX) {

        SFC sfc = childSFC;
        HashMap<Long, VNF> vnfMap = sfc.getVnfMap();
        int vnfnum = sfc.getVnfMap().size();
        //深さを設定
        sfc.setDepth((int) (Math.sqrt(vnfnum) / NFVUtil.depth_alpha));

        //START/ENDをそれぞれ設定する．
        //sfc.getStartVNFSet().add(new Long(1));
        sfc.getEndVNFSet().add(new Long(vnfnum+startIDX-1));

        CustomIDSet startSet = new CustomIDSet();

        //Startタスクの最大数
        int start_num = Double.valueOf(vnfnum * NFVUtil.startNumRate).intValue();
        this.startVNFNum = start_num;
        //深さ
        int depth = sfc.getDepth();

        //STARTファンクション設定処理
        Iterator<VNF> vnfIte = vnfMap.values().iterator();
        CustomIDSet tmpStartSet = new CustomIDSet();
        while (vnfIte.hasNext()) {
            VNF t = vnfIte.next();
            if (t.getIDVector().get(1) <= startIDX+start_num) {
                continue;
            } else {
                tmpStartSet.add(t.getIDVector().get(1));
            }
        }

        long vnfNum = sfc.getVnfMap().size();
        //トップレベルにおける依存関係の決定のためのループ
        for (int i = 0; i < vnfnum; i++) {
            //最新のタスクを取得する
            VNF vnf = vnfMap.get(new Long(startIDX+i));
            //もしStartタスクであれば，StartSetへ追加しておく．
            if (vnf.getDpredList().isEmpty()) {
                startSet.add(vnf.getIDVector().get(1));
            }

            //当該タスクがENDタスクであれば，何もしない
            if (vnf.getIDVector().get(1).longValue() == startIDX +vnfNum-1 ) {
                CustomIDSet vnfSet = new CustomIDSet();
                vnfSet.add(vnf.getIDVector().get(1));
                VNFCluster cluster = new VNFCluster(vnf.getIDVector().get(1), null, vnfSet, vnf.getWorkLoad());
                //System.out.println(cluster.getClusterID());
                vnf.setClusterID(cluster.getClusterID());
                sfc.getVNFClusterMap().put(cluster.getClusterID(), cluster);
                continue;
            }

            //START VNF以外＋自分より若いIDのVNF以外を，後続VNF候補とする．
            TreeSet<Long> remainSet =
                    //this.getCandidateTaskIDSetFromApl(task, true);
                    this.getCandidateTaskFromOldOnlyMulti(sfc, vnf, true);

            //データ依存辺の出力辺数を決める．正規分布とする．
            long ddoutnum = NFVUtil.genLong2(NFVUtil.sfc_vnf_outdegree_min, NFVUtil.sfc_vnf_outdegree_max, 1, 0.5);
            int dsucidx;
            int dsize = remainSet.size();
            Long dsucID = new Long(0);
            Long[] dcandidates = remainSet.toArray(new Long[0]);
            // Long[] dcandidates = this.dQueue.toArray(new Long[0]);
            int inverval = (vnfnum / depth);

            SortedSet<Long> tmpSet = null;
            for (int j = 0; j < ddoutnum; j++) {
                if (dsize > 0) {
                    if (j == 0) {
                        long tmpnextID = vnf.getIDVector().get(1) + inverval;
                        dsucID = Math.min(startIDX+vnfnum-1, tmpnextID);
                    } else {
                        if (tmpStartSet.isEmpty()) {

                            //全タスクが規定の深さまで行っていれば，通常通りランダム操作にうつる．
                            dsucidx = NFVUtil.genInt(0, dsize - 1);
                            dsucID = dcandidates[dsucidx];

                        } else {
                            tmpSet = tmpStartSet.getObjSet().tailSet(vnf.getIDVector().get(1));
                            tmpSet.remove(vnf.getIDVector().get(1));
                            if (tmpSet.isEmpty()) {
                                dsucidx = NFVUtil.genInt(0, dsize - 1);
                                dsucID = dcandidates[dsucidx];
                            } else {
                                dsucID = tmpSet.first();
                                tmpStartSet.remove(dsucID);
                            }

                        }
                    }

                } else {
                    break;
                }

                VNF dsucTask = sfc.findVNFByLastID(dsucID);
                if(vnf == null || dsucTask ==null){
                    System.out.println("test");
                }
                DataDependence dd = new DataDependence(vnf.getIDVector(), dsucTask.getIDVector(), 0, 0, 0);
                // long datasize = 0;
                long tmp = 0;
               /* if(j == 0){
                    datasize = this.generateLongValueForSize2(this.ddedge_sizemin, this.ddedge_sizemax);
                    tmp = datasize;
                }else{
                    datasize =tmp;
                }*/
                //データサイズの決定
                long datasize = NFVUtil.genLong2(NFVUtil.vnf_datasize_min,
                        NFVUtil.vnf_datasize_max, NFVUtil.dist_vnf_datasize, NFVUtil.dist_vnf_datasize_mu);

                dd.setMaxDataSize(datasize);
                dd.setAveDataSize(datasize);
                dd.setMinDataSize(datasize);
                remainSet.remove(dsucID);
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
            //クラスタを生成しておく．
            //VNFクラスタも作っておく．
            CustomIDSet vnfSet = new CustomIDSet();
            vnfSet.add(vnf.getIDVector().get(1));


            VNFCluster cluster = new VNFCluster(vnf.getIDVector().get(1), null, vnfSet, vnf.getWorkLoad());
            //System.out.println(cluster.getClusterID());
            vnf.setClusterID(cluster.getClusterID());

            sfc.getVNFClusterMap().put(cluster.getClusterID(), cluster);
            if (vnf.getDpredList().isEmpty()) {
                sfc.getStartVNFSet().add(vnf.getIDVector().get(1));

            }

            if (vnf.getDsucList().isEmpty()) {
                sfc.getEndVNFSet().add(vnf.getIDVector().get(1));
            }


        }
        VNF endTask = sfc.findVNFByLastID(new Long(startIDX + vnfnum-1));
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
            this.updateAncestorMulti(ansSet, childSFC, dpredTask);
        }
        //全体のPGの命令数を集計する．
        //VNF task = (BBTask)AplOperator.getInstance().calculateInstructions(AplOperator.getInstance().getApl());
        //SFCGenerator.getIns().setSfc(sfc);

        return sfc;

    }
    public CustomIDSet updateAncestorMulti(CustomIDSet allSet, SFC sfc, VNF task) {
        //すでに自分がチェック済みであればそのままリターン
        if (allSet.contains(task.getIDVector().get(1))) {
            return allSet;
        }

        //以降はまだチェック済みでない場合の処理
        //先行タスクの先祖たちをかき集めてからallSetへ自分を追加し，リターン

        LinkedList<DataDependence> dpredList = task.getDpredList();
        Iterator<DataDependence> dpredIte = dpredList.iterator();

        while (dpredIte.hasNext()) {
            DataDependence dd = dpredIte.next();
            VNF dpredTask =sfc.findVNFByLastID(dd.getFromID().get(1));
            //まずは先行タスクのIDを先祖に追加する．
            task.getAncestorIDList().add(dpredTask.getIDVector().get(1));
            //再帰CALL
            this.updateAncestorMulti(allSet, sfc, dpredTask);
            HashSet<Long> newSet = dpredTask.getAncestorIDList();
            task.getAncestorIDList().addAll(newSet);

        }

        //allSetへ自分を追加する．
        allSet.add(task.getIDVector().get(1));

        return allSet;

    }

    public CustomIDSet autoupdateAncestor(SFC sfc, CustomIDSet allSet, VNF task) {
        //すでに自分がチェック済みであればそのままリターン
        if (allSet.contains(task.getIDVector().get(1))) {
            return allSet;
        }

        //以降はまだチェック済みでない場合の処理
        //先行タスクの先祖たちをかき集めてからallSetへ自分を追加し，リターン

        LinkedList<DataDependence> dpredList = task.getDpredList();
        Iterator<DataDependence> dpredIte = dpredList.iterator();

        while (dpredIte.hasNext()) {
            DataDependence dd = dpredIte.next();
            VNF dpredTask = sfc.findVNFByLastID(dd.getFromID().get(1));
            //まずは先行タスクのIDを先祖に追加する．
            task.getAncestorIDList().add(dpredTask.getIDVector().get(1));
            //再帰CALL
            this.autoupdateAncestor(sfc, allSet, dpredTask);
            HashSet<Long> newSet = dpredTask.getAncestorIDList();
            task.getAncestorIDList().addAll(newSet);

        }

        //allSetへ自分を追加する．
        allSet.add(task.getIDVector().get(1));

        return allSet;

    }



    public CustomIDSet updateAncestor(CustomIDSet allSet, VNF task) {
        //すでに自分がチェック済みであればそのままリターン
        if (allSet.contains(task.getIDVector().get(1))) {
            return allSet;
        }

        //以降はまだチェック済みでない場合の処理
        //先行タスクの先祖たちをかき集めてからallSetへ自分を追加し，リターン

        LinkedList<DataDependence> dpredList = task.getDpredList();
        Iterator<DataDependence> dpredIte = dpredList.iterator();

        while (dpredIte.hasNext()) {
            DataDependence dd = dpredIte.next();
            VNF dpredTask = SFCGenerator.getIns().getSfc().findVNFByLastID(dd.getFromID().get(1));
            //まずは先行タスクのIDを先祖に追加する．
            task.getAncestorIDList().add(dpredTask.getIDVector().get(1));
            //再帰CALL
            this.updateAncestor(allSet, dpredTask);
            HashSet<Long> newSet = dpredTask.getAncestorIDList();
            task.getAncestorIDList().addAll(newSet);

        }

        //allSetへ自分を追加する．
        allSet.add(task.getIDVector().get(1));

        return allSet;

    }

    public SFC getSfc() {
        return SFCGenerator.sfc;
    }

    public void setSfc(SFC sfcObj) {
        SFCGenerator.sfc = sfcObj;
    }

    public LinkedList<SFC> getSfcList() {
        return sfcList;
    }

    public void setSfcList(LinkedList<SFC> sfcList) {
        this.sfcList = sfcList;
    }
}
