package net.gripps.cloud.core;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/17.
 */
public class Core implements Serializable {

    /**
     * スレッド数．もしHyper Threadingがonであれば2．
     * そうでなければ1となる．
     */
    private int threadNum;

    /**
     * 想定される使用率の上限
     */
    private int maxUsage;

    /**
     * MIPS
     */
    private long mips;

    /**
     * コアのID(使いみちなし？）
     */
    private Long coreID;

    /**
     * 帯域幅（使いみちなし？）
     */
    private long bw;

    /**
     * VCPUのMap
     */
    private HashMap<Long, VCPU> vCPUMap;

    /**
     * コアのprefix（文字列）
     */
    private String prefix;

    /**
     *
     */
    private HashMap<String, Long> prefixMap;


    public Core(String prefix, int threadNum, long mips, Long coreID,  HashMap<Long, VCPU> vCPUMap, int usage) {
        this.prefix = prefix;
        this.threadNum = threadNum;
        this.mips = mips;
        this.coreID = coreID;
        this.bw = bw;
        this.vCPUMap = vCPUMap;
        this.maxUsage = usage;

    }

    public Core(int threadNum, long mips, Long coreID, long bw, HashMap<Long, VCPU> vCPUMap, String prefix, HashMap<String, Long> prefixMap, int usage) {
        this.threadNum = threadNum;
        this.mips = mips;
        this.coreID = coreID;
        this.bw = bw;
        this.vCPUMap = vCPUMap;
        this.prefix = prefix;
        this.prefixMap = prefixMap;
        this.maxUsage = usage;
    }

    public int  getMaxUsage() {
        return maxUsage;
    }

    public void setMaxUsage(int  maxUsage) {
        this.maxUsage = maxUsage;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public HashMap<Long, VCPU> getvCPUMap() {
        return vCPUMap;
    }

    public void setvCPUMap(HashMap<Long, VCPU> vCPUMap) {
        this.vCPUMap = vCPUMap;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public long getMips() {
        return mips;
    }

    public void setMips(long mips) {
        this.mips = mips;
    }

    public Long getCoreID() {
        return coreID;
    }

    public void setCoreID(Long coreID) {
        this.coreID = coreID;
    }

    public long getBw() {
        return bw;
    }

    public void setBw(long bw) {
        this.bw = bw;
    }

    public HashMap<String, Long> getPrefixMap() {
        return prefixMap;
    }

    public void setPrefixMap(HashMap<String, Long> prefixMap) {
        this.prefixMap = prefixMap;
    }
}
