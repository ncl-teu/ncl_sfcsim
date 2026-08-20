package net.gripps.cloud.nfv.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.clustering.SF_CUVAlgorithm;
import net.gripps.cloud.nfv.clustering.RandomVNFClusteringAlgorithm;
import net.gripps.cloud.nfv.listscheduling.*;
import net.gripps.cloud.nfv.optimization.CoordVNFAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Properties;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/01.
 */
public class NFVSchedulingTest {
    public static void main(String[] args) {
        if (args == null || args.length < 1) {
            System.err.println("Usage: NFVSchedulingTest <nfv.properties> [DAG]");
            System.err.println("  [DAG] default=OFF, enable values: DAG/on/true/1/yes");
            return;
        }
        //設定ファイルを取得
        String fileName = args[0];
        boolean dagExportEnabled = isDagExportEnabled(args);
        System.out.println("[DAG-EXPORT] " + (dagExportEnabled ? "ON" : "OFF"));
        //Utilの初期化（設定ファイルの値の読み込み）
        //单例实例
        NFVUtil.getIns().initialize(fileName);

        //SFCの生成
        //VNF集合の生成
        //   SFC sfc = SFCGenerator.getIns().singleSFCProcess();
        //其实SFC就是DAG，VNF就是DAG中的节点，数据依赖关系就是DAG中的边。每个节点的属性包括工作量等，每条边的属性包括数据大小等。
        //把SFC生成的过程放在一个单例类中，生成一次后，复制多份，分别用于不同算法的测试，保证输入的一致性。
        SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
        // SFC sfc = SFCGenerator.getIns().singleSFCProcess();
        //By SUN
        //这里注释掉了，后面现用现生成。
        //SFC sfc2 = (SFC) sfc.deepCopy();
        //SFC sfc3 = (SFC) sfc.deepCopy();
        //SFC sfc4 = (SFC) sfc.deepCopy();
        //SFC sfc5 = (SFC) sfc.deepCopy();
        //SFC sfc6 = (SFC) sfc.deepCopy();


        //次はクラウド環境の生成
        //設定値の読み込みを行う．
        //把CloudEnvironment的生成过程放在一个单例类中，生成一次后，复制多份，分别用于不同算法的测试，保证输入的一致性。
        //其实Cloud就是任务处理节点的集合，任务处理节点的属性包括计算能力和带宽等。
        CloudUtil.getInstance().initialize(fileName);
        NFVEnvironment env = new NFVEnvironment();
        // Print key runtime knobs to avoid config-file confusion during experiments.
        System.out.println("ConfigFile:" + fileName
                + " / dl_mode=" + NFVUtil.cloud_container_dl_mode
                + " / repository_bw=" + NFVUtil.repository_bw
                + " / image_size_range=" + NFVUtil.vnf_image_size_min + "-" + NFVUtil.vnf_image_size_max
                + " / nheft_vcpu_eft_tolerance=" + NFVUtil.nheft_vcpu_eft_tolerance
                + " / nheft_vcpu_open_requires_comp_advantage="
                + NFVUtil.nheft_vcpu_open_requires_comp_advantage
                + " / nheft_vcpu_open_requires_drt_advantage="
                + NFVUtil.nheft_vcpu_open_requires_drt_advantage
                + " / nheft_vcpu_open_requires_irt_advantage="
                + NFVUtil.nheft_vcpu_open_requires_irt_advantage
                + " / nheft_vcpu_open_gate_logic="
                + NFVUtil.describeNHEFTGateLogic(NFVUtil.nheft_vcpu_open_gate_logic)
                + " / random_seed=" + CloudUtil.random_seed);
        Properties experimentProps = loadPropertiesFile(fileName, "EXPERIMENT-CONFIG");
        NHEFTModeConfig baselineNheftMode = new NHEFTModeConfig(
                "NHEFT",
                NFVUtil.nheft_vcpu_eft_tolerance,
                NFVUtil.nheft_vcpu_open_requires_comp_advantage,
                NFVUtil.nheft_vcpu_open_requires_drt_advantage,
                NFVUtil.nheft_vcpu_open_requires_irt_advantage,
                NFVUtil.nheft_vcpu_open_gate_logic
        );
        NHEFTModeConfig secondaryNheftMode = loadOptionalSecondaryNHEFTMode(experimentProps);
        AlgorithmRunConfig algorithmRunConfig = loadAlgorithmRunConfig(experimentProps, secondaryNheftMode);
        printNHEFTModeConfig("NHEFT-MODE1", baselineNheftMode);
        if (secondaryNheftMode != null) {
            printNHEFTModeConfig("NHEFT-MODE2", secondaryNheftMode);
        }
        printAlgorithmRunConfig(algorithmRunConfig, secondaryNheftMode);
        //By SUN
        //这里注释掉了，后面现用现生成。
        //NFVEnvironment env2 = (NFVEnvironment) env.deepCopy();
        //NFVEnvironment env3 = (NFVEnvironment) env.deepCopy();
        //NFVEnvironment env4 = (NFVEnvironment) env.deepCopy();
        //NFVEnvironment env5 = (NFVEnvironment) env.deepCopy();
        //NFVEnvironment env6 = (NFVEnvironment) env.deepCopy();

        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        //计算总工作量（遍历VNF,VNF可以看作任务）
        long totalSize = 0;
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            totalSize += vnf.getWorkLoad();
        }

