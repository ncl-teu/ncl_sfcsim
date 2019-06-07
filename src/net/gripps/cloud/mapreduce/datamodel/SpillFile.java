package net.gripps.cloud.mapreduce.datamodel;

import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01.
 */
public class SpillFile {

    private HashMap<Long, KeyElement> keyElemSet;

    public SpillFile(HashMap<Long, KeyElement> keyElemSet) {
        this.keyElemSet = keyElemSet;
    }

    public HashMap<Long, KeyElement> getKeyElemSet() {
        return keyElemSet;
    }

    public void setKeyElemSet(HashMap<Long, KeyElement> keyElemSet) {
        this.keyElemSet = keyElemSet;
    }
}
