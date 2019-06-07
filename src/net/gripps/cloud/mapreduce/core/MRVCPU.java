package net.gripps.cloud.mapreduce.core;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.mapreduce.MRMgr;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.datamodel.*;
import net.gripps.cloud.mapreduce.logger.MRLog;
import net.gripps.cloud.mapreduce.time.HistoryInfo;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.clustering.common.aplmodel.CustomIDSet;

import java.security.Key;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class MRVCPU extends VCPU {

    /**
     * STATE_IN_RECEIVED;
     * STATE_MAP_START
     * STATE_MAP_FIN
     * STATE_COLLECT_START
     * STATE_COLLECT_FNIN
     * STATE_SPILL_START
     * STATE_SPILL_FIN
     * STATE_MERGE_START
     * STATE_MERGE_FIN
     * STATE_SHUFFLE_SEND
     */
    protected int status;

    protected LinkedBlockingQueue<InputSplit> isQueue;

    protected long currentISID;

    /**
     * Reducerとして管轄しているキー集合
     */
    protected CustomIDSet managedKeySet;

    protected LinkedBlockingQueue<KeyElement> sfQueue;

    /**
     * 履歴情報のリスト
     */
    protected LinkedList<HistoryInfo> historyList;

    /**
     * <キー, 受信もとmapper数></キー,>
     */
    protected HashMap<Long, Long> recMap;

    protected double maxSFComTime;

    protected ShuffleFileSplit sfs;





    public MRVCPU(Long id, long speed, Vector<Long> assignedTaskList, Vector<Long> scheduledTaskList, String prefix, String cPrefix, HashMap<String, Long> prefixMap, String vmID, long mips, long usedMips) {
        super(id, speed, assignedTaskList, scheduledTaskList, prefix, cPrefix, prefixMap, vmID, mips, usedMips);
        this.isQueue = new LinkedBlockingQueue<InputSplit>();
        this.historyList = new LinkedList<HistoryInfo>();
        this.managedKeySet = new CustomIDSet();
        this.sfQueue = new LinkedBlockingQueue<KeyElement>();
        this.recMap = new HashMap<Long, Long>();
        this.maxSFComTime = 0;
        this.sfs = new ShuffleFileSplit();



    }

    public MRVCPU(String prefix, String cPrefix, HashMap<String, Long> prefixMap, String vmID, long mips, long usedMips) {
        super(prefix, cPrefix, prefixMap, vmID, mips, usedMips);
        this.isQueue = new LinkedBlockingQueue<InputSplit>();
        this.historyList = new LinkedList<HistoryInfo>();
        this.managedKeySet = new CustomIDSet();
        this.sfQueue = new LinkedBlockingQueue<KeyElement>();
        this.recMap = new HashMap<Long, Long>();
        this.sfs = new ShuffleFileSplit();

    }

    public MRVCPU() {
        this.isQueue = new LinkedBlockingQueue<InputSplit>();
        this.historyList = new LinkedList<HistoryInfo>();
        this.managedKeySet = new CustomIDSet();
        this.sfQueue = new LinkedBlockingQueue<KeyElement>();
        this.recMap = new HashMap<Long, Long>();
        this.maxSFComTime = 0;
        this.sfs = new ShuffleFileSplit();


    }


    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!this.isQueue.isEmpty()) {
                //設定する．
                this.status = MRUtil.STATE_IN_RECEIVED;
                //キューの先頭を取り出す．
                InputSplit split = this.isQueue.poll();
                if (MRUtil.dfs_transfer_mode == 0) {
                    //Map処理
                    //通信時間を取り出して，その時間分，待つ．
                    double comTime = split.getComTime();
                    MRLog.getIns().log(",1:DFS->Mapper[pal]"+","+"mID,"+this.getPrefix() +","+ "comTime,"+comTime);

                    this.process(comTime);
                }
                //splitをMap処理への入力とする．
                OutputSplit os = this.mapProcess(split);
                //カウントアップ
                MRMgr.getIns().countUpSplitNum(1);

                //次は，OutputSplitをcollectProcessへ渡す．
                MergedSplit ms = this.collectProcess(os);

                //今度は，MSから複数のMP（複数キーから構成）を作って
                //複数のSpillファイルから構成されるSpillSplitを得る．
                SpillSplit ss = this.spillProcess(ms);

                //次は，mergeProcessによって一つのファイルとする．
                // spillファイルの数だけ，書き込み時間が増える．
                MergedFileSplit mss = this.mergeProcess(ss);


                //Shuffleフェーズ
                this.shuffleSendProcess(mss);

                //System.out.println("test");


            }

            //一方，Reducer側の処理．
            if(!this.sfQueue.isEmpty()){
                //キューから取り出す．
                KeyElement ele = this.sfQueue.poll();
                ShuffleFileSplit sfs;
                //パラレルモード
                if(MRUtil.dfs_transfer_mode == 0){
                    sfs = this.processSFQueue(ele, MRUtil.dfs_transfer_mode);
                }else{
                    //逐次モード
                    sfs = this.processSFQueue(ele, 1);

                }
                //当該Reducerですべてのデータを受信したら，入力ファイルを生成する．
                if(sfs != null){
                    //sfsを入力として，Reduce処理にうつる．
                    this.reduceProcess(sfs);
                    System.out.println("Reducer:"+this.getPrefix() + " Finished!!");
                    //break;
                    //最後にnullとする．
                    //this.sfs = null;
                }
            }
  /*
            switch(this.status){
                //Mapperとしての処理
                case MRUtil.STATE_IN_RECEIVED:

                    break;
                case MRUtil.STATE_MAP_START:
                    break;
                case MRUtil.STATE_MAP_FIN:
                    break;
                case MRUtil.STATE_COLLECT_START:
                    break;
                case MRUtil.STATE_COLLECT_FNIN:
                    break;
                case MRUtil.STATE_SPILL_START:
                    break;
                case MRUtil.STATE_MERGE_START:
                    break;
                case MRUtil.STATE_MERGE_FIN:
                    break;
                case MRUtil.STATE_SHUFFLE_SEND:
                    break;
                    //Reducerとしての処理
            }
   */
        }


    }

    public boolean process(double val) {
        try {
            long formattedValue = (long) (val * 1000 / MRUtil.time_speed_rate);
            Thread.sleep(formattedValue);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }


    /**
     * 出力レコードサイズ: k1: w1, k2:w2....とあるが，
     * 出力レコード自体のサイズは，size(out)=入力レコードサイズ x ρとする．
     * このうち，k_iの分のサイズは，size(out) x w(k_i)/{w(k_1)+w(k_2)+...}
     *
     * @param is
     * @return
     */
    @Override
    public OutputSplit mapProcess(InputSplit is) {

        //まずは自分を登録．
        MRMgr.getIns().getRetMapperMap().put(this.getPrefix(), this);

        Iterator<InputRecord> rIte = is.getIrList().listIterator();
        this.currentISID = is.getIsID();
        long totalWorkload = 0;
        OutputSplit os = new OutputSplit(is.getIsID(), -1, is.getKeyKindsNum());

        //レコードごとのループ
        while (rIte.hasNext()) {
            InputRecord ir = rIte.next();
            long totalWeight = ir.getTotalWeight();
            Iterator<Long> keyIte = ir.getKeyCountMap().keySet().iterator();
            totalWorkload += ir.getWorkLoad();

            //出力用レコード作成
            double outputRecordSize = ir.getRecordSize() * MRUtil.split_out_in_rate;
            OutputRecord or = new OutputRecord(ir.getRecordID(), outputRecordSize, ir.getKeyCountMap(), ir.getWorkLoad());
            while (keyIte.hasNext()) {
                Long key = keyIte.next();
                Long weight = ir.getKeyCountMap().get(key);
                KeyElement ele = new KeyElement(key, weight);
                //サイズを決める．
                double eleSize = MRUtil.getRoundedValue(outputRecordSize * weight / (double) totalWeight);
                ele.setSize(eleSize);
                or.getKeyElementMap().put(key, ele);
            }
            //OutputSplitに出力レコードを追加する．
            os.getOrList().add(or);
            os.setSize((long) (is.getSize() * MRUtil.split_out_in_rate));

        }
        double val = MRUtil.getRoundedValue(totalWorkload / (double) this.getSpeed());
        MRLog.getIns().log(",2:MapProcess"+","+"mID,"+this.getPrefix()+","+"Proc.Time,"+val);
        //処理をする．
        this.process(val);
        return os;

    }

    /**
     * /**
     * * Collect処理
     * * OutputSplit (複数のOutputRecord）を入力として，
     * * キーでグループ化これらを合体して，MS(MergedSplit)を生成する．
     * * 各出力レコード単位でグループ化(partitinon)して
     * * バッファに蓄積する（serialization)するので，
     * * 出力レコード数 x (平均partition + 平均serializaiton)だけかかる．
     * * その後，一定サイズをもったMPごとに分割される．
     * * つまり，MPあたり複数のキーを持つ．
     */
    @Override
    public MergedSplit collectProcess(OutputSplit os) {
        long recordNum = os.getOrList().size();
        Iterator<OutputRecord> orIte = os.getOrList().listIterator();
        HashMap<Long, KeyElement> groupedSet = new HashMap<Long, KeyElement>();
        double size = 0d;
        int max_key_kinds = 0;

        //出力レコード単位のループ
        while (orIte.hasNext()) {
            OutputRecord or = orIte.next();
            Iterator<KeyElement> keyIte = or.getKeyElementMap().values().iterator();
            if (max_key_kinds <= or.getKeyElementMap().size()) {
                max_key_kinds = or.getKeyElementMap().size();
            }
            //Keyelementごとのループ
            while (keyIte.hasNext()) {
                KeyElement ele = keyIte.next();
                if (groupedSet.containsKey(ele.getKey())) {
                    KeyElement org = groupedSet.get(ele.getKey());
                    org.setSize(org.getSize() + ele.getSize());
                    org.setWeight(org.getWeight() + ele.getWeight());

                } else {
                    groupedSet.put(ele.getKey(), ele);
                }
                size += ele.getSize();

            }
        }
        MergedSplit ms = new MergedSplit(groupedSet, size, os.getIsID());
        //時間の算出
        //Partitioning時間: キー数 x 1キーあたりのpartition時間= NlogN x 仕事量 / 速度
        double time_partitioning = MRUtil.getRoundedValue(max_key_kinds * MRUtil.log2(max_key_kinds) * (MRUtil.workload_partitioning_per_key / (double) this.getSpeed()));
        double total_time_partition = time_partitioning * recordNum;

        //Serialization時間
        double time_ser = MRUtil.getRoundedValue(MRUtil.out_record_workload_perMB * size/(double) this.getSpeed());
        double totalTime = time_ser + total_time_partition;
        MRLog.getIns().log(",3:Part.+Seri."+","+"mID,"+this.getPrefix()+","+"Time,"+totalTime);
        //処理をする．
        this.process(totalTime);


        return ms;
    }

    /**
     * *    MPをそのままspillファイルとして，指定サイズ単位で書き出す．
     * *    spillファイルあたり，複数のキー出力から構成される
     *    今回は，1キー = 1 spillファイルとする．
     * @param ms
     * @return
     */
    @Override
    public SpillSplit spillProcess(MergedSplit ms) {
        //Reducer数に応じて，分割する？
        //この場合は，ms内の各キー要素を書き出すことにする．
        long spillNum = ms.getGroupedSet().size();
        double totalTime = 0;
        Iterator<KeyElement> keyIte = ms.getGroupedSet().values().iterator();
        HashMap<Long, KeyElement> gSet = new HashMap<Long, KeyElement>();
        SpillSplit ss = new SpillSplit(new LinkedList<SpillFile>());

        while(keyIte.hasNext()){
            KeyElement ele = keyIte.next();
            totalTime += MRUtil.getRoundedValue(MRUtil.out_record_workload_perMB * ele.getSize() / (double)this.getSpeed());
            SpillFile sf = new SpillFile(new HashMap<Long, KeyElement>());
            sf.getKeyElemSet().put(ele.getKey(), ele);
            ss.getSfList().add(sf);
        }
        MRLog.getIns().log(",4:SpillProc."+","+"mID,"+this.getPrefix()+"time,"+totalTime);
        this.process(totalTime);
        //次にspillSplitを生成する．

        return ss;
    }

    /**
     *      * Spillファイルを入力として，spillファイル
     *      * どうしを結合して，一つのMergedFile(MergedFileSplit)を生成する．
     * @param ss
     * @return
     */
    @Override
    public MergedFileSplit mergeProcess(SpillSplit ss) {
        Iterator<SpillFile> sfIte = ss.getSfList().listIterator();
        HashMap<Long, KeyElement> keyMap = new HashMap<Long, KeyElement>();
        double size  = 0.0d;
        double readTime = 0.0d;
        double writeTime = 0.0d;
        double mergeTime = 0.0d;
        //Spillファイル毎のループ
        while(sfIte.hasNext()){
            SpillFile sf = sfIte.next();

            Iterator<KeyElement> keIte = sf.getKeyElemSet().values().iterator();
            while(keIte.hasNext()){
                KeyElement ke = keIte.next();
                size += ke.getSize();
                keyMap.put(ke.getKey(), ke);
                readTime += MRUtil.getRoundedValue(ke.getSize() * MRUtil.spill_read_workload_perMB / (double) this.getSpeed());
                writeTime+=MRUtil.getRoundedValue(ke.getSize() * MRUtil.out_record_workload_perMB / (double) this.getSpeed());
                mergeTime+=MRUtil.getRoundedValue(MRUtil.merge_workload_permerge / (double) this.getSpeed());
            }
        }
        MergedFileSplit mfs = new MergedFileSplit(keyMap, this.currentISID, size);
        //時間の計算:
        //Spillファイル数 x (読み取り時間 + 書き込み時間)
        double totalTime = readTime + writeTime + mergeTime;
        MRLog.getIns().log(",5:Merge Proc."+","+"mID,"+this.getPrefix()+","+"time,"+totalTime);

        this.process(totalTime);



        return mfs;
    }

    /**
     *
     * @param key
     * @return
     */
    public MRVCPU findTargetOfKey(Long key){
        HashMap<String, MRVCPU> elem = MRMgr.getIns().getRetReducerMap();
        Iterator<MRVCPU> redIte = elem.values().iterator();

        MRVCPU retVCPU = null;
        //各キーの送付先を決めるためのループ
        //Reducerごとのループ
        while(redIte.hasNext()){
            MRVCPU vcpu = redIte.next();
            if(vcpu.getManagedKeySet().contains(key)){
                retVCPU = vcpu;
                break;
            }
        }
        return retVCPU;
    }

    /**
     *      * MergedFileSplitをキー単位に分割して各Reducerへ送る
     *      * 実際には，MergedPartition(MP)単位で送る．
     *
     *      */
    @Override
    public void shuffleSendProcess(MergedFileSplit mfs) {
        //Reducer数をpvorivionerから取得する．
        long reducerNum = MRMgr.getIns().getUsedProvisioning().calcReducerNum();

        //MergedFileの分割法を，Schedulerに任せる．
        LinkedList<HashMap<Long, KeyElement>> mpList = MRMgr.getIns().getUsedShceduling().divideMergedFile(mfs, reducerNum);
        //実際には，pListの要素数 = 実際のReducer数　となる．
        long actualReducerNum = mpList.size();

        //Reducer集合が無ければ，いろいろセットする．
        if(MRMgr.getIns().getRetReducerMap().isEmpty()){
           // MRMgr.getIns().setActualReducers(actualReducerNum);
            MRMgr.getIns().setActualReducers(reducerNum);
            //1MPを1Reducerが担当する．
            int len = mpList.size();
            Iterator<HashMap<Long, KeyElement>> mpIte = mpList.listIterator();
            Iterator<MRVCPU> redIte = MRMgr.getIns().getRetReducerMap().values().iterator();

            while(mpIte.hasNext()){
                HashMap<Long, KeyElement> mp = mpIte.next();
                MRVCPU red = redIte.next();

                Iterator<KeyElement> keIte = mp.values().iterator();
                while(keIte.hasNext()){
                    KeyElement ke = keIte.next();
                    red.getManagedKeySet().add(ke.getKey());
                }
            }
        }

        Iterator<KeyElement> keyIte = mfs.getKeyElementMap().values().iterator();

        while(keyIte.hasNext()){
            KeyElement  ele = keyIte.next();
            //当該キーを保持するReducerを取得．
            MRVCPU reducer  = this.findTargetOfKey(ele.getKey());
            //マルチキャストモード
            if(MRUtil.dfs_transfer_mode == 0){
                ele.setFromPrefix(this.getPrefix());
                double comTime = MRUtil.calcComTimeParallel(MRUtil.getRoundedValue(ele.getSize()), MRMgr.getIns().getRetMapperMap().size(),
                        MRMgr.getIns().getRetReducerMap().size(), this, reducer);
                ele.setComTime(comTime);
                //とりあえず待たない．
                reducer.getSfQueue().offer(ele);
                //reducer.offerSFQueueInParallelMode(ele, comTime);
            }else{
                //逐次転送
                double comTime = MRUtil.calcComTime(MRUtil.getRoundedValue(ele.getSize()), this, reducer);
                MRLog.getIns().log(",6:ShuffleSend[Ser]:"+","+"mID,"+this.getPrefix() +","+"rID,"+reducer.getPrefix() +","+
                        "keyID,"+ ele.getKey()+","+"comTime,"+comTime);
                this.process(comTime);
                reducer.getSfQueue().offer(ele);

            }

        }

        //super.shuffleSendProcess(mfs);
    }

    public ShuffleFileSplit  processSFQueue(KeyElement ele, int  mode){
        //とりあえずキューイングする．
        //this.sfQueue.offer(ele);
        double comTime = ele.getComTime();

        if(MRUtil.dfs_transfer_mode == 0){
            MRLog.getIns().log(",6':ShuffleSend_candidate[para]:"+","+"mID,"+ele.getFromPrefix() +","+"rID,"+this.getPrefix() +","+
                    "keyID,"+ ele.getKey()+","+"comTime,"+comTime);
        }
        if(comTime >= this.maxSFComTime){
            this.maxSFComTime = comTime;

        }
        boolean isSatisfied = true;
        //1つのキーが，mapperNum分きたら，そのキーについては終了．
        //トータルでtHost分のキー種類が来たら終了．
        FSHost fsHost = MRMgr.getIns().getEnv().getFsHost();
        //キーの種類数を取得．
        long keyKindsNum = fsHost.getIfList().get(0).getKeyKindsNum();
        long cnt = 0;
        if(this.recMap.containsKey(ele.getKey())){
            cnt = this.recMap.get(ele.getKey());
            KeyElement e = this.sfs.getSfMap().get(ele.getKey());
            e.setWeight(e.getWeight() + ele.getWeight());
            e.setSize(e.getSize() + ele.getSize());


        }else{
            this.sfs.getSfMap().put(ele.getKey(), ele);
            this.sfs.setFileID(0);

        }
        cnt++;
        this.recMap.put(ele.getKey(), new Long(cnt));
        this.sfs.setSize(this.sfs.getSize() + (long)ele.getSize());



        long mapperNum = MRMgr.getIns().getRetMapperMap().size();
        if(this.recMap.size() ==this.managedKeySet.getList().size()){
            Iterator<Long> valIte = this.recMap.values().iterator();
            while(valIte.hasNext()){
                Long val = valIte.next();
                //System.out.println("all:"+mapperNum+" / val:"+val);
                if(val < mapperNum ){
                    isSatisfied = false;
                    break;
                }else{

                }
            }
        }else{
            isSatisfied = false;
        }
        //すべて受信した場合
        if(isSatisfied){
            if(mode== 0){
                MRLog.getIns().log(",6:ShuffleSend[para]:"+","+"mID,"+ele.getFromPrefix() +","+"rID,"+this.getPrefix() +","+
                        "keyID,"+ ele.getKey()+","+"comTime,"+this.maxSFComTime);
                //最大値分，待つ．
                this.process(this.maxSFComTime);
            }

            //シャッフルファイル生成．
            return this.processShuffleFileSplit();
        }else{
            return null;
        }


    }

    /**
     * シャッフルファイル生成処理
     * //this.sfsとしてすでに出来上がっているので，後は時間をかけるだけ．
     */
    public ShuffleFileSplit processShuffleFileSplit(){
        //まずはmapperごと＋1キーごとのファイルを生成．
        long splitSize = 0;

        //1キーごとのファイルを生成
        Iterator<KeyElement> keyIte = this.sfs.getSfMap().values().iterator();
        while(keyIte.hasNext()){
            KeyElement ke = keyIte.next();
            splitSize += ke.getSize();

        }
        //keyIte = 1キーごとのファイル．
        //keyIteのサイズ x out_record_workload_perMB /速度
        double totalTimeWriteAsFiles = MRUtil.getRoundedValue(splitSize * MRUtil.out_record_workload_perMB / (double)this.getSpeed());
        MRLog.getIns().log(",7:Gen. ShuffleFiles:"+","+"rID,"+this.getPrefix() +","+ "time,"+totalTimeWriteAsFiles);
        //ファイルたちを生成するのにかかる時間だけ待つ．
        this.process(totalTimeWriteAsFiles);

        //その後
        //merge_workload_permerge x キー数 / 速度
        long keyKinds = sfs.getSfMap().size();

        //マージ処理
        double timeForMerge = MRUtil.getRoundedValue(keyKinds * MRUtil.merge_workload_permerge / (double)this.getSpeed());
        this.process(timeForMerge);
        MRLog.getIns().log(",8:Merge ShuffleFiles:"+","+"rID,"+this.getPrefix() + ","+"time,"+timeForMerge);


        return this.sfs;
    }


    public LinkedBlockingQueue<KeyElement> getSfQueue() {
        return sfQueue;
    }

    public void setSfQueue(LinkedBlockingQueue<KeyElement> sfQueue) {
        this.sfQueue = sfQueue;
    }

    @Override
    public ShuffleFileSplit shuffleReceiveProcess() {
        return super.shuffleReceiveProcess();
    }

    @Override
    public ReduceOutputFile reduceProcess(ShuffleFileSplit sfs) {
        //キーごとに処理をする．
        Iterator<KeyElement> eleIte = sfs.getSfMap().values().iterator();
        double totalTime = 0;
        double totalOutSize = 0;
        while(eleIte.hasNext()){
            KeyElement ele = eleIte.next();
            double outSize = ele.getSize() * MRUtil.reduce_out_in_rate;
            totalOutSize += outSize;
            //サイズに対して処理をする．
            double time = MRUtil.getRoundedValue(outSize * MRUtil.out_record_workload_perMB / (double) this.getSpeed());
            totalTime += time;
        }
        //変更をする．
        sfs.setSize((long)totalOutSize);
        //送る．
        FSHost fsHost = MRMgr.getIns().getEnv().getFsHost();
        double comTime = MRUtil.calcComTimeReducerToFSHost(this, fsHost, sfs);
        totalTime += comTime;
        MRLog.getIns().log(",9:Reduce+Send Process:"+"rID,"+this.getPrefix() +","+ "time,"+totalTime);
        this.process(totalTime);
        fsHost.processOutput(sfs);

        //次に，出力ファイルを送る．
        return null;
    }

    @Override
    public boolean sendReduceOutputFile(ReduceOutputFile rof) {
        return super.sendReduceOutputFile(rof);
    }

    public int getStatus() {
        return status;
    }

    public synchronized void setStatus(int status) {
        this.status = status;
    }

    public LinkedBlockingQueue<InputSplit> getIsQueue() {
        return isQueue;
    }

    public void setIsQueue(LinkedBlockingQueue<InputSplit> isQueue) {
        this.isQueue = isQueue;
    }

    public long getCurrentISID() {
        return currentISID;
    }

    public void setCurrentISID(long currentISID) {
        this.currentISID = currentISID;
    }

    public CustomIDSet getManagedKeySet() {
        return managedKeySet;
    }

    public void setManagedKeySet(CustomIDSet managedKeySet) {
        this.managedKeySet = managedKeySet;
    }

    public LinkedList<HistoryInfo> getHistoryList() {
        return historyList;
    }

    public void setHistoryList(LinkedList<HistoryInfo> historyList) {
        this.historyList = historyList;
    }


    public HashMap<Long, Long> getRecMap() {
        return recMap;
    }

    public void setRecMap(HashMap<Long, Long> recMap) {
        this.recMap = recMap;
    }

    public double getMaxSFComTime() {
        return maxSFComTime;
    }

    public void setMaxSFComTime(double maxSFComTime) {
        this.maxSFComTime = maxSFComTime;
    }
}
