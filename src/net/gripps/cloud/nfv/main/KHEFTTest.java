package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.listscheduling.*;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;

public class KHEFTTest {

    public static void main(String[] args) {
        for (int i = 0; i < 1; i++) {
            System.out.println("KHEFTTest------------------------------");
            //設定ファイルを取得
            String fileName = args[0];
            //Utilの初期化（設定ファイルの値の読み込み）
            //Network Function Virtualization初始化值
            //可以理解NFV就是一个task
            NFVUtil.getIns().initialize(fileName);
            System.out.println("sun1");

            //SFCの生成
            //VNF集合の生成
            //SFC sfc = SFCGenerator.getIns().singleSFCProcess();
            //SFC可以理解为一种workflow
            SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
            SFC sfc2 = (SFC) sfc.deepCopy();
            SFC sfc3 = (SFC) sfc.deepCopy();
            SFC sfc4 = (SFC) sfc.deepCopy();
            SFC sfc5 = (SFC) sfc.deepCopy();
            SFC sfc6 = (SFC) sfc.deepCopy();

            SFC sfc7 = (SFC) sfc.deepCopy();
            SFC sfc8 = (SFC) sfc.deepCopy();
            SFC sfc9 = (SFC) sfc.deepCopy();
            SFC sfc10 = (SFC) sfc.deepCopy();
            SFC sfc11 = (SFC) sfc.deepCopy();
            SFC sfc12 = (SFC) sfc.deepCopy();
            SFC sfc13 = (SFC) sfc.deepCopy();


            //次はクラウド環境の生成
            //設定値の読み込みを行う．
            CloudUtil.getInstance().initialize(fileName);
            NFVEnvironment env = new NFVEnvironment();
            NFVEnvironment env2 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env3 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env4 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env5 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env6 = (NFVEnvironment) env.deepCopy();

            NFVEnvironment env7 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env8 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env9 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env10 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env11 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env12 = (NFVEnvironment) env.deepCopy();
            NFVEnvironment env13 = (NFVEnvironment) env.deepCopy();
            System.out.println("sun2");
            Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
            long totalSize = 0;
            while (vIte.hasNext()) {
                VNF vnf = vIte.next();
                totalSize += vnf.getWorkLoad();
            }

            //double time = NFVUtil.getRoundedValue((double) totalSize / (double) alg1.getMaxSpeed());
            //データサイズ，仕事量の合計値の計算．
            //计算总数据大小和工作量。
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
                //System.out.println(host.getBw());
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


            //HEFT_VNFAlgorithm alg8 = new HEFT_VNFAlgorithm(env8, sfc8);
            //alg8.mainProcess();
            ////System.out.println("makespan[HEFT-VNF]:"+alg8.getMakeSpan()+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size());
            //System.out.println("SLR[HEFT]:"+NFVUtil.getRoundedValue(alg8.getMakeSpan()/alg8.getTotalCPProcTimeAtMaxSpeed())+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size()+"\n");
            //System.out.println(alg8.getMakeSpan());
            //System.out.println("");

            //Ideal_HEFT_VNFAlgorithm alg10 = new Ideal_HEFT_VNFAlgorithm(env10, sfc10);
            //alg10.mainProcess();
            ////System.out.println("makespan[HEFT-VNF]:"+alg8.getMakeSpan()+" / # of vCPUs: "+alg8.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg8.getHostSet().size());
            ////System.out.print("SLR[IDEAL_HEFT]:" + NFVUtil.getRoundedValue(alg10.getMakeSpan() / alg10.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg10.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg10.getHostSet().size() + "\n");
            //System.out.println("MakeSpan[IDEAL_HEFT]:" + alg10.getMakeSpan());
            //System.out.println("");

            KHEFTBESTAlgorithm alg13 = new KHEFTBESTAlgorithm(env13, sfc13);
            alg13.mainProcess();
            // System.out.println("makespan[KHEFT]:"+alg9.getMakeSpan()+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size());
            //System.out.print("SLR[K-HEFT-BEST]:" + NFVUtil.getRoundedValue(alg13.getMakeSpan() / alg13.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg13.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg13.getHostSet().size() + "\n");
            //System.out.println("MakeSpan[K-HEFT-BEST]:" + alg13.getMakeSpan());

            DHEFTAlgorithm alg7 = new DHEFTAlgorithm(env7, sfc7);
            alg7.mainProcess();
            //System.out.println("makespan[DHEFT]:"+alg7.getMakeSpan()+" / # of vCPUs: "+alg7.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg7.getHostSet().size());
            //System.out.print("SLR[D-HEFT]:" + NFVUtil.getRoundedValue(alg7.getMakeSpan() / alg7.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg7.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg7.getHostSet().size() + "\n");
            System.out.println("MakeSpan[D-HEFT]:" + alg7.getMakeSpan());
            System.out.println("SLR[D-HEFT]:" + alg7.getMakeSpan()/alg13.getMakeSpan());
            //System.out.print("SLR2[D-HEFT]:" + NFVUtil.getRoundedValue(alg7.getMakeSpan() / alg7.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg7.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg7.getHostSet().size() + "\n");
            System.out.println("");


            KHEFTAlgorithm alg9 = new KHEFTAlgorithm(env9, sfc9);
            alg9.mainProcess();
            // System.out.println("makespan[KHEFT]:"+alg9.getMakeSpan()+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size());
            //System.out.print("SLR[K-HEFT]:" + NFVUtil.getRoundedValue(alg9.getMakeSpan() / alg9.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg9.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg9.getHostSet().size() + "\n");
            System.out.println("MakeSpan[K-HEFT]:" + alg9.getMakeSpan());
            System.out.println("SLR[K-HEFT]:" + alg9.getMakeSpan()/alg13.getMakeSpan());
            //System.out.print("SLR2[K-HEFT]:" + NFVUtil.getRoundedValue(alg9.getMakeSpan() / alg9.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg9.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg9.getHostSet().size() + "\n");
            System.out.println("");

            //System.exit(111);


            //NFVUtil.repository_bw = 258;
            //KHEFTPLUSAlgorithm alg11 = new KHEFTPLUSAlgorithm(env11, sfc11);
            //alg11.mainProcess();
            //// System.out.println("makespan[KHEFT]:"+alg9.getMakeSpan()+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size());
            ////System.out.print("SLR[K-HEFT-PLUS]:" + NFVUtil.getRoundedValue(alg11.getMakeSpan() / alg11.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg11.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg11.getHostSet().size() + "\n");
            //System.out.println("MakeSpan[K-HEFT-PLUS]:" + alg11.getMakeSpan());
            //System.out.println("");


            KHEFTPROAlgorithm alg12 = new KHEFTPROAlgorithm(env12, sfc12);
            alg12.mainProcess();
            // System.out.println("makespan[KHEFT]:"+alg9.getMakeSpan()+" / # of vCPUs: "+alg9.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg9.getHostSet().size());
            //System.out.print("SLR[K-HEFT-PRO]:" + NFVUtil.getRoundedValue(alg12.getMakeSpan() / alg12.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg12.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg12.getHostSet().size() + "\n");
            //System.out.println("MakeSpan[K-HEFT-PRO]:" + alg12.getMakeSpan());
            System.out.println("SLR[K-HEFT-PRO]:" + alg12.getMakeSpan()/alg13.getMakeSpan());
            System.out.print("SLR2[K-HEFT-PRO]:" + NFVUtil.getRoundedValue(alg12.getMakeSpan() / alg12.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg12.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg12.getHostSet().size() + "\n");




        }
    }
}

//4,6,7,8