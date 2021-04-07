package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.clustering.common.aplmodel.AplOperator;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

/**
 * VNF(Virtualized Network Function)を表すクラスです．
 * Created by Hidehiro Kanemitsu  on 2018/11/02.
 */
public class VNF  implements Serializable{


    private Vector<Long> IDVector;
    /**
     * このVNFを処理するのに必要な最低のMIPS
     * 未実装．
     */
    protected long  requiredMips;

    /**
     * このVNFが割り当てられているVMが受信すべき最低限のBW
     * 未実装
     */
    protected long  requiredRecvBW;

    /**
     * このVNFが割り当てられているVMが送信すべき最低限のBW
     * 未実装
     */
    protected long requiredSendBW;

    /**
     * このVNFが割り当てられているvCPUID
     */
    protected String vCPUID;

    /**
     * クラウドに割り当てられたかどうか
     */
    protected boolean isAssignedInCloud;

    /**
     * 仕事量
     */
    protected long workLoad;

    /**
     * 送受信される最大のデータサイズ
     */
    protected long maxData;

    /**
     * 送受信される最小のデータサイズ
     */
    protected long minData;

    /**
     * 深さ
     */
    protected int depth;

    /**
     * SFC作成用
     */
    protected HashSet<Long> ancestorIDList;

    /**
     * SFC作成用
     */
    protected Vector<Long> parentTask;

    /**
     * 先行VNFリスト
     */
    protected LinkedList<DataDependence> dpredList;

    /**
     * 後続VNFリスト
     */
    protected LinkedList<DataDependence> dsucList;


    /**
     * 開始時刻
     */
    protected double startTime;

    /**
     * 終了時刻
     */
    protected double finishTime;

    /**
     * EST (Earliest Start Time)
     *
     */
    protected double EST;

    /**
     * DRT (Data Arrival Time)
     */
    protected double DRT;

    /**
     * このファンクションの処理タイプ
     * 未実装
     */
    protected int type;

    /**
     * tlevel
     */
    protected double tlevel;

    /**
     * blevel
     */
    protected double blevel;

    /**
     * tlevelを決めている先行VNFのID
     */
    protected Long dominantPredID;

    /**
     * blevelを決めている後続VNFのID
     */
    protected Long dominantSucID;

    /**
     * このVNFをvCPUへ割り当てることによる，使用率の増分．
     * %単位．
     */
    protected int  usage;

    /**
     * このVNFを所属するクラスタID
     */
    protected Long clusterID;

    /**
     * このVNFが割り当てられているマシンID
     * 例えば，携帯端末のIDに相当する．
     */
    protected Long machineID;

    /**
     * 各アルゴリズム独自で決められるスケジュール優先度．
     */
    protected double priority;

    protected long hLevel;

    /**
     * For PEFT
     */
    protected double aveOCT;


    //
    protected long imageSize;







    public VNF(int in_type, long weight, long requiredMips, long requiredRecvBW, long  requiredSendBW, String  vCPUID, int percent) {
        //super(in_type, in_maxweight, in_aveweight, in_minweight);
        this.requiredMips = requiredMips;
        /*
        if((in_type != NFVUtil.VNF_TYPE_VEND)&&(in_type != NFVUtil.VNF_TYPE_VSTART)){
            this.type = NFVUtil.genInt2(1, NFVUtil.vnf_type_max, 1, 0.5);
        }*/
        if(in_type >= 1){
            this.type = in_type;

        }else{
            this.type = NFVUtil.genInt2(1, NFVUtil.vnf_type_max, 1, 0.5);
        }



        this.workLoad = weight;
        this.requiredRecvBW = requiredRecvBW;
        this.requiredSendBW = requiredSendBW;
        this.vCPUID = vCPUID;
        this.maxData = 0;
        this.minData = NFVUtil.MAXValue;
        this.depth = -1;
        this.ancestorIDList = new HashSet<Long>();
        this.parentTask = new Vector<Long>();
        this.dsucList = new LinkedList<DataDependence>();
        this.dpredList = new LinkedList<DataDependence>();
        this.startTime = -1;
        this.finishTime = -1;
        this.EST = -1;
        this.DRT = -1;

        this.tlevel = -1;
        this.blevel = -1;
        this.dominantPredID = new Long(-1);
        this.dominantSucID = new Long(-1);
        this.usage = percent ;
        this.clusterID = -1L;
        this.isAssignedInCloud = false;
        this.priority = -1d;
        this.hLevel = -1;
        this.aveOCT = -1d;

    }

