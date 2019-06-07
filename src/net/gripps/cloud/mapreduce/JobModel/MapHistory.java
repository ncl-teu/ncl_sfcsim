package net.gripps.cloud.mapreduce.JobModel;

import java.io.Serializable;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 * 1つのMapタスクに関する履歴情報です．
 */
public class MapHistory implements Serializable {

    /**
     * InputSplitが到着した時刻
     */
    private long  dataArrivalTime;

    /**
     * Map処理の開始時刻
     */
    private long mapStartTime;

    /**
     * Map処理の終了時刻
     */
    private long mapFinishTime;



}
