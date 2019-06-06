package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.CloudUtil;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.io.Serializable;
import java.util.Iterator;

public class VNFCluster implements Serializable {

    /**
     * 割当先となるvCPUのID(文字列）
     */
    private String vcpuID;

    /**
     * このクラスタに含まれているVNFの集合
     */
    private CustomIDSet vnfSet;

    /**
     * このクラスタに含まれるVNFのworkloadの総和
     */
    private long clusterSize;


    /**
     *  クラスタID
     */
    private Long clusterID;

    /**
     * このクラスタのtlevel値
     * top内のvnfのtlevelの最小値
     */
    private double tlevel;

    /**
     * このクラスタのblevel値
     * btm内のvnfのblevelの最大値
     */
    private double blevel;


    /**
     * このクラスタのtlevelを決めているVNFのID
     */
    private Long tlevelDominantVNF;

    /**
     * このクラスタのblevelを決めているVNFのID
     */
    private Long blevelDominantVNF;

    /**
     * 外部クラスタからの入力辺のみを持つVNF集合
     */
    CustomIDSet topSet;

    /**
     * 一部，当該クラスタ内のタスクからの入力辺をもつVNF集合
     */
    CustomIDSet inSet;

    /**
     * 外部クラスタへの辺のみをもつVNF集合
     */
    CustomIDSet btmSet;

    /**
     * 当該クラスタ内VNFへの辺をもつVNF集合
     */
    CustomIDSet outSet;


    private double startTime;

    private double finishTime;

    private double EST;

    private double DRT;

    private boolean isLinear;

    /**
     * コンストラクタ
     * @param clusterID
     */
   /* public VNFCluster(Long clusterID) {
        this.clusterID = clusterID;
        this.clusterSize = 0;
        this.tlevel = -1;
        this.blevel = -1;
        this.vcpuID = null;
        this.vnfSet = new CustomIDSet();
    }
    */

    /**
     * コンストラクタ
     * @param clusterID
     * @param vcpuID
     * @param vnfSet
     * @param clusterSize
     */
    public VNFCluster(Long clusterID, String vcpuID, CustomIDSet vnfSet, long clusterSize) {
        this.vcpuID = vcpuID;
        this.vnfSet = vnfSet;
        this.clusterSize = clusterSize;
        this.clusterID = clusterID;
        this.tlevel = -1;
        this.blevel = -1;
        this.startTime = -1;
        this.finishTime = -1;
        this.EST = -1;
        this.DRT = -1;
        this.topSet = new CustomIDSet();
        this.inSet = new CustomIDSet();
        this.btmSet = new CustomIDSet();
        this.outSet = new CustomIDSet();
        this.tlevelDominantVNF = -1L;
        this.blevelDominantVNF = -1L;
        this.isLinear = true;

        this.tlevel = -1;
        this.blevel = -1;
        //this.configureVNFCluster();
    }

    /**
     * 対象となるVNFをクラスタへ含みます．
     * もしVNF IDが，より小さいものであれば，クラスタIDを，新規VNFのIDへと変更します．
     *
     * @param vnf
     * @return
     */
    /*public boolean addVNF(VNF vnf){
        //すでに存在するVNFを追加しようとしているのであれば，何もせずにfalseを返す．
        if(this.vnfSet.contains(vnf.getIDVector().get(1))){
            return false;
        }else{
            Long vnfID = vnf.getIDVector().get(1);
            this.vnfSet.add(vnf.getIDVector().get(1));
            //クラスタサイズを更新しておく．
            this.clusterSize += vnf.getWorkLoad();
            if(this.clusterID.longValue() > vnfID.longValue()){
                this.clusterID = vnfID;
            }
            this.configureVNFCluster();
            return true;
        }
    }
*/
    /**
     * VNFクラスタを追加する処理．つまり，クラスタ同士のマージを
     * します．
     * @param
     * @return
     */
    /*public boolean addVNFCluster(VNFCluster cluster){
        if(this.clusterID.longValue() == cluster.getClusterID().longValue()){
            return false;
        }else {
            //VNF集合の更新
            this.vnfSet.addAll(cluster.getVnfSet());
            this.clusterSize += cluster.getClusterSize();
            if (this.clusterID.longValue() > cluster.getClusterID().longValue()) {
                this.clusterID = cluster.getClusterID();
            }

        }
        this.configureVNFCluster();
        return true;
    }
    */