    public Long getDominantPredID() {
        return dominantPredID;
    }

    public int  getUsage() {
        return usage;
    }

    public Long getClusterID() {
        return clusterID;
    }

    public void setClusterID(Long clusterID) {
        this.clusterID = clusterID;
    }

    public void setUsage(int  usage) {
        this.usage = usage;
    }

    public void setDominantPredID(Long dominantPredID) {
        this.dominantPredID = dominantPredID;
    }

    public Long getDominantSucID() {
        return dominantSucID;
    }

    public void setDominantSucID(Long dominantSucID) {
        this.dominantSucID = dominantSucID;
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

    public Vector<Long> getIDVector() {
        return IDVector;
    }

    public void setIDVector(Vector<Long> IDVector) {
        this.IDVector = IDVector;
    }

    public Long getRequiredMips() {
        return requiredMips;
    }

    public void setRequiredMips(Long requiredMips) {
        this.requiredMips = requiredMips;
    }

    public Long getRequiredRecvBW() {
        return requiredRecvBW;
    }

    public void setRequiredRecvBW(Long requiredRecvBW) {
        this.requiredRecvBW = requiredRecvBW;
    }

    public Long getRequiredSendBW() {
        return requiredSendBW;
    }

    public void setRequiredSendBW(Long requiredSendBW) {
        this.requiredSendBW = requiredSendBW;
    }

    public String  getvCPUID() {
        return vCPUID;
    }

    public void setvCPUID(String  vCPUID) {
        this.vCPUID = vCPUID;
    }

    public LinkedList<DataDependence> getDpredList() {
        return dpredList;
    }

    public void setDpredList(LinkedList<DataDependence> dpredList) {
        this.dpredList = dpredList;
    }

    public LinkedList<DataDependence> getDsucList() {
        return dsucList;
    }

    public void setDsucList(LinkedList<DataDependence> dsucList) {
        this.dsucList = dsucList;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    /**
     * @param dd
     * @return
     */
    public boolean addDsuc(DataDependence dd) {
        Vector<Long> in_fromID = dd.getFromID();
        long fid =  in_fromID.get(1);

        Vector<Long> in_toID = dd.getToID();
        long tid = in_toID.get(1);

        if(fid == tid){
            return false;
        }
        int size = this.dsucList.size();
        //Dsucリストを全てチェックするループ
        for (int i = 0; i < size; i++) {
            DataDependence tmp_dd = this.dsucList.get(i);
            Vector<Long> tmp_fromID = tmp_dd.getFromID();
            long tmp_fid = tmp_fromID.get(1);

            Vector<Long> tmp_toID = tmp_dd.getToID();
            long tmp_tid = tmp_toID.get(1);

            //同じIDであれば追加せず，falseを返す．
            if ((fid == tmp_fid) &&(tid == tmp_tid)) {
                return false;
            }
        }


        if(this.maxData <= dd.getMaxDataSize()){
            this.maxData = dd.getMaxDataSize();
        }

        if(this.minData>=dd.getMaxDataSize()){
            this.minData = dd.getMaxDataSize();
        }
        this.dsucList.add(dd);
        return true;
    }


    public boolean addDpred(DataDependence dd) {
        Vector<Long> in_fromID = dd.getFromID();
        long fid = in_fromID.get(1);

        Vector<Long> in_toID = dd.getToID();
        long tid = in_toID.get(1);

        if(fid == tid){
            return false;
        }

        int size = this.dpredList.size();
        //Dpredリストを全てチェックするループ
        for (int i = 0; i < size; i++) {
            DataDependence tmp_dd = this.dpredList.get(i);
            Vector<Long> tmp_fromID = tmp_dd.getFromID();
            long tmp_fid = tmp_fromID.get(1);

            Vector<Long> tmp_toID = tmp_dd.getToID();
            long tmp_tid = tmp_toID.get(1);

            //同じIDであれば追加せず，falseを返す．
            if ((fid == tmp_fid)&&(tid == tmp_tid)) {
                /*if(AplOperator.getInstance().isIDEqual(tmp_fromID,in_fromID)){ */
                return false;
            }
        }

        if(this.maxData <= dd.getMaxDataSize()){
            this.maxData = dd.getMaxDataSize();
        }

        if(this.minData>=dd.getMaxDataSize()){
            this.minData = dd.getMaxDataSize();
        }

        this.dpredList.add(dd);
        return true;
    }

    /**
     * @param fromID
     * @param toID
     * @return
     */
    public DataDependence findDDFromDpredList(Vector<Long> fromID, Vector<Long> toID) {
        int size = this.dpredList.size();
        Iterator<DataDependence> ite = this.dpredList.iterator();
        //DpredListのリスト内チェックループ
        //for (int i = 0; i < size; i++) {
        while(ite.hasNext()){
            // DataDependence tmp_dd = this.dpredList.get(i);
            DataDependence tmp_dd = ite.next();
            //同じIDが見つかれば，それを取得する
            if ((tmp_dd.getFromID().get(1).longValue() == fromID.get(1).longValue()) &&
                    (tmp_dd.getToID().get(1).longValue() == toID.get(1).longValue())) {
                return tmp_dd;
            }
        }
        return null;
    }

    public DataDependence findDDFromDsucList(Vector<Long> fromID, Vector<Long> toID) {
        int size = this.dsucList.size();
        Iterator<DataDependence> ite = this.dsucList.iterator();
        //DpredListのリスト内チェックループ
        //for (int i = 0; i < size; i++) {
        while(ite.hasNext()){
            // DataDependence tmp_dd = this.dpredList.get(i);
            DataDependence tmp_dd = ite.next();
            //同じIDが見つかれば，それを取得する
            if ((tmp_dd.getFromID().get(1).longValue() == fromID.get(1).longValue()) &&
                    (tmp_dd.getToID().get(1).longValue() == toID.get(1).longValue())) {
                return tmp_dd;
            }
        }
        return null;
    }

    public boolean isDpredAllChecked(){
        Iterator<DataDependence> dpredIte = this.dpredList.iterator();
        boolean ret = true;
        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();
            if(!dpred.getIsChecked()){
                ret = false;
                break;
            }
        }
        return ret;
    }

    public long getMaxData() {
        return maxData;
    }

    public void setMaxData(long maxData) {
        this.maxData = maxData;
    }

    public long getMinData() {
        return minData;
    }

    public void setMinData(long minData) {
        this.minData = minData;
    }

    public long getWorkLoad() {
        return workLoad;
    }

    public void setWorkLoad(long workLoad) {
        this.workLoad = workLoad;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public HashSet<Long> getAncestorIDList() {
        return ancestorIDList;
    }

    public void setAncestorIDList(HashSet<Long> ancestorIDList) {
        this.ancestorIDList = ancestorIDList;

    }

    public Vector<Long> getParentTask() {
        return parentTask;
    }

    public void setParentTask(Vector<Long> parentTask) {
        this.parentTask = parentTask;
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

    public boolean isAssignedInCloud() {
        return isAssignedInCloud;
    }

    public void setAssignedInCloud(boolean assignedInCloud) {
        isAssignedInCloud = assignedInCloud;
    }

    public Long getMachineID() {
        return machineID;
    }

    public void setMachineID(Long machineID) {
        this.machineID = machineID;
    }

    public double getPriority() {
        return priority;
    }

    public void setPriority(double priority) {
        this.priority = priority;
    }

    public long gethLevel() {
        return hLevel;
    }

    public void sethLevel(long hLevel) {
        this.hLevel = hLevel;
    }

    public double getAveOCT() {
        return aveOCT;
    }

    public void setAveOCT(double aveOCT) {
        this.aveOCT = aveOCT;
    }

    //
    public long getImageSize() { return imageSize; }

    public void setImageSize(long imageSize) { this.imageSize = imageSize; }
}
