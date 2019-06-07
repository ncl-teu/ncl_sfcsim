package net.gripps.cloud.mapreduce.provisioning;

import net.gripps.cloud.mapreduce.MRMgr;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.core.MRCloudEnvironment;
import net.gripps.cloud.mapreduce.core.MRVCPU;

import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/12
 */
public class BaseProvisioningAlgorithm implements IMRProvisioning{

    /**
     * クラウド環境
     */
    protected MRCloudEnvironment env;

    /**
     * mapperの数
     */
    protected long mapperNum;


    /**
     * reducerの数
     */
    protected long reducerNum;

    /**
     * Provisioningで得られたmapper集合
     */
    protected HashMap<String, MRVCPU> mapperMap;

    /**
     * Provisioningで得られたreducer集合
     */
    protected HashMap<String, MRVCPU> reducerMap;

    public BaseProvisioningAlgorithm(MRCloudEnvironment in_env, long mapperNum,
                                     long reducerNum,
                                     HashMap<String, MRVCPU> mapperMap,
                                     HashMap<String, MRVCPU> reducerMap) {
        this.env = in_env;
        this.mapperNum = mapperNum;
        this.reducerNum = reducerNum;
        this.mapperMap = mapperMap;
        this.reducerMap = reducerMap;
    }

    public BaseProvisioningAlgorithm(MRCloudEnvironment in_env) {
        this.env = in_env;
        this.mapperNum = 0;
        this.reducerNum = 0;
        this.mapperMap = new HashMap<String, MRVCPU>();
        this.reducerMap = new HashMap<String, MRVCPU>();

    }



    public long getMapperNum() {
        return mapperNum;
    }

    public void setMapperNum(long mapperNum) {
        this.mapperNum = mapperNum;
    }

    public long getReducerNum() {
        return reducerNum;
    }

    public void setReducerNum(long reducerNum) {
        this.reducerNum = reducerNum;
    }

    public HashMap<String, MRVCPU> getMapperMap() {
        return mapperMap;
    }

    public void setMapperMap(HashMap<String, MRVCPU> mapperMap) {
        this.mapperMap = mapperMap;
    }

    public HashMap<String, MRVCPU> getReducerMap() {
        return reducerMap;
    }

    public void setReducerMap(HashMap<String, MRVCPU> reducerMap) {
        this.reducerMap = reducerMap;
    }

    /**
     * デフォルトでは，設定ファイルの値を使う．
     * @return
     */
    @Override
    public long calcMapperNum() {
        long allNum = this.env.getGlobal_vcpuMap().size();
        long mappernum = (long)(allNum * MRUtil.num_mapper_rate);
        this.mapperNum = mappernum;

        return mappernum;
    }

    /**
     * デフォルトでは，設定ファイルの値を使う．
     * @return
     */
    @Override
    public long calcReducerNum() {
      /*  long allNum = this.env.getGlobal_vcpuMap().size();
        double val = MRUtil.getRoundedValue(1.0d - MRUtil.num_mapper_rate);

        long reducernum = (long)(allNum * val);
        this.reducerNum = reducernum;
*/

      long mapperNum = this.mapperNum;
      long reducernum =(long)(Math.min(MRMgr.getIns().getKeyKindsNum(), this.mapperNum * MRUtil.num_reducer_rate_to_mapper));

        return reducernum;

    }

    public MRCloudEnvironment getEnv() {
        return env;
    }

    public void setEnv(MRCloudEnvironment env) {
        this.env = env;
    }
}
