 package net.gripps.cloud.mapreduce;

import net.gripps.cloud.core.*;
import net.gripps.cloud.mapreduce.core.FSHost;
import net.gripps.cloud.mapreduce.core.MRCloudEnvironment;
import net.gripps.cloud.mapreduce.core.MRVCPU;
import net.gripps.cloud.mapreduce.provisioning.BaseProvisioningAlgorithm;
import net.gripps.cloud.mapreduce.provisioning.ConvexProvisioningAlgorithm;
import net.gripps.cloud.mapreduce.provisioning.IMRProvisioning;
import net.gripps.cloud.mapreduce.scheduling.IMRScheduling;
import net.gripps.cloud.mapreduce.scheduling.BaseMRScheduling;
import net.gripps.environment.CPU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01
 */
public class MRMgr implements Runnable {


    /**
     *  MapReduce用のクラウド環境
     */
    protected  MRCloudEnvironment env;

    protected ArrayList<MRVCPU> mapperList;

    protected ArrayList<MRVCPU> reducerList;

    /**
     * Mapper集合
     */
    protected HashMap<String, MRVCPU> retMapperMap;

    /**
     * Reducer集合
     */
    protected HashMap<String, MRVCPU> retReducerMap;



    private long startTimeStamp;

    private long finTimeStamp;

    private IMRScheduling[] scheds;

    private IMRScheduling usedShceduling;

    private IMRProvisioning[] provs;

    private IMRProvisioning usedProvisioning;

    protected long keyKindsNum;

    protected   long currentSentSplitNum;

    protected long totalInputSplitNum;




    /**
     * シングルトンオブジェクト
     */
    private static MRMgr own;

    public static MRMgr getIns(){
        if(MRMgr.own == null){
            MRMgr.own = new MRMgr();
        }else{

        }
        return MRMgr.own;
    }

    private MRMgr(){

        //クラウド環境を生成する．
        this.env = new MRCloudEnvironment();
        this.currentSentSplitNum = 0;
        this.totalInputSplitNum = 0;

//**************スケジューリングアルゴリズムの設定 *******************/
        this.scheds = new IMRScheduling[MRUtil.mr_algorithm_scheduling_num];
        this.scheds[0] = new BaseMRScheduling();
        this.usedShceduling = this.scheds[MRUtil.mr_algorithm_scheduling_using];
//**************スケジューリングアルゴリズムの設定 END*******************/

// **************プロビジョニングアルゴリズムの設定 *******************/
        this.provs = new IMRProvisioning[MRUtil.mr_algorithm_provisioning_num];
        this.provs[0] = new BaseProvisioningAlgorithm(this.env);
        this.provs[1] = new ConvexProvisioningAlgorithm(this.env);
        this.usedProvisioning = this.provs[MRUtil.mr_algorithm_provisioning_using];
// **************プロビジョニングアルゴリズムの設定 END *******************/



    }



    /**
     * Mapper/Reducerの集合を設定する．
     */
    public void configureWorkers(){
        Iterator<ComputeHost> cIte = this.env.getGlobal_hostMap().values().iterator();
        //mapper数を算出する．
        //int allNum = this.env.getGlobal_vcpuMap().size();
        // int mapper_num = (int)(allNum * MRUtil.num_mapper_rate);
        //int reducer_num = allNum - mapper_num;
        long mapper_num = this.usedProvisioning.calcMapperNum();
        long reducer_num = this.usedProvisioning.calcReducerNum();

        int cnt_mapper = 0;

        while(cIte.hasNext()){
            ComputeHost host = cIte.next();
            Iterator<CPU> cpuIte = host.getCpuMap().values().iterator();
            while(cpuIte.hasNext()){
                CloudCPU cpu = (CloudCPU)cpuIte.next();
                Iterator<Core> coreIte = cpu.getCoreMap().values().iterator();
                while(coreIte.hasNext()){
                    Core core = coreIte.next();
                    Iterator<VCPU> vcpuIte = core.getvCPUMap().values().iterator();
                    while(vcpuIte.hasNext()){
                        MRVCPU vcpu = (MRVCPU)vcpuIte.next();

                        if(cnt_mapper <=  mapper_num){
                            this.mapperList.add(vcpu);

                        }else{
                            this.reducerList.add(vcpu);
                        }
                        Thread t = new Thread(vcpu);
                        t.start();
                        cnt_mapper ++;

                    }
                }
            }
        }


    }



