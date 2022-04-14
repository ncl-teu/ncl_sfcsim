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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class KHEFTTest_Auto {



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

/*        List<Double>  SLR_CMWSL= new ArrayList();
        List<Integer>  vCPUs_CMWSL= new ArrayList();
        List<Integer>  Hosts_CMWSL= new ArrayList();
        for(int i = 0; i < 100; i++) {
            SF_CUVAlgorithm alg4 = new SF_CUVAlgorithm(env4, sfc4);
            alg4.mainProcess();
            //System.out.println("makespan[CMWSL-VNF-ALL]:"+alg4.getMakeSpan()+" / # of vCPUs: "+alg4.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg4.getHostSet().size());
            //System.out.println("SLR[CMWSL]:" + NFVUtil.getRoundedValue(alg4.getMakeSpan() / alg4.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg4.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg4.getHostSet().size() + "\n");

            SLR_CMWSL.add(NFVUtil.getRoundedValue(alg4.getMakeSpan() / alg4.getTotalCPProcTimeAtMaxSpeed()));
            vCPUs_CMWSL.add(alg4.getAssignedVCPUMap().size());
            Hosts_CMWSL.add(alg4.getHostSet().size());
        }

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

        int roop = 100;
        //何回実行した平均を取るか

        List<Double>  SLR_HEFT= new ArrayList();
        List<Integer>  vCPUs_HEFT= new ArrayList();
        List<Integer>  Hosts_HEFT= new ArrayList();
        for(int i = 0; i < roop; i++) {
            HEFT_VNFAlgorithm alg8 = new HEFT_VNFAlgorithm(env8, sfc8);
            alg8.mainProcess();
            SLR_HEFT.add(NFVUtil.getRoundedValue(alg8.getMakeSpan() / alg8.getTotalCPProcTimeAtMaxSpeed()));
            vCPUs_HEFT.add(alg8.getAssignedVCPUMap().size());
            Hosts_HEFT.add(alg8.getHostSet().size());
            }

        List<Double>  SLR_IHEFT= new ArrayList();
        List<Integer>  vCPUs_IHEFT= new ArrayList();
        List<Integer>  Hosts_IHEFT= new ArrayList();
        for(int i = 0; i < roop; i++) {
            Ideal_HEFT_VNFAlgorithm alg10 = new Ideal_HEFT_VNFAlgorithm(env10, sfc10);
            alg10.mainProcess();
            SLR_IHEFT.add(NFVUtil.getRoundedValue(alg10.getMakeSpan() / alg10.getTotalCPProcTimeAtMaxSpeed()));
            vCPUs_IHEFT.add(alg10.getAssignedVCPUMap().size());
            Hosts_IHEFT.add(alg10.getHostSet().size());
        }

        List<Double>  SLR_DHEFT= new ArrayList();
        List<Integer>  vCPUs_DHEFT= new ArrayList();
        List<Integer>  Hosts_DHEFT= new ArrayList();
        for(int i = 0; i < roop; i++) {
            DHEFTAlgorithm alg7 = new DHEFTAlgorithm(env7, sfc7);
            alg7.mainProcess();
            SLR_DHEFT.add(NFVUtil.getRoundedValue(alg7.getMakeSpan() / alg7.getTotalCPProcTimeAtMaxSpeed()));
            vCPUs_DHEFT.add(alg7.getAssignedVCPUMap().size());
            Hosts_DHEFT.add(alg7.getHostSet().size());
        }


        List<Double>  SLR_KHEFT= new ArrayList();
        List<Integer>  vCPUs_KHEFT= new ArrayList();
        List<Integer>  Hosts_KHEFT= new ArrayList();

        for(int i = 0; i < roop; i++) {
            KHEFTAlgorithm alg9 = new KHEFTAlgorithm(env9, sfc9);
            alg9.mainProcess();
            SLR_KHEFT.add(NFVUtil.getRoundedValue(alg9.getMakeSpan() / alg9.getTotalCPProcTimeAtMaxSpeed()));
            vCPUs_KHEFT.add(alg9.getAssignedVCPUMap().size());
            Hosts_KHEFT.add(alg9.getHostSet().size());
        }

            FileWriter fw = null;
            PrintWriter pw = null;

        Iterator<Double> slr_HEFT_ite = SLR_HEFT.iterator();
        Iterator<Integer> vcpu_HEFT_ite = vCPUs_HEFT.iterator();
        Iterator<Integer> hosts_HEFT_ite = Hosts_HEFT.iterator();

        Iterator<Double> slr_IHEFT_ite = SLR_IHEFT.iterator();
        Iterator<Integer> vcpu_IHEFT_ite = vCPUs_IHEFT.iterator();
        Iterator<Integer> hosts_IHEFT_ite = Hosts_IHEFT.iterator();

        Iterator<Double> slr_DHEFT_ite = SLR_DHEFT.iterator();
        Iterator<Integer> vcpu_DHEFT_ite = vCPUs_DHEFT.iterator();
        Iterator<Integer> hosts_DHEFT_ite = Hosts_DHEFT.iterator();

            Iterator<Double> slr_KHEFT_ite = SLR_KHEFT.iterator();
            Iterator<Integer> vcpu_KHEFT_ite = vCPUs_KHEFT.iterator();
            Iterator<Integer> hosts_KHEFT_ite = Hosts_KHEFT.iterator();

            try {
                fw = new FileWriter("vnf300CCR3.csv", false);
                //fileNameで出力ファイル名を指定

                pw = new PrintWriter(new BufferedWriter(fw));

                pw.print("SLR[HEFT]");
                pw.print(",");
                pw.print("vCPUs");
                pw.print(",");
                pw.print("Hosts");

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print("SLR[IDEAL_HEFT]");
                pw.print(",");
                pw.print("vCPUs");
                pw.print(",");
                pw.print("Hosts");

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print("SLR[D-HEFT]");
                pw.print(",");
                pw.print("vCPUs");
                pw.print(",");
                pw.print("Hosts");

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print("SLR[K-HEFT]");
                pw.print(",");
                pw.print("vCPUs");
                pw.print(",");
                pw.print("Hosts");
                pw.println();
                double HEFT_SLRSUM = 0;
                int HEFT_vcpusum = 0;
                int HEFT_hostssum = 0;
                double IHEFT_SLRSUM = 0;
                int IHEFT_vcpusum = 0;
                int IHEFT_hostssum = 0;
                double DHEFT_SLRSUM = 0;
                int DHEFT_vcpusum = 0;
                int DHEFT_hostssum = 0;
                double KHEFT_SLRSUM = 0;
                int KHEFT_vcpusum = 0;
                int KHEFT_hostssum = 0;
                while (slr_KHEFT_ite.hasNext()) {

                    double slr_HEFT = slr_HEFT_ite.next();
                    int vcpu_HEFT = vcpu_HEFT_ite.next();
                    int hosts_HEFT = hosts_HEFT_ite.next();

                    HEFT_SLRSUM += slr_HEFT;
                    HEFT_vcpusum += vcpu_HEFT;
                    HEFT_hostssum += hosts_HEFT;

                    double slr_IHEFT = slr_IHEFT_ite.next();
                    int vcpu_IHEFT = vcpu_IHEFT_ite.next();
                    int hosts_IHEFT = hosts_IHEFT_ite.next();

                    IHEFT_SLRSUM += slr_IHEFT;
                    IHEFT_vcpusum += vcpu_IHEFT;
                    IHEFT_hostssum += hosts_IHEFT;

                    double slr_DHEFT = slr_DHEFT_ite.next();
                    int vcpu_DHEFT = vcpu_DHEFT_ite.next();
                    int hosts_DHEFT = hosts_DHEFT_ite.next();

                    DHEFT_SLRSUM += slr_DHEFT;
                    DHEFT_vcpusum += vcpu_DHEFT;
                    DHEFT_hostssum += hosts_DHEFT;

                    double slr_KHEFT = slr_KHEFT_ite.next();
                    int vcpu_KHEFT = vcpu_KHEFT_ite.next();
                    int hosts_KHEFT = hosts_KHEFT_ite.next();

                    KHEFT_SLRSUM += slr_KHEFT;
                    KHEFT_vcpusum += vcpu_KHEFT;
                    KHEFT_hostssum += hosts_KHEFT;

                    pw.print(slr_HEFT);
                    pw.print(",");
                    pw.print(vcpu_HEFT);
                    pw.print(",");
                    pw.print(hosts_HEFT);

                    pw.print(",");
                    pw.print(" ");
                    pw.print(",");

                    pw.print(slr_IHEFT);
                    pw.print(",");
                    pw.print(vcpu_IHEFT);
                    pw.print(",");
                    pw.print(hosts_IHEFT);

                    pw.print(",");
                    pw.print(" ");
                    pw.print(",");

                    pw.print(slr_DHEFT);
                    pw.print(",");
                    pw.print(vcpu_DHEFT);
                    pw.print(",");
                    pw.print(hosts_DHEFT);

                    pw.print(",");
                    pw.print(" ");
                    pw.print(",");

                    pw.print(slr_KHEFT);
                    pw.print(",");
                    pw.print(vcpu_KHEFT);
                    pw.print(",");
                    pw.print(hosts_KHEFT);
                    pw.println();
                }
                double HEHT_SLRave = HEFT_SLRSUM / roop;
                double HEFT_vcpuave = HEFT_vcpusum / (double)roop;
                double HEFT_hostsave = HEFT_hostssum / (double)roop;

                double IHEFT_SLRave = IHEFT_SLRSUM / roop;
                double IHEFT_vcpuave = IHEFT_vcpusum / (double)roop;
                double IHEFT_hostsave = IHEFT_hostssum / (double)roop;

                double DHEFT_SLRave = DHEFT_SLRSUM / roop;
                double DHEFT_vcpuave = DHEFT_vcpusum / (double)roop;
                double DHEFT_hostsave = DHEFT_hostssum / (double)roop;

                double KHEFT_SLRave = KHEFT_SLRSUM / roop;
                double KHEFT_vcpuave = KHEFT_vcpusum / (double)roop;
                double KHEFT_hostsave = KHEFT_hostssum / (double)roop;

                pw.println();
                pw.print(HEHT_SLRave);
                pw.print(",");
                pw.print(HEFT_vcpuave);
                pw.print(",");
                pw.print(HEFT_hostsave);

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print(IHEFT_SLRave);
                pw.print(",");
                pw.print(IHEFT_vcpuave);
                pw.print(",");
                pw.print(IHEFT_hostsave);

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print(DHEFT_SLRave);
                pw.print(",");
                pw.print(DHEFT_vcpuave);
                pw.print(",");
                pw.print(DHEFT_hostsave);

                pw.print(",");
                pw.print(" ");
                pw.print(",");

                pw.print(KHEFT_SLRave);
                pw.print(",");
                pw.print(KHEFT_vcpuave);
                pw.print(",");
                pw.print(KHEFT_hostsave);
                pw.println();

                System.out.println("CSV--OK");

            } catch (Exception e) {
                e.printStackTrace();
            } finally {

                pw.flush();
                pw.close();
            }
    }
}

