package net.gripps.cloud.nfv.sfc;


import java.io.Serializable;
import java.util.Comparator;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 */
public class StartTimeComparator implements Comparator, Serializable {

    @Override
    public int compare(Object o1, Object o2) {
        //優先度の小さい順にソートする．
        // long value = ((AbstractTask)o1).getPriorityBlevel() - ((AbstractTask)o2).getPriorityBlevel();
        VNF m1 = (VNF)o1;
        VNF m2 = (VNF)o2;
        if(m1.getStartTime() > m2.getStartTime()){
            return 1;
        }
        if(m1.getStartTime() < m2.getStartTime()){
            return -1;
        }

        return 0;

    }
}
