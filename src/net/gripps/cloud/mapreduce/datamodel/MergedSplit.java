package net.gripps.cloud.mapreduce.datamodel;

import net.gripps.cloud.mapreduce.datamodel.KeyElement;

import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01.
 * キーごとにグループ化されたデータを保持する．つまり
 * KeyElementの集合を持つ．
 */
public class MergedSplit {


    private HashMap<Long, KeyElement> groupedSet;

    private double  size;

    private long id;

    public MergedSplit(HashMap<Long, KeyElement> groupedSet, double  size, long id) {
        this.groupedSet = groupedSet;
        this.size = size;
        this.id = id;
    }

    public HashMap<Long, KeyElement> getGroupedSet() {
        return groupedSet;
    }

    public void setGroupedSet(HashMap<Long, KeyElement> groupedSet) {
        this.groupedSet = groupedSet;
    }

    public double  getSize() {
        return size;
    }

    public void setSize(double  size) {
        this.size = size;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