    public  void configureVNFCluster(){
        /**if(SFCGenerator.getIns().getSfc()==null){
            return;
        }**/
        //一旦クリアする．

        this.getTopSet().getObjSet().clear();
        this.getInSet().getObjSet().clear();
        this.getBtmSet().getObjSet().clear();
        this.getOutSet().getObjSet().clear();
        Iterator<Long> vnfIte = this.getVnfSet().iterator();
        SFC sfc = SFCGenerator.getIns().getSfc();
        //VNFに対するループ
        while(vnfIte.hasNext()){
            Long id = vnfIte.next();

            VNF vnf =sfc .findVNFByLastID(id);

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

                if(this.getVnfSet().contains(fromVNF.getIDVector().get(1))){
                    fromThisNum ++;
                }else{
                    fromOutNum++;
                }
            }
            Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
            while(dsucIte.hasNext()){
                DataDependence dsuc = dsucIte.next();
                VNF toVNF = sfc.findVNFByLastID(dsuc.getToID().get(1));
                if(this.getVnfSet().contains(toVNF.getIDVector().get(1))){
                    toThisNum++;
                }else{
                    toOutNum++;
                }
            }
            if((fromOutNum >0 )&&(fromThisNum==0)){
                this.getTopSet().add(vnf.getIDVector().get(1));
                this.getInSet().add(vnf.getIDVector().get(1));
            }
            if((fromOutNum>0)&&(fromThisNum>0)){
                this.getInSet().add(vnf.getIDVector().get(1));
            }
            if((toOutNum>0)&&(toThisNum==0)){
                this.getBtmSet().add(vnf.getIDVector().get(1));
                this.getOutSet().add(vnf.getIDVector().get(1));
            }
            if((toOutNum>0)&&(toThisNum>0)){
                this.getOutSet().add(vnf.getIDVector().get(1));
            }
            //start/endに対する処理
            if(sfc.getStartVNFSet().contains(vnf.getIDVector().get(1))){
                this.getTopSet().add(vnf.getIDVector().get(1));
                this.getInSet().add(vnf.getIDVector().get(1));
            }
            if(sfc.getEndVNFSet().contains(vnf.getIDVector().get(1))){
                this.getBtmSet().add(vnf.getIDVector().get(1));
                this.getOutSet().add(vnf.getIDVector().get(1));

            }
        }
        //Tlevel値の更新をする．
        //this.configureTlevel();
    }




    public String getVcpuID() {
        return vcpuID;
    }

    public void setVcpuID(String vcpuID) {
        this.vcpuID = vcpuID;
    }

    public CustomIDSet getVnfSet() {
        return vnfSet;
    }

    public void setVnfSet(CustomIDSet vnfSet) {
        this.vnfSet = vnfSet;
    }

    public long getClusterSize() {
        return clusterSize;
    }

    public void setClusterSize(long clusterSize) {
        this.clusterSize = clusterSize;
    }

    public Long getClusterID() {
        return clusterID;
    }

    public void setClusterID(Long clusterID) {
        this.clusterID = clusterID;
    }

    public double getTlevel() {
        return tlevel;
    }

    public void setTlevel(double tlevel) {
        this.tlevel = tlevel;
    }

    public double getBlevel() {
        return blevel;
    }

    public void setBlevel(double blevel) {
        this.blevel = blevel;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    public double getEST() {
        return EST;
    }

    public void setEST(double EST) {
        this.EST = EST;
    }

    public double getDRT() {
        return DRT;
    }

    public void setDRT(double DRT) {
        this.DRT = DRT;
    }

    public CustomIDSet getTopSet() {
        return topSet;
    }

    public void setTopSet(CustomIDSet topSet) {
        this.topSet = topSet;
    }

    public CustomIDSet getInSet() {
        return inSet;
    }

    public void setInSet(CustomIDSet inSet) {
        this.inSet = inSet;
    }

    public CustomIDSet getBtmSet() {
        return btmSet;
    }

    public void setBtmSet(CustomIDSet btmSet) {
        this.btmSet = btmSet;
    }

    public CustomIDSet getOutSet() {
        return outSet;
    }

    public void setOutSet(CustomIDSet outSet) {
        this.outSet = outSet;
    }

    public Long getTlevelDominantVNF() {
        return tlevelDominantVNF;
    }

    public void setTlevelDominantVNF(Long tlevelDominantVNF) {
        this.tlevelDominantVNF = tlevelDominantVNF;
    }

    public Long getBlevelDominantVNF() {
        return blevelDominantVNF;
    }

    public void setBlevelDominantVNF(Long blevelDominantVNF) {
        this.blevelDominantVNF = blevelDominantVNF;
    }

    public boolean isLinear() {
        return isLinear;
    }

    public void setLinear(boolean linear) {
        isLinear = linear;
    }
}
