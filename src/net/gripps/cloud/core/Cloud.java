package net.gripps.cloud.core;

import net.gripps.cloud.mapreduce.core.FSHost;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/08.
 */
public class Cloud implements Serializable, Cloneable{

    protected  Long id;

    protected HashMap<Long, ComputeHost> computeHostMap;

    protected long bw;

    /**
     * ファイルシステムとなるホスト
     * MapReduceで使う
     *
     */
    protected FSHost fsHost;

    public Cloud(Long id, HashMap<Long, ComputeHost> computeHostMap, long bw) {
        this.id = id;
        this.computeHostMap = computeHostMap;
        this.bw = bw;
        this.fsHost = null;
    }

    public Cloud(){
        this.id = new Long(-1);
        this.computeHostMap = new HashMap<Long, ComputeHost>();
        this.bw = -1;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public HashMap<Long, ComputeHost> getComputeHostMap() {
        return computeHostMap;
    }

    public void setComputeHostMap(HashMap<Long, ComputeHost> computeHostMap) {
        this.computeHostMap = computeHostMap;
    }

    public long getBw() {
        return bw;
    }

    public void setBw(long bw) {
        this.bw = bw;
    }

    public FSHost getFsHost() {
        return fsHost;
    }

    public void setFsHost(FSHost fsHost) {
        this.fsHost = fsHost;
    }
}
