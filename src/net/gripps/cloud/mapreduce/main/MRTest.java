package net.gripps.cloud.mapreduce.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.mapreduce.MRMgr;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.logger.MRLog;
import net.gripps.cloud.mapreduce.provisioning.ConvexProvisioningAlgorithm;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01
 */
public class MRTest {

    public static void main(String[] args){

        //MRLog.getIns().log("test");
        //設定ファイルを
        String fileName = args[0];
        MRLog.getIns().log(",Type,Column1,Column2,Column3,Column4,Column5,Column6,Column7,Column8,");
        //クラウド側の設定
        CloudUtil.getInstance().initialize(fileName);
        //MapReduce独自の部分の設定
        MRUtil.getIns().initialize(fileName);

        //MapReduceの制御プロセスの設定
        MRMgr.getIns().initialize();
        ConvexProvisioningAlgorithm p = new ConvexProvisioningAlgorithm(MRMgr.getIns().getEnv());
        p.calcOptimalMapperNum();
        //CCNMgr自体のスレッドを起
        Thread mgrThread = new Thread(MRMgr.getIns());
        mgrThread.start();
    }
}