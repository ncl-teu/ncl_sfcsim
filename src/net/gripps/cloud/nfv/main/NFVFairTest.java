package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.fairscheduling.FairnessIndexInfo;
import net.gripps.cloud.nfv.fairscheduling.RandomFairSchedulingAlgorithm;
import net.gripps.cloud.nfv.listscheduling.FWS_VNFAlgorithm;
import net.gripps.cloud.nfv.listscheduling.HEFT_VNFAlgorithm;
import net.gripps.cloud.nfv.listscheduling.RandomVNFListSchedulingAlgorithm;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu
 */
public class NFVFairTest {
    public static void main(String[] args) {
        //設定ファイルを取得
        String fileName = args[0];
        //Utilの初期化（設定ファイルの値の読み込み）
        NFVUtil.getIns().initialize(fileName);

        //SFCの生成
        //VNF集合の生成
        //SFC sfc = SFCGenerator.getIns().singleSFCProcess();
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        SFC sfc2 = (SFC) sfc.deepCopy();
        SFC sfc3 = (SFC) sfc.deepCopy();
        SFC sfc4 = (SFC) sfc.deepCopy();
        SFC sfc5 = (SFC) sfc.deepCopy();
        SFC sfc6 = (SFC) sfc.deepCopy();


        //次はクラウド環境の生成
        //設定値の読み込みを行う．
        CloudUtil.getInstance().initialize(fileName);
        NFVEnvironment env = new NFVEnvironment();
        NFVEnvironment env2 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env3 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env4 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env5 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env6 = (NFVEnvironment) env.deepCopy();

        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        long totalSize = 0;
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            totalSize += vnf.getWorkLoad();
        }
        Iterator<VNF> vnfIte = sfc.getVnfMap().values().iterator();
        long totalWorkload = 0;
        long totalDataSize = 0;
        long totalEdgeNum = 0;
        while(vnfIte.hasNext()){
            VNF vnf = vnfIte.next();
            totalWorkload += vnf.getWorkLoad();
            totalEdgeNum += vnf.getDsucList().size();
            Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
            while(dsucIte.hasNext()) {
                DataDependence dd = dsucIte.next();
                totalDataSize += dd.getMaxDataSize();
            }
        }
        //次に，環境．
        long totalSpeed = 0;
        long totalBW = 0;
        long hostNum = env.getGlobal_hostMap().size();
        Iterator<ComputeHost> cIte = env.getGlobal_hostMap().values().iterator();
        while(cIte.hasNext()){
            ComputeHost host = cIte.next();
            totalBW += host.getBw();
        }
        double ave_bw = NFVUtil.getRoundedValue((double)totalBW / (double)hostNum);

        Iterator<VCPU> vcpuIte = env.getGlobal_vcpuMap().values().iterator();
        long vcpuNum = env.getGlobal_vcpuMap().size();
        while(vcpuIte.hasNext()){
            VCPU vcpu = vcpuIte.next();
            totalSpeed += vcpu.getMips();
        }
        double ave_speed = NFVUtil.getRoundedValue((double)totalSpeed/(double)vcpuNum);

        double ave_workload = NFVUtil.getRoundedValue((double)totalWorkload / (double) sfc.getVnfMap().size());
        double ave_datasize = NFVUtil.getRoundedValue((double)totalDataSize / (double)totalEdgeNum);

        double ave_comTime = NFVUtil.getRoundedValue((double)ave_datasize / (double) ave_bw);
        double ave_procTime = NFVUtil.getRoundedValue((double)ave_workload / (double)ave_speed);
        double CCR = NFVUtil.getRoundedValue((double)ave_comTime / (double)ave_procTime);

        System.out.println("CCR: "+ CCR + " /VNF Num:"+sfc.getVnfMap().size());




        double w_load = NFVUtil.nfv_fairness_weight_overlap;
        double w_duration = 1 - w_load;

