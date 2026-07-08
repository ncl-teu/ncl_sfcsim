package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.Iterator;
import java.util.HashMap;

/**
 * Created by User on 2026/04/08.
 * NHEFT_VNFAlgorithm：HEFT算法的改进版本
 *
 * 这是一个新的调度算法，基于HEFT（Heterogeneous Earliest Finish Time）算法
 */
public class DHEFT_VNFAlgorithm extends BaseVNFSchedulingAlgorithm {

    private static final double EPS = 0.000001d;
    protected long imageDownloadTotalCount;
    protected long imageDownloadFromRepoCount;
    protected long imageDownloadFromHostCount;
    protected HashMap<String, String> imageDownloadSourceByVnf;

    private static final class DownloadPlan {
        private VM sourceVM;
        private boolean fromRepo;
        private double startTime;
        private double finishTime;
        private ComputeHost sourceHost;
        private ComputeHost targetHost;
    }

    public DHEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.imageDownloadTotalCount = 0L;
        this.imageDownloadFromRepoCount = 0L;
        this.imageDownloadFromHostCount = 0L;
        this.imageDownloadSourceByVnf = new HashMap<String, String>();
    }

    protected void recordImageDownload(boolean fromRepo, double startTime, double finishTime) {
        if (finishTime - startTime <= EPS) {
            return;
        }
        this.imageDownloadTotalCount++;
        if (fromRepo) {
            this.imageDownloadFromRepoCount++;
        } else {
            this.imageDownloadFromHostCount++;
        }
    }

    public long getImageDownloadTotalCount() {
        return imageDownloadTotalCount;
    }

    public long getImageDownloadFromRepoCount() {
        return imageDownloadFromRepoCount;
    }

    public long getImageDownloadFromHostCount() {
        return imageDownloadFromHostCount;
    }

    protected String buildVnfKey(VNF vnf) {
        if (vnf == null || vnf.getIDVector() == null || vnf.getIDVector().size() < 2) {
            return null;
        }
        return String.valueOf(vnf.getIDVector().get(0))
                + NFVUtil.DELIMITER
                + String.valueOf(vnf.getIDVector().get(1));
    }

    protected void recordImageDownloadSource(VNF vnf, boolean hasDownload, boolean fromRepo, VM sourceVM) {
        String key = this.buildVnfKey(vnf);
        if (key == null) {
            return;
        }
        if (!hasDownload) {
            this.imageDownloadSourceByVnf.put(key, "none");
            return;
        }
        if (fromRepo) {
            this.imageDownloadSourceByVnf.put(key, "repo");
            return;
        }
        if (sourceVM != null && sourceVM.getVMID() != null) {
            this.imageDownloadSourceByVnf.put(key, "vm:" + sourceVM.getVMID());
            return;
        }
        this.imageDownloadSourceByVnf.put(key, "host");
    }

    @Override
    public String getImageDownloadSourceForVNF(VNF vnf) {
        String key = this.buildVnfKey(vnf);
        if (key == null) {
            return null;
        }
        return this.imageDownloadSourceByVnf.get(key);
    }

    public VNF selectVNF() {
        //Freeリストから選択する．
        long size = this.freeVNFSet.getList().size();
        Iterator<Long> idIte = this.freeVNFSet.iterator();
        Long retID = 0L;
        VNF selectedVNF = null;
        double maxBlevel = -1d;
        while (idIte.hasNext()) {
            Long id = idIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            if(vnf.getBlevel() >= maxBlevel){
                maxBlevel = vnf.getBlevel();
                selectedVNF = vnf;
            }

        }
        //SFCから，指定IDのVNFを取得する．
         //selectedVNF = this.sfc.findVNFByLastID(retID);
        //VNFをスケジュールする．これは，親クラスであるAbstractFairSchedulingAlgorithmのscheduleVNFメソッド
        //をcallしており，fairnessに基づいて割り当てている．
        //this.scheduleVNF(selectedVNF, this.env.getGlobal_vcpuMap());

        return selectedVNF;
    }
    //@Override
    //public double calcImageComTime(VNF vnf, VCPU vcpu) {
    //    // ★ DHEFT中，calcImageComTime()现在只是返回从repo下载的时间
    //    // 复用逻辑已经在calcDownloadImageTime()中实现了
    //    // 这样可以避免在getDLInfo()调用链中重复计算
    //    return this.calcImageComTimeFromRepo(vnf, vcpu);
    //}





    /**
     * 计算“从其他 VM 复用镜像”的下载计划。
     * 规则：基于 Host 级队列建模，源 Host 与目标 Host 都必须空闲后才能开始传输。
     */
    private DownloadPlan buildVmSourcePlan(VNF vnf, VCPU targetVcpu, VM sourceVM) {
        if (sourceVM == null) {
            return null;
        }

        VM targetVM = this.findVM(targetVcpu);
        if (targetVM == null) {
            return null;
        }

        double readyTime = this.getImageReadyTimeOnVM(sourceVM, vnf.getType());
        if (readyTime >= NFVUtil.MAXValue) {
            return null;
        }

        ComputeHost srcHost = this.findHostByVM(sourceVM);
        ComputeHost dstHost = this.findHostByVCPU(targetVcpu);
        if (srcHost == null || dstHost == null) {
            return null;
        }

        double sourceBusyUntil = this.getHostQueueTail(srcHost);
        double targetBusyUntil = this.getHostQueueTail(dstHost);
        double start = Math.max(readyTime, Math.max(sourceBusyUntil, targetBusyUntil));
        double duration;
        if (sourceVM.getVMID().equals(targetVM.getVMID())) {
            // Same VM: no transfer needed; only wait until image becomes ready.
            duration = 0.0d;
        } else {
            if (srcHost.getDcID().longValue() == dstHost.getDcID().longValue()
                    && srcHost.getMachineID() == dstHost.getMachineID()) {
                // Same physical host: treat as local access.
                duration = 0.0d;
            } else {
                long dataSize = vnf.getImageSize();
                long bw = Math.min(srcHost.getBw(), dstHost.getBw());
                if (bw <= 0) {
                    return null;
                }
                duration = CloudUtil.getRoundedValue((double) dataSize / (double) bw);
            }
        }

        DownloadPlan plan = new DownloadPlan();
        plan.sourceVM = sourceVM;
        plan.fromRepo = false;
        plan.startTime = start;
        plan.finishTime = start + duration;
        plan.sourceHost = srcHost;
        plan.targetHost = dstHost;
        return plan;
    }

    /**
     * 计算“从 Docker repository 下载”的计划。
     * 规则：基于 Host 级队列建模，repo Host 与目标 Host 都必须空闲后才能开始传输。
     */
    private DownloadPlan buildRepoPlan(VNF vnf, VCPU targetVcpu) {
        ComputeHost dstHost = this.findHostByVCPU(targetVcpu);
        NFVEnvironment nEnv = (NFVEnvironment) this.env;
        ComputeHost repoHost = nEnv.getDockerRepository();
        if (dstHost == null || repoHost == null) {
            return null;
        }

        DownloadPlan plan = new DownloadPlan();
        plan.fromRepo = true;
        plan.sourceVM = null;
        plan.sourceHost = repoHost;
        plan.targetHost = dstHost;

        double repoQueueTail = this.getHostQueueTail(repoHost);
        double targetQueueTail = this.getHostQueueTail(dstHost);
        double queueTail = Math.max(repoQueueTail, targetQueueTail);
        double duration = this.calcImageComTimeFromRepo(vnf, targetVcpu);
        if (duration >= NFVUtil.MAXValue) {
            return null;
        }
        plan.startTime = queueTail;
        plan.finishTime = queueTail + duration;
        return plan;
    }

    /**
     * 计算最佳下载方案：先看 repo，再遍历所有缓存 VM；若当前目标 VM 自身已有 image，则直接 0。
     */
    private DownloadPlan findBestDownloadPlan(VNF vnf, VCPU targetVcpu) {
        VM targetVM = this.findVM(targetVcpu);
        if (targetVM == null) {
            return null;
        }

        if (this.isTraceEnabled()) {
            this.trace("[DHEFT-DL-START]",
                    this.formatVNF(vnf)
                            + " target=" + this.formatVCPU(targetVcpu));
        }

        double targetReadyTime = this.getImageReadyTimeOnVM(targetVM, vnf.getType());
        if (targetReadyTime <= EPS) {
            DownloadPlan zero = new DownloadPlan();
            zero.sourceVM = targetVM;
            zero.fromRepo = false;
            zero.startTime = 0.0d;
            zero.finishTime = 0.0d;
            zero.sourceHost = this.findHostByVM(targetVM);
            zero.targetHost = this.findHostByVCPU(targetVcpu);
            if (this.isTraceEnabled()) {
                this.trace("[DHEFT-DL-REUSE]",
                        this.formatVNF(vnf)
                                + " target=" + this.formatVCPU(targetVcpu)
                                + " alreadyHasImage=true (same VM reuse)");
            }
            return zero;
        }

        DownloadPlan best = this.buildRepoPlan(vnf, targetVcpu);
        if (this.isTraceEnabled() && best != null) {
            this.trace("[DHEFT-DL-REPO]",
                    this.formatVNF(vnf)
                            + " target=" + this.formatVCPU(targetVcpu)
                            + " repoStart=" + best.startTime
                            + " repoFinish=" + best.finishTime);
        }

        Iterator<VM> vmIterator = this.env.getGlobal_vmMap().values().iterator();
        while (vmIterator.hasNext()) {
            VM vm = vmIterator.next();
            if (vm == null) {
                continue;
            }

            DownloadPlan candidate = this.buildVmSourcePlan(vnf, targetVcpu, vm);
            if (candidate == null) {
                continue;
            }

            if (this.isTraceEnabled()) {
                this.trace("[DHEFT-DL-CAND]",
                        this.formatVNF(vnf)
                                + " target=" + this.formatVCPU(targetVcpu)
                                + " sourceVM=" + vm.getVMID()
                                + " start=" + candidate.startTime
                                + " finish=" + candidate.finishTime
                                + " fromRepo=" + candidate.fromRepo);
            }

            if (best == null || candidate.finishTime < best.finishTime) {
                best = candidate;
            }
        }

        if (this.isTraceEnabled() && best != null) {
            this.trace("[DHEFT-DL-SELECT]",
                    this.formatVNF(vnf)
                            + " target=" + this.formatVCPU(targetVcpu)
                            + " selectedFinish=" + best.finishTime
                            + " selectedStart=" + best.startTime
                            + " fromRepo=" + best.fromRepo
                            + " sourceVM=" + (best.sourceVM == null ? "null" : best.sourceVM.getVMID()));
        }

        return best;
    }

    private double getImageReadyTimeOnVM(VM vm, int type) {
        if (vm == null) {
            return NFVUtil.MAXValue;
        }

        double readyFromRegistry = vm.getImageReadyTime(type);
        if (readyFromRegistry < NFVUtil.MAXValue) {
            return readyFromRegistry;
        }

        if (!vm.containsType(type)) {
            return NFVUtil.MAXValue;
        }

        boolean foundProducer = false;
        double readyTime = 0.0d;
        Iterator<VCPU> vmVcpuIte = vm.getvCPUMap().values().iterator();
        while (vmVcpuIte.hasNext()) {
            VCPU vmVcpu = vmVcpuIte.next();
            Iterator<VNF> qIte = vmVcpu.getVnfQueue().iterator();
            while (qIte.hasNext()) {
                VNF qVnf = qIte.next();
                if (qVnf.getType() != type) {
                    continue;
                }
                foundProducer = true;
                double cachedReady = qVnf.getDlFinishTime();
                if (cachedReady <= 0.0d) {
                    cachedReady = qVnf.getFinishTime();
                }
                if (cachedReady > readyTime) {
                    readyTime = cachedReady;
                }
            }
        }

        // If there is no producer in any queue, treat it as preloaded.
        return foundProducer ? readyTime : 0.0d;
    }

    //这里重写了calcDownloadImageTime方法，实现考虑复用的镜像下载时间计算
    //改进版核心逻辑：
    // 1. 如果当前VM已有该image，返回0
    // 2. 否则，从其他已缓存的VM下载（如果存在），或从repo下载
    // 3. 传输排队由 Host 级队列统一建模（source host + target host）
    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU vcpu) {
        DownloadPlan plan = this.findBestDownloadPlan(vnf, vcpu);
        if (plan == null) {
            return NFVUtil.MAXValue;
        }

        // Mirror the selected source queue semantics with a pure duration return.
        return Math.max(0.0d, plan.finishTime - plan.startTime);
    }


    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu) {
        HashMap<String, Double> map = new HashMap<String, Double>();

        DownloadPlan plan = this.findBestDownloadPlan(vnf, vcpu);
        if (plan == null) {
            map.put("start", (double) NFVUtil.MAXValue);
            map.put("finish", (double) NFVUtil.MAXValue);
            return map;
        }

        map.put("start", plan.startTime);
        map.put("finish", plan.finishTime);
        return map;
    }

    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;

        VCPU retCPU = null;
        if (this.isTraceEnabled()) {
            this.trace("[DHEFT-START]",
                    "Scheduling " + this.formatVNF(vnf)
                            + ", candidateVCPU=" + map.size());
        }
        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            if (cpu.getVMID() == null || this.env.getGlobal_vmMap().get(cpu.getVMID()) == null) {
                continue;
            }
            double est = this.calcEST(vnf, cpu);
            double dTime = this.calcDownloadImageTime(vnf, cpu);
            double execTime = this.calcExecTime(vnf.getWorkLoad(), cpu);
            //SUN 这个地方bug了，计算了两次dTime
            //double ftime = est + dTime + execTime;
            double ftime = est + execTime;
            if (this.isTraceEnabled()) {
                this.trace("[DHEFT-CAND]",
                        this.formatVNF(vnf)
                                + " -> " + this.formatVCPU(cpu)
                                + ", est=" + est
                                + ", dlTime=" + dTime
                                + ", execTime=" + execTime
                                + ", finish=" + ftime);
            }
            if (ftime <= ret_finishtime) {
                ret_finishtime = ftime;
                ret_starttime = est;
                retCPU = cpu;
            }
        }

        if (retCPU == null) {
            throw new IllegalStateException("No VM-bound vCPU candidate found for VNF " + vnf.getIDVector().get(1));
        }

        if (this.isTraceEnabled()) {
            this.trace("[DHEFT-SELECT]",
                    this.formatVNF(vnf)
                            + " -> " + this.formatVCPU(retCPU)
                            + ", start=" + ret_starttime
                            + ", finish=" + ret_finishtime);
        }

        //这个地方就是找到最佳下载方案，并将下载时间信息直接写入VNF对象中，后续在添加到队列时会根据这个信息来排队
        DownloadPlan plan = this.findBestDownloadPlan(vnf, retCPU);
        if (plan != null) {
            vnf.setDlStartTime(plan.startTime);
            vnf.setDlFinishTime(plan.finishTime);
            boolean hasDownload = plan.finishTime > plan.startTime + EPS;
            this.recordImageDownloadSource(vnf, hasDownload, plan.fromRepo, plan.sourceVM);
            if (this.isTraceEnabled()) {
                this.trace("[DHEFT-COMMIT]",
                        "VNF=" + vnf.getIDVector().get(1)
                                + " target=" + retCPU.getPrefix()
                                + " dlStart=" + plan.startTime
                                + " dlFinish=" + plan.finishTime
                                + " fromRepo=" + plan.fromRepo
                                + " sourceVM=" + (plan.sourceVM == null ? "null" : plan.sourceVM.getVMID()));
            }
            // Only commit real transfers to host queues.
            // Zero-transfer local reuse should not pollute queues.
            if (plan.finishTime > plan.startTime) {
                this.recordImageDownload(plan.fromRepo, plan.startTime, plan.finishTime);
                this.commitHostQueues(vnf, plan);
            }
        } else {
            vnf.setDlStartTime(0.0d);
            vnf.setDlFinishTime(0.0d);
            this.recordImageDownloadSource(vnf, false, false, null);
        }

        vnf.setStartTime(ret_starttime);
        vnf.setFinishTime(ret_finishtime);
        vnf.setEST(ret_starttime);
        vnf.setvCPUID(retCPU.getPrefix());

        this.addVNFQueue(retCPU, vnf);
        this.markImageTypeForVM(vnf, retCPU);

        double ct = this.calcCT(retCPU);
        retCPU.setFinishTimeAtClusteringPhase(ct);

        this.assignedVCPUMap.put(retCPU.getPrefix(), retCPU);
        Long DCID = NFVUtil.getIns().getDCID(retCPU.getPrefix());
        Cloud cloud = this.env.getDcMap().get(DCID);
        Long HostID = NFVUtil.getIns().getHostID(retCPU.getPrefix());
        ComputeHost host = cloud.getComputeHostMap().get(HostID);
        this.hostSet.put(DCID + NFVUtil.DELIMITER + HostID, host);

        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));
        this.updateFreeList(vnf);
    }

    public void mainProcess() {
        //未スケジュールなVNFが残っている間，行うループ
        while (!this.getUnScheduledVNFSet().isEmpty()) {
            VNF vnf = this.selectVNF();
            if(vnf == null){
                System.out.println("test");
            }
            //vcpu全体から，vnfの割当先を選択する．
            this.scheduleVNF(vnf, this.vcpuMap);
        }
        double val = -1;
        Iterator<Long> endITe = this.getSfc().getEndVNFSet().iterator();
        while (endITe.hasNext()) {
            Long eID = endITe.next();
            VNF endVNF = this.sfc.findVNFByLastID(eID);
            if (endVNF.getFinishTime() >= val) {
                val = endVNF.getFinishTime();
            }
        }
        //応答時間を決める．
        this.makeSpan = val;
    }

    private ComputeHost findHostByVCPU(VCPU vcpu) {
        if (vcpu == null) {
            return null;
        }
        long dcID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        long hostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        Cloud cloud = this.env.getDcMap().get(dcID);
        if (cloud == null) {
            return null;
        }
        return cloud.getComputeHostMap().get(hostID);
    }

    private ComputeHost findHostByVM(VM vm) {
        if (vm == null || vm.getHostID() == null) {
            return null;
        }
        return this.env.getGlobal_hostMap().get(vm.getHostID());
    }

    private double getHostQueueTail(ComputeHost host) {
        if (host == null || host.getDlQueue() == null || host.getDlQueue().isEmpty()) {
            return 0.0d;
        }
        return host.getDlQueue().getLast().getDlFinishTime();
    }

    private void commitHostQueues(VNF vnf, DownloadPlan plan) {
        if (plan == null || plan.sourceHost == null || plan.targetHost == null) {
            return;
        }
        // The same transfer occupies both source and destination host network endpoints.
        plan.sourceHost.addDLQueue(vnf);
        if (!plan.sourceHost.getPrefix().equals(plan.targetHost.getPrefix())) {
            plan.targetHost.addDLQueue(vnf);
        }
    }
}
