package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.fairscheduling.RandomFairSchedulingAlgorithm;
import net.gripps.cloud.nfv.listscheduling.*;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.Iterator;

public class KHEFTTest {

    public static void main(String[] args){
        //設定ファイルを取得
        String fileName = args[0];
        //Utilの初期化（設定ファイルの値の読み込み）
        NFVUtil.getIns().initialize(fileName);

        //SFCの生成
        //VNF集合の生成
        //SFC sfc = SFCGenerator.getIns().singleSFCProcess();
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        SFC sfc2 = (SFC)sfc.deepCopy();
        SFC sfc3 = (SFC)sfc.deepCopy();
        SFC sfc4 = (SFC)sfc.deepCopy();
        SFC sfc5 = (SFC)sfc.deepCopy();
        SFC sfc6 = (SFC)sfc.deepCopy();

        SFC sfc7 = (SFC)sfc.deepCopy();
        SFC sfc8 = (SFC)sfc.deepCopy();
        SFC sfc9 = (SFC)sfc.deepCopy();
        SFC sfc10 = (SFC)sfc.deepCopy();



        //次はクラウド環境の生成
        //設定値の読み込みを行う．
        CloudUtil.getInstance().initialize(fileName);
        NFVEnvironment env = new NFVEnvironment();
        NFVEnvironment env2 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env3 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env4 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env5 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env6 = (NFVEnvironment)env.deepCopy();

        NFVEnvironment env7 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env8 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env9 = (NFVEnvironment)env.deepCopy();
        NFVEnvironment env10 = (NFVEnvironment)env.deepCopy();

        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        long totalSize = 0;
        while(vIte.hasNext()){
            VNF vnf = vIte.next();
            totalSize += vnf.getWorkLoad();
        }


   /*     System.out.println("HostNum:"+env.getGlobal_hostMap().size() + "/vCPUNum:"+env.getGlobal_vcpuMap().size());
        RandomVNFClusteringAlgorithm alg1 = new RandomVNFClusteringAlgorithm(env,sfc);
        alg1.mainProcess();


        double time = NFVUtil.getRoundedValue((double)totalSize/(double)alg1.getAveSpeed());
        System.out.println("Exec. Time at One vCPU: "+ time);
        //System.out.println("makespan[RandomVNFClustering]:"+alg1.getMakeSpan() +" / # of vCPUs: "+alg1.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg1.getHostSet().size());
        System.out.println("SLR[RandomVNFClustering]:"+NFVUtil.getRoundedValue(alg1.getMakeSpan()/alg1.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg1.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg1.getHostSet().size()+"\n");
*/
/*
        RandomVNFListSchedulingAlgorithm alg2 = new RandomVNFListSchedulingAlgorithm(env2, sfc2);
        alg2.mainProcess();
        System.out.println("makespan[RandomListSched]:"+alg2.getMakeSpan()+" / # of vCPUs: "+alg2.getAssignedVCPUMap().size() + "/ # of Hosts:"+alg2.getHostSet().size());

/*
        HEFT_VNFAlgorithm alg3 = new HEFT_VNFAlgorithm(env3, sfc3);
        alg3.mainProcess();
        System.out.println("makespan[HEFT-VNF]:"+alg3.getMakeSpan()+" / # of vCPUs: "+alg3.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg3.getHostSet().size());
*/
/*
        SF_CUVAlgorithm alg4 = new SF_CUVAlgorithm(env4, sfc4);
        alg4.mainProcess();
        //System.out.println("makespan[CMWSL-VNF-ALL]:"+alg4.getMakeSpan()+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg4.getHostSet().size());
        System.out.println("SLR[CMWSL]:"+NFVUtil.getRoundedValue(alg4.getMakeSpan()/alg4.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg4.getHostSet().size()+"\n");
*/
/*
        SF_CUVAlgorithm alg5 = new SF_CUVAlgorithm(env5, sfc5);
        alg5.mainProcess();
        System.out.println("makespan[SF-CUV]:"+alg5.getMakeSpan()+" / # of vCPUs: "+alg5.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg5.getHostSet().size());
        System.out.println("SLR[SF-CUV]:"+NFVUtil.getRoundedValue(alg5.getMakeSpan()/alg5.getTotalCPProcTimeAtMaxSpeed())+"\n");

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
        //System.out.println("makespan[FairSched]:"+alg6.getMakeSpan()+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size());
        System.out.println("SLR[FairSched]:"+NFVUtil.getRoundedValue(alg6.getMakeSpan()/alg6.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size()+"\n");

        Iterator<VCPU> vcIte = alg6.getAssignedVCPUMap().values().iterator();
  /*      while(vcIte.hasNext()){
            VCPU vc = vcIte.next();
            System.out.println("VCPUID:"+vc.getPrefix()+ "/ Num:"+vc.getVnfQueue().size() + "/Sped:"+vc.getMips()+"/BW:"+alg6.getBW(vc));
        }
   */


        HEFT_VNFAlgorithm alg8 = new HEFT_VNFAlgorithm(env8, sfc8);
        alg8.mainProcess();
        //System.out.println("makespan[HEFT-VNF]:"+alg8.getMakeSpan()+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size());
        System.out.println("SLR[HEFT]:"+NFVUtil.getRoundedValue(alg8.getMakeSpan()/alg8.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size()+"\n");

        Ideal_HEFT_VNFAlgorithm alg10 = new Ideal_HEFT_VNFAlgorithm(env10, sfc10);
        alg10.mainProcess();
        //System.out.println("makespan[HEFT-VNF]:"+alg8.getMakeSpan()+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size());
        System.out.println("SLR[IDEAL_HEFT]:"+NFVUtil.getRoundedValue(alg10.getMakeSpan()/alg10.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg10.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg10.getHostSet().size()+"\n");

        DHEFTAlgorithm alg7 = new DHEFTAlgorithm(env7, sfc7);
        alg7.mainProcess();
        //System.out.println("makespan[DHEFT]:"+alg7.getMakeSpan()+" / # of vCPUs: "+alg7.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg7.getHostSet().size());
        System.out.println("SLR[D-HEFT]:"+NFVUtil.getRoundedValue(alg7.getMakeSpan()/alg7.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg7.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg7.getHostSet().size()+"\n");


        KHEFTAlgorithm alg9 = new KHEFTAlgorithm(env9, sfc9);
        alg9.mainProcess();
       // System.out.println("makespan[KHEFT]:"+alg9.getMakeSpan()+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size());
        System.out.println("SLR[K-HEFT]:"+NFVUtil.getRoundedValue(alg9.getMakeSpan()/alg9.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size()+"\n");



    }
}

//4,6,7,8