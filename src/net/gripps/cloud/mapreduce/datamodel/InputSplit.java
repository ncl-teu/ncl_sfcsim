package net.gripps.cloud.mapreduce.datamodel;

import net.gripps.cloud.mapreduce.MRUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class InputSplit implements Serializable {


    protected long isID;

    /**
     * 当該inputsplitのサイズ．
     */
    protected long size;

    /**
     * レコードのリスト
     */
    protected LinkedList<InputRecord> irList;

    protected long keyKindsNum;

    protected double comTime;


    public InputSplit(long id, long size, long kinds) {
        this.isID = id;
        this.size = size;
        this.keyKindsNum = kinds;
        this.irList = new LinkedList<InputRecord>();
        this.comTime = 0;

        //レコードの生成をする．
        this.generateRecord();
    }



    public void generateRecord(){
        double rSize = MRUtil.size_of_inputrecord;
        //トータルレコード数
        int len = (int)Math.ceil(this.size / rSize);
        //レコード生成
        //レコード単位のループ
        for(int i=0;i<len;i++){
            long total = 0;
            //キーの種類を決める．

            HashMap<Long, Long> keyMap = new HashMap<Long, Long>();

            //キーごとのループ
            for(int j=0;j<this.keyKindsNum;j++){
                //keycountMap<Long, Long>=<何番目のキーか, 数>
                long keyWeight = MRUtil.genLong2(MRUtil.key_weight_min, MRUtil.key_weight_max,
                        MRUtil.dist_key_weight, MRUtil.dist_host_mips_mu);
                keyMap.put(new Long(j), new Long(keyWeight));
                total += keyWeight;
            }
            //インスタンス生成
            long workload = Double.valueOf(MRUtil.in_record_workload_perMB * rSize).longValue();
            InputRecord r = new InputRecord(new Long(i), rSize, keyMap, workload);
            r.setTotalWeight(total);
            this.irList.add(r);

        }
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getIsID() {
        return isID;
    }

    public void setIsID(long isID) {
        this.isID = isID;
    }

    public LinkedList<InputRecord> getIrList() {
        return irList;
    }

    public void setIrList(LinkedList<InputRecord> irList) {
        this.irList = irList;
    }

    public long getKeyKindsNum() {
        return keyKindsNum;
    }

    public void setKeyKindsNum(long keyKindsNum) {
        this.keyKindsNum = keyKindsNum;
    }

    public double getComTime() {
        return comTime;
    }

    public void setComTime(double comTime) {
        this.comTime = comTime;
    }
}
