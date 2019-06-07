package net.gripps.cloud.mapreduce.datamodel;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 *      * 出力レコードサイズ: k1: w1, k2:w2....とあるが，
 *      * 出力レコード自体のサイズは，size(out)=入力レコードサイズ x ρとする．
 *      * このうち，k_iの分のサイズは，size(out) x w(k_i)/{w(k_1)+w(k_2)+...}
 */
public class OutputRecord extends InputRecord implements Serializable {

    protected HashMap<Long, KeyElement> keyElementMap;

    public OutputRecord(long recordID, double recordSize, HashMap<Long, Long> keyCountMap, long workLoad) {
        super(recordID, recordSize, keyCountMap, workLoad);
        this.keyElementMap = new HashMap<Long, KeyElement>();

    }

    public HashMap<Long, KeyElement> getKeyElementMap() {
        return keyElementMap;
    }

    public void setKeyElementMap(HashMap<Long, KeyElement> keyElementMap) {
        this.keyElementMap = keyElementMap;
    }
}
