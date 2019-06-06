package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.fairscheduling.RandomFairSchedulingAlgorithm;
import net.gripps.cloud.nfv.listscheduling.FWS_VNFAlgorithm;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.Iterator;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/01.
 */
public class NFVTest {
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
        double time = NFVUtil.getRoundedValue((double)totalSize/(double)alg1.getAveSpeed());
        System.out.println("Exec. Time at One vCPU: "+ time);
       System.out.println("makespan[RnadomVNFClustering]:"+alg1.getMakeSpan() +" / # of vCPUs: "+alg1.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg1.getHostSet().size());
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
        alg5.mainProcess();
        System.out.println("makespan[VNF-CONHF]:"+alg5.getMakeSpan()+" / # of vCPUs: "+alg5.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg5.getHostSet().size());
/*
     NFVUtil.cmwsl_sched_area = 1;
     SF_CUVAlgorithm alg6 = new SF_CUVAlgorithm(env6, sfc6);
     alg6.mainProcess();
     System.out.println("makespan[CMWSL-VNF-HOST]:"+alg6.getMakeSpan()+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size());
*/

        RandomFairSchedulingAlgorithm alg6 = new RandomFairSchedulingAlgorithm(env6, sfc6);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg6.mainProcess();
        System.out.println("makespan[FairSched]:"+alg6.getMakeSpan()+" / # of vCPUs: "+alg6.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg6.getHostSet().size());
        Iterator<VCPU> vcIte = alg6.getAssignedVCPUMap().values().iterator();
  /*      while(vcIte.hasNext()){
            VCPU vc = vcIte.next();
            System.out.println("VCPUID:"+vc.getPrefix()+ "/ Num:"+vc.getVnfQueue().size() + "/Sped:"+vc.getMips()+"/BW:"+alg6.getBW(vc));
        }
*/

        FWS_VNFAlgorithm alg2 = new FWS_VNFAlgorithm(env2, sfc2);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg2.mainProcess();
        System.out.println("makespan[FWS]:"+alg2.getMakeSpan()+" / # of vCPUs: "+alg2.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg2.getHostSet().size());



        CoordVNFAlgorithm alg3 = new CoordVNFAlgorithm(env3, sfc3);
        //alg6.setMaxHostNum(alg5.getHostSet().size());
        alg3.mainProcess();
        System.out.println("makespan[CoordVNF]:"+alg3.getMakeSpan()+" / # of vCPUs: "+alg3.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg3.getHostSet().size());




    }
}
