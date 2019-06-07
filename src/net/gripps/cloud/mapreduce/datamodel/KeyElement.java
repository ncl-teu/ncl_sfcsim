package net.gripps.cloud.mapreduce.datamodel;

import java.io.Serializable;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/11.
 * OutputRecordを構成する出力キー要素を表します．
 */
public class KeyElement implements Serializable {

    /**
     * 当該キー要素のデータサイズ
     */
    private double size;

    /**
     * キー
     */
    private long key;

    /**
     * キーの重み
     */
    private long weight;

    double comTime;

    String fromPrefix;

    public KeyElement(long key, long weight) {
        this.key = key;
        this.weight = weight;
        this.comTime = 0.0d;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        this.key = key;
    }

    public long getWeight() {
        return weight;
    }

    public void setWeight(long weight) {
        this.weight = weight;
    }

    public double getComTime() {
        return comTime;
    }

    public void setComTime(double comTime) {
        this.comTime = comTime;
    }

    public String getFromPrefix() {
        return fromPrefix;
    }

    public void setFromPrefix(String fromPrefix) {
        this.fromPrefix = fromPrefix;
    }
}
