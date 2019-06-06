package net.gripps.cloud.core;

import net.gripps.environment.CPU;

import java.util.HashMap;
import java.util.Vector;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/17.
 */
public class CloudCPU extends CPU {

    /**
     *
     */
    private long mips;



    /**
     *
     */
    private HashMap<Long, Core> coreMap;

    /**
     *
     */
    private String prefix;


    private HashMap<String, Long> prefixMap;

    public CloudCPU(Long id, long speed, Vector<Long> assignedTaskList, Vector<Long> scheduledTaskList,  long mips, HashMap<Long, Core> coreMap, String prefix, HashMap<String, Long> prefixMap) {
        super(id, speed, assignedTaskList, scheduledTaskList);
        this.mips = mips;
        this.coreMap = coreMap;
        this.prefix = prefix;
        this.prefixMap = prefixMap;
    }

    public long getMips() {
        return this.mips;
    }

    public void setMips(long mips) {
        this.mips = mips;
    }


    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }



    public HashMap<Long, Core> getCoreMap() {
        return coreMap;
    }

    public void setCoreMap(HashMap<Long, Core> coreMap) {
        this.coreMap = coreMap;
    }

    public HashMap<String, Long> getPrefixMap() {
        return prefixMap;
    }

    public void setPrefixMap(HashMap<String, Long> prefixMap) {
        this.prefixMap = prefixMap;
    }

}
