package net.gripps.cloud.mapreduce.core;

import net.gripps.cloud.mapreduce.datamodel.*;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 * Core / vCPUに対して実装すべき機能です．
 */
public interface IMapReduce {

    /**
     * Map処理
     * @param is
     * @return 複数のOutputRecordから構成されるOutputSplit
     */
    public OutputSplit mapProcess(InputSplit is);

    /**
     * Collect処理
     * OutputSplit (複数のOutputRecord）を入力として，
     * キーでグループ化
     * 各出力レコード単位でグループ化(partitinon)して
     * バッファに蓄積する（serialization)するので，
     * 出力レコード数 x (平均partition + 平均serializaiton)だけかかる．
     * その後，一定サイズをもったMPごとに分割される．
     * つまり，MPあたり複数のキーを持つ．
     */
    public MergedSplit collectProcess(OutputSplit os);

    /**
     *    MPをそのままspillファイルとして，指定サイズ単位で書き出す．
     *    spillファイルあたり，複数のキー出力から構成される
     * @param ms
     * @return
     */
    public SpillSplit spillProcess(MergedSplit ms);

    /**
     * Spillファイルを入力として，spillファイル
     * どうしを結合して，一つのMergedFile(MergedFileSplit)を生成する．
     * @param ss
     */
    public MergedFileSplit  mergeProcess(SpillSplit ss);

    /**
     * MergedFileSplitをキー単位に分割して各Reducerへ送る
     * 実際には，MergedPartition(MP)単位で送る．
     */
    public void shuffleSendProcess(MergedFileSplit mfs);


    /**
     * キューに到着したMPを取り出して，
     * キー毎にデータを分割して，それを複数の
     * partitionfileとして生成する．
     * 1partition file (PF)= 0.xキー
     * それから，同じキーのPFどうしを合わせてShuffleFileたち(=ShuffleFileSplit)
     * を作る．
     */
    public ShuffleFileSplit  shuffleReceiveProcess();

    /**
     * Shuffleフェーズであり，ファイルを出力する．
     * @param sfs
     * @return
     */
    public ReduceOutputFile reduceProcess(ShuffleFileSplit sfs);

    /**
     * 出力ファイルを(H)DFSへ送信する処理です．
     * @param rof
     * @return
     */
    public boolean sendReduceOutputFile(ReduceOutputFile rof);


}

