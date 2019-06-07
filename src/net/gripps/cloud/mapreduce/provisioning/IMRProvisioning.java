package net.gripps.cloud.mapreduce.provisioning;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/12
 */
public interface IMRProvisioning {

    /**
     * Mapper数を返します．
     * @return
     */
    public long calcMapperNum();

    /**
     * Reducer数を返します．
     * @return
     */
    public long calcReducerNum();
}
