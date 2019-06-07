package net.gripps.cloud.mapreduce.datamodel;

import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 * InputSplitを構成するデータ単位です．
 * MapReduceでは，このレコード単位で読み書きが行われます．
 * すなわち，
 * レコードの処理のworkload: 一定とする．なぜならレコードサイズは同一だから．
 * レコードから出力されるデータサイズ: キーの種類数 x ρ（固定値）とする．
 * キーの種類数は，乱数で割り振る．
 */
public class InputRecord {

    protected long recordID;

    /**
     * レコードサイズ
     */
    protected double  recordSize;

    /**
     * 当該レコード内にあるキーリスト
     * key1->何個
     * key2->何個
     * といった具合？
     */
    protected HashMap<Long, Long> keyCountMap;

    protected long totalWeight;


    /**
     * 仕事量
     */
    protected long workLoad;

    public InputRecord(long recordID, double recordSize, HashMap<Long, Long> keyCountMap, long workLoad) {
        this.recordID = recordID;
        this.recordSize = recordSize;
        this.keyCountMap = keyCountMap;
        this.workLoad = workLoad;
    }

    public long getWorkLoad() {
        return workLoad;
    }

    public void setWorkLoad(long workLoad) {
        this.workLoad = workLoad;
    }

    public long getRecordID() {
        return recordID;
    }

    public void setRecordID(long recordID) {
        this.recordID = recordID;
    }

    public double  getRecordSize() {
        return recordSize;
    }

    public void setRecordSize(double  recordSize) {
        this.recordSize = recordSize;
    }

    public HashMap<Long, Long> getKeyCountMap() {
        return keyCountMap;
    }

    public void setKeyCountMap(HashMap<Long, Long> keyCountMap) {
        this.keyCountMap = keyCountMap;
    }

    public long getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(long totalWeight) {
        this.totalWeight = totalWeight;
    }
}
