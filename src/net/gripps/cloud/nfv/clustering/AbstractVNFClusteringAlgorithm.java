package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.CloudUtil;

import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.sfc.*;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;
import net.gripps.environment.CPU;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 * サンプルのクラスタリングアルゴリズムです．
 */
public  abstract class AbstractVNFClusteringAlgorithm extends BaseVNFSchedulingAlgorithm {

    /**
     * まだチェックが終わっていないクラスタ集合
     */
    protected CustomIDSet UEXClusterSet;

    /**
     * シングルクラスタから構成されて，かつ実行レディ状態
     * となったVNFクラスタの集合．
     */
    protected CustomIDSet freeClusterSet;


    protected int btmMode;


    /**
     * インスタンス化時に，各VNFへのtlevel/blevel値の反映を行っている．
     * @param env
     * @param sfc
     */
    public AbstractVNFClusteringAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        //this.initialize();
        this.btmMode = 0;
    }

    @Override
    public void initialize() {
        super.initialize();

        this.UEXClusterSet = new CustomIDSet();
        //UEXに，クラスタすべてを入れる．
        Iterator<Long> keyIte = this.sfc.getVNFClusterMap().keySet().iterator();
        while(keyIte.hasNext()){
            Long id = keyIte.next();
            this.UEXClusterSet.add(id);
        }
        this.freeClusterSet = new CustomIDSet();
        //freeリストへ入れる．
        Iterator<Long> startIte = this.sfc.getStartVNFSet().iterator();
        while(startIte.hasNext()){
            Long vnfID = startIte.next();
            VNF vnf = this.sfc.findVNFByLastID(vnfID);
            Long clusterID = vnf.getClusterID();
            this.freeClusterSet.add(clusterID);
        }
        //各クラスタにおいて，topリストの中でtlevelの最小値をクラスタのtlevelとする．
        Iterator<VNFCluster> cIte = this.sfc.getVNFClusterMap().values().iterator();
        while(cIte.hasNext()){
            VNFCluster c = cIte.next();

            if(c.getTopSet().isEmpty()){
                this.configureVNFCluster(c);
            }
            this.configureClusterTlevel(c);
            this.configureClusterBlevel(c);
        }

    }

    /**
     *
     * @param cluster
     */
    public void configureClusterTlevel(VNFCluster cluster){

        double maxTlevel = -1d;

        //Iterator<VNFCluster> cIte = sfc.getVNFClusterMap().values().iterator();

        //VNFCluster c = cIte.next();
        Iterator<Long> topIte = cluster.getTopSet().iterator();
        while(topIte.hasNext()){
            Long topID = topIte.next();
            VNF topTask = this.sfc.findVNFByLastID(topID);
            double tmpTlevel = topTask.getTlevel();
            //System.out.println("VNFID:"+topTask.getIDVector().get(1)+" tmpTlevel:"+tmpTlevel);
            if(maxTlevel <= tmpTlevel){
                maxTlevel = tmpTlevel;
                cluster.setTlevelDominantVNF(topID);
            }
        }
        //System.out.println("tlevel:"+minTlevel);
        cluster.setTlevel(maxTlevel);
    }

    /**
     *
     * @param allSet
     * @param removeSet
     * @return
     */
    public CustomIDSet getSubSet(CustomIDSet allSet, CustomIDSet removeSet) {
        CustomIDSet retSet = new CustomIDSet();

        Iterator<Long> aIte = allSet.iterator();
        while (aIte.hasNext()) {
            Long id = aIte.next();
            //削除対象に入っていないもののみ，追加する．
            if (!removeSet.contains(id)) {
                retSet.add(id);
            }
        }
        return retSet;
    }

    public void configureClusterBlevel(VNFCluster cluster){
       /* Iterator<Long> btmIte = null;
        if(this.btmMode == 0){
           btmIte = cluster.getBtmSet().iterator();
        }else{
            btmIte = cluster.getOutSet().iterator();
        }
*/
        Iterator<Long> btmIte = cluster.getOutSet().iterator();
        double maxBlevel = -1;
        long speed = -1L;

        if(cluster.getVcpuID() != null){
            VCPU vcpu = this.env.getGlobal_vcpuMap().get(cluster.getVcpuID());
            speed = vcpu.getMips();
        }else{
            speed = this.usedSpeed;
        }
        while(btmIte.hasNext()){
            Long btmID = btmIte.next();
            VNF btmVNF = this.sfc.findVNFByLastID(btmID);

            CustomIDSet descendantSet = this.getDescendantsInCluster(new CustomIDSet(), btmVNF);
            CustomIDSet allSet = cluster.getVnfSet();
            CustomIDSet retSet = this.getSubSet(allSet, descendantSet);
            long totalSize = this.calcWorkLoad(retSet) - btmVNF.getWorkLoad();

           // double tmpBlevel = btmVNF.getTlevel() + btmVNF.getBlevel();

            double tmpBlevel = this.calcExecTime(totalSize, speed) + btmVNF.getBlevel();
            if(maxBlevel <= tmpBlevel){
                maxBlevel = tmpBlevel;
                cluster.setBlevelDominantVNF(btmID);
            }
        }
        cluster.setBlevel(maxBlevel);



    }





    public long calcWorkLoad(CustomIDSet set) {
        Iterator<Long> ite = set.iterator();
        long val = 0;
        while (ite.hasNext()) {
            Long id = ite.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            val += vnf.getWorkLoad();
        }
        return val;
    }





    /**
     * 何らかの基準でVNFClusterを選択します．
     * 実装クラス側で実装してください．
     * @return
     */
    public abstract VNFCluster selectVNFCluster();


    /**
     * 指定クラスタに対して何らかの処理を行います．
     * 例えば，clusterを他クラスタとマージするなど，です．
     * freeからシングルクラスタを選んでvcpuへ割り当てる．
     * そして，選ばれたクラスタの後続クラスタをマージする，
     * という流れはどうか？もう片方のvcpuがヒマなVMには，
     * CMWSLによるクラスタサイズ決定を用いて，下限値まで
     * クラスタを割り当てる．
     * @param cluster
     */
    public abstract VNFCluster  processVNFCluster(VNFCluster cluster);


    /**
     * 指定のVNFクラスタを割り当てる．
     * 例えば，
     * @param cluster
     */
    public void scheduleVNFCluster(VNFCluster cluster, HashMap<String, VCPU> map){
        //選択されたクラスタに対し，なんらかの処理（例えば他クラスタと合体）をします．
        //その結果をnewClusterへと格納します．
        VNFCluster newCluster = this.processVNFCluster(cluster);
        //未スケジュール集合から削除する．
        this.removeVNFFromUEX(newCluster);
    }

    public int getBtmMode() {
        return btmMode;
    }

    public void setBtmMode(int btmMode) {
        this.btmMode = btmMode;
    }

    /**
     * 未スケジュールVNF集合から，指定クラスタ内のすべてのVNF
     * を削除する処理
     */
    public void removeVNFFromUEX(VNFCluster cluster){
        Iterator<Long> vnfIte = cluster.getVnfSet().iterator();
        while(vnfIte.hasNext()){
            Long id = vnfIte.next();
            this.unScheduledVNFSet.remove(id);
        }
    }

    /**
     * vnfが属するクラスタにおいて，vnfの子孫たちを取得する．
     * vnf自身は含まないものとする．
     *
     * @param vnf
     * @return
     */
    public CustomIDSet getDescendantsInCluster(CustomIDSet set, VNF vnf) {
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        Long clusterID = vnf.getClusterID();

        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
            Long sucID = sucVNF.getIDVector().get(1);
            if (set.contains(sucID)) {
                continue;
            } else {
                if (sucVNF.getClusterID().longValue() == clusterID) {
                    set.add(sucVNF.getIDVector().get(1));
                    set = this.getDescendantsInCluster(set, sucVNF);
                } else {
                    continue;
                }
            }

        }
        return set;
    }

        /**
         * メイン処理です．あくまで例です．
         * 継承側での参考にしてください．
         */
    public void mainProcess(){
        //未スケジュールなVNFが残っている間，行うループ
        while(!this.getUnScheduledVNFSet().isEmpty()){
            VNFCluster cluster = this.selectVNFCluster();
            //vcpu全体から，クラスタの割当先を選択する．
            //そして，スケジュールする．
            this.scheduleVNFCluster(cluster, this.vcpuMap);
        }
    }

    /**
     * 重要なメソッドです．
     * VNFクラスタを追加する処理．つまり，クラスタ同士のマージを
     * します．たとえ1VNFから構成されるクラスタでもマージできます．
     * fromからtoを取り込む．
     * @param fromCluster
     * @param toCluster
     * @return
     */
    public VNFCluster clustering(VNFCluster fromCluster, VNFCluster toCluster){



        if(fromCluster.getClusterID().longValue() == toCluster.getClusterID().longValue()){
            this.UEXClusterSet.remove(fromCluster.getClusterID());
            //this.freeClusterSet.remove(fromCluster.getClusterID());
            //Freeリストの更新．

            updateFreeClusterSet(fromCluster);

            return fromCluster;
        }else {
            //VNF集合の更新
            //fromCluster.getVnfSet().addAll(toCluster.getVnfSet());
            Iterator<Long> vIte = toCluster.getVnfSet().iterator();
            while(vIte.hasNext()){
                Long id = vIte.next();
                fromCluster.getVnfSet().add(id);
            }
            //消える方のクラスタ
            long orgID;
            VCPU tovcpu  = this.env.getGlobal_vcpuMap().get(toCluster.getVcpuID());
            if(tovcpu !=null){
                tovcpu.setTaskClusterID(new Long(-1));
            }

            CustomIDSet fromSet = fromCluster.getVnfSet();
            if (fromCluster.getClusterID().longValue() > toCluster.getClusterID().longValue()) {
                 orgID = fromCluster.getClusterID().longValue();
                fromCluster.setClusterID(toCluster.getClusterID());
                this.sfc.getVNFClusterMap().remove(orgID);
                this.sfc.getVNFClusterMap().put(fromCluster.getClusterID(), fromCluster);
                if((fromCluster.getVcpuID() == null)&&(toCluster.getVcpuID()!=null)){
                    fromCluster.setVcpuID(toCluster.getVcpuID());
                    this.freeClusterSet.remove(fromCluster.getClusterID());
                    //this.freeClusterSet.remove(toCluster.getClusterID());
                }else{
                    this.freeClusterSet.add(toCluster.getClusterID());
                }
                this.UEXClusterSet.remove(toCluster.getClusterID());
                this.UEXClusterSet.remove(fromCluster.getClusterID());
                this.freeClusterSet.remove(orgID);
            }else{
                orgID = toCluster.getClusterID();
                this.sfc.getVNFClusterMap().remove(orgID);
               // this.UEXClusterSet.remove(fromCluster.getClusterID());
                this.freeClusterSet.remove(orgID);
            }

            this.UEXClusterSet.remove(orgID);
            //this.UEXClusterSet.remove(fromCluster.getClusterID());
            //this.sfc.getVNFClusterMap().remove(orgID);
         //   this.sfc.getVNFClusterMap().remove(orgID);
            //各vcpuの割当先をorgIDとする．
            long retID = fromCluster.getClusterID();
            Iterator<Long> toVIte = fromCluster.getVnfSet().iterator();
            String vcpuID = fromCluster.getVcpuID();
            while(toVIte.hasNext()){
                Long id = toVIte.next();
                VNF v = this.sfc.findVNFByLastID(id);
                v.setClusterID(retID);
                v.setvCPUID(vcpuID);

            }
            long orgSize = fromCluster.getClusterSize();
            orgSize += toCluster.getClusterSize();
            fromCluster.setClusterSize(orgSize);
        }

        //Freeリストの更新．
        updateFreeClusterSet(fromCluster);

        //top/in/out/btm集合の更新
        this.configureVNFCluster(fromCluster);
        //各VNFのlevel更新，及びfreeクラスタ更新処理．
        //fromCluster = this.configureLevel(fromCluster);


        if(fromCluster.getVcpuID()!=null){
            this.freeClusterSet.remove(fromCluster.getClusterID());
        }else{
            this.freeClusterSet.add(fromCluster.getClusterID());
        }
        //this.UEXClusterSet.remove(fromCluster.getClusterID());
        this.configureLevel(fromCluster);
        //tlevel/blevelの更新
        this.configureClusterTlevel(fromCluster);
        this.configureClusterBlevel(fromCluster);


        return fromCluster;
    }

    /**
     * 各VNFのレベルを更新する．
     * @param cluster
     * @return
     */
    public VNFCluster configureLevel(VNFCluster cluster){
        return cluster;

    }

    /**
     * clusterをvcpuへ割り当てる処理．
     * @param cluster
     * @param vcpu
     * @return
     */
    public VNFCluster assignVCPU(VNFCluster cluster, VCPU vcpu){

     /*   if(vcpu == null){
            System.out.println("testlarit");
            return cluster;
        }
        */
        cluster.setVcpuID(vcpu.getPrefix());
        Iterator<Long> vIte = cluster.getVnfSet().iterator();
        while(vIte.hasNext()){
            Long id = vIte.next();
            VNF task = this.sfc.findVNFByLastID(id);
            task.setvCPUID(vcpu.getPrefix());
        }

        return cluster;
    }

    /**
     * clusterのoutVNFの後続タスクにたいして行う．
     * @param cluster
     */
    public void updateFreeClusterSet(VNFCluster cluster){
        Iterator<Long> oIte = cluster.getOutSet().iterator();
        CustomIDSet set = new CustomIDSet();

        while(oIte.hasNext()){
            Long id =oIte.next();
            VNF oVNF = this.sfc.findVNFByLastID(id);
            //oVNFの後続を見る．
            Iterator<DataDependence> dsucIte = oVNF.getDsucList().iterator();
            while(dsucIte.hasNext()){
                DataDependence dsuc = dsucIte.next();
                VNF dsucVNF =this.sfc.findVNFByLastID(dsuc.getToID().get(1));
                if(dsucVNF.getClusterID().longValue() == cluster.getClusterID().longValue()){
                    continue;
                    //違うクラスタ内VNFのときだけチェックする．
                }
                DataDependence dpred = dsucVNF.findDDFromDpredList(oVNF.getIDVector(), dsucVNF.getIDVector());
                //dpredをexaminedとする．
                dpred.setIsChecked(true);
                if(dsucVNF.isDpredAllChecked()){
                    VNFCluster cls = this.sfc.findVNFCluster(dsucVNF.getClusterID());
                    if(cls.getVcpuID() != null){
                        //すでにチェック済みであれば，消す．
                        this.freeClusterSet.remove(cls.getClusterID());
                        this.UEXClusterSet.remove(cls.getClusterID());
                    }else{
                        this.freeClusterSet.add(cls.getClusterID());
                    }
                    /*
                    if(this.UEXClusterSet.contains(dsucVNF.getClusterID())){
                        this.freeClusterSet.add(dsucVNF.getClusterID());
                    }else{
                        //すでにチェック済みクラスタであれば，無視．
                        this.freeClusterSet.remove(dsucVNF.getClusterID());
                    }
                    */
                }
            }

        }
    }



    public  void configureVNFCluster(VNFCluster cluster){
     /*   if(SFCGenerator.getIns().getSfc()==null){
            return;
        }*/
        //一旦クリアする．

        cluster.getTopSet().getObjSet().clear();
        cluster.getInSet().getObjSet().clear();
        cluster.getBtmSet().getObjSet().clear();
        cluster.getOutSet().getObjSet().clear();
        Iterator<Long> vnfIte = cluster.getVnfSet().iterator();

        //VNFに対するループ
        while(vnfIte.hasNext()){
            Long id = vnfIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);

            //入力辺チェック
            Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
            int fromOutNum = 0;
            int fromThisNum = 0;
            int  toOutNum = 0;
            int toThisNum=0;
            //Top: fromOut >0 and fromThisNum=0
            //In: fromOut>0 and fromThisNum>0
            //btm: toOutNum>0 and toThisNum=0
            //out: toOutNum>0 and toThisNum>0
            while(dpredIte.hasNext()){
                DataDependence dpred = dpredIte.next();
                VNF fromVNF = sfc.findVNFByLastID(dpred.getFromID().get(1));

               // if(cluster.getVnfSet().contains(fromVNF.getIDVector().get(1))){
                if(fromVNF.getClusterID().longValue() == cluster.getClusterID().longValue()){
                    fromThisNum ++;
                }else{
                    fromOutNum++;
                }
            }
            Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
            while(dsucIte.hasNext()){
                DataDependence dsuc = dsucIte.next();
                VNF toVNF = sfc.findVNFByLastID(dsuc.getToID().get(1));
                //if(cluster.getVnfSet().contains(toVNF.getIDVector().get(1))){
                if(toVNF.getClusterID().longValue() == cluster.getClusterID().longValue()){
                    toThisNum++;
                }else{
                    toOutNum++;
                }
            }
            if((fromOutNum >=0 )&&(fromThisNum==0)){
                cluster.getTopSet().add(vnf.getIDVector().get(1));
                cluster.getInSet().add(vnf.getIDVector().get(1));
            }
            if((fromOutNum>=0)&&(fromThisNum>0)){
                cluster.getInSet().add(vnf.getIDVector().get(1));
            }
            if((toOutNum>=0)&&(toThisNum==0)){
                cluster.getBtmSet().add(vnf.getIDVector().get(1));
                cluster.getOutSet().add(vnf.getIDVector().get(1));
            }
            if((toOutNum>=0)&&(toThisNum>0)){
                cluster.getOutSet().add(vnf.getIDVector().get(1));
            }
            //start/endに対する処理
            if(sfc.getStartVNFSet().contains(vnf.getIDVector().get(1))){
                cluster.getTopSet().add(vnf.getIDVector().get(1));
               cluster.getInSet().add(vnf.getIDVector().get(1));
            }
            if(sfc.getEndVNFSet().contains(vnf.getIDVector().get(1))){
                cluster.getBtmSet().add(vnf.getIDVector().get(1));
                cluster.getOutSet().add(vnf.getIDVector().get(1));

            }
        }
        //Tlevel値の更新をする．
        //this.configureTlevel();
    }
}