        //输出VNF和环境的基本信息，包括VNF的数量，环境中主机和vCPU的数量等。
        System.out.println("HostNum:" + env.getGlobal_hostMap().size() + "/vCPUNum:" + env.getGlobal_vcpuMap().size());

        System.out.println("===== All VNFs in SFC =====");
        Iterator<VNF> printIte = sfc.getVnfMap().values().iterator();
        while (printIte.hasNext()) {
            VNF vnf = printIte.next();
            System.out.println(
                    "VNF ID=" + vnf.getIDVector().get(1)
                            + ", type=" + vnf.getType()
                            + ", workload=" + vnf.getWorkLoad()
                            + ", imageSize=" + vnf.getImageSize()
                            + ", depth=" + vnf.getDepth()
                            + ", clusterID=" + vnf.getClusterID()
                            + ", predNum=" + vnf.getDpredList().size()
                            + ", sucNum=" + vnf.getDsucList().size()
            );
        }
        System.out.println("===== End of VNFs =====");




        //随机聚类算法的测试。首先，创建算法实例，调用mainProcess方法执行算法，然后计算并输出SLR（Schedule Length Ratio）和其他相关信息。
        //SLR是算法性能的一个指标，计算方法是算法的makespan（完成时间）除以在最大速度下处理所有任务所需的总处理时间。还输出了分配的vCPU数量、使用的主机数量和实例数量等信息。
        //评判SLR的大小，通常SLR越接近1越好，表示算法的完成时间接近于理论最优完成时间。
        //不存在SLR为0的情况，因为至少需要处理所有任务的时间。SLR大于1表示算法的完成时间超过了理论最优完成时间，SLR小于1表示算法的完成时间优于理论最优完成时间（这通常是不可能的，除非存在某些特殊情况）。因此，SLR越接近1，算法性能越好。

        SFC sfc0 = (SFC) sfc.deepCopy();
        NFVEnvironment env0 = (NFVEnvironment) env.deepCopy();
        RandomVNFClusteringAlgorithm alg1 = new RandomVNFClusteringAlgorithm(env0, sfc0);
        alg1.mainProcess();
        double time = NFVUtil.getRoundedValue((double) totalSize / (double) alg1.getMaxSpeed());
        //迭代VNF，计算总工作量、总数据大小和总边数等信息，这些信息将用于后续的性能分析和算法评估。
        //总工作量是所有VNF的工作量之和，总数据大小是所有数据依赖关系的数据大小之和，总边数是所有VNF的出边数量之和。
        //CCR（Communication to Computation Ratio）是通信时间与计算时间的比值，计算方法是平均数据大小除以平均带宽，再除以平均工作量除以平均速度。CCR越大，表示通信时间相对于计算时间越长，这可能会影响算法的性能。
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
        //正式开始计算CCR，平均速度是总速度除以vCPU数量，平均工作量是总工作量除以VNF数量，平均数据大小是总数据大小除以边的数量。然后计算平均通信时间、平均处理时间和CCR。
        double ave_speed = NFVUtil.getRoundedValue((double) totalSpeed / (double) vcpuNum);

        double ave_workload = NFVUtil.getRoundedValue((double) totalWorkload / (double) sfc.getVnfMap().size());
        double ave_datasize = NFVUtil.getRoundedValue((double) totalDataSize / (double) totalEdgeNum);

        double ave_comTime = NFVUtil.getRoundedValue((double) ave_datasize / (double) ave_bw);
        double ave_procTime = NFVUtil.getRoundedValue((double) ave_workload / (double) ave_speed);
        double CCR = NFVUtil.getRoundedValue((double) ave_comTime / (double) ave_procTime);

        // IDR_image:
        //   T_image / T_comp, where T_image uses an effective repo->host bandwidth
        //   based on per-host bottleneck BW and harmonic mean aggregation.
        double ave_image_size = calcAverageImageSize(sfc);
        double bw_img_eff = calcRepoToHostEffectiveBandwidth(env);
        double ave_image_comTime;
        if (bw_img_eff > 0.0d) {
            ave_image_comTime = NFVUtil.getRoundedValue(ave_image_size / bw_img_eff);
        } else {
            ave_image_comTime = NFVUtil.MAXValue;
        }
        double IDR_image = NFVUtil.getRoundedValue((double) ave_image_comTime / (double) ave_procTime);
        double NCCR_total = NFVUtil.getRoundedValue(CCR + IDR_image);

