package net.gripps.cloud.mapreduce.datamodel;

import java.io.Serializable;
import java.security.Key;
import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01.
 * //Reduceの
 */
public class ShuffleFileSplit implements Serializable {

    /**
     * マージされた集合
     * 複数キーからなる．
     */
    private HashMap<Long, KeyElement> sfMap;

    private long fileID;

    private long size;

    public ShuffleFileSplit(HashMap<Long, KeyElement> sfMap, long fileID, long size) {
        this.sfMap = sfMap;
        this.fileID = fileID;
        this.size = size;
    }

    public ShuffleFileSplit() {
        this.sfMap = new HashMap<Long, KeyElement>();
        this.fileID = -1L;
        this.size = 0L;

    }

    public HashMap<Long, KeyElement> getSfMap() {
        return sfMap;
    }

    public void setSfMap(HashMap<Long, KeyElement> sfMap) {
        this.sfMap = sfMap;
    }

    public long getFileID() {
        return fileID;
    }

    public void setFileID(long fileID) {
        this.fileID = fileID;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
