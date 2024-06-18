package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class KHEFTBESTAlgorithm extends HEFT_VNFAlgorithm {
    public KHEFTBESTAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;
        VCPU retCPU = null;

        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            //ESTを計算する
            double est = this.calcEST(vnf, cpu);
            //完了時刻を計算する
            double fTime = est + this.calcExecTime(vnf.getWorkLoad(), cpu);
            //VNFの完了時刻を最小にするVCPUを探す
            //找到使 VNF 完成时间最小化的 VCPU
            if (fTime <= ret_finishtime) {
                ret_finishtime = fTime;
                ret_starttime = est;
                retCPU = cpu;
            }

            //DockerイメージのDLが必要かを判別する
            //判断是否需要Docker镜像DL
            double dTime = this.calcDownloadImageTimeBest(vnf, cpu);
            if (dTime == -1) {
                continue;
            }
            //イメージのDL完了時刻:DLInfoから取得
            //图像DL完成时间：从DLInfo获得
            double dCompTime = this.getDLInfo(vnf, cpu).get("finish");
            //DL完了時刻がタスクの実行開始時刻に間に合うか判別
            //間に合う:DLを割り当て
            //間に合わない:DHEFTAlgorithmを使う
            //判断DL完成时间是否赶上任务执行开始时间
            //准时：分配DL
            //无法及时完成：使用 DHEFTAlgorithm
            if (dCompTime <= est) {
                continue;
            } else if (dCompTime > est) {
                double DHEFT_fTime = est + dTime + this.calcExecTime(vnf.getWorkLoad(), cpu);
                if (DHEFT_fTime <= ret_finishtime) {
                    ret_finishtime = DHEFT_fTime;
                    ret_starttime = est;
                    retCPU = cpu;
                }
            }
        }

        //DLQueueにVNFを追加
        LinkedList<VNF> dlQueue = retCPU.getDlQueue();
        dlQueue.add(vnf);
        retCPU.setDlQueue(dlQueue);

        //vnfの時刻を更新する．
        vnf.setStartTime(ret_starttime);
        vnf.setFinishTime(ret_finishtime);
        vnf.setEST(ret_starttime);
        vnf.setvCPUID(retCPU.getPrefix());

        //retCPUにおいて，vnfを追加する
        this.addVNFQueue(retCPU, vnf);
        //VMを取得する。

        double ct = this.calcCT(retCPU);
        retCPU.setFinishTimeAtClusteringPhase(ct);
        //retCPUの時刻更新

        this.assignedVCPUMap.put(retCPU.getPrefix(), retCPU);
        Long DCID = NFVUtil.getIns().getDCID(retCPU.getPrefix());
        Cloud cloud = this.env.getDcMap().get(DCID);
        Long HostID = NFVUtil.getIns().getHostID(retCPU.getPrefix());
        ComputeHost host = cloud.getComputeHostMap().get(HostID);
        this.hostSet.put(DCID + NFVUtil.DELIMITER + HostID, host);

        //未スケジュール集合から削除する．
        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));

        //Freeリスト更新
        this.updateFreeList(vnf);

    }
}