    /**
     *
     */
    public void initialize(){
        this.mapperList = new ArrayList< MRVCPU>();
        this.reducerList = new ArrayList< MRVCPU>();
        this.retMapperMap = new HashMap<String, MRVCPU>();
        this.retReducerMap = new HashMap<String, MRVCPU>();


        //ファイルシステムの構成
        FSHost fsHost = this.env.getFsHost();
        //FSホストに対して，入力ファイルを構成する．
        fsHost.initialize();
        //ワーカーの準備(Mapper/Reducer)
        //次は，Mapper/Reducerの構成
        this.configureWorkers();
//開始
        this.startTimeStamp = System.currentTimeMillis();

        //データを送信する．

        this.usedShceduling.sendInputSplits(fsHost, this.mapperList);

//終了
        /*
        this.finTimeStamp = System.currentTimeMillis();
        double sec = MRUtil.getRoundedValue((finTimeStamp - startTimeStamp)/(double)1000);
        System.out.println("Elapsed Time:"+sec + " sec");
*/


    }

    @Override
    public void run() {
        //DFSにデータを配備する．

    }

    public synchronized  void setActualReducers(long num){
        Iterator<MRVCPU> redIte = this.getReducerList().listIterator();
        int i= 0;
        while(redIte.hasNext()){
            MRVCPU vcpu = redIte.next();
            this.retReducerMap.put(vcpu.getPrefix(), vcpu);

            if(i == num-1){
                break;
            }
            i++;
        }
    }


    public MRCloudEnvironment getEnv() {
        return env;
    }

    public void setEnv(MRCloudEnvironment env) {
        this.env = env;
    }

    public ArrayList<MRVCPU> getMapperList() {
        return mapperList;
    }

    public void setMapperList(ArrayList<MRVCPU> mapperList) {
        this.mapperList = mapperList;
    }

    public ArrayList<MRVCPU> getReducerList() {
        return reducerList;
    }

    public void setReducerList(ArrayList<MRVCPU> reducerList) {
        this.reducerList = reducerList;
    }

    public HashMap<String, MRVCPU> getRetMapperMap() {
        return retMapperMap;
    }

    public void setRetMapperMap(HashMap<String, MRVCPU> retMapperMap) {
        this.retMapperMap = retMapperMap;
    }

    public HashMap<String, MRVCPU> getRetReducerMap() {
        return retReducerMap;
    }

    public void setRetReducerMap(HashMap<String, MRVCPU> retReducerMap) {
        this.retReducerMap = retReducerMap;
    }

    public long getStartTimeStamp() {
        return startTimeStamp;
    }

    public void setStartTimeStamp(long startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
    }

    public long getFinTimeStamp() {
        return finTimeStamp;
    }

    public void setFinTimeStamp(long finTimeStamp) {
        this.finTimeStamp = finTimeStamp;
    }

    public IMRScheduling getUsedShceduling() {
        return usedShceduling;
    }

    public IMRProvisioning getUsedProvisioning() {
        return usedProvisioning;
    }

    public long getKeyKindsNum() {
        return keyKindsNum;
    }

    public void setKeyKindsNum(long keyKindsNum) {
        this.keyKindsNum = keyKindsNum;
    }

    public synchronized  long getCurrentSentSplitNum() {
        return currentSentSplitNum;
    }

    public synchronized  void setCurrentSentSplitNum(long currentSentSplitNum) {
        this.currentSentSplitNum = currentSentSplitNum;
    }

    public synchronized void  countUpSplitNum(long cnt){
        this.currentSentSplitNum += cnt;
    }

    public long getTotalInputSplitNum() {
        return totalInputSplitNum;
    }

    public void setTotalInputSplitNum(long totalInputSplitNum) {
        this.totalInputSplitNum = totalInputSplitNum;
    }
}
