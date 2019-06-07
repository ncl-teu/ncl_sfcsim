package net.gripps.cloud.mapreduce.datamodel;

import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 * 複数の出力レコードから構成されます．
 * 1つのMap処理の出力に相当します．
 */
public class OutputSplit extends InputSplit {
    protected LinkedList<OutputRecord> orList;

    public OutputSplit(long id, long size, long kinds) {
        super(id, size, kinds);
        this.orList = new LinkedList<OutputRecord>();
    }

    public LinkedList<OutputRecord> getOrList() {
        return orList;
    }

    public void setOrList(LinkedList<OutputRecord> orList) {
        this.orList = orList;
    }
}