        System.out.println("CCR: " + CCR + " /VNF Num:" + sfc.getVnfMap().size());
        System.out.println("CCR_data: " + CCR
                + " / IDR_image: " + IDR_image
                + " / NCCR_total: " + NCCR_total);


        System.out.println("[RandomVNFClustering]----------");
        System.out.println("[RandomVNFClustering]makespan:"+alg1.getMakeSpan());
        System.out.println("[RandomVNFClustering]SLR:" + NFVUtil.getRoundedValue(alg1.getMakeSpan() / alg1.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg1.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg1.getHostSet().size() +
                "/# of Ins:" + alg1.calcTotalFunctionInstanceNum());
/*
        RandomVNFListSchedulingAlgorithm alg2 = new RandomVNFListSchedulingAlgorithm(env2, sfc2);
        alg2.mainProcess();
        System.out.println("makespan[RandomListSched]:"+alg2.getMakeSpan()+" / # of vCPUs: "+alg2.getAssignedVCPUMap().size() + "/ # of Hosts:"+alg2.getHostSet().size());
*/
        HEFT_VNFAlgorithm heft = null;
        SFC sfc6 = null;
        NFVEnvironment env6 = null;
        long heftImageDlTotal = 0L;
        if (algorithmRunConfig.runHEFT) {
            sfc6 = (SFC) sfc.deepCopy();
            env6 = (NFVEnvironment) env.deepCopy();
            heft = new HEFT_VNFAlgorithm(env6, sfc6);
            heft.mainProcess();
            System.out.println("[HEFT]----------");
            System.out.println("[HEFT]makespan:"+heft.getMakeSpan());
            System.out.println("[HEFT]SLR:" + NFVUtil.getRoundedValue(heft.getMakeSpan() / heft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + heft.getAssignedVCPUMap().size() + "/ # of Hosts:" + heft.getHostSet().size()
                    + "/# of Ins:" + heft.calcTotalFunctionInstanceNum());
            heftImageDlTotal = countActualImageDownloads(sfc6);
            System.out.println("[HEFT]imageDL_total=" + heftImageDlTotal
                    + " / fromRepo=" + heftImageDlTotal
                    + " / fromHost=0");
            heft.printCriticalPathSummary("HEFT");
        }

/*
        SFC sfc6d = (SFC) sfc.deepCopy();
        NFVEnvironment env6d = (NFVEnvironment) env.deepCopy();
        HEFTD_VNFAlgorithm heftd = new HEFTD_VNFAlgorithm(env6d, sfc6d);
        heftd.mainProcess();
        System.out.println("[HEFTD]----------");
        System.out.println("[HEFTD]makespan:"+heftd.getMakeSpan());
        System.out.println("[HEFTD]SLR:" + NFVUtil.getRoundedValue(heftd.getMakeSpan() / heftd.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + heftd.getAssignedVCPUMap().size() + "/ # of Hosts:" + heftd.getHostSet().size()
                + "/# of Ins:" + heftd.calcTotalFunctionInstanceNum());
*/
        DHEFT_VNFAlgorithm dheft = null;
        SFC sfc7 = null;
        NFVEnvironment env7 = null;
        if (algorithmRunConfig.runDHEFT) {
            sfc7 = (SFC) sfc.deepCopy();
            env7 = (NFVEnvironment) env.deepCopy();
            dheft = new DHEFT_VNFAlgorithm(env7, sfc7);
            dheft.mainProcess();
            System.out.println("[DHEFT]----------");
            System.out.println("[DHEFT]makespan:"+dheft.getMakeSpan());
            System.out.println("[DHEFT]SLR:" + NFVUtil.getRoundedValue(dheft.getMakeSpan() / dheft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + dheft.getAssignedVCPUMap().size() + "/ # of Hosts:" + dheft.getHostSet().size()
                    + "/# of Ins:" + dheft.calcTotalFunctionInstanceNum());
            System.out.println("[DHEFT]imageDL_total=" + dheft.getImageDownloadTotalCount()
                    + " / fromRepo=" + dheft.getImageDownloadFromRepoCount()
                    + " / fromHost=" + dheft.getImageDownloadFromHostCount());
            dheft.printCriticalPathSummary("DHEFT");
        }

        NHEFTRunResult baselineNheftRun = null;
        if (algorithmRunConfig.runNHEFT) {
            baselineNheftRun = runNHEFTMode(baselineNheftMode, sfc, env);
        }
        if (algorithmRunConfig.runNHEFTMode2) {
            runNHEFTMode(secondaryNheftMode, sfc, env);
            // Restore the default NHEFT knobs after the optional comparison mode
            // so any later helper code still sees the baseline configuration.
            setNHEFTMode(baselineNheftMode);
        }

        if (dagExportEnabled) {
            try {
                DAGMetadataExporter.ExportContext exportContext = DAGMetadataExporter.prepareExportContext(CloudUtil.random_seed);
                Path copiedProperties = DAGMetadataExporter.copyPropertiesFile(fileName, exportContext);

                if (heft == null || dheft == null || baselineNheftRun == null) {
                    System.err.println("[DAG-EXPORT] skipped: HEFT, DHEFT, and NHEFT must all run when DAG export is enabled.");
                    return;
                }

                DAGMetadataExporter.AlgorithmSnapshot heftSnapshot =
                        DAGMetadataExporter.buildAlgorithmSnapshot(
                                "HEFT",
                                heft,
                                sfc6,
                                env6,
                                heftImageDlTotal,
                                heftImageDlTotal,
                                0L
                        );
                DAGMetadataExporter.AlgorithmSnapshot dheftSnapshot =
                        DAGMetadataExporter.buildAlgorithmSnapshot(
                                "DHEFT",
                                dheft,
                                sfc7,
                                env7,
                                dheft.getImageDownloadTotalCount(),
                                dheft.getImageDownloadFromRepoCount(),
                                dheft.getImageDownloadFromHostCount()
                        );
                DAGMetadataExporter.AlgorithmSnapshot nheftSnapshot =
                        DAGMetadataExporter.buildAlgorithmSnapshot(
                                "NHEFT",
                                baselineNheftRun.algorithm,
                                baselineNheftRun.sfc,
                                baselineNheftRun.env,
                                baselineNheftRun.algorithm.getImageDownloadTotalCount(),
                                baselineNheftRun.algorithm.getImageDownloadFromRepoCount(),
                                baselineNheftRun.algorithm.getImageDownloadFromHostCount()
                        );

                DAGMetadataExporter.exportAll(
                        exportContext,
                        fileName,
                        copiedProperties,
                        sfc,
                        env,
                        CCR,
                        IDR_image,
                        NCCR_total,
                        heftSnapshot,
                        dheftSnapshot,
                        nheftSnapshot
                );

                System.out.println("[DAG-EXPORT] outputDir=" + exportContext.getOutputDir().toAbsolutePath());
            } catch (Exception e) {
                System.err.println("[DAG-EXPORT] failed: " + e.getMessage());
                e.printStackTrace();
            }
        }


        //SFC sfc9 = (SFC) sfc.deepCopy();
        //NFVEnvironment env9 = (NFVEnvironment) env.deepCopy();
        //NPHEFT_VNFAlgorithm npheft = new NPHEFT_VNFAlgorithm(env9, sfc9);
        //npheft.mainProcess();
        //System.out.println("makespan[NPHEFT]:"+npheft.getMakeSpan());
        //System.out.println("SLR[NPHEFT]:" + NFVUtil.getRoundedValue(npheft.getMakeSpan() / npheft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + npheft.getAssignedVCPUMap().size() + "/ # of Hosts:" + npheft.getHostSet().size()
        //        + "/# of Ins:" + npheft.calcTotalFunctionInstanceNum());
        //
        //SFC sfc10 = (SFC) sfc.deepCopy();
        //NFVEnvironment env10 = (NFVEnvironment) env.deepCopy();
        //NPPHEFT_VNFAlgorithm nppheft = new NPPHEFT_VNFAlgorithm(env10, sfc10);
        //nppheft.mainProcess();
        //System.out.println("makespan[NPPHEFT]:"+nppheft.getMakeSpan());
        //System.out.println("SLR[NPPHEFT]:" + NFVUtil.getRoundedValue(nppheft.getMakeSpan() / nppheft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + nppheft.getAssignedVCPUMap().size() + "/ # of Hosts:" + nppheft.getHostSet().size()
        //        + "/# of Ins:" + nppheft.calcTotalFunctionInstanceNum());


        ////SFC sfc4 = (SFC) sfc.deepCopy();
        //NFVEnvironment env9 = (NFVEnvironment) env.deepCopy();
        //NPHEFT_VNFAlgorithm npheft = new NPHEFT_VNFAlgorithm(env9, sfc9);
        //npheft.mainProcess();
        //System.out.println("makespan[NPHEFT]:"+npheft.getMakeSpan());
        //System.out.println("SLR[NPHEFT]:" + NFVUtil.getRoundedValue(npheft.getMakeSpan() / npheft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + npheft.getAssignedVCPUMap().size() + "/ # of Hosts:" + npheft.getHostSet().size()
        //        + "/# of Ins:" + npheft.calcTotalFunctionInstanceNum());



        //SFC sfc4 = (SFC) sfc.deepCopy();
        //NFVEnvironment env4 = (NFVEnvironment) env.deepCopy();
        //PEFT_VNFAlgorithm peft = new PEFT_VNFAlgorithm(env4, sfc4);
        //peft.mainProcess();
        //System.out.println("SLR[PEFT]:" + NFVUtil.getRoundedValue(peft.getMakeSpan() / peft.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + peft.getAssignedVCPUMap().size() + "/ # of Hosts:" + peft.getHostSet().size()
        //        + "/# of Ins:" + peft.calcTotalFunctionInstanceNum());



        //SFC sfc5 = (SFC) sfc.deepCopy();
        //NFVEnvironment env5 = (NFVEnvironment) env.deepCopy();
        //SF_CUVAlgorithm alg5 = new SF_CUVAlgorithm(env5, sfc5);
        ////alg5.setUpdateMode(0);
        //alg5.mainProcess();
        //System.out.println("SLR[SF_CUV]:" + NFVUtil.getRoundedValue(alg5.getMakeSpan() / alg5.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg5.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg5.getHostSet().size()
        //        + "/# of Ins:" + alg5.calcTotalFunctionInstanceNum());
/*
        HierarchicalVNFClusteringAlgorithm h = new HierarchicalVNFClusteringAlgorithm(env4, sfc4);
         h.configLevel();
        h.mainProcess();
        System.out.println("SLR[HClustering]:"+NFVUtil.getRoundedValue(h.getMakeSpan()/h.getTotalCPProcTimeAtMaxSpeed()) +" / # of vCPUs: "+h.getAssignedVCPUMap().size()+ "/ # of Hosts:"+h.getHostSet().size()
                +"/# of Ins:"+h.calcTotalFunctionInstanceNum());

 */
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

        //SFC sfc2 = (SFC) sfc.deepCopy();
        //NFVEnvironment env2 = (NFVEnvironment) env.deepCopy();
        //FWS_VNFAlgorithm alg2 = new FWS_VNFAlgorithm(env2, sfc2);
        ////alg6.setMaxHostNum(alg5.getHostSet().size());
        //alg2.mainProcess();
        //System.out.println("SLR[FWS]:" + NFVUtil.getRoundedValue(alg2.getMakeSpan() / alg2.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + alg2.getAssignedVCPUMap().size() + "/ # of Hosts:" + alg2.getHostSet().size()
        //        + "/# of Ins:" + alg2.calcTotalFunctionInstanceNum());



        //SFC sfc3 = (SFC) sfc.deepCopy();
        //NFVEnvironment env3 = (NFVEnvironment) env.deepCopy();
        //CoordVNFAlgorithm algc = new CoordVNFAlgorithm(env3, sfc3);
        ////alg6.setMaxHostNum(alg5.getHostSet().size());
        //algc.mainProcess();
        //
        //System.out.println("SLR[CoordVNF]:" + NFVUtil.getRoundedValue(algc.getMakeSpan() / algc.getTotalCPProcTimeAtMaxSpeed()) + " / # of vCPUs: " + algc.getAssignedVCPUMap().size() + "/ # of Hosts:" + algc.getHostSet().size()
        //        + "/# of Ins:" + algc.calcTotalFunctionInstanceNum());


    }

    private static NHEFTRunResult runNHEFTMode(NHEFTModeConfig modeConfig,
                                               SFC baseSfc,
                                               NFVEnvironment baseEnv) {
        setNHEFTMode(modeConfig);
        SFC modeSfc = (SFC) baseSfc.deepCopy();
        NFVEnvironment modeEnv = (NFVEnvironment) baseEnv.deepCopy();
        NHEFT_VNFAlgorithm algorithm = new NHEFT_VNFAlgorithm(modeEnv, modeSfc);
        algorithm.mainProcess();

        String label = modeConfig.label;
        System.out.println("[" + label + "]----------");
        System.out.println("[" + label + "]makespan:" + algorithm.getMakeSpan());
        System.out.println("[" + label + "]SLR:"
                + NFVUtil.getRoundedValue(algorithm.getMakeSpan() / algorithm.getTotalCPProcTimeAtMaxSpeed())
                + " / # of vCPUs: " + algorithm.getAssignedVCPUMap().size()
                + "/ # of Hosts:" + algorithm.getHostSet().size()
                + "/# of Ins:" + algorithm.calcTotalFunctionInstanceNum());
        System.out.println("[" + label + "]imageDL_total=" + algorithm.getImageDownloadTotalCount()
                + " / fromRepo=" + algorithm.getImageDownloadFromRepoCount()
                + " / fromHost=" + algorithm.getImageDownloadFromHostCount());
        algorithm.printCriticalPathSummary(label);
        return new NHEFTRunResult(label, modeSfc, modeEnv, algorithm);
    }

    private static void setNHEFTMode(NHEFTModeConfig modeConfig) {
        NFVUtil.nheft_vcpu_eft_tolerance = modeConfig.tolerance;
        NFVUtil.nheft_vcpu_open_requires_comp_advantage = modeConfig.requireCompAdvantage;
        NFVUtil.nheft_vcpu_open_requires_drt_advantage = modeConfig.requireDRTAdvantage;
        NFVUtil.nheft_vcpu_open_requires_irt_advantage = modeConfig.requireIRTAdvantage;
        NFVUtil.nheft_vcpu_open_gate_logic = modeConfig.gateLogic;
    }

    private static void printNHEFTModeConfig(String tag, NHEFTModeConfig modeConfig) {
        if (modeConfig == null) {
            return;
        }
        System.out.println("[" + tag + "] label=" + modeConfig.label
                + " / nheft_vcpu_eft_tolerance=" + modeConfig.tolerance
                + " / nheft_vcpu_open_requires_comp_advantage=" + modeConfig.requireCompAdvantage
                + " / nheft_vcpu_open_requires_drt_advantage=" + modeConfig.requireDRTAdvantage
                + " / nheft_vcpu_open_requires_irt_advantage=" + modeConfig.requireIRTAdvantage
                + " / nheft_vcpu_open_gate_logic="
                + NFVUtil.describeNHEFTGateLogic(modeConfig.gateLogic));
    }

    private static Properties loadPropertiesFile(String propFilePath, String tag) {
        Properties props = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(propFilePath);
            props.load(inputStream);
            return props;
        } catch (IOException e) {
            System.err.println("[" + tag + "] failed to load properties: " + e.getMessage());
            return props;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    private static NHEFTModeConfig loadOptionalSecondaryNHEFTMode(Properties props) {
        if (props == null) {
            return null;
        }

        boolean enabled = parseOptionalBooleanProperty(props, "nheft_mode2_enabled", false);
        if (!enabled) {
            return null;
        }

        String label = props.getProperty("nheft_mode2_label", "GHEFT").trim();
        if (label.length() == 0) {
            label = "GHEFT";
        }

        return new NHEFTModeConfig(
                label,
                parseOptionalDoubleProperty(
                        props,
                        "nheft_mode2_vcpu_eft_tolerance",
                        NFVUtil.nheft_vcpu_eft_tolerance
                ),
                parseOptionalZeroOneProperty(
                        props,
                        "nheft_mode2_open_requires_comp_advantage",
                        NFVUtil.nheft_vcpu_open_requires_comp_advantage
                ),
                parseOptionalZeroOneProperty(
                        props,
                        "nheft_mode2_open_requires_drt_advantage",
                        NFVUtil.nheft_vcpu_open_requires_drt_advantage
                ),
                parseOptionalZeroOneProperty(
                        props,
                        "nheft_mode2_open_requires_irt_advantage",
                        NFVUtil.nheft_vcpu_open_requires_irt_advantage
                ),
                parseOptionalGateLogicProperty(
                        props,
                        "nheft_mode2_open_gate_logic",
                        NFVUtil.nheft_vcpu_open_gate_logic
                )
        );
    }

    private static AlgorithmRunConfig loadAlgorithmRunConfig(Properties props,
                                                             NHEFTModeConfig secondaryNheftMode) {
        boolean hasMode2 = (secondaryNheftMode != null);
        boolean runHEFT = parseOptionalBooleanProperty(props, "run_heft", true);
        boolean runDHEFT = parseOptionalBooleanProperty(props, "run_dheft", true);
        boolean runNHEFT = parseOptionalBooleanProperty(props, "run_nheft", true);
        boolean runNHEFTMode2 = parseOptionalBooleanProperty(props, "run_nheft_mode2", hasMode2);

        if (!hasMode2 && runNHEFTMode2) {
            System.err.println("[RUN-CONFIG] run_nheft_mode2=1 but nheft_mode2_enabled is OFF. "
                    + "The secondary NHEFT mode will be skipped.");
            runNHEFTMode2 = false;
        }

        return new AlgorithmRunConfig(
                runHEFT,
                runDHEFT,
                runNHEFT,
                runNHEFTMode2
        );
    }

    private static void printAlgorithmRunConfig(AlgorithmRunConfig algorithmRunConfig,
                                                NHEFTModeConfig secondaryNheftMode) {
        String mode2Label = (secondaryNheftMode == null) ? "NHEFT-MODE2" : secondaryNheftMode.label;
        System.out.println("[RUN-CONFIG]"
                + " run_heft=" + (algorithmRunConfig.runHEFT ? 1 : 0)
                + " / run_dheft=" + (algorithmRunConfig.runDHEFT ? 1 : 0)
                + " / run_nheft=" + (algorithmRunConfig.runNHEFT ? 1 : 0)
                + " / run_nheft_mode2=" + (algorithmRunConfig.runNHEFTMode2 ? 1 : 0)
                + " (" + mode2Label + ")");
    }

    private static boolean isDagExportEnabled(String[] args) {
        if (args == null || args.length < 2) {
            return false;
        }
        String raw = args[1];
        if (raw == null) {
            return false;
        }
        String val = raw.trim().toLowerCase();
        if (val.isEmpty()) {
            return false;
        }

        int eqIdx = val.indexOf('=');
        if (eqIdx > 0) {
            String key = val.substring(0, eqIdx).trim();
            String rhs = val.substring(eqIdx + 1).trim();
            if ("dag".equals(key) || "exportdag".equals(key) || "dagexport".equals(key)) {
                return parseSwitchBoolean(rhs, false);
            }
        }

        if ("dag".equals(val)) {
            return true;
        }
        return parseSwitchBoolean(val, false);
    }

    private static boolean parseSwitchBoolean(String val, boolean defaultValue) {
        if (val == null) {
            return defaultValue;
        }
        String v = val.trim().toLowerCase();
        if (v.isEmpty()) {
            return defaultValue;
        }
        if ("1".equals(v) || "true".equals(v) || "on".equals(v)
                || "yes".equals(v) || "y".equals(v)
                || "enable".equals(v) || "enabled".equals(v)) {
            return true;
        }
        if ("0".equals(v) || "false".equals(v) || "off".equals(v)
                || "no".equals(v) || "n".equals(v)
                || "disable".equals(v) || "disabled".equals(v)) {
            return false;
        }
        return defaultValue;
    }

    private static boolean parseOptionalBooleanProperty(Properties props,
                                                        String key,
                                                        boolean defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        String rawValue = props.getProperty(key);
        if (rawValue == null) {
            return defaultValue;
        }
        return parseSwitchBoolean(rawValue, defaultValue);
    }

    private static double parseOptionalDoubleProperty(Properties props,
                                                      String key,
                                                      double defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        String rawValue = props.getProperty(key);
        if (rawValue == null || rawValue.trim().length() == 0) {
            return defaultValue;
        }
        try {
            double value = Double.valueOf(rawValue.trim()).doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d) {
                throw new NumberFormatException("not a finite non-negative ratio");
            }
            return value;
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + key + "='" + rawValue
                    + "'; using default " + defaultValue);
            return defaultValue;
        }
    }

    private static int parseOptionalZeroOneProperty(Properties props,
                                                    String key,
                                                    int defaultValue) {
        if (props == null) {
            return defaultValue;
        }
        String rawValue = props.getProperty(key);
        if (rawValue == null || rawValue.trim().length() == 0) {
            return defaultValue;
        }

        String value = rawValue.trim();
        try {
            if ("true".equalsIgnoreCase(value)) {
                return 1;
            }
            if ("false".equalsIgnoreCase(value)) {
                return 0;
            }
            int flag = Integer.valueOf(value).intValue();
            if (flag != 0 && flag != 1) {
                throw new NumberFormatException("not 0 or 1");
            }
            return flag;
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + key + "='" + rawValue
                    + "'; using default " + defaultValue);
            return defaultValue;
        }
    }

