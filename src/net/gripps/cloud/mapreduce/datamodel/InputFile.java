package net.gripps.cloud.mapreduce.datamodel;

import net.gripps.cloud.mapreduce.MRUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/30.
 */
public class InputFile implements Serializable {

    /**
     * ファイルID
     */
    private long fileID;

    /**
     * ファイルサイズ(MB)
     */
    private long size;

    /**
     * InputSplitのリスト
     * これは，後で生成されることになる．
     */
    private LinkedList<InputSplit> splitList;

    /**
     * キーの種類数
     */
    private long keyKindsNum;


    /**
     *  一度の分割に要する仕事量
     *  これは全inputsplitで同一とする．
     *
     */
   // private long splitWorkload;

    /**
     * InputSplit一つのサイズ(MB)
     * サイズが固定の場合だけ設定される．
     * dynamicの場合は-1が設定される．
     */
   // private long inputSplitSize;

    public InputFile(long id, long size, long kindNum) {
        this.fileID = id;
        this.size = size;
        this.splitList = new LinkedList<InputSplit>();
        this.keyKindsNum = kindNum;

       // this.splitWorkload = splitWorkload;
      //  this.inputSplitSize = inputSplitSize;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public LinkedList<InputSplit> getSplitList() {
        return splitList;
    }

    public void setSplitList(LinkedList<InputSplit> splitList) {
        this.splitList = splitList;
    }

    public long getFileID() {
        return fileID;
    }

    public void setFileID(long fileID) {
        this.fileID = fileID;
    }

    public long getKeyKindsNum() {
        return keyKindsNum;
    }

    public void setKeyKindsNum(long keyKindsNum) {
        this.keyKindsNum = keyKindsNum;
    }
}
