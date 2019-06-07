package net.gripps.cloud.mapreduce.core;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.mapreduce.MRMgr;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.datamodel.InputFile;
import net.gripps.cloud.mapreduce.datamodel.InputSplit;
import net.gripps.cloud.mapreduce.datamodel.ShuffleFileSplit;
import net.gripps.cloud.mapreduce.logger.MRLog;
import net.gripps.environment.CPU;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/30.
 * File System (FS)ホストです．
 * 入力データを最初に保持しているサーバです．
 * このサーバから各Mapperへデータが配布されます．
 * HadoopではHDFSに相当します．
 *
 */
public class FSHost extends ComputeHost {

    /**
     * HDDのサイズ(MB)
     */
    private long hddSize;

    /**
     * 逐次的に入力データをMapperへ送る場合はtrue
     * すべて，マルチキャストで送る場合はfalseとなる．
     */
    private boolean isSerialTransfer;

    /**
     * 入力データによるキュー
     */
    private ArrayList<InputFile> ifList;

    /**
     * InputSplitサイズが固定かどうか
     * (true: 動的 false: 固定）
     */
    private boolean isSizeDynamic;

    private LinkedBlockingQueue<ShuffleFileSplit> sfsQueue;

    private long currentKeyKindsNum;




    public FSHost(long machineID, TreeMap<Long, CPU> cpuMap, int num,
                  HashMap<String, VM> vmMap, Long dcID, String p, long bw,  boolean  mode) {
        super(machineID, cpuMap, num, vmMap, dcID, p, bw);
        this.hddSize = MRUtil.MAXValue;
        this.isSerialTransfer = mode;
        this.ifList = new ArrayList<InputFile>();
        this.sfsQueue = new LinkedBlockingQueue<ShuffleFileSplit>();

        this.currentKeyKindsNum = 0;
    }

    /**
     * 初期化処理です．入力ファイルの配置をします．
     */
    public void initialize(){
        long inputNum = MRUtil.num_of_inputfiles;
        //指定数分だけ入力ファイルを生成し，そしてリストへ入れる．
        int dyamicValue = MRUtil.is_size_dynamic;
        long splitSize = -1;
        if(dyamicValue ==0){
            this.isSizeDynamic = false;
            splitSize = MRUtil.size_of_inputsplit;
        }else{
            this.isSizeDynamic = true;
            splitSize = -1;
        }
        //入力ファイルごとのループ
        for(int i=0;i<inputNum;i++){
            //入力ファイルサイズを決める．
            long fileSize = MRUtil.genLong(MRUtil.size_of_inputfile_min, MRUtil.size_of_inputfile_max);
            //キーの種類数
            long keyKindsNum = MRUtil.genLong2(MRUtil.in_record_num_of_kinds_keys_min, MRUtil.in_record_num_of_kinds_keys_max,
                    MRUtil.dist_in_record_num_of_kinds_keys, MRUtil.dist_in_record_num_of_kinds_mu);
            //キー種類数をセットする．
            MRMgr.getIns().setKeyKindsNum(keyKindsNum);
//System.out.println("Initial Key Num:"+keyKindsNum);

            InputFile inFile = new InputFile(new Long(i), fileSize, keyKindsNum);
            //次に，inputSplitを生成する．
            if(!isSizeDynamic){

                //固定の場合の処理
                //inputsplitサイズが固定なので，個数がわかる．
                //レコードリストの生成
                //まずは，inputSplit生成する．
                InputSplit inSplit = new InputSplit(new Long(0), splitSize, keyKindsNum);
                //入力ファイルのsplitリストに追加する．
                inFile.getSplitList().add(inSplit);
                long leftSize = fileSize - splitSize;
                long totalSize = splitSize;
                long cnt = 1;
                while(leftSize >0){
                    //まずはサイズを決める．
                    if(leftSize >= splitSize){

                    }else{
                        splitSize = leftSize;
                    }
                    InputSplit inSplit2 = new InputSplit(new Long(cnt), splitSize, keyKindsNum);
                    //入力ファイルのsplitリストに追加する．
                    inFile.getSplitList().add(inSplit2);
                    totalSize += splitSize;
                    leftSize = fileSize - totalSize;
                    cnt ++;
                }


            }else{
//ここは後で実装する
            }


            MRMgr.getIns().setTotalInputSplitNum( inFile.getSplitList().size());

            //リストへ追加する．
            this.ifList.add(inFile);
        }

    }