    private static int parseOptionalGateLogicProperty(Properties props,
                                                      String key,
                                                      int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim().toLowerCase();
        if (value.length() == 0) {
            return defaultValue;
        }
        if ("all".equals(value) || "and".equals(value) || "strict".equals(value) || "0".equals(value)) {
            return NFVUtil.NHEFT_VCPU_OPEN_GATE_LOGIC_ALL;
        }
        if ("any".equals(value) || "or".equals(value) || "relaxed".equals(value) || "1".equals(value)) {
            return NFVUtil.NHEFT_VCPU_OPEN_GATE_LOGIC_ANY;
        }
        System.err.println("[NHEFT-MODE2] invalid " + key + "=" + raw + ", fallback=" + defaultValue);
        return defaultValue;
    }

    private static long countActualImageDownloads(SFC sfc) {
        if (sfc == null || sfc.getVnfMap() == null) {
            return 0L;
        }
        long count = 0L;
        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            if (vnf == null) {
                continue;
            }
            double dlStart = vnf.getDlStartTime();
            double dlFinish = vnf.getDlFinishTime();
            if (dlStart >= 0.0d && dlFinish >= 0.0d && (dlFinish - dlStart) > 0.000001d) {
                count++;
            }
        }
        return count;
    }

    /**
     * Average image size over VNFs that have positive image size.
     */
    private static double calcAverageImageSize(SFC sfc) {
        if (sfc == null || sfc.getVnfMap() == null || sfc.getVnfMap().isEmpty()) {
            return 0.0d;
        }
        long totalImageSize = 0L;
        long imageVnfCount = 0L;
        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            if (vnf == null) {
                continue;
            }
            long imageSize = vnf.getImageSize();
            if (imageSize <= 0L) {
                continue;
            }
            totalImageSize += imageSize;
            imageVnfCount++;
        }
        if (imageVnfCount == 0L) {
            return 0.0d;
        }
        return NFVUtil.getRoundedValue((double) totalImageSize / (double) imageVnfCount);
    }

    /**
     * Effective repo->host bandwidth for image download:
     * 1) For each host h, use bottleneck BW from repo to h.
     * 2) Aggregate per-host BW by harmonic mean (time-domain consistent).
     */
    private static double calcRepoToHostEffectiveBandwidth(NFVEnvironment env) {
        if (env == null || env.getGlobal_hostMap() == null || env.getGlobal_hostMap().isEmpty()) {
            return 0.0d;
        }
        ComputeHost repoHost = env.getDockerRepository();
        if (repoHost == null) {
            return 0.0d;
        }

        long repoBW = repoHost.getBw();
        if (repoBW <= 0L) {
            return 0.0d;
        }

        long repoDcID = repoHost.getDcID();
        Cloud repoCloud = env.getDcMap().get(repoDcID);
        long repoDcBW = (repoCloud == null) ? NFVUtil.MAXValue : repoCloud.getBw();

        double reciprocalSum = 0.0d;
        long validHostCount = 0L;

        Iterator<ComputeHost> hIte = env.getGlobal_hostMap().values().iterator();
        while (hIte.hasNext()) {
            ComputeHost dstHost = hIte.next();
            if (dstHost == null) {
                continue;
            }

            long hostPathBW = Math.min(repoBW, dstHost.getBw());
            if (hostPathBW <= 0L) {
                continue;
            }

            long dstDcID = dstHost.getDcID();
            long realBW = hostPathBW;
            if (repoDcID != dstDcID) {
                Cloud dstCloud = env.getDcMap().get(dstDcID);
                long dstDcBW = (dstCloud == null) ? NFVUtil.MAXValue : dstCloud.getBw();
                long dcPathBW = Math.min(repoDcBW, dstDcBW);
                realBW = Math.min(hostPathBW, dcPathBW);
            }

            if (realBW <= 0L) {
                continue;
            }

            reciprocalSum += 1.0d / (double) realBW;
            validHostCount++;
        }

        if (validHostCount == 0L || reciprocalSum <= 0.0d) {
            return 0.0d;
        }
        return NFVUtil.getRoundedValue((double) validHostCount / reciprocalSum);
    }

    private static class NHEFTModeConfig {
        private final String label;
        private final double tolerance;
        private final int requireCompAdvantage;
        private final int requireDRTAdvantage;
        private final int requireIRTAdvantage;
        private final int gateLogic;

        private NHEFTModeConfig(String label,
                                double tolerance,
                                int requireCompAdvantage,
                                int requireDRTAdvantage,
                                int requireIRTAdvantage,
                                int gateLogic) {
            this.label = label;
            this.tolerance = tolerance;
            this.requireCompAdvantage = requireCompAdvantage;
            this.requireDRTAdvantage = requireDRTAdvantage;
            this.requireIRTAdvantage = requireIRTAdvantage;
            this.gateLogic = gateLogic;
        }
    }

    private static class AlgorithmRunConfig {
        private final boolean runHEFT;
        private final boolean runDHEFT;
        private final boolean runNHEFT;
        private final boolean runNHEFTMode2;

        private AlgorithmRunConfig(boolean runHEFT,
                                   boolean runDHEFT,
                                   boolean runNHEFT,
                                   boolean runNHEFTMode2) {
            this.runHEFT = runHEFT;
            this.runDHEFT = runDHEFT;
            this.runNHEFT = runNHEFT;
            this.runNHEFTMode2 = runNHEFTMode2;
        }
    }

    private static class NHEFTRunResult {
        private final String label;
        private final SFC sfc;
        private final NFVEnvironment env;
        private final NHEFT_VNFAlgorithm algorithm;

        private NHEFTRunResult(String label,
                               SFC sfc,
                               NFVEnvironment env,
                               NHEFT_VNFAlgorithm algorithm) {
            this.label = label;
            this.sfc = sfc;
            this.env = env;
            this.algorithm = algorithm;
        }
    }
}
