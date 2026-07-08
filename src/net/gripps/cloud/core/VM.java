package net.gripps.cloud.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.environment.CPU;
import net.gripps.environment.Machine;
import net.gripps.cloud.nfv.sfc.VNF;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * Created by kanemih on 2018/11/01.
 */
public class VM /*extends Machine*/ implements Serializable {
    /**
     * vCPUのリスト
     */
    private HashMap<String, VCPU> vCPUMap;

    /**
     * メモリのサイズ(MB)
     */
    private long ramSize;

    /**
     * このVMのID(文字列）
     */
    private String VMID;

    /**
     * このVMが属するホストID
     */
    private String hostID;

    /**
     * オリジナルVMのID
     * もしこのVMが，元々のVMに対してインスタンス生成されたものであれば，
     * オリジナルのものと同じはず．
     */
    private String  orgVMID;

    private String ipAddr;

    private HashSet<Integer> typeSet;

    /**
     * 镜像类型的“真正可用时刻”。
     * 未完成下载的镜像只记录这里，不立刻当作 typeSet 已可用。
     */
    private HashMap<Integer, Double> imageReadyTimeMap;

    /**
     * VM単位の画像ダウンロードキュー。
     * 同一VMからのダウンロードはこのキューで直列化する。
     */
    private LinkedList<VNF> dlQueue;




    public VM(String  ID, String in_hostID,   HashMap<String, VCPU> vCPUMap, long ramSize, String orgVMID) {
        //super(machineID, cpuMap, num);

        this.VMID = ID;
        this.hostID = in_hostID;
        this.vCPUMap = vCPUMap;
        this.ramSize = ramSize;
        this.orgVMID = orgVMID;
        this.ipAddr = null;
        this.typeSet = new HashSet<Integer>();
        this.imageReadyTimeMap = new HashMap<Integer, Double>();
        this.dlQueue = new LinkedList<VNF>();
        this.dlQueue = new LinkedList<VNF>();

    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public String getVMID() {
        return VMID;
    }

    public void setVMID(String VMID) {
        this.VMID = VMID;
    }

    public String getHostID() {
        return hostID;
    }

    public void setHostID(String hostID) {
        this.hostID = hostID;
    }

    public String getOrgVMID() {
        return orgVMID;
    }

    public void setOrgVMID(String orgVMID) {
        this.orgVMID = orgVMID;
    }

    /**
     * 入力となるVMと同一VMを複製する．
     * @return
     */
    public VM replicate(){
        System.gc();
        try{
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(this);
            out.close();
            byte[] bytes = bout.toByteArray();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object newObject = in.readObject();
            in.close();
            VM newVM = (VM) newObject;

            return newVM;

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public HashSet<Integer> getTypeSet() {
        return typeSet;
    }

    public void setTypeSet(HashSet<Integer> typeSet) {
        this.typeSet = typeSet;
    }

    public boolean containsType(int type){
        return this.typeSet.contains(new Integer(type));
    }

    public void registerImageReadyTime(int type, double readyTime) {
        Integer key = Integer.valueOf(type);
        this.imageReadyTimeMap.put(key, Double.valueOf(readyTime));
        if (readyTime <= 0.0d) {
            this.typeSet.add(key);
        }
    }

    public double getImageReadyTime(int type) {
        Double ready = this.imageReadyTimeMap.get(Integer.valueOf(type));
        if (ready == null) {
            return NFVUtil.MAXValue;
        }
        return ready.doubleValue();
    }

    public LinkedList<VNF> getDlQueue() {
        return dlQueue;
    }

    public void setDlQueue(LinkedList<VNF> dlQueue) {
        this.dlQueue = dlQueue;
    }

    public void addDLQueue(VNF vnf) {
        this.dlQueue.add(vnf);
    }

    public double getDlQueueFinishTime() {
        if (this.dlQueue == null || this.dlQueue.isEmpty()) {
            return 0.0d;
        }
        return this.dlQueue.getLast().getDlFinishTime();
    }

    /**
     * このVM自体のCPU使用率を計算します．
     * @return
     */
    public double getCPUUsage(){
        int vCPUNum = this.vCPUMap.size();
        Iterator<VCPU> vIte = this.vCPUMap.values().iterator();
        long totalMips = 0;
        long totalUsedMips =0 ;
        while(vIte.hasNext()){
            VCPU vCPU = vIte.next();
            totalMips += vCPU.getMips();
            totalUsedMips += vCPU.getUsedMips();
        }
        return CloudUtil.getRoundedValue((double)(100*totalUsedMips/totalMips));

    }

    public HashMap<String, VCPU> getvCPUMap() {
        return vCPUMap;
    }

    public void setvCPUMap(HashMap<String, VCPU> vCPUMap) {
        this.vCPUMap = vCPUMap;
    }

    public long getRamSize() {
        return ramSize;
    }

    public void setRamSize(long ramSize) {
        this.ramSize = ramSize;
    }
}
