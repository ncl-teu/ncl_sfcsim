package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.HUtil;
import net.gripps.cloud.nfv.clustering.HierarchicalVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.listscheduling.FWS_VNFAlgorithm;
import net.gripps.cloud.nfv.listscheduling.RandomVNFListSchedulingAlgorithm;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.HSFCGenerator;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/16.
 */
public class HClusteringTest {
    public static void main(String[] args) {
        //設定ファイルを取得
        String fileName = args[0];
        //Utilの初期化（設定ファイルの値の読み込み）
        NFVUtil.getIns().initialize(fileName);
        HUtil.getIns().initialize(fileName);

        //SFCの生成(アルゴリズム数分，複製する）
        //VNF集合の生成
         SFC sfc = HSFCGenerator.getIns().singleSFCProcess();
        //SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        SFC sfc2 = (SFC) sfc.deepCopy();
        SFC sfc3 = (SFC) sfc.deepCopy();
        SFC sfc4 = (SFC) sfc.deepCopy();
        SFC sfc5 = (SFC) sfc.deepCopy();
        SFC sfc6 = (SFC) sfc.deepCopy();


        //次はクラウド環境の生成（アルゴリズム数分，複製する）
        //設定値の読み込みを行う．
        CloudUtil.getInstance().initialize(fileName);
        NFVEnvironment env = new NFVEnvironment();
        NFVEnvironment env2 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env3 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env4 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env5 = (NFVEnvironment) env.deepCopy();
        NFVEnvironment env6 = (NFVEnvironment) env.deepCopy();

        //SFC内のVNFの仕事量の合計を計算する．
        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        long totalSize = 0;
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            totalSize += vnf.getWorkLoad();
        }

        //クラウド内のホスト数，vCPU数の出力．
        System.out.println("HostNum:" + env.getGlobal_hostMap().size() + "/vCPUNum:" + env.getGlobal_vcpuMap().size());

        //double time = NFVUtil.getRoundedValue((double) totalSize / (double) alg1.getMaxSpeed());
        //データサイズ，仕事量の合計値の計算．
        Iterator<VNF> vnfIte = sfc.getVnfMap().values().iterator();
        long totalWorkload = 0;
        long totalDataSize = 0;
        long totalEdgeNum = 0;
        while (vnfIte.hasNext()) {
            VNF vnf = vnfIte.next();
            totalWorkload += vnf.getWorkLoad();
            totalEdgeNum += vnf.getDsucList().size();
            Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
            while (dsucIte.hasNext()) {
                DataDependence dd = dsucIte.next();
                totalDataSize += dd.getMaxDataSize();
            }
        }
        //次に，環境．
        long totalSpeed = 0;
        long totalBW = 0;
        long hostNum = env.getGlobal_hostMap().size();
        Iterator<ComputeHost> cIte = env.getGlobal_hostMap().values().iterator();
        while (cIte.hasNext()) {
            ComputeHost host = cIte.next();
            totalBW += host.getBw();
        }
        double ave_bw = NFVUtil.getRoundedValue((double) totalBW / (double) hostNum);

        Iterator<VCPU> vcpuIte = env.getGlobal_vcpuMap().values().iterator();
        long vcpuNum = env.getGlobal_vcpuMap().size();
        while (vcpuIte.hasNext()) {
            VCPU vcpu = vcpuIte.next();
            totalSpeed += vcpu.getMips();
        }
        double ave_speed = NFVUtil.getRoundedValue((double) totalSpeed / (double) vcpuNum);

        double ave_workload = NFVUtil.getRoundedValue((double) totalWorkload / (double) sfc.getVnfMap().size());
        double ave_datasize = NFVUtil.getRoundedValue((double) totalDataSize / (double) totalEdgeNum);

        double ave_comTime = NFVUtil.getRoundedValue(ave_datasize / ave_bw);
        double ave_procTime = NFVUtil.getRoundedValue(ave_workload / ave_speed);
        double CCR = NFVUtil.getRoundedValue(ave_comTime / ave_procTime);

        //CCR: Communication to Computation Ratio: SFCにおける平均データサイズ/ 平均仕事量の比率．
        System.out.println("CCR: " + CCR + " /VNF Num:" + sfc.getVnfMap().size());

        //ランダムにVNFをクラスタリングするアルゴリズム
        RandomVNFClusteringAlgorithm alg1 = new RandomVNFClusteringAlgorithm(env, sfc);
        //実際のスケジューリング処理を行う．
        alg1.mainProcess();
        System.out.println("SLR[RandomVNFClustering]:" + NFVUtil.getRoundedValue(alg1.getMakeSpan() / alg1.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg1.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg1.getHostSet().size() +
                "/# of Ins:" + alg1.calcTotalFunctionInstanceNum());

        HierarchicalVNFClusteringAlgorithm hclustering = new HierarchicalVNFClusteringAlgorithm(env4, sfc4);
        hclustering.mainProcess();
        System.out.println("SLR[HClustering]:" + NFVUtil.getRoundedValue(hclustering.getMakeSpan() / hclustering.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + hclustering.getAssignedVCPUMap().size() + "/ # of Hosts:" + hclustering.getHostSet().size() +
                "/# of Ins:" + hclustering.calcTotalFunctionInstanceNum());
/*
//ランダムリストスケジューリングアルゴリズム
        RandomVNFListSchedulingAlgorithm alg4 = new RandomVNFListSchedulingAlgorithm(env4, sfc4);
        alg4.mainProcess();
        System.out.println("makespan[RandomListSched]:"+alg4.getMakeSpan()+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size() + "/ # of Hosts:"+alg4.getHostSet().size());
*/
//HEFT Algorithm.
/*
        HEFT_VNFAlgorithm alg3 = new HEFT_VNFAlgorithm(env3, sfc3);
        alg3.mainProcess();
        System.out.println("makespan[HEFT-VNF]:"+alg3.getMakeSpan()+" / # of vCPUs: "+alg3.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg3.getHostSet().size());


/***
Service Function Clustering for Utilizing vCPU and Functions(SF-CUV) Algorithm by H. Kanemitsu.
 */
/*
        SF_CUVAlgorithm alg5 = new SF_CUVAlgorithm(env5, sfc5);
        //alg5.setUpdateMode(0);
        alg5.mainProcess();
        System.out.println("SLR[SF_CUV]:" + NFVUtil.getRoundedValue(alg5.getMakeSpan() / alg5.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg5.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg5.getHostSet().size()
                + "/# of Ins:" + alg5.calcTotalFunctionInstanceNum());

 */



        FWS_VNFAlgorithm alg2 = new FWS_VNFAlgorithm(env2, sfc2);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg2.mainProcess();
        System.out.println("SLR[FWS]:" + NFVUtil.getRoundedValue(alg2.getMakeSpan() / alg2.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg2.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg2.getHostSet().size()
                + "/# of Ins:" + alg2.calcTotalFunctionInstanceNum());



        CoordVNFAlgorithm alg3 = new CoordVNFAlgorithm(env3, sfc3);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg3.mainProcess();
        System.out.println("SLR[CoordVNF]:" + NFVUtil.getRoundedValue(alg3.getMakeSpan() / alg3.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg3.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg3.getHostSet().size()
                + "/# of Ins:" + alg3.calcTotalFunctionInstanceNum());


    }

}
