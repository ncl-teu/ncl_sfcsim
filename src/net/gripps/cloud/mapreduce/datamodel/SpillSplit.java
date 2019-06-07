package net.gripps.cloud.mapreduce.datamodel;

import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01.
 * 複数のSpillファイルから構成されます．
 */
public class SpillSplit {

    private LinkedList<SpillFile> sfList;

    public SpillSplit(LinkedList<SpillFile> sfList) {
        this.sfList = sfList;
    }

    public LinkedList<SpillFile> getSfList() {
        return sfList;
    }

    public void setSfList(LinkedList<SpillFile> sfList) {
        this.sfList = sfList;
    }
}