    public boolean sendInputSplitSeq(InputFile file, InputSplit split, MRVCPU mapper){
        long   fsBW = -1L;
        int len = file.getSplitList().size();
        //逐次転送モード
        fsBW = this.getBw();
        //まずは送信時間を計算する．
        double comTime = this.calcComTime(fsBW, mapper, split);
        MRLog.getIns().log(",1:DFS->Mapper[seq]"+","+"mID,"+mapper.getPrefix() +","+ "comTime,"+comTime);

        //処理を指定時間行う．単位は秒．
        this.process(comTime);

        mapper.getIsQueue().offer(split);
        return true;
    }


    public  double calcComTime(double fsHostBW, MRVCPU target, InputSplit split){
        double retBW = -1L;
        long dcID = MRUtil.getIns().getDCID(target.getPrefix());
        long hostID = MRUtil.getIns().getHostID(target.getPrefix());

        Cloud cloud = MRMgr.getIns().getEnv().getDcMap().get(dcID);
        double  cloudBW = cloud.getBw();
        ComputeHost host = cloud.getComputeHostMap().get(hostID);
        FSHost fsHost = MRMgr.getIns().getEnv().getFsHost();
        long fsDCID =fsHost.getDcID();
        Cloud fsCloud = MRMgr.getIns().getEnv().getDcMap().get(fsDCID);

        retBW = Math.min(fsHost.getBw(), host.getBw());

        //同一クラウド内であれば，ホスト間のBW vs FSのBWということになる．
        if(fsDCID == dcID){
            // retBW = Math.min(fsHost.getBw(), host.getBw());
        }else{
            retBW = Math.min(retBW, fsCloud.getBw());
            retBW = Math.min(retBW, cloud.getBw());
        }
        retBW = Math.min(retBW, fsHostBW);
        double comTime = MRUtil.getRoundedValue((double)split.getSize() / (double) retBW);

        return MRUtil.getRoundedValue(comTime);

    }

    /**
     * 指定時間（秒）の間，待つことによってあたかも処理しているかのように見せる．
     * @param val
     * @return
     */
    public boolean process(double val){
        try{
            long formattedValue = (long)(val * 1000/MRUtil.time_speed_rate);
            Thread.sleep(formattedValue);
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;

    }

    public boolean processOutput(ShuffleFileSplit sfs){
        this.sfsQueue.offer(sfs);

        this.currentKeyKindsNum += sfs.getSfMap().size();
        //受信したファイルのキー数の合計値が最大になれば修了．
        long keyKindsNum = this.getIfList().get(0).getKeyKindsNum();
        //System.out.println("current:"+this.currentKeyKindsNum +"/"+keyKindsNum);

        if(this.currentKeyKindsNum >= keyKindsNum){
            MRMgr.getIns().setFinTimeStamp(System.currentTimeMillis());
            long duration = MRMgr.getIns().getFinTimeStamp() - MRMgr.getIns().getStartTimeStamp();
            double time = MRUtil.getRoundedValue(duration/(double)1000);
            // System.out.println("Time: "+MRUtil.getRoundedValue( time*MRUtil.time_speed_rate) + "(sec).");
            MRLog.getIns().log(",10: Finish at DFS:"+ ","+ "Total Time Duration(sec),"+MRUtil.getRoundedValue( time*MRUtil.time_speed_rate) );
            if(MRMgr.getIns().getTotalInputSplitNum() == MRMgr.getIns().getCurrentSentSplitNum()){
                String mode = null;
                if(MRUtil.mr_algorithm_provisioning_using == 0){
                    mode = "All nodes";
                }else{
                    mode = "Optimal Derivation of # of Mappers";
                }
                System.out.println("END of " + mode + " mode");
                System.out.println("# of Used mappers: "+MRMgr.getIns().getRetMapperMap().size() + "# of Used Reducers:"+MRMgr.getIns().getRetReducerMap().size());
                System.exit(1);
            }
            //
        }
        return true;
    }
    public long getHddSize() {
        return hddSize;
    }

    public void setHddSize(long hddSize) {
        this.hddSize = hddSize;
    }

    public boolean isSerialTransfer() {
        return isSerialTransfer;
    }

    public void setSerialTransfer(boolean serialTransfer) {
        isSerialTransfer = serialTransfer;
    }

    public ArrayList<InputFile> getIfList() {
        return ifList;
    }

    public void setIfList(ArrayList<InputFile> ifList) {
        this.ifList = ifList;
    }

    public boolean isSizeDynamic() {
        return isSizeDynamic;
    }

    public void setSizeDynamic(boolean sizeDynamic) {
        isSizeDynamic = sizeDynamic;
    }

    public LinkedBlockingQueue<ShuffleFileSplit> getSfsQueue() {
        return sfsQueue;
    }

    public void setSfsQueue(LinkedBlockingQueue<ShuffleFileSplit> sfsQueue) {
        this.sfsQueue = sfsQueue;
    }


}
