package net.gripps.cloud.core;

import net.gripps.environment.CPU;
import net.gripps.environment.Machine;

import java.util.HashMap;
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
}



