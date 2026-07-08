package net.gripps.cloud.nfv.listscheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BandwidthTimeSlot;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * NHEFT: DHEFT + bandwidth-aware concurrent image download.
 *
 * Dynamic behavior:
 * - Reuse cached image source when available.
 * - Track link occupancy with slots.
 * - During download simulation, task bandwidth changes over time as other tasks end/start.
 */
public class NHEFT_VNFAlgorithm extends DHEFT_VNFAlgorithm {

    // EPS: a tiny time offset used to avoid getting stuck on exact boundary timestamps.
    // EPS：微小时间偏移，用于跨过边界时刻，避免在同一时间点反复判断导致死循环。
    private static final double EPS = 0.000001d;
    // NHEFT policy: image download/share must be evaluated by dynamic bandwidth slots only.

    // Keyed by logical link id (DC pair / host pair), value is the time-slot BW occupancy model.
    // 按“逻辑链路ID（DC对/Host对）”索引带宽时隙，用于记录和查询带宽占用。
    private final Map<String, BandwidthTimeSlot> linkSlotMap;

    public NHEFT_VNFAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.linkSlotMap = new HashMap<String, BandwidthTimeSlot>();
    }

    //Data:2026-04-14
    /**
     * NHEFT uses absolute-time link simulation, so EST must read absolute DL start/finish.
     * Base getDLInfo() is duration/queue based and can underestimate when plan.startTime > 0.
     */
    @Override
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu) {
        // Return absolute download [start, finish] on the global timeline.
        // 返回全局时间轴上的下载开始/结束时刻（不是单纯时长）。
        HashMap<String, Double> map = new HashMap<String, Double>();
        map.put("start", 0.0d);
        map.put("finish", 0.0d);

        if (NFVUtil.cloud_container_dl_mode != 1) {
            return map;
        }

        VM vm = this.findVM(vcpu);
        if (vm == null) {
            return map;
        }

        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan != null) {
            map.put("start", plan.startTime);
            map.put("finish", plan.finishTime);
        }
        return map;
    }

    @Override
    public double calcDownloadImageTime(VNF vnf, VCPU vcpu) {
        // Compatibility path for callers that still expect a duration.
        // 兼容旧调用方：这里返回“下载时长”，而不是绝对完成时刻。
        VM vm = this.findVM(vcpu);
        if (vm == null) {
            return NFVUtil.MAXValue;
        }

        DownloadPlan plan = this.findBestPlan(vnf, vcpu, false);
        if (plan == null) {
            return this.calcImageComTimeFromRepo(vnf, vcpu);
        }
        // Base getDLInfo() expects duration, not absolute finish time.
        return Math.max(0.0d, plan.finishTime - plan.startTime);
    }

    /**
     * Commit actual reservations after the VNF has been assigned by scheduler.
     */
    @Override
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;

        VCPU retCPU = null;
        if (NFVUtil.debug_nheft == 1) {
            this.trace("[NHEFT-START]", "Scheduling " + this.formatVNF(vnf) + ", candidateVCPU=" + map.size());
        }
        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            if (cpu.getVMID() == null || this.env.getGlobal_vmMap().get(cpu.getVMID()) == null) {
                continue;
            }
            double est = this.calcEST(vnf, cpu);
            double ftime = est + this.calcExecTime(vnf.getWorkLoad(), cpu);
            if (NFVUtil.debug_nheft == 1) {
                this.trace("[NHEFT-CAND]",
                        this.formatVNF(vnf)
                                + " -> " + this.formatVCPU(cpu)
                                + ", est=" + est
                                + ", execTime=" + this.calcExecTime(vnf.getWorkLoad(), cpu)
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

        if (NFVUtil.debug_nheft == 1) {
            this.trace("[NHEFT-SELECT]",
                    this.formatVNF(vnf)
                            + " -> " + this.formatVCPU(retCPU)
                            + ", start=" + ret_starttime
                            + ", finish=" + ret_finishtime);
        }

        DownloadPlan plan = this.findBestPlan(vnf, retCPU, true);
        if (plan != null) {
            vnf.setDlStartTime(plan.startTime);
            vnf.setDlFinishTime(plan.finishTime);
            boolean hasDownload = plan.finishTime > plan.startTime + EPS;
            this.recordImageDownloadSource(vnf, hasDownload, plan.fromRepo, plan.sourceVM);
            if (NFVUtil.debug_nheft == 1) {
                System.out.println("[NHEFT-COMMIT] VNF=" + vnf.getIDVector().get(1)
                        + " target=" + retCPU.getPrefix()
                        + " dlStart=" + plan.startTime
                        + " dlFinish=" + plan.finishTime
                        + " fromRepo=" + plan.fromRepo
                        + " dynamic=" + (plan.dynamicResult != null)
                        + " sourceVM=" + (plan.sourceVM == null ? "null" : plan.sourceVM.getVMID()));
            }
            if (plan.finishTime > plan.startTime) {
                this.recordImageDownload(plan.fromRepo, plan.startTime, plan.finishTime);
                if (plan.fromRepo) {
                    retCPU.addDLQueue(vnf);
                } else if (plan.sourceVM != null) {
                    plan.sourceVM.addDLQueue(vnf);
                }
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

    private DownloadPlan findBestPlan(VNF vnf, VCPU targetVCPU, boolean commit) {
        // Find the fastest source plan for this target:
        // 1) repo as candidate A
        // 2) every cache-eligible VM as candidate B
        // choose the one with minimum finishTime.
        // 为目标vCPU选择最快下载方案：
        // 先评估repo，再评估所有可复用缓存VM，最终选择finishTime最小的方案。
        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        if (vnf.getImageSize() <= 0L) {
            // Zero-size image means no transfer is needed regardless of source choice.
            DownloadPlan zero = new DownloadPlan();
            zero.startTime = 0.0d;
            zero.finishTime = 0.0d;
            zero.fromRepo = false;
            zero.sourceVM = targetVM;
            return zero;
        }

        double targetReady = this.getImageReadyTimeOnVM(targetVM, vnf.getType());
        if (targetReady <= EPS) {
            DownloadPlan zero = new DownloadPlan();
            zero.startTime = 0.0d;
            zero.finishTime = 0.0d;
            zero.fromRepo = false;
            zero.sourceVM = targetVM;
            return zero;
        }

        // Candidate A: Docker repository
        DownloadPlan best = this.buildPlanFromRepo(vnf, targetVCPU, 0.0d);

        // Candidate B: cache VMs that have this image type in typeSet.
        // Keep this optimistic enough for the dynamic model: if typeSet says the image exists,
        // let the plan builder evaluate it directly instead of applying extra ready-time pruning.
        Iterator<VM> vmIte = this.env.getGlobal_vmMap().values().iterator();
        while (vmIte.hasNext()) {
            VM srcVM = vmIte.next();
            if (srcVM == null) {
                continue;
            }
            double srcReady = this.getImageReadyTimeOnVM(srcVM, vnf.getType());
            if (srcReady >= NFVUtil.MAXValue) {
                continue;
            }
            ComputeHost srcHost = this.getHostByPrefix(srcVM.getHostID());
            if (srcHost == null) {
                continue;
            }
            DownloadPlan candidate = this.buildPlanFromHost(vnf, targetVCPU, srcHost, false, srcReady);
            if (candidate != null) {
                candidate.sourceVM = srcVM;
            }
            if (candidate != null && (best == null || candidate.finishTime < best.finishTime)) {
                best = candidate;
            }
        }

        // NHEFT policy: keep image transfer planning fully dynamic.
        // Do not fallback to DHEFT-style static queue estimation here.

        // Optional debug output: when enabled print chosen plan details
        if (best != null) {
            try {
                if (NFVUtil.debug_nheft == 1) {
                    System.out.println("[NHEFT-DEBUG] VNF=" + vnf.getIDVector().get(1)
                            + " target=" + (targetVCPU != null ? targetVCPU.getPrefix() : "null")
                            + " planStart=" + best.startTime
                            + " planFinish=" + best.finishTime
                            + " fromRepo=" + best.fromRepo);
                    if (best.dynamicResult != null) {
                        double dynDur = Math.max(0.0d, best.finishTime - best.startTime);
                        double statDur = this.calcImageComTimeFromRepo(vnf, targetVCPU);
                        System.out.println("[NHEFT-DEBUG] dynDur=" + dynDur + " statDur=" + statDur);
                    }
                }
            } catch (Exception _e) {
                // ignore
            }
        }

        // Persist the selected dynamic reservation so later VNFs see real link occupancy.
        // 将选中的动态预留写回，确保后续VNF能够看到真实的链路占用。
        if (commit && best != null && best.dynamicResult != null) {
            String taskId = this.makeTaskId(vnf, targetVCPU, best.fromRepo);
            this.commitDynamicReservation(taskId, best.dcSlot, best.hostSlot, best.dynamicResult);
        } else if (commit && best != null) {
            // No dynamic segment means either local cache hit (duration ~= 0)
            // or no transfer reservation is needed for this plan.
            if (NFVUtil.debug_nheft == 1 && (best.finishTime - best.startTime) > EPS) {
                System.out.println("[NHEFT-WARN] Non-zero image transfer plan without dynamic segments: VNF="
                        + vnf.getIDVector().get(1)
                        + ", target=" + targetVCPU.getPrefix()
                        + ", fromRepo=" + best.fromRepo
                        + ", planStart=" + best.startTime
                        + ", planFinish=" + best.finishTime);
            }
        }

        return best;
    }

    private DownloadPlan chooseStableBestPlan(VNF vnf, VCPU targetVCPU, DownloadPlan dynamicBest) {
        DownloadPlan best = this.applyPessimismGuard(vnf, targetVCPU, dynamicBest);
        DownloadPlan staticBest = this.findBestStaticPlan(vnf, targetVCPU);

        if (best == null) {
            return staticBest;
        }
        if (staticBest == null) {
            return best;
        }
        if (staticBest.finishTime <= best.finishTime + EPS) {
            return staticBest;
        }
        return best;
    }

    private DownloadPlan findBestStaticPlan(VNF vnf, VCPU targetVCPU) {
        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        double targetReady = this.getStaticImageReadyTimeOnVM(targetVM, vnf.getType());
        if (targetVM.containsType(vnf.getType()) && targetReady <= EPS) {
            DownloadPlan zero = new DownloadPlan();
            zero.startTime = 0.0d;
            zero.finishTime = 0.0d;
            zero.fromRepo = false;
            zero.sourceVM = targetVM;
            return zero;
        }

        DownloadPlan best = this.buildStaticRepoPlan(vnf, targetVCPU);
        Iterator<VM> vmIte = this.env.getGlobal_vmMap().values().iterator();
        while (vmIte.hasNext()) {
            VM vm = vmIte.next();
            if (vm == null) {
                continue;
            }
            double srcReady = this.getStaticImageReadyTimeOnVM(vm, vnf.getType());
            if (srcReady >= NFVUtil.MAXValue) {
                continue;
            }
            DownloadPlan candidate = this.buildStaticVmSourcePlan(vnf, targetVCPU, vm);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.finishTime < best.finishTime) {
                best = candidate;
            }
        }
        return best;
    }

    private DownloadPlan buildStaticRepoPlan(VNF vnf, VCPU targetVCPU) {
        DownloadPlan plan = new DownloadPlan();
        plan.fromRepo = true;
        plan.sourceVM = null;

        double queueTail = 0.0d;
        if (targetVCPU.getDlQueue() != null && !targetVCPU.getDlQueue().isEmpty()) {
            queueTail = targetVCPU.getDlQueue().getLast().getDlFinishTime();
        }
        double duration = this.calcImageComTimeFromRepo(vnf, targetVCPU);
        if (duration >= NFVUtil.MAXValue) {
            return null;
        }

        plan.startTime = queueTail;
        plan.finishTime = queueTail + duration;
        return plan;
    }

    private DownloadPlan buildStaticVmSourcePlan(VNF vnf, VCPU targetVCPU, VM sourceVM) {
        if (sourceVM == null) {
            return null;
        }

        VM targetVM = this.findVM(targetVCPU);
        if (targetVM == null) {
            return null;
        }

        double readyTime = this.getStaticImageReadyTimeOnVM(sourceVM, vnf.getType());
        if (readyTime >= NFVUtil.MAXValue) {
            return null;
        }

        double sourceBusyUntil = sourceVM.getDlQueueFinishTime();
        double start = Math.max(readyTime, sourceBusyUntil);
        double duration;

        if (sourceVM.getVMID().equals(targetVM.getVMID())) {
            duration = 0.0d;
        } else {
            ComputeHost srcHost = this.env.getGlobal_hostMap().get(sourceVM.getHostID());
            ComputeHost dstHost = this.env.getGlobal_hostMap().get(targetVM.getHostID());
            if (srcHost == null || dstHost == null) {
                return null;
            }

            long dataSize = vnf.getImageSize();
            long bw = Math.min(srcHost.getBw(), dstHost.getBw());
            if (bw <= 0) {
                return null;
            }
            duration = CloudUtil.getRoundedValue((double) dataSize / (double) bw);
        }

        DownloadPlan plan = new DownloadPlan();
        plan.sourceVM = sourceVM;
        plan.fromRepo = false;
        plan.startTime = start;
        plan.finishTime = start + duration;
        return plan;
    }

    private double getStaticImageReadyTimeOnVM(VM vm, int type) {
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

        return foundProducer ? readyTime : 0.0d;
    }

    private DownloadPlan applyDHEFTUpperBound(VNF vnf, VCPU targetVCPU, DownloadPlan best) {
        if (best == null) {
            return null;
        }
        HashMap<String, Double> staticInfo = super.getDLInfo(vnf, targetVCPU);
        if (staticInfo == null) {
            return best;
        }
        Double staticStartObj = staticInfo.get("start");
        Double staticFinishObj = staticInfo.get("finish");
        if (staticStartObj == null || staticFinishObj == null) {
            return best;
        }
        double staticStart = staticStartObj.doubleValue();
        double staticFinish = staticFinishObj.doubleValue();
        if (staticStart >= NFVUtil.MAXValue || staticFinish >= NFVUtil.MAXValue) {
            return best;
        }
        if (staticFinish + EPS >= best.finishTime) {
            return best;
        }

        DownloadPlan bounded = new DownloadPlan();
        bounded.startTime = staticStart;
        bounded.finishTime = staticFinish;
        // Static bounded fallback should be treated conservatively as a repo-based plan
        // since we don't have source VM id from super.getDLInfo().
        bounded.fromRepo = true;
        bounded.sourceVM = null;
        return bounded;
    }

    private DownloadPlan applyPessimismGuard(VNF vnf, VCPU targetVCPU, DownloadPlan best) {
        if (best == null) {
            return null;
        }
        if (!best.fromRepo) {
            return best;
        }
        double dynamicDur = Math.max(0.0d, best.finishTime - best.startTime);
        double staticDur = this.calcImageComTimeFromRepo(vnf, targetVCPU);
        if (staticDur <= 0.0d || staticDur >= NFVUtil.MAXValue) {
            return best;
        }
        if (dynamicDur <= staticDur) {
            return best;
        }

        // Anchor bounded pre-download near arrival to avoid huge early pessimistic tails.
        double arrival = this.calcDeadLine(vnf, targetVCPU).get("arrival_time");
        double start = Math.max(0.0d, arrival - staticDur);

        DownloadPlan bounded = new DownloadPlan();
        bounded.startTime = start;
        bounded.finishTime = start + staticDur;
        bounded.fromRepo = best.fromRepo;
        bounded.sourceVM = best.sourceVM;
        bounded.dcSlot = best.dcSlot;
        bounded.hostSlot = best.hostSlot;
        bounded.dynamicResult = best.dynamicResult;
        return bounded;
    }

    private DownloadPlan buildPlanFromRepo(VNF vnf, VCPU targetVCPU, double earliestStart) {
        NFVEnvironment nEnv = (NFVEnvironment) this.env;
        ComputeHost repo = nEnv.getDockerRepository();
        return this.buildPlanFromHost(vnf, targetVCPU, repo, true, earliestStart);
    }

    private DownloadPlan buildPlanFromHost(VNF vnf, VCPU targetVCPU, ComputeHost srcHost, boolean fromRepo, double earliestStart) {
        // Build one concrete plan from a fixed source host to the target host.
        // 从一个固定源主机构建到目标主机的具体下载计划。
        ComputeHost dstHost = this.getHostByVCPU(targetVCPU);
        if (dstHost == null || srcHost == null) {
            return null;
        }

        // Same physical host within the same DC => no download needed.
        // 仅在“同一个DC里的同一台物理机”时，才把下载时间视为 0。
        if (srcHost.getDcID().longValue() == dstHost.getDcID().longValue()
                && srcHost.getMachineID() == dstHost.getMachineID()) {
            DownloadPlan p = new DownloadPlan();
            p.startTime = Math.max(0.0d, earliestStart);
            p.finishTime = p.startTime;
            p.fromRepo = fromRepo;
            return p;
        }

        long dataSize = vnf.getImageSize();
        long srcDCID = srcHost.getDcID();
        long dstDCID = dstHost.getDcID();

        Cloud srcCloud = this.env.getDcMap().get(srcDCID);
        Cloud dstCloud = this.env.getDcMap().get(dstDCID);
        if (srcCloud == null || dstCloud == null) {
            return null;
        }

        long dcCap = NFVUtil.MAXValue;
        if (srcDCID != dstDCID) {
            dcCap = Math.min(srcCloud.getBw(), dstCloud.getBw());
        }

        long hostCap = Math.min(srcHost.getBw(), dstHost.getBw());
        long maxTransferBW = Math.min(dcCap, hostCap);
        if (maxTransferBW <= 0) {
            return null;
        }

        BandwidthTimeSlot dcSlot = null;
        if (srcDCID != dstDCID) {
            String dcKey = this.makeDCPairKey(srcDCID, dstDCID);
            dcSlot = this.getOrCreateSlot(dcKey, dcCap);
        }

        String hostKey = this.makeHostPairKey(srcHost.getPrefix(), dstHost.getPrefix());
        BandwidthTimeSlot hostSlot = this.getOrCreateSlot(hostKey, hostCap);

        // Keep pre-download close to execution demand to avoid excessive early reservations.
        double arrival = this.calcDeadLine(vnf, targetVCPU).get("arrival_time");
        double roughDuration = (double) dataSize / (double) maxTransferBW;
        double searchFrom = Math.max(Math.max(0.0d, earliestStart), arrival - roughDuration);
        double baselineStart = this.findEarliestPositiveStart(dcSlot, hostSlot, searchFrom);
        double start = this.adjustDownloadStartForVariant(
                vnf,
                targetVCPU,
                dcSlot,
                hostSlot,
                maxTransferBW,
                dataSize,
                baselineStart
        );
        start = Math.max(start, earliestStart);
        DynamicResult res = this.simulateDynamicDownload(start, dataSize, maxTransferBW, dcSlot, hostSlot);

        DownloadPlan p = new DownloadPlan();
        p.startTime = start;
        p.finishTime = res.finishTime;
        p.fromRepo = fromRepo;
        p.dynamicResult = res;
        p.dcSlot = dcSlot;
        p.hostSlot = hostSlot;
        return p;
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
        double readyTime = NFVUtil.MAXValue;
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
                if (cachedReady <= EPS) {
                    return 0.0d;
                }
                if (cachedReady < readyTime) {
                    readyTime = cachedReady;
                }
            }
        }

        return foundProducer ? readyTime : 0.0d;
    }

    /**
     * Extension hook for variants (for example NPHEFT) that only want to adjust
     * the planned download start time while reusing all baseline NHEFT logic.
     */
    protected double adjustDownloadStartForVariant(VNF vnf,
                                                   VCPU targetVCPU,
                                                   BandwidthTimeSlot dcSlot,
                                                   BandwidthTimeSlot hostSlot,
                                                   long maxTransferBW,
                                                   long dataSize,
                                                   double baselineStart) {
        return baselineStart;
    }

    private double findEarliestPositiveStart(BandwidthTimeSlot dcSlot, BandwidthTimeSlot hostSlot, double fromTime) {
        // Scan timeline to find the first time point with strictly positive available BW.
        // 扫描时间轴，找到“可用带宽>0”的最早时刻作为下载起点候选。
        double t = Math.max(0.0d, fromTime);
        for (int i = 0; i < 1024; i++) {
            long dcAvail = (dcSlot == null) ? NFVUtil.MAXValue : dcSlot.getAvailableBWAt(t);
            long hostAvail = (hostSlot == null) ? NFVUtil.MAXValue : hostSlot.getAvailableBWAt(t);
            if (Math.min(dcAvail, hostAvail) > 0) {
                return t;
            }

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(t);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(t);
            double next = Math.min(nextDc, nextHost);
            if (next == Double.MAX_VALUE) {
                return t;
            }
            t = next + EPS;
        }
        return t;
    }

    protected DynamicResult simulateDynamicDownload(double startTime,
                                                    long dataSize,
                                                    long maxTransferBW,
                                                    BandwidthTimeSlot dcSlot,
                                                    BandwidthTimeSlot hostSlot) {
        // Core NHEFT simulation:
        // advance on each BW-change boundary, allocate min(maxTransferBW, link-available),
        // and accumulate segments until all image bytes are transferred.
        // NHEFT核心：
        // 在每个带宽变化边界推进时间，按 min(链路可用, 最大传输带宽) 分配带宽，
        // 持续累积分段，直到镜像全部传完。
        DynamicResult result = new DynamicResult();
        double t = startTime;
        double remaining = (double) dataSize;

        for (int i = 0; i < 4096 && remaining > 0.0d; i++) {
            long dcAvail = (dcSlot == null) ? NFVUtil.MAXValue : dcSlot.getAvailableBWAt(t);
            long hostAvail = (hostSlot == null) ? NFVUtil.MAXValue : hostSlot.getAvailableBWAt(t);
            long allocBW = Math.min(maxTransferBW, Math.min(dcAvail, hostAvail));

            double nextDc = (dcSlot == null) ? Double.MAX_VALUE : dcSlot.getNextChangeTime(t);
            double nextHost = (hostSlot == null) ? Double.MAX_VALUE : hostSlot.getNextChangeTime(t);
            double nextChange = Math.min(nextDc, nextHost);

            if (allocBW <= 0L) {
                if (nextChange == Double.MAX_VALUE) {
                    // no progress path; fallback to avoid dead loop
                    nextChange = t + 1.0d;
                }
                t = nextChange + EPS;
                continue;
            }

            if (nextChange == Double.MAX_VALUE) {
                double dur = remaining / (double) allocBW;
                double end = t + dur;
                result.addSegment(t, end, allocBW);
                t = end;
                remaining = 0.0d;
                break;
            }

            double dur = Math.max(EPS, nextChange - t);
            double transferable = dur * (double) allocBW;
            if (transferable >= remaining) {
                double needDur = remaining / (double) allocBW;
                double end = t + needDur;
                result.addSegment(t, end, allocBW);
                t = end;
                remaining = 0.0d;
                break;
            } else {
                double end = t + dur;
                result.addSegment(t, end, allocBW);
                remaining -= transferable;
                t = end + EPS;
            }
        }

        result.finishTime = t;
        return result;
    }

    private void commitDynamicReservation(String taskId,
                                          BandwidthTimeSlot dcSlot,
                                          BandwidthTimeSlot hostSlot,
                                          DynamicResult result) {
        // Persist each simulated segment into slot occupancy to affect later tasks.
        // 将模拟出的每个分段写入带宽占用表，影响后续任务的可用带宽判断。
        for (int i = 0; i < result.segmentCount; i++) {
            double s = result.startTimes[i];
            double e = result.endTimes[i];
            long bw = result.bandwidths[i];
            String segTaskId = taskId + "#seg" + i;
            if (dcSlot != null) {
                dcSlot.reserveBW(s, e, bw, segTaskId + "#dc");
            }
            if (hostSlot != null) {
                hostSlot.reserveBW(s, e, bw, segTaskId + "#host");
            }
        }
    }

    private void commitStaticReservation(String taskId,
                                         VNF vnf,
                                         VCPU targetVCPU,
                                         DownloadPlan plan) {
        if (plan == null) {
            return;
        }
        double duration = plan.finishTime - plan.startTime;
        if (duration <= EPS) {
            return;
        }

        ComputeHost dstHost = this.getHostByVCPU(targetVCPU);
        if (dstHost == null) {
            return;
        }

        ComputeHost srcHost = null;
        if (plan.fromRepo) {
            NFVEnvironment nEnv = (NFVEnvironment) this.env;
            srcHost = nEnv.getDockerRepository();
        } else if (plan.sourceVM != null) {
            srcHost = this.getHostByPrefix(plan.sourceVM.getHostID());
        }
        if (srcHost == null) {
            return;
        }

        // Same physical host inside same DC: zero-transfer case (already filtered by duration check).
        if (srcHost.getDcID().longValue() == dstHost.getDcID().longValue()
                && srcHost.getMachineID() == dstHost.getMachineID()) {
            return;
        }

        long srcDCID = srcHost.getDcID();
        long dstDCID = dstHost.getDcID();
        Cloud srcCloud = this.env.getDcMap().get(srcDCID);
        Cloud dstCloud = this.env.getDcMap().get(dstDCID);
        if (srcCloud == null || dstCloud == null) {
            return;
        }

        long dcCap = NFVUtil.MAXValue;
        BandwidthTimeSlot dcSlot = null;
        if (srcDCID != dstDCID) {
            dcCap = Math.min(srcCloud.getBw(), dstCloud.getBw());
            String dcKey = this.makeDCPairKey(srcDCID, dstDCID);
            dcSlot = this.getOrCreateSlot(dcKey, dcCap);
        }

        long hostCap = Math.min(srcHost.getBw(), dstHost.getBw());
        String hostKey = this.makeHostPairKey(srcHost.getPrefix(), dstHost.getPrefix());
        BandwidthTimeSlot hostSlot = this.getOrCreateSlot(hostKey, hostCap);

        long maxTransferBW = Math.min(dcCap, hostCap);
        if (maxTransferBW <= 0L) {
            return;
        }

        // Static plan duration may include rounding; derive a safe BW and clamp to link max.
        long requiredBW = (long) Math.ceil((double) vnf.getImageSize() / duration);
        if (requiredBW <= 0L) {
            requiredBW = 1L;
        }
        long reserveBW = Math.min(maxTransferBW, requiredBW);

        DynamicResult synthetic = new DynamicResult();
        synthetic.addSegment(plan.startTime, plan.finishTime, reserveBW);
        synthetic.finishTime = plan.finishTime;

        this.commitDynamicReservation(taskId + "#static", dcSlot, hostSlot, synthetic);
    }

    private BandwidthTimeSlot getOrCreateSlot(String key, long capacity) {
        BandwidthTimeSlot slot = this.linkSlotMap.get(key);
        if (slot == null) {
            slot = new BandwidthTimeSlot(key, Math.max(1L, capacity));
            this.linkSlotMap.put(key, slot);
        }
        return slot;
    }

    private ComputeHost getHostByVCPU(VCPU vcpu) {
        long dcID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        long hostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        Cloud c = this.env.getDcMap().get(dcID);
        return c == null ? null : c.getComputeHostMap().get(hostID);
    }

    private ComputeHost getHostByPrefix(String hostPrefix) {
        return this.env.getGlobal_hostMap().get(hostPrefix);
    }

    private String makeDCPairKey(long a, long b) {
        long min = Math.min(a, b);
        long max = Math.max(a, b);
        return "DCLink^" + min + "^" + max;
    }

    private String makeHostPairKey(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return "HostLink^" + a + "^" + b;
        }
        return "HostLink^" + b + "^" + a;
    }

    private String makeTaskId(VNF vnf, VCPU vcpu, boolean fromRepo) {
        return "NHEFT^" + vnf.getIDVector().get(0) + "^" + vnf.getIDVector().get(1) + "^" + vcpu.getPrefix() + "^" + (fromRepo ? "repo" : "cache");
    }



    private static class DownloadPlan {
        // Absolute timeline fields for one selected source plan.
        // 单个已选下载方案的绝对时间字段。
        double startTime;
        double finishTime;
        boolean fromRepo;
        VM sourceVM;
        BandwidthTimeSlot dcSlot;
        BandwidthTimeSlot hostSlot;
        DynamicResult dynamicResult;
    }

    protected static class DynamicResult {
        // Piecewise transfer trace generated by dynamic simulation.
        // 动态仿真得到的分段传输轨迹。
        int segmentCount = 0;
        double finishTime = 0.0d;
        double[] startTimes = new double[4096];
        double[] endTimes = new double[4096];
        long[] bandwidths = new long[4096];

        void addSegment(double s, double e, long bw) {
            if (segmentCount >= startTimes.length) {
                return;
            }
            startTimes[segmentCount] = s;
            endTimes[segmentCount] = e;
            bandwidths[segmentCount] = bw;
            segmentCount++;
        }
    }
}
