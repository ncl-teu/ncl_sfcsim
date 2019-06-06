package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.listscheduling.FWS_VNFAlgorithm;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/01.
 */
public class NFVSchedulingTest {
    public static void main(String[] args){
        //設定ファイルを取得
        String fileName = args[0];
        //Utilの初期化（設定ファイルの値の読み込み）
        NFVUtil.getIns().initialize(fileName);

        //SFCの生成
        //VNF集合の生成
      // SFC sfc = SFCGenerator.getIns().singleSFCProcess();
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
       SFC sfc2 = (SFC)sfc.deepCopy();
       SFC sfc3 = (SFC)sfc.deepCopy();
        SFC sfc4 = (SFC)sfc.deepCopy();
        SFC sfc5 = (SFC)sfc.deepCopy();
        SFC sfc6 = (SFC)sfc.deepCopy();


        //次はクラウド環境の生成
        //設定値の読み込みを行う．
        CloudUtil.getInstance().initialize(fileName);
        NFVEnvironment env = new NFVEnvironment();
        NFVEnvironment env2 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env3 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env4 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env5 = (NFVEnvironment)env.deepCopy();
     NFVEnvironment env6 = (NFVEnvironment)env.deepCopy();

        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        long totalSize = 0;
        while(vIte.hasNext()){
            VNF vnf = vIte.next();
            totalSize += vnf.getWorkLoad();
        }


        System.out.println("HostNum:"+env.getGlobal_hostMap().size() + "/vCPUNum:"+env.getGlobal_vcpuMap().size());

        RandomVNFClusteringAlgorithm alg1 = new RandomVNFClusteringAlgorithm(env,sfc);
        alg1.mainProcess();
        double time = NFVUtil.getRoundedValue((double)totalSize/(double)alg1.getMaxSpeed());
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
       System.out.println("SLR[RandomVNFClustering]:"+NFVUtil.getRoundedValue(alg1.getMakeSpan()/alg1.getTotalCPProcTimeAtMaxSpeed()) +" / # of vCPUs: "+alg1.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg1.getHostSet().size() +
               "/# of Ins:"+alg1.calcTotalFunctionInstanceNum());
/*
        RandomVNFListSchedulingAlgorithm alg2 = new RandomVNFListSchedulingAlgorithm(env2, sfc2);
        alg2.mainProcess();
        System.out.println("makespan[RandomListSched]:"+alg2.getMakeSpan()+" / # of vCPUs: "+alg2.getAssignedVCPUMap().size() + "/ # of Hosts:"+alg2.getHostSet().size());
*/
/*
        HEFT_VNFAlgorithm alg3 = new HEFT_VNFAlgorithm(env3, sfc3);
        alg3.mainProcess();
        System.out.println("makespan[HEFT-VNF]:"+alg3.getMakeSpan()+" / # of vCPUs: "+alg3.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg3.getHostSet().size());
/*
        SF_CUVAlgorithm alg4 = new SF_CUVAlgorithm(env4, sfc4);
        alg4.mainProcess();
        System.out.println("makespan[CMWSL-VNF-ALL]:"+alg4.getMakeSpan()+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg4.getHostSet().size());
*/


        SF_CUVAlgorithm alg5 = new SF_CUVAlgorithm(env5, sfc5);
        //alg5.setUpdateMode(0);
        alg5.mainProcess();
        System.out.println("SLR[SF_CUV]:"+NFVUtil.getRoundedValue(alg5.getMakeSpan()/alg5.getTotalCPProcTimeAtMaxSpeed()) +" / # of vCPUs: "+alg5.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg5.getHostSet().size()
        +"/# of Ins:"+alg5.calcTotalFunctionInstanceNum());
/*
        SF_CUVAlgorithm alg4 = new SF_CUVAlgorithm(env4, sfc4);
        alg4.setBtmMode(1);
        alg4.mainProcess();
        System.out.println("makespan[VNF-CONHF_out]:"+alg4.getMakeSpan()+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg4.getHostSet().size());
*/
/*
     NFVUtil.cmwsl_sched_area = 1;
     SF_CUVAlgorithm alg6 = new SF_CUVAlgorithm(env6, sfc6);
     alg6.mainProcess();
     System.out.println("makespan[CMWSL-VNF-HOST]:"+alg6.getMakeSpan()+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size());
*/
/*
        RandomFairSchedulingAlgorithm alg6 = new RandomFairSchedulingAlgorithm(env6, sfc6);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg6.mainProcess();
        System.out.println("makespan[FairSched]:"+alg6.getMakeSpan()+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size());
        Iterator<VCPU> vcIte = alg6.getAssignedVCPUMap().values().iterator();
        */
  /*      while(vcIte.hasNext()){
            VCPU vc = vcIte.next();
            System.out.println("VCPUID:"+vc.getPrefix()+ "/ Num:"+vc.getVnfQueue().size() + "/Sped:"+vc.getMips()+"/BW:"+alg6.getBW(vc));
        }
*/

        FWS_VNFAlgorithm alg2 = new FWS_VNFAlgorithm(env2, sfc2);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg2.mainProcess();
        System.out.println("SLR[FWS]:"+NFVUtil.getRoundedValue(alg2.getMakeSpan()/alg2.getTotalCPProcTimeAtMaxSpeed()) +" / # of vCPUs: "+alg2.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg2.getHostSet().size()
        +"/# of Ins:"+alg2.calcTotalFunctionInstanceNum());



        CoordVNFAlgorithm alg3 = new CoordVNFAlgorithm(env3, sfc3);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg3.mainProcess();
        System.out.println("SLR[CoordVNF]:"+NFVUtil.getRoundedValue(alg3.getMakeSpan()/alg3.getTotalCPProcTimeAtMaxSpeed()) +" / # of vCPUs: "+alg3.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg3.getHostSet().size()
        +"/# of Ins:"+alg3.calcTotalFunctionInstanceNum());




    }
}
