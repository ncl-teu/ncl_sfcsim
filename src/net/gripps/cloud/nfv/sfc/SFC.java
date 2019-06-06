package net.gripps.cloud.nfv.sfc;


import net.gripps.clustering.common.aplmodel.CustomIDSet;

import java.io.*;
import java.util.*;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/03.
 * サービスファンクションチェイン(SFC: Service Function Chain)
 * を表すクラスです．SFC自体がVNFを継承しているので，複数SFCをスケジュールする場合，
 * あたかも1つのVNFであるかのように扱うことが可能です．
 */
public class SFC  extends VNF implements Serializable {


    /**
     * VNFのMap
     */
    private HashMap<Long, VNF> vnfMap;

    /**
     * VNFクラスタのMap
     */
    private HashMap<Long, VNFCluster> VNFClusterMap;

    /**
     * SFCのID．
     */
    private Long sfcID;

    /**
     * 応答時間
     */
    private double responseTime;

    /**
     * 開始時刻
     */
    private double nfvStartTime;

    /**
     * 深さ
     */
    private int depth;

    /**
     * START VNF集合
     */
    private CustomIDSet startVNFSet;

    /**
     * END VNF集合
     */
    private CustomIDSet  endVNFSet;

    private long maxWorkload;

    private long minWorkload;

    private long totalWorkload;

    /**
     * レベル毎に格納された，VNF集合．
     * HSFCGeneratorでのみ使われる．
     */
    private ArrayList<HashMap<Long, VNF>> levelVNFSet;



    public SFC(int in_type, long in_maxweight, long in_aveweight, long in_minweight, long requiredMips,
               long requiredRecvBW, long requiredSendBW, String vCPUID, HashMap<Long, VNF> vnfMap,
               HashMap<Long, VNFCluster> VNFClusterMap, Long sfcID, double responseTime, double startTime) {
        super(in_type, in_maxweight, requiredMips, requiredRecvBW, requiredSendBW, vCPUID, -1);
        this.vnfMap = vnfMap;
        this.VNFClusterMap = VNFClusterMap;
        this.sfcID = sfcID;
        this.responseTime = responseTime;
        this.nfvStartTime = startTime;
        this.depth = 0;
        this.startVNFSet = new CustomIDSet();
        this.endVNFSet= new CustomIDSet();
        this.maxWorkload = 0;
        this.minWorkload = 0;
        this.totalWorkload = 0;
        this.levelVNFSet = new ArrayList<HashMap<Long, VNF>>();

    }

    public long getTotalWorkload() {
        return totalWorkload;
    }

    public void setTotalWorkload(long totalWorkload) {
        this.totalWorkload = totalWorkload;
    }

    public  synchronized Serializable deepCopy(){
        //System.gc();

        try{
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(this);
            out.close();
            byte[] bytes = bout.toByteArray();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object newObject = in.readObject();
            in.close();
            return (Serializable) newObject;
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * インデックスは1から
     * @param vnfmap_in
     * @return
     */
    public long createNewID(HashMap<Long, VNF> vnfmap_in) {
        int size = vnfmap_in.size();
        //System.out.println("サイズ: "+size);
        if (size < 1) {
            return (long) 1;
        }else{
            return (long)(size+1);
        }
    }

    /**
     *
     * @param vnf
     * @return
     */
    public VNF  addVNF(VNF vnf) {
        long newid = this.createNewID(this.vnfMap);

        Vector<Long> pID = new Vector<Long>();

        // pID = (Vector<Long>)this.getIDVector().clone();

        pID.add(this.sfcID);
        Long nID = new Long(newid);
        pID.add(nID);
        //System.out.println("new ID: "+newid);
        if(this.maxWorkload <= vnf.getWorkLoad()){
            this.maxWorkload = vnf.getWorkLoad();
        }

        if(this.minWorkload >= vnf.getWorkLoad()){
            this.minWorkload = vnf.getWorkLoad();
        }

        vnf.setIDVector(pID);
        vnf.setParentTask(this.getIDVector());
        this.vnfMap.put(nID, vnf);

        return vnf;

    }

    public VNF  addVNF(VNF vnf, long id) {
        long newid = id;

        Vector<Long> pID = new Vector<Long>();

        // pID = (Vector<Long>)this.getIDVector().clone();
        pID.add(new Long(1));
        Long nID = new Long(newid);
        pID.add(nID);
        //System.out.println("new ID: "+newid);
        if(this.maxWorkload <= vnf.getWorkLoad()){
            this.maxWorkload = vnf.getWorkLoad();
        }

        if(this.minWorkload >= vnf.getWorkLoad()){
            this.minWorkload = vnf.getWorkLoad();
        }

        vnf.setIDVector(pID);
        vnf.setParentTask(this.getIDVector());
        this.vnfMap.put(nID, vnf);

        return vnf;

    }

    public VNF findVNFByLastID(Long id){

        return this.vnfMap.get(id);

    }

    /**
     * 指定タイプと同じタイプのVNF集合を取得します．
     * @param type
     * @return
     */
    public HashMap<Long, VNF> getVNFSet(int type){
        Iterator<VNF> vIte =  this.vnfMap.values().iterator();
        HashMap<Long, VNF> retMap = new HashMap<Long, VNF>();

        while(vIte.hasNext()){
            VNF vnf = vIte.next();
            if(vnf.getType() == type){
                retMap.put(vnf.getIDVector().get(1), vnf);
            }else{

            }
        }
        return retMap;

    }

    public VNFCluster findVNFCluster(Long id){
        return this.VNFClusterMap.get(id);
    }

    public HashMap<Long, VNF> getVnfMap() {
        return vnfMap;
    }

    public void setVnfMap(HashMap<Long, VNF> vnfMap) {
        this.vnfMap = vnfMap;
    }

    public Long getSfcID() {
        return sfcID;
    }

    public void setSfcID(Long sfcID) {
        this.sfcID = sfcID;
    }

    public double getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(double responseTime) {
        this.responseTime = responseTime;
    }

    public HashMap<Long, VNFCluster> getVNFClusterMap() {
        return VNFClusterMap;
    }

    public void setVNFClusterMap(HashMap<Long, VNFCluster> VNFClusterMap) {
        this.VNFClusterMap = VNFClusterMap;
    }

    public double getNfvStartTime() {
        return nfvStartTime;
    }

    public void setNfvStartTime(double nfvStartTime) {
        this.nfvStartTime = nfvStartTime;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
    }

    public CustomIDSet getStartVNFSet() {
        return startVNFSet;
    }

    public void setStartVNFSet(CustomIDSet startVNVSet) {
        this.startVNFSet = startVNVSet;
    }

    public CustomIDSet getEndVNFSet() {
        return endVNFSet;
    }

    public void setEndVNFSet(CustomIDSet endVNFSet) {
        this.endVNFSet = endVNFSet;
    }

    public long getMaxWorkload() {
        return maxWorkload;
    }

    public void setMaxWorkload(long maxWorkload) {
        this.maxWorkload = maxWorkload;
    }

    public long getMinWorkload() {
        return minWorkload;
    }

    public void setMinWorkload(long minWorkload) {
        this.minWorkload = minWorkload;
    }

    public ArrayList<HashMap<Long, VNF>> getLevelVNFSet() {
        return levelVNFSet;
    }

    public void setLevelVNFSet(ArrayList<HashMap<Long, VNF>> levelVNFSet) {
        this.levelVNFSet = levelVNFSet;
    }
}
