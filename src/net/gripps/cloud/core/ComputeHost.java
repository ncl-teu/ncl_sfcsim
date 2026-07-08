package net.gripps.cloud.core;

import net.gripps.environment.CPU;
import net.gripps.environment.Machine;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/01.
 */
public class ComputeHost extends Machine {

    /**
     * VMのMapです．同一VMで複製した場合も，別個のVMとして扱います．
     * ただし，VM内に，「オリジナルVMID」を保持させているので，どのVMからの複製かは
     * わかります．
     */
    private HashMap<String, VM> vmMap;


    /**
     * 当該ホストが属するデータセンターID
     */
    private Long dcID;

    /**
     * このホストのprefix（文字列）
     */
    private String prefix;

    /**
     * このホストのIPアドレス
     */
    private String ipAddr;

    /**
     * Docker repositoryなど，ホスト単位で直列化したいDL処理の待ち行列。
     */
    private LinkedList<VNF> dlQueue;



    public ComputeHost(long machineID,
                       TreeMap<Long, CPU> cpuMap,
                       int num,
                       HashMap<String, VM> vmMap,
                       Long dcID,
                       String p,
                       long bw)
    {
        super(machineID, cpuMap, num);
        this.vmMap = vmMap;
        this.dcID = dcID;
        this.prefix =p;
        this.setBw(bw);
        this.ipAddr = null;
        this.dlQueue = new LinkedList<VNF>();

    }

    public HashMap<String, VM> getVmMap() {
        return vmMap;
    }

    public void setVmMap(HashMap<String, VM> vmMap) {
        this.vmMap = vmMap;
    }

    public Long getDcID() {
        return dcID;
    }

    public void setDcID(Long dcID) {
        this.dcID = dcID;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
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
}



