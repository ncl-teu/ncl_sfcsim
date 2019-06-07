package net.gripps.cloud.mapreduce.scheduling;

import net.gripps.cloud.mapreduce.core.FSHost;
import net.gripps.cloud.mapreduce.core.MRVCPU;
import net.gripps.cloud.mapreduce.datamodel.KeyElement;
import net.gripps.cloud.mapreduce.datamodel.MergedFileSplit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/09.
 */
public interface IMRScheduling {

    /**
     * InputFile内のInputSplitを送信する処理です．
     * 各InputSplitをどのMRVCPUへ送信するかも，
     * このメソッド内で定義します．
     * すべてのInputSplitをMRVCPUのキューへ格納
     * したら終了させてください．
     */
    public void sendInputSplits(FSHost fsHost, ArrayList<MRVCPU> mapperList) ;


    /**
     * Mergedファイルを，特定基準のもので分割する．
     * @param mfs
     * @return
     */
    public LinkedList<HashMap<Long, KeyElement>> divideMergedFile(MergedFileSplit mfs, long reducerNum);



}