        RandomFairSchedulingAlgorithm fair = new RandomFairSchedulingAlgorithm(env, sfc);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        System.out.println("HostNum:" + env.getGlobal_hostMap().size() + "/vCPUNum:" + env.getGlobal_vcpuMap().size());
        double time = NFVUtil.getRoundedValue((double) totalSize / (double) fair.getAveSpeed());
        System.out.println("Exec. Time at One vCPU: " + time);
        fair.mainProcess();
        FairnessIndexInfo info0 = fair.calcFairnessIndex();
        double f0 =1-( w_load * info0.getLoadFairness() + w_duration * info0.getDurationFairness());


        System.out.println("SpeedUp[RandomFairSched]:" + NFVUtil.getRoundedValue(time/fair.getMakeSpan()) + " / # of vCPUs: "
                + fair.getAssignedVCPUMap().size() + "/ # of Hosts:" + fair.getHostSet().size() + "/1-FI:"+NFVUtil.getRoundedValue(f0));
        Iterator<VCPU> vcIte = fair.getAssignedVCPUMap().values().iterator();

        RandomVNFClusteringAlgorithm alg4 = new RandomVNFClusteringAlgorithm(env4, sfc4);
        alg4.mainProcess();
        FairnessIndexInfo info4 = alg4.calcFairnessIndex();
        double f4 =1-( w_load * info4.getLoadFairness() + w_duration * info4.getDurationFairness());

        System.out.println("SpeedUp[RandomVNFClustering]:" + NFVUtil.getRoundedValue(time/alg4.getMakeSpan()) + " / # of vCPUs: " + alg4.getAssignedVCPUMap().size() + "/ # of Hosts:"
                + alg4.getHostSet().size()+"/1-FI:"+NFVUtil.getRoundedValue(f4));


        HEFT_VNFAlgorithm alg5 = new HEFT_VNFAlgorithm(env5, sfc5);
        alg5.mainProcess();
        FairnessIndexInfo info5 = alg5.calcFairnessIndex();
        double f5 =1-( w_load * info5.getLoadFairness() + w_duration * info5.getDurationFairness());


        System.out.println("SpeedUp[HEFT]:" + NFVUtil.getRoundedValue(time/alg5.getMakeSpan()) + " / # of vCPUs: " + alg5.getAssignedVCPUMap().size() + "/ # of Hosts:"
                + alg5.getHostSet().size()        + "/1-FI:"+NFVUtil.getRoundedValue(f5));



        RandomVNFListSchedulingAlgorithm alg6 = new RandomVNFListSchedulingAlgorithm(env6, sfc6);
        alg6.mainProcess();
        FairnessIndexInfo info6 = alg6.calcFairnessIndex();
        double f6 =1-( w_load * info6.getLoadFairness() + w_duration * info6.getDurationFairness());

        System.out.println("SpeedUp[RandomList]:" + NFVUtil.getRoundedValue(time/alg6.getMakeSpan()) + " / # of vCPUs: " + alg6.getAssignedVCPUMap().size() + "/ # of Hosts:"
                + alg6.getHostSet().size()+ "/1-FI:"+NFVUtil.getRoundedValue(f6));

        FWS_VNFAlgorithm alg2 = new FWS_VNFAlgorithm(env2, sfc2);
        alg2.mainProcess();
        FairnessIndexInfo info2 = alg2.calcFairnessIndex();
        double f2 =1-( w_load * info2.getLoadFairness() + w_duration * info2.getDurationFairness());


        System.out.println("SpeedUp[FWS]:" + NFVUtil.getRoundedValue(time/alg2.getMakeSpan()) + " / # of vCPUs: " +
                alg2.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg2.getHostSet().size()        + "/1-FI:"+NFVUtil.getRoundedValue(f2));


        CoordVNFAlgorithm alg3 = new CoordVNFAlgorithm(env3, sfc3);
        alg3.mainProcess();
        FairnessIndexInfo info3 = fair.calcFairnessIndex();
        double f3 =1-( w_load * info3.getLoadFairness() + w_duration * info3.getDurationFairness());

        System.out.println("SpeedUp[CoordVNF]:" + NFVUtil.getRoundedValue(time/alg3.getMakeSpan())  + " / # of vCPUs: " +
                alg3.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg3.getHostSet().size()+ "/1-FI:"+NFVUtil.getRoundedValue(f3));


    }
}
