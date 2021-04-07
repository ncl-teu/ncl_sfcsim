package net.gripps.cloud.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.mapreduce.core.IMapReduce;
import net.gripps.cloud.mapreduce.datamodel.*;
import net.gripps.cloud.nfv.sfc.StartTimeComparator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.environment.CPU;

import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Vector;

/**
 * Created by kanemih on 2018/11/01.
 * CPUを継承するクラスです．
 * CPUクラスに，さらに所属するVMを表す機能が追加されます．
 */
public class VCPU extends CPU  implements Runnable, IMapReduce {

    /**
     * vCPUのIDです．dc_id^host_id^cpu_id^core_id^number
     * から構成されます．
     */
    private String prefix;

    /**
     * コアのPrefix
     */
    private String corePrefix;

    /**
     *
     */
    private HashMap<String, Long> prefixMap;

    /**
     * このvCPUが所属するVMのID
     */
    private String VMID;

    /**
     * MIPSの定義
     */
    private long mips;


    /**
     * 占有されているMIPS
     */
    private long usedMips;




    /**
     * VNFのリスト．最初から持っている場合もあれば，あとで追加される場合もある．
     */
   // private HashMap<Long, VNF> vnfMap;


    /**
     * 割り当てられたVNFのキュー
     */
    private PriorityQueue<VNF> vnfQueue;


    private double finishTimeAtClusteringPhase;

    private  long  remainedCapacity;

    private boolean isFake;


    //
    private HashMap<Long, VNF> dlVNFMap;

    public VCPU(){
        this.isFake = true;

    }



    @Override
    public void run() {

    }

    public VCPU(Long id, long speed, Vector<Long> assignedTaskList, Vector<Long> scheduledTaskList,
                String prefix, String cPrefix, HashMap<String, Long> prefixMap, String  vmID, long mips, long usedMips) {
        super(id, speed, assignedTaskList, scheduledTaskList);
        this.prefix = prefix;
        this.corePrefix = cPrefix;
        this.prefixMap = prefixMap;
        VMID = vmID;
        this.mips = mips;
        this.usedMips = usedMips;
       // this.vnfMap = vnfMap;
        this.setSpeed(mips);
        //this.assignedVNFSet = new CustomIDSet();
        this.vnfQueue = new PriorityQueue<VNF>(5, new StartTimeComparator());
        this.finishTimeAtClusteringPhase = 0d;

        this.remainedCapacity = mips * 10000;
        this.isFake = false;


        this.dlVNFMap = new HashMap<Long, VNF>();


    }

    public VCPU(String prefix, String cPrefix, HashMap<String, Long> prefixMap, String  vmID, long mips, long usedMips) {
        this.prefix = prefix;
        this.corePrefix = cPrefix;
        this.prefixMap = prefixMap;
        VMID = vmID;
        this.mips = mips;
        this.setSpeed(mips);
        this.usedMips = usedMips;
       // this.vnfMap = vnfMap;
      //  this.assignedVNFSet = new CustomIDSet();
        this.vnfQueue = new PriorityQueue<VNF>(5, new StartTimeComparator());
        this.finishTimeAtClusteringPhase = 0d;
        this.isFake = false;


        this.dlVNFMap = new HashMap<Long, VNF>();


    }

    public HashMap<Long, VNF> getDlVNFMap(){
        return dlVNFMap;
    }

    public void setDlVNFMap(HashMap<Long, VNF> dlVNFMap){
        this.dlVNFMap = dlVNFMap;
    }





    @Override
    public OutputSplit mapProcess(InputSplit is) {
        return null;
    }

    @Override
    public MergedSplit collectProcess(OutputSplit os) {
        return null;
    }

    @Override
    public SpillSplit spillProcess(MergedSplit ms) {
        return null;
    }

    @Override
    public MergedFileSplit mergeProcess(SpillSplit ss) {
        return null;
    }

    @Override
    public void shuffleSendProcess(MergedFileSplit mfs) {

    }

    @Override
    public ShuffleFileSplit shuffleReceiveProcess() {
        return null;
    }

    @Override
    public ReduceOutputFile reduceProcess(ShuffleFileSplit sfs) {
        return null;
    }

    @Override
    public boolean sendReduceOutputFile(ReduceOutputFile rof) {
        return false;
    }

    public boolean isFake() {
        return isFake;
    }

    public void setFake(boolean fake) {
        isFake = fake;
    }

    public String getCorePrefix() {
        return corePrefix;
    }

    public void setCorePrefix(String corePrefix) {
        this.corePrefix = corePrefix;
    }

    public PriorityQueue<VNF> getVnfQueue() {
        return vnfQueue;
    }

    public void setVnfQueue(PriorityQueue<VNF> vnfQueue) {
        this.vnfQueue = vnfQueue;
    }

    public HashMap<String, Long> getPrefixMap() {
        return prefixMap;
    }

    public void setPrefixMap(HashMap<String, Long> prefixMap) {
        this.prefixMap = prefixMap;
    }

    public long getUsedMips() {
        return usedMips;
    }

    public void setUsedMips(long usedMips) {
        this.usedMips = usedMips;
    }

    public String getVMID() {

        return VMID;
    }

    public Long getVCPUID(){
        return this.prefixMap.get(CloudUtil.ID_VCPU);
    }


    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String vCPUID) {
        this.prefix = vCPUID;
    }

    public void setVMID(String VMID) {
        this.VMID = VMID;
    }

    public long getMips() {
        return mips;
    }

    public void setMips(long mips) {
        this.mips = mips;
    }

    public double getFinishTimeAtClusteringPhase() {
        return finishTimeAtClusteringPhase;
    }

    public void setFinishTimeAtClusteringPhase(double finishTimeAtClusteringPhase) {
        this.finishTimeAtClusteringPhase = finishTimeAtClusteringPhase;
    }

    public long getRemainedCapacity() {
        return remainedCapacity;
    }

    public void setRemainedCapacity(long remainedCapacity) {
        this.remainedCapacity = remainedCapacity;
    }



    /**
    public HashMap<Long, VNF> getVnfMap() {
        return vnfMap;
    }

    public void setVnfMap(HashMap<Long, VNF> vnfMap) {
        this.vnfMap = vnfMap;
    }
 **/
}
