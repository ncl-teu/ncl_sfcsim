package net.gripps.cloud.mapreduce.datamodel;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01.
 */
public class MergedFileSplit implements Serializable {

    /**
     * 一つのファイルを生成する．
     */
    protected HashMap<Long, KeyElement> keyElementMap;

    /**
     * InputSplitと同じIDを指す．
     */
    protected long id;

    /**
     * ファイルサイズ
     */
    protected double size;

    public MergedFileSplit(HashMap<Long, KeyElement> keyElementMap, long id, double size) {
        this.keyElementMap = keyElementMap;
        this.id = id;
        this.size = size;
    }

    public HashMap<Long, KeyElement> getKeyElementMap() {
        return keyElementMap;
    }

    public void setKeyElementMap(HashMap<Long, KeyElement> keyElementMap) {
        this.keyElementMap = keyElementMap;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }
}
