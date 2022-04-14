package net.gripps.cloud.nfv.sfc;


//import com.sun.javafx.embed.HostDragStartListener;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.fairscheduling.FairInfoAtHost;
import net.gripps.cloud.nfv.fairscheduling.FairInfoAtVCPU;
import net.gripps.cloud.nfv.fairscheduling.FairnessIndexInfo;
import net.gripps.cloud.nfv.fairscheduling.HostStatistics;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;
import net.gripps.environment.CPU;



import java.util.*;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.<br>
 * 基本となるVNFのスケジューリングアルゴリズムです．
 * 独自の割当てアルゴリズムを開発する場合は，
 * このクラスを継承すると便利です．
 * VNF（もしくはVNFCluster)の割当先は，あくまで"vCPU"を
 * 想定しています．
 */
public class BaseVNFSchedulingAlgorithm {

    /**
     * メイクスパン(応答時間）
     */
    protected double makeSpan;


    /**
     * クラウド環境
     */
    protected CloudEnvironment env;

    /**
     * SFC
     */
    protected SFC sfc;

    /**
     * 最大処理速度
     */
    protected long maxSpeed;

    /**
     * 最小処理速度
     */
    protected long minSpeed;

    /**
     * 平均処理速度
     */
    protected long aveSpeed;

    /**
     * 最大の帯域幅
     */
    protected long maxBW;

    /**
     * 最小の帯域幅
     */
    protected long minBW;

    /**
     * 平均の帯域幅
     */
    protected long aveBW;
    /**
     * 先行VNFをすべてスケジュール済みであるような
     * VNFの集合
     */
    protected CustomIDSet freeVNFSet;
    /**
     * スケジュール済みのVNF集合
     */
    protected CustomIDSet scheduledVNFSet;
    /**
     * 未スケジュールのVNF集合
     */
    protected CustomIDSet unScheduledVNFSet;

    /**
     * vCPUの集合．スケジューリング時の
     * 参照用
     */
    protected HashMap<String, VCPU> vcpuMap;

    protected HashMap<String, VCPU> assignedVCPUMap;

    protected HashMap<String, ComputeHost> hostSet;

    protected double totalCPProcTimeAtMaxSpeed;


    /**
     * level計算に実際に使う処理速度
     */
    protected long usedSpeed;

    /**
     * level計算に実際に使う帯域幅
     */
    protected long usedBW;

    protected int constrainedMode;

    protected HashMap<String, HostStatistics> fairHostMap;

    /**
     * トータルとしてのファンクションインスタンス数
     * 1インスタンス = 1ファンクション　とした場合．
     */
    protected long totalFunctionInstanceNum;



    public BaseVNFSchedulingAlgorithm(CloudEnvironment env, SFC sfc) {
        this.makeSpan = -1;
        this.env = env;
        this.sfc = sfc;
        this.maxSpeed = -1;
        this.minSpeed = NFVUtil.MAXValue;
        this.aveSpeed = 0;
        this.maxBW = -1;
        this.minBW = NFVUtil.MAXValue;
        this.aveBW = 0;
        this.freeVNFSet = new CustomIDSet();
        //    this.scheduledVNFSet = new CustomIDSet();
        this.unScheduledVNFSet = new CustomIDSet();
        this.vcpuMap = new HashMap<String, VCPU>();
        this.assignedVCPUMap = new HashMap<String, VCPU>();
        this.constrainedMode = NFVUtil.cloud_constrained_mode;
        this.hostSet = new HashMap<String, ComputeHost>();
        this.totalFunctionInstanceNum = 0;
        this.totalCPProcTimeAtMaxSpeed = 0;
        this.fairHostMap = new HashMap<String, HostStatistics>();
        this.initialize();
    }


    /**
     * 準備のための処理です．継承クラスでも，このメソッド
     * をCallしてください．
     * 1. 各VNFに対し，平均の処理速度/平均帯域幅に基づいてtlevel/blevelをセットする．
     */
    public void initialize() {
        this.setSpeedBW();
        long[] arrSpeed = {this.aveSpeed, this.maxSpeed, this.minSpeed};
        long[] arrBW = {this.aveBW, this.maxBW, this.minBW};

        //レベル計算につかう処理速度，BWを決める．
        this.usedSpeed = arrSpeed[NFVUtil.calcmode_level];
        this.usedBW = arrBW[NFVUtil.calcmode_level];


        //STARTVNF集合から，blevelを求める．
        CustomIDSet startSet = sfc.getStartVNFSet();
        Iterator<Long> startIte = startSet.iterator();
        while (startIte.hasNext()) {
            Long startID = startIte.next();
            //Free集合へ追加しておく．
            this.freeVNFSet.add(startID);
            VNF startVNF = this.sfc.findVNFByLastID(startID);
            this.calcBlevel(startVNF, new CustomIDSet());
        }
        CustomIDSet endSet = sfc.getEndVNFSet();
        Iterator<Long> endIte = endSet.iterator();
        while (endIte.hasNext()) {
            Long endID = endIte.next();
            VNF endVNF = this.sfc.findVNFByLastID(endID);
            this.calcTlevel(endVNF, new CustomIDSet());
        }
        //全VNFを未スケジュール集合へ追加する．
        Iterator<VNF> vnfIte = this.sfc.getVnfMap().values().iterator();
        while (vnfIte.hasNext()) {
            VNF vnf = vnfIte.next();
            this.unScheduledVNFSet.add(vnf.getIDVector().get(1));
        }

        Iterator<Long> eIte = this.sfc.getEndVNFSet().iterator();
        long totalMax = 0;
        LinkedList<VNF> vnfList = new LinkedList<VNF>();

        while (eIte.hasNext()) {
            long tmpTotal = 0;
            Long id = eIte.next();
            VNF vnf = this.sfc.findVNFByLastID(id);
            vnfList.add(vnf);
            tmpTotal += vnf.getWorkLoad();
            while (!vnf.getDpredList().isEmpty()) {
                vnf = this.sfc.findVNFByLastID(vnf.getDominantPredID());
                vnfList.add(vnf);

                tmpTotal += vnf.getWorkLoad();
            }
            if (totalMax <= tmpTotal) {
                totalMax = tmpTotal;
            }
        }
        this.totalCPProcTimeAtMaxSpeed = this.calcExecTime(totalMax, this.aveSpeed);


    }

    public double getTotalCPProcTimeAtMaxSpeed() {
        return totalCPProcTimeAtMaxSpeed;
    }

    public void setTotalCPProcTimeAtMaxSpeed(double totalCPProcTimeAtMaxSpeed) {
        this.totalCPProcTimeAtMaxSpeed = totalCPProcTimeAtMaxSpeed;
    }

    /**
     * 最大，最小，平均を計算します．
     */
    public void setSpeedBW() {

        long totalBW = 0;
        long totalSpeed = 0;
        long bw_num = 0;
        long speed_num = 0;

        long max_speed = -1;
        long max_bw = -1;
        long ave_speed = 0;
        long ave_bw = 0;
        long min_speed = NFVUtil.MAXValue;
        long min_bw = NFVUtil.MAXValue;

        Iterator<Cloud> cloudIte = this.env.getDcMap().values().iterator();
        while (cloudIte.hasNext()) {
            //クラウドはBWのみ（ルータと考えてもらって良い）
            Cloud cloud = cloudIte.next();
            totalBW += cloud.getBw();
            bw_num++;
            if (max_bw <= cloud.getBw()) {
                max_bw = cloud.getBw();
            }
            if (min_bw >= cloud.getBw()) {
                min_bw = cloud.getBw();
            }
            Iterator<ComputeHost> hostIte = cloud.getComputeHostMap().values().iterator();
            while (hostIte.hasNext()) {
                ComputeHost host = hostIte.next();
                //まずはBW
                totalBW += host.getBw();
                bw_num++;
                if (max_bw <= host.getBw()) {
                    max_bw = host.getBw();
                }
                if (min_bw >= host.getBw()) {
                    min_bw = host.getBw();
                }
                Iterator<CPU> cpuIte = host.getCpuMap().values().iterator();
                while (cpuIte.hasNext()) {
                    CloudCPU cpu = (CloudCPU) cpuIte.next();
                    Iterator<Core> coreIte = cpu.getCoreMap().values().iterator();
                    while (coreIte.hasNext()) {
                        Core core = coreIte.next();
                        Iterator<VCPU> vcpuIte = core.getvCPUMap().values().iterator();
                        while (vcpuIte.hasNext()) {
                            VCPU vcpu = vcpuIte.next();
                            this.vcpuMap.put(vcpu.getPrefix(), vcpu);
                            totalSpeed += vcpu.getMips();
                            speed_num++;
                            if (max_speed <= vcpu.getMips()) {
                                max_speed = vcpu.getSpeed();

                            }
                            if (min_speed >= vcpu.getMips()) {
                                min_speed = vcpu.getMips();
                            }
                        }
                    }
                }
            }
        }
        ave_speed = totalSpeed / speed_num;
        ave_bw = totalBW / bw_num;

        this.maxSpeed = max_speed;
        this.minSpeed = min_speed;
        this.aveSpeed = ave_speed;

        this.maxBW = max_bw;
        this.minBW = min_bw;
        this.aveBW = ave_bw;
    }

    /**
     * Prefixから，クラウドを取得する．
     *
     * @param prefix
     * @return
     */
    public Cloud findCloud(String prefix) {
        long id = CloudUtil.getInstance().getDCID(prefix);
        return this.env.getDcMap().get(new Long(id));
    }

    /**
     * prefixから，ComputeHostを取得する．
     *
     * @param prefix
     * @return
     */
    public ComputeHost findHost(String prefix) {
        long id = CloudUtil.getInstance().getHostID(prefix);
        Cloud cloud = this.findCloud(prefix);
        return cloud.getComputeHostMap().get(new Long(id));
    }


    /**
     * 選択されたVNFをスケジュールするためのメソッドです．
     * 具体的には，指定範囲のvCPUに対して，どのvCPUのどの時間スロットへ
     * 割り当てるのかを決めて，そのスロットへ割り当てます．
     *
     * @param vnf 割当対象のVNF
     * @param map 割当先候補となるvCPUの集合．
     */
    public void scheduleVNF(VNF vnf, HashMap<String, VCPU> map) {
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;

        VCPU retCPU = null;
        //VCPUのイテレータを取得
        Iterator<VCPU> cpuIte = map.values().iterator();
        while (cpuIte.hasNext()) {
            VCPU cpu = cpuIte.next();
            //ESTを計算する．
            double est = this.calcEST(vnf, cpu);
            //完了時刻を計算する．
            double ftime = est + this.calcExecTime(vnf.getWorkLoad(), cpu);
            //VNFの完了時刻を最小にするVCPUを探す．
            if (ftime <= ret_finishtime) {
                ret_finishtime = ftime;
                ret_starttime = est;
                retCPU = cpu;
            }
        }

        double dTime = this.calcDownloadImageTime(vnf,retCPU);
        if(dTime == -1){
            dTime = 0;
        }
        //vnfの時刻を更新する．
        vnf.setStartTime(ret_starttime + dTime);
        vnf.setFinishTime(ret_finishtime + dTime);
        vnf.setEST(ret_starttime);
        vnf.setvCPUID(retCPU.getPrefix());

        //retCPUにおいて，vnfを追加する
        // retCPU.getVnfQueue().add(vnf);
        this.addVNFQueue(retCPU, vnf);

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


    public double calcDownloadImageTime(VNF vnf, VCPU vcpu){
        VM vm = this.findVM(vcpu);
        if(vm == null){
            return -1;
        }
        if(vm.containsType(vnf.getType())){
            return 0.0d;
        }else{
            return this.calcImageComTime(vnf.getImageSize(), vcpu);
        }
    }

    public VM findVM(VCPU vcpu){
        Iterator<VM> vIte = this.env.getGlobal_vmMap().values().iterator();
        VM retVM = null;
        while(vIte.hasNext()){
            VM vm = vIte.next();
            if(vm.getvCPUMap().containsKey(vcpu.getPrefix())){
                retVM = vm;
                break;
            }
        }
        return retVM;
    }

    /**
     * imageDataをリポジトリからダウンロードするのにかかる時間を計算する。
     *
     * @param dataSize

     * @return
     */
    public double calcImageComTime(long dataSize, VCPU vcpu) {
        //DCの情報@vcpu側
        Long fromDCID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        //DBの情報@リポジトリ
        NFVEnvironment nEnv = (NFVEnvironment)this.env;
        //リポジトリのdc
        Long toDCID = nEnv.getDockerRepository().getDcID();

        //Long toDCID = CloudUtil.getInstance().getDCID(toVCPU.getPrefix());
        long dcBW = NFVUtil.MAXValue;
        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);
        boolean isSameDC = false;
        //同一クラウド内であれば，DC間の通信は考慮しなくて良い．
        if (fromDCID.longValue() == toDCID.longValue()) {
            isSameDC = true;
        } else {
            //DCが異なれば，DC間の通信も考慮スべき．
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());

        }
        Long fromHostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());


        //後は，ホスト間での通信
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = nEnv.getDockerRepository();
        long hostBW = NFVUtil.MAXValue;
        if(isSameDC){
            if (fromHost.getMachineID() == toHost.getMachineID()) {
                //同一ホストなら，0を返す．
                return 0;
            } else {
                hostBW = Math.min(fromHost.getBw(), toHost.getBw());

            }
        }else{
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());
        }


        long realBW = Math.min(dcBW, hostBW);

        double time = CloudUtil.getRoundedValue((double) dataSize / (double) realBW);

        return time;

    }

    public long calcTotalFunctionInstanceNum() {
        long num = 0;
        Iterator<VCPU> vIte = this.assignedVCPUMap.values().iterator();
        while (vIte.hasNext()) {
            VCPU vcpu = vIte.next();
            HashMap<Integer, Long> map = new HashMap<Integer, Long>();
            Iterator<VNF> vnfIte = vcpu.getVnfQueue().iterator();
            while (vnfIte.hasNext()) {
                VNF vnf = vnfIte.next();
                if (map.containsKey(vnf.getType())) {
                    long val = map.get(vnf.getType());
                    val++;
                    map.put(vnf.getType(), val);
                } else {
                    map.put(vnf.getType(), new Long(1));
                }
            }
            num += map.size();
            map.clear();
        }
        return num;

    }

    public HashMap<String, VCPU> getSameHostVCPUMap(VCPU vcpu) {
        HashMap<String, VCPU> map = new HashMap<String, VCPU>();

        Long DCID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        Cloud cloud = this.env.getDcMap().get(DCID);
        Long hostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        ComputeHost host = cloud.getComputeHostMap().get(hostID);
        Iterator<VM> vIte2 = host.getVmMap().values().iterator();
        Iterator<CPU> cpuIte = host.getCpuMap().values().iterator();
        while (cpuIte.hasNext()) {
            CloudCPU cpu = (CloudCPU) cpuIte.next();
            Iterator<Core> cIte = cpu.getCoreMap().values().iterator();
            while (cIte.hasNext()) {
                Core c = cIte.next();
                Iterator<VCPU> vvIte = c.getvCPUMap().values().iterator();
                while (vvIte.hasNext()) {
                    VCPU vcpu2 = vvIte.next();
                    map.put(vcpu2.getPrefix(), vcpu2);
                }
            }

        }
        return map;
    }

    public boolean addVNFQueue(VCPU vcpu, VNF vnf) {
        PriorityQueue<VNF> queue = vcpu.getVnfQueue();
        Iterator<VNF> vITe = queue.iterator();
        boolean isFound = false;
        while (vITe.hasNext()) {
            VNF v = vITe.next();
            if (v.getIDVector().get(1).longValue() == vnf.getIDVector().get(1).longValue()) {
                isFound = true;
                break;
            }
        }
        if (isFound) {
            return false;
        } else {
            queue.add(vnf);
        }
        return true;
    }

    /**
     * @param vnf
     * @return
     */
    public void updateFreeList(VNF vnf) {
        //まずはfreeリストからvnfを削除する．
        this.freeVNFSet.remove(vnf.getIDVector().get(1));

        //次にfreeとなるVNFを選択する．
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();

        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            Long toID = dsuc.getToID().get(1);
            VNF sucVNF = this.sfc.findVNFByLastID(toID);
            if (this.freeVNFSet.contains(toID)) {
                continue;
            } else {
                boolean isFree = true;
                //sucVNFの先行VNFを見る．
                Iterator<DataDependence> dpredIte = sucVNF.getDpredList().iterator();
                while (dpredIte.hasNext()) {
                    DataDependence dpred = dpredIte.next();
                    Long fromID = dpred.getFromID().get(1);
                    if (this.unScheduledVNFSet.contains(fromID)) {
                        isFree = false;
                        break;
                    }
                }
                if (isFree) {
                    this.freeVNFSet.add(toID);
                }
            }
        }
    }

    /**
     * @param prefix
     * @return
     */
    public CloudCPU findCPU(String prefix) {
        long id = CloudUtil.getInstance().getCPUID(prefix);
        ComputeHost host = this.findHost(prefix);
        return (CloudCPU) host.getCpuMap().get(new Long(id));
    }

    /**
     * @param prefix
     * @return
     */
    public Core findCore(String prefix) {
        CloudCPU cpu = this.findCPU(prefix);
        long id = CloudUtil.getInstance().getCoreID(prefix);
        return cpu.getCoreMap().get(new Long(id));

    }

    /**
     * @param prefix
     * @return
     */
    public VCPU findVCPU(String prefix) {
        long id = CloudUtil.getInstance().getVCPUID(prefix);
        Core core = this.findCore(prefix);
        return core.getvCPUMap().get(new Long(id));

    }



    public double calcDuration(VNF vnf, VCPU cpu){
        double arrival_time = 0;
        double nodeDuration = 0;

        if (vnf.getDpredList().isEmpty()) {

        } else {
            Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
            while (dpredIte.hasNext()) {
                DataDependence dpred = dpredIte.next();
                VNF dpredTask = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                if(dpredTask.getvCPUID() == null){
                    continue;
                }
                //先行VNFのvcpuを取得する．
                VCPU predCPU = this.findVCPU(dpredTask.getvCPUID());

                double nw_time = 0;
                //先行タスクからのデータ転送時間を求める．
                nw_time = this.calcComTime(dpred.getMaxDataSize(), predCPU, cpu);

                double tmp_arrival_time = dpredTask.getStartTime() + this.calcExecTime(dpredTask.getWorkLoad(), predCPU) + nw_time;
                if (arrival_time <= tmp_arrival_time) {
                    arrival_time = tmp_arrival_time;
                }
            }
        }
        //arrival_time(DRT) ~ 最後のFinishTimeまでの範囲で，task/cpu速度の時間が埋められる
        //箇所があるかどうかを調べる．
        Object[] oa = cpu.getVnfQueue().toArray();
        double ret_starttime = NFVUtil.MAXValue;

        if (oa.length > 1) {
            boolean isInserted = false;
            //startTimeの小さい順にソート
            Arrays.sort(oa, new StartTimeComparator());
            int len = oa.length;
            for (int i = 0; i < len - 1; i++) {
                VNF t = ((VNF) oa[i]);
                double finish_time = t.getStartTime() + this.calcExecTime(t.getWorkLoad(), cpu);
                //次の要素の開始時刻を取得する．
                VNF t2 = ((VNF) oa[i + 1]);
                double start_time2 = t2.getStartTime();
                nodeDuration = start_time2 - finish_time;
            }
        }
        return nodeDuration;
    }

    //dlQueue:imageをDLする順番で並べている
    //dlList:dlQueueから取得したデータを格納する
    //map:
    protected HashMap<String, Double> getDLInfo(VNF vnf, VCPU vcpu){
        //imageのDL開始時刻と完了時刻 DLに関する情報を得る
        double dl_finish_time = 0;
        HashMap<String, Double> map = new HashMap<String, Double>();
        map.put("start", 0.0d);
        map.put("finish", 0.0d);


        if(NFVUtil.cloud_container_dl_mode == 1){
            LinkedList<VNF> dlList = vcpu.getDlQueue();
            if(dlList.isEmpty()){

            }else{
                //最後の要素の完了時刻を取得する。
                VNF lastVNF = dlList.getLast();
                double dlFinishTime = lastVNF.getDlFinishTime();
                map.put("start", dlFinishTime);
                //当該VNFのimage DL完了時刻を求める。
                dl_finish_time = dlFinishTime + this.calcDownloadImageTime(vnf, vcpu);
                map.put("finish", dl_finish_time);
            }

        }
        return map;
    }

    /**
     *
     * デッドライン,arrival_timeを計算する
     *
     * @param vnf
     * @param cpu
     * @return
     */
    protected HashMap<String, Double> calcDeadLine(VNF vnf, VCPU cpu){
        HashMap<String,Double> data = new HashMap<String, Double>();
        double arrival_time = 0;
        //arrivalTime:タスクの実行に必要なデータがノードに届く時刻
        double nCompTime = calcCT(cpu);
        //nCompTime:ノードで実行中のタスクの実行完了時刻
        //pred_finish_time:先行タスクの実行完了時刻

        if (vnf.getDpredList().isEmpty()) {

        } else {
            Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
            while (dpredIte.hasNext()) {
                DataDependence dpred = dpredIte.next();
                VNF dpredTask = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                if(dpredTask.getvCPUID() == null){
                    continue;
                }
                //先行VNFのvcpuを取得する．
                VCPU predCPU = this.findVCPU(dpredTask.getvCPUID());

                double nw_time = 0;
                //先行タスクからのデータ転送時間を求める．
                nw_time = this.calcComTime(dpred.getMaxDataSize(), predCPU, cpu);

                //pred_finish_time = dpredTask.getStartTime() + this.calcExecTime(dpredTask.getWorkLoad(), predCPU);
                //double tmp_arrival_time = pred_finish_time + nw_time;
                double tmp_arrival_time = dpredTask.getStartTime() + this.calcExecTime(dpredTask.getWorkLoad(), predCPU) + nw_time;
                if (arrival_time <= tmp_arrival_time) {
                    arrival_time = tmp_arrival_time;
                }
            }
        }
        data.put("arrival_time", arrival_time);
        double dead_line = Math.max(arrival_time, nCompTime);
        data.put("dead_line", dead_line);
        return data;
    }
    /**
     *
     * 指定VNFのESTを取得する．
     * constrainedモードがONならば，コアの利用率上限以内に収まる範囲で，割り当てられる箇所を探す．
     * もしなければ，-1を返す．
     *
     * @param vnf
     * @return
     */
    protected double calcEST(VNF vnf, VCPU cpu) {
        double arrival_time = 0;
        double dl_finish_time = 0;
        //vcpuの、ダウンロード開始時刻と完了時刻を計算する。
        dl_finish_time = this.getDLInfo(vnf, cpu).get("finish");
        arrival_time = this.calcDeadLine(vnf, cpu).get("arrival_time");

        //arrival_time(DRT) ~ 最後のFinishTimeまでの範囲で，task/cpu速度の時間が埋められる
        //箇所があるかどうかを調べる．
        Object[] oa = cpu.getVnfQueue().toArray();
        double ret_starttime = NFVUtil.MAXValue;

        if (oa.length > 1) {
            boolean isInserted = false;
            //startTimeの小さい順にソート
            Arrays.sort(oa, new StartTimeComparator());
            int len = oa.length;
            for (int i = 0; i < len - 1; i++) {
                VNF t = ((VNF) oa[i]);
                double finish_time = t.getStartTime() + this.calcExecTime(t.getWorkLoad(), cpu);
                //次の要素の開始時刻を取得する．
                VNF t2 = ((VNF) oa[i + 1]);
                double start_time2 = t2.getStartTime();
                double duration = start_time2 - finish_time;
                double s_candidateTime = Math.max(finish_time, arrival_time);
                //当該タスクの終了時刻を計算する．
                double ftime = s_candidateTime + this.calcExecTime(vnf.getWorkLoad(), cpu);
                //挿入可能な場合は，その候補の開始時刻を返す．
                if (ftime <= start_time2) {
                    //s_candidateTime ~ fTimeの間の，利用率合計値の最大値を計算する．
                    if (this.constrainedMode == 1) {
                        Core core = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());
                        if (this.isAssignedInDuration(s_candidateTime, ftime, core, cpu, vnf)) {
                            //割り当て可能なら，計算続行
                            if (ret_starttime >= s_candidateTime) {
                                ret_starttime = s_candidateTime;
                                isInserted = true;
                            }
                        } else {
                            //過負荷のために割り当て不能なら，continueする．
                            continue;
                        }
                    }

                } else {
                    continue;
                }

            }
            if (isInserted) {
                return Math.max(dl_finish_time, ret_starttime);
            } else {
                //挿入できない場合は，ENDテクニックを行う．
                //ENDテクニックであれば，過負荷とはならない．
                VNF finTask = ((VNF) oa[len - 1]);
                double end_starttime = Math.max(finTask.getStartTime() + this.calcExecTime(finTask.getWorkLoad(), cpu), arrival_time);
                double end_finishtime = end_starttime + this.calcExecTime(vnf.getWorkLoad(), cpu);
                Core c = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());
                if (this.constrainedMode == 1) {
                    if (this.isAssignedInDuration(end_starttime, end_finishtime, c, cpu, vnf)) {
                        return Math.max(dl_finish_time, end_starttime);
                    } else {
                        //ENDテクニックでもだめなら，もう片方のVCPUが終わるまで．
                        Iterator<VCPU> vITe = c.getvCPUMap().values().iterator();
                        double ret_newstarttime = -1;
                        while (vITe.hasNext()) {
                            VCPU v = vITe.next();
                            double CT = this.calcCT(v);
                            if (CT >= ret_newstarttime) {
                                ret_newstarttime = CT;
                            }

                        }
                        return Math.max(dl_finish_time, ret_newstarttime);
                    }
                } else {
                    return Math.max(dl_finish_time,
                            Math.max(finTask.getStartTime() + this.calcExecTime(finTask.getWorkLoad(), cpu), arrival_time));

                }
            }

        } else {

            double currentST = this.calcST(cpu);
            double currentCT = this.calcCT(cpu);
            Core core = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());

            //bakfillできる場合，
            double assumedCT = arrival_time + this.calcExecTime(vnf.getWorkLoad(), cpu);
            boolean flg = false;
            if (assumedCT <= currentST) {
                if (this.constrainedMode == 1) {
                    //bakfillする．
                    if (this.isAssignedInDuration(arrival_time, assumedCT, core, cpu, vnf)) {
                        return Math.max(dl_finish_time, arrival_time);
                    } else {
                        //currentCTと他vcpuのCTの大きい方を，開始時刻とする．
                        flg = true;
                    }
                } else {
                    return Math.max(dl_finish_time,arrival_time);
                }

            } else {
                //currentCTと他vcpuのCTの大きい方を，開始時刻とする．
                flg = true;
            }
            if (flg) {
                if (this.constrainedMode == 1) {
                    Iterator<VCPU> vITe = core.getvCPUMap().values().iterator();
                    double ret_newstarttime = -1;
                    while (vITe.hasNext()) {
                        VCPU v = vITe.next();
                        double CT = this.calcCT(v);
                        if (CT >= ret_newstarttime) {
                            ret_newstarttime = CT;
                        }

                    }
                    return Math.max(dl_finish_time, ret_newstarttime);

                } else {
                    return Math.max(dl_finish_time, currentCT);
                }
            }

        }
        return Math.max(dl_finish_time, arrival_time);


    }


    /**
     * 負荷具合を見て，当該区間で割当可能かどうかをチェックする．
     * コア内にあるvCPU全てに対してチェックして，コアとしての負荷の
     * 上限以下・以上かどうかのチェックをします．
     *
     * @param start
     * @param end
     * @param core
     * @param vcpu
     * @return
     */
    public boolean isAssignedInDuration(double start, double end, Core core, VCPU vcpu, VNF vnf) {
        //vcpu自体は，他にvnfが割り当てられないので，負荷としてはその値．
        long totalUsage = vnf.getUsage();
        long maxUsage = core.getMaxUsage();
        Iterator<VCPU> vIte = core.getvCPUMap().values().iterator();
        int size = core.getvCPUMap().size();

        while (vIte.hasNext()) {
            VCPU v = vIte.next();
            if (v.getPrefix().equals(vcpu.getPrefix())) {
                continue;
            } else {
                //異なるvcpuのときだけ計算する．
                Iterator<VNF> vnfIte = vcpu.getVnfQueue().iterator();
                long maxVCPUUsage = 0;
                while (vnfIte.hasNext()) {
                    VNF f = vnfIte.next();
                    if ((f.getFinishTime() < start) || (f.getStartTime() > end)) {
                        //含まれない場合は無視．
                    } else {
                        if (maxVCPUUsage <= f.getUsage()) {
                            maxVCPUUsage = f.getUsage();
                        }
                    }
                }
                totalUsage += maxVCPUUsage;
            }
        }
        //平均使用率を求める．
        double averageUsage = NFVUtil.getRoundedValue((double) totalUsage / (double) size);
        //コアの上限を超えれば，false;
        return !(averageUsage > maxUsage);

    }

    /**
     * @param vnf
     * @param set
     * @return
     */
    public double calcBlevel(VNF vnf, CustomIDSet set) {
        //先行VNFたちを取得する．
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        //END VNFであれば，blevelは処理時間
        if (vnf.getDsucList().isEmpty()) {
            double value = this.calcExecTime(vnf.getWorkLoad(), this.usedSpeed);
            vnf.setBlevel(value);
            set.add(vnf.getIDVector().get(1));
            return value;
        }

        double maxValue = -1;

        //以降はENDではない場合の処理
        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            Long toID = dsuc.getToID().get(1);
            VNF toVNF = this.sfc.findVNFByLastID(toID);
            double tmpValue = 0.0d;
            long dataSize = dsuc.getMaxDataSize();
            if (set.contains(toID)) {
                tmpValue = this.calcExecTime(vnf.getWorkLoad(), this.usedSpeed) + this.calcComTime(dataSize, this.usedBW) + toVNF.getBlevel();
            } else {
                tmpValue = this.calcExecTime(vnf.getWorkLoad(), this.usedSpeed) + this.calcComTime(dataSize, this.usedBW) + this.calcBlevel(toVNF, set);
            }
            if (maxValue <= tmpValue) {
                maxValue = tmpValue;
                vnf.setDominantSucID(toID);
                vnf.setBlevel(maxValue);
            }
        }
        return maxValue;
    }


    /**
     * @param vnf
     * @param set
     * @return
     */
    public double calcActualTlevel(VNF vnf, CustomIDSet set) {
        //先行VNFたちを取得する．
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        //START VNFであれば，tlevelは0となる．
        if (vnf.getDpredList().isEmpty()) {
            vnf.setTlevel(0.0d);
            set.add(vnf.getIDVector().get(1));
            return 0.0d;
        }

        double maxValue = -1;
        VCPU vcpu = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
        //以降はSTARTではない場合の処理
        while (dpredIte.hasNext()) {
            DataDependence dpred = dpredIte.next();
            Long fromID = dpred.getFromID().get(1);
            VNF fromVNF = this.sfc.findVNFByLastID(fromID);
            VCPU fromvcpu = this.env.getGlobal_vcpuMap().get(fromVNF.getvCPUID());
            double tmpValue = 0.0d;
            long dataSize = dpred.getMaxDataSize();
            if (set.contains(fromID)) {
                tmpValue = fromVNF.getTlevel() + this.calcExecTime(fromVNF.getWorkLoad(), fromvcpu) + this.calcComTime(dataSize, fromvcpu, vcpu);
            } else {
                tmpValue = this.calcActualTlevel(fromVNF, set) + this.calcExecTime(fromVNF.getWorkLoad(), fromvcpu) + this.calcComTime(dataSize, fromvcpu, vcpu);
            }
            if (maxValue <= tmpValue) {
                maxValue = tmpValue;
                vnf.setDominantPredID(fromID);
                vnf.setTlevel(maxValue);
            }
        }
        return maxValue;
    }

    /**
     * @param vnf
     * @param set
     * @return
     */
    public double calcActualBlevel(VNF vnf, CustomIDSet set) {
        //先行VNFたちを取得する．
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        VCPU vcpu = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());
        //END VNFであれば，blevelは処理時間
        if (vnf.getDsucList().isEmpty()) {
            double value = this.calcExecTime(vnf.getWorkLoad(), vcpu);
            vnf.setBlevel(value);
            set.add(vnf.getIDVector().get(1));
            return value;
        }

        double maxValue = -1;

        //以降はENDではない場合の処理
        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            Long toID = dsuc.getToID().get(1);
            VNF toVNF = this.sfc.findVNFByLastID(toID);
            VCPU tovcpu = this.env.getGlobal_vcpuMap().get(toVNF.getvCPUID());
            double tmpValue = 0.0d;
            long dataSize = dsuc.getMaxDataSize();
            if ((vcpu == null) || (tovcpu == null)) {
                System.out.println("NULL DESU");
            }
            if (set.contains(toID)) {
                tmpValue = this.calcExecTime(vnf.getWorkLoad(), vcpu) + this.calcComTime(dataSize, vcpu, tovcpu) + toVNF.getBlevel();
            } else {
                tmpValue = this.calcExecTime(vnf.getWorkLoad(), vcpu) + this.calcComTime(dataSize, vcpu, tovcpu) + this.calcActualBlevel(toVNF, set);
            }
            if (maxValue <= tmpValue) {
                maxValue = tmpValue;
                vnf.setDominantSucID(toID);
                vnf.setBlevel(maxValue);
            }
        }
        return maxValue;
    }

    /**
     * @param vnf
     * @param set
     * @return
     */
    public double calcTlevel(VNF vnf, CustomIDSet set) {
        //先行VNFたちを取得する．
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        //START VNFであれば，tlevelは0となる．
        if (vnf.getDpredList().isEmpty()) {
            vnf.setTlevel(0.0d);
            set.add(vnf.getIDVector().get(1));
            return 0.0d;
        }

        double maxValue = -1;

        //以降はSTARTではない場合の処理
        while (dpredIte.hasNext()) {
            DataDependence dpred = dpredIte.next();
            Long fromID = dpred.getFromID().get(1);
            VNF fromVNF = this.sfc.findVNFByLastID(fromID);
            double tmpValue = 0.0d;
            long dataSize = dpred.getMaxDataSize();
            if (set.contains(fromID)) {
                tmpValue = fromVNF.getTlevel() + this.calcExecTime(fromVNF.getWorkLoad(), this.usedSpeed) + this.calcComTime(dataSize, this.usedBW);
            } else {
                tmpValue = this.calcTlevel(fromVNF, set) + this.calcExecTime(fromVNF.getWorkLoad(), this.usedSpeed) + this.calcComTime(dataSize, this.usedBW);
            }
            if (maxValue <= tmpValue) {
                maxValue = tmpValue;
                vnf.setDominantPredID(fromID);
                vnf.setTlevel(maxValue);
            }
        }
        return maxValue;
    }

    /**
     * 規定の仕事量をvCPUで処理した場合の実行時間
     * を計算します．
     *
     * @param workLoad
     * @param vcpu
     * @return
     */
    public double calcExecTime(long workLoad, VCPU vcpu) {
        return CloudUtil.getRoundedValue((double) workLoad / (double) vcpu.getMips());
    }

    /**
     * 指定の処理速度のもとでの実行時間を求める．
     *
     * @param workLoad
     * @param speed
     * @return
     */
    public double calcExecTime(long workLoad, long speed) {
        return CloudUtil.getRoundedValue((double) workLoad / (double) speed);

    }

    public long getBW(VCPU u) {
        Long DCID = CloudUtil.getInstance().getDCID(u.getPrefix());
        Long hostID = CloudUtil.getInstance().getHostID(u.getPrefix());

        Cloud cloud = this.env.getDcMap().get(DCID);

        ComputeHost host = cloud.getComputeHostMap().get(hostID);
        long hostBW = host.getBw();
        return hostBW;
    }

    /**
     * 与えられたデータに対し，通信時間を求めます．
     *
     * @param dataSize
     * @param fromVCPU
     * @param toVCPU
     * @return
     */
    public double calcComTime(long dataSize, VCPU fromVCPU, VCPU toVCPU) {
        //DCの情報．
        Long fromDCID = CloudUtil.getInstance().getDCID(fromVCPU.getPrefix());
        Long toDCID = CloudUtil.getInstance().getDCID(toVCPU.getPrefix());
        long dcBW = NFVUtil.MAXValue;
        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);
        boolean isSameDC = false;
        //同一クラウド内であれば，DC間の通信は考慮しなくて良い．
        if (fromDCID.longValue() == toDCID.longValue()) {
            isSameDC = true;
        } else {
            //DCが異なれば，DC間の通信も考慮スべき．
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());

        }
        Long fromHostID = CloudUtil.getInstance().getHostID(fromVCPU.getPrefix());
        Long toHostID = CloudUtil.getInstance().getHostID(toVCPU.getPrefix());

        //後は，ホスト間での通信
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = toCloud.getComputeHostMap().get(toHostID);
        long hostBW = NFVUtil.MAXValue;
        if(isSameDC){
            if (fromHost.getMachineID() == toHost.getMachineID()) {
                //同一ホストなら，0を返す．
                return 0;
            } else {
                hostBW = Math.min(fromHost.getBw(), toHost.getBw());
            }
        }else{
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());
        }


        long realBW = Math.min(dcBW, hostBW);

        double time = CloudUtil.getRoundedValue((double) dataSize / (double) realBW);

        return time;

    }

    /**
     * 指定の帯域幅のもとでの通信時間を求める
     *
     * @param dataSize
     * @param bw
     * @return
     */
    public double calcComTime(long dataSize, long bw) {

        double time = CloudUtil.getRoundedValue((double) dataSize / (double) bw);
        return time;
    }


    /**
     * 当該VCPUの占有時間（開始?終了）
     *
     * @param cpu
     * @return
     */
    public double calcDuration(VCPU cpu) {
        return 0;
    }

    /**
     * 当該VCPUの再早開始時刻を求める
     *
     * @param vcpu
     * @return
     */
    public double calcST(VCPU vcpu) {
        PriorityQueue<VNF> queue = vcpu.getVnfQueue();
        Iterator<VNF> vIte = queue.iterator();
        double ST = NFVUtil.MAXValue;
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            if (ST >= vnf.getStartTime()) {
                ST = vnf.getStartTime();
            }
        }
        return ST;
    }

    /**
     * 当該VCPUの完了時刻を計算する．
     *
     * @param vcpu
     * @return
     */
    public double calcCT(VCPU vcpu) {
        PriorityQueue<VNF> queue = vcpu.getVnfQueue();
        Iterator<VNF> vIte = queue.iterator();
        double CT = 0;
        while (vIte.hasNext()) {
            VNF vnf = vIte.next();
            if (CT <= vnf.getFinishTime()) {
                CT = vnf.getFinishTime();
            }
        }
        return CT;
    }


    public double getMakeSpan() {

        //応答時間 = makspan = END VNFの終了時刻．
        double val = -1;
        Iterator<Long> endITe = this.sfc.getEndVNFSet().iterator();
        double FT = -1d;
        while (endITe.hasNext()) {
            Long eID = endITe.next();
            VNF endVNF = this.sfc.findVNFByLastID(eID);
            Iterator<DataDependence> dpredIte = endVNF.getDpredList().iterator();
            while (dpredIte.hasNext()) {
                DataDependence dpred = dpredIte.next();
                VNF preVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                double finishTime = preVNF.getFinishTime();
                if (finishTime >= FT) {
                    FT = finishTime;
                }

            }
        }
        //応答時間を決める．
        this.makeSpan = FT;
        return this.makeSpan;
    }

    /**
     * 負荷に関する公平性を計算する．
     * @return
     */
    public FairnessIndexInfo calcFairnessIndex(){
        Iterator<ComputeHost> hIte = this.env.getGlobal_hostMap().values().iterator();
        while(hIte.hasNext()){
            ComputeHost h = hIte.next();
            FairInfoAtHost ret = this.calcTimeDurationAtHost(h);
            //初期値であれば除外する．
            if(ret.getDuration()<=0){
            }else{
                double load = ret.getTotalExecTime() - ret.getDuration();
                HostStatistics stat = new HostStatistics(h.getPrefix(), load, ret.getDuration());
                this.fairHostMap.put(h.getPrefix(), stat);
            }
        }

        //再度，計算する．
        //Fair_loadでは，F_load = upValue_load/downValue_load
        Iterator<HostStatistics> staIte = this.fairHostMap.values().iterator();
        long size = this.fairHostMap.size();
        double upValue_load_root = 0;
        double upValue_load = 0;
        double downValue_load = 0;

        double upValue_duration_root = 0;
        double upValue_duration = 0;
        double downvalue_duration = 0;
        while(staIte.hasNext()){
            HostStatistics stat = staIte.next();
            upValue_load_root += stat.getAttr_load();
            downValue_load += Math.pow(stat.getAttr_load(), 2);

            upValue_duration_root += stat.getAttr_duration();
            downvalue_duration += Math.pow(stat.getAttr_duration(), 2);
        }
        upValue_load = Math.pow(upValue_load_root, 2);
        downValue_load = size * downValue_load;
        double FI_load= NFVUtil.getRoundedValue(upValue_load / downValue_load);

        //次に，Durationの公平性
        // F_duration = upValue_duration / downValue_duration
        upValue_duration = Math.pow(upValue_duration_root, 2);
        downvalue_duration = size * downvalue_duration;
        double FI_duration = NFVUtil.getRoundedValue(upValue_duration / downvalue_duration);
        FairnessIndexInfo info = new FairnessIndexInfo(FI_load, FI_duration);

        return info;


    }


    /**
     * 指定されたホストの占有時間を求めます．
     * @param h
     * @return
     */
    public FairInfoAtHost calcTimeDurationAtHost(ComputeHost h){
        Iterator<VM> vIte = h.getVmMap().values().iterator();
        double startTime = NFVUtil.MAXValue;
        double finishTime = -1;

        double retDuration = -1;
        double retTotalExec = 0;

        //VMごとのループ
        while(vIte.hasNext()){
            VM vm = vIte.next();
            Iterator<VCPU> vcpuIte = vm.getvCPUMap().values().iterator();
            while(vcpuIte.hasNext()){
                VCPU vcpu = vcpuIte.next();
                FairInfoAtVCPU info  = this.calcTimeDurationAtVCPU(vcpu);
                retTotalExec += info.getTotalExecTime();
                if(info.getStartTime()<=startTime){
                    startTime = info.getStartTime();
                }

                if(info.getFinishTime() >= finishTime){
                    finishTime = info.getFinishTime();
                }
            }
        }
        retDuration = finishTime - startTime;
        FairInfoAtHost hostInfo = new FairInfoAtHost(h.getPrefix(), retDuration, retTotalExec);


        return hostInfo;
    }

    private FairInfoAtVCPU calcTimeDurationAtVCPU(VCPU vcpu){
        Iterator<VNF> vIte = vcpu.getVnfQueue().iterator();
        double startTime = NFVUtil.MAXValue;
        double finishTime = -1;
        double totalExec = 0;
        while(vIte.hasNext()){
            VNF vnf = vIte.next();
            double tmpStart = vnf.getStartTime();
            double tmpEnd = vnf.getFinishTime();
            if(startTime >= tmpStart){
                startTime = tmpStart;
            }
            if(finishTime <= tmpEnd){
                finishTime = tmpEnd;
            }
            totalExec += vnf.getFinishTime() - vnf.getStartTime();
        }
        FairInfoAtVCPU info = new FairInfoAtVCPU(startTime, finishTime);
        info.setTotalExecTime(totalExec);

        return info;
    }

    public void setMakeSpan(double makeSpan) {
        this.makeSpan = makeSpan;
    }

    public CloudEnvironment getEnv() {
        return env;
    }

    public void setEnv(CloudEnvironment env) {
        this.env = env;
    }

    public SFC getSfc() {
        return sfc;
    }

    public void setSfc(SFC sfc) {
        this.sfc = sfc;
    }

    public long getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(long maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public long getMinSpeed() {
        return minSpeed;
    }

    public void setMinSpeed(long minSpeed) {
        this.minSpeed = minSpeed;
    }

    public long getAveSpeed() {
        return aveSpeed;
    }

    public void setAveSpeed(long aveSpeed) {
        this.aveSpeed = aveSpeed;
    }

    public long getMaxBW() {
        return maxBW;
    }

    public void setMaxBW(long maxBW) {
        this.maxBW = maxBW;
    }

    public long getMinBW() {
        return minBW;
    }

    public void setMinBW(long minBW) {
        this.minBW = minBW;
    }

    public long getAveBW() {
        return aveBW;
    }

    public void setAveBW(long aveBW) {
        this.aveBW = aveBW;
    }

    public CustomIDSet getFreeVNFSet() {
        return freeVNFSet;
    }

    public void setFreeVNFSet(CustomIDSet freeVNFSet) {
        this.freeVNFSet = freeVNFSet;
    }


    public CustomIDSet getUnScheduledVNFSet() {
        return unScheduledVNFSet;
    }

    public void setUnScheduledVNFSet(CustomIDSet unScheduledVNFSet) {
        this.unScheduledVNFSet = unScheduledVNFSet;
    }

    public HashMap<String, VCPU> getVcpuMap() {
        return vcpuMap;
    }

    public void setVcpuMap(HashMap<String, VCPU> vcpuMap) {
        this.vcpuMap = vcpuMap;
    }

    public long getUsedSpeed() {
        return usedSpeed;
    }

    public void setUsedSpeed(long usedSpeed) {
        this.usedSpeed = usedSpeed;
    }

    public long getUsedBW() {
        return usedBW;
    }

    public void setUsedBW(long usedBW) {
        this.usedBW = usedBW;
    }

    public CustomIDSet getScheduledVNFSet() {
        return scheduledVNFSet;
    }

    public void setScheduledVNFSet(CustomIDSet scheduledVNFSet) {
        this.scheduledVNFSet = scheduledVNFSet;
    }

    public HashMap<String, VCPU> getAssignedVCPUMap() {
        return assignedVCPUMap;
    }

    public void setAssignedVCPUMap(HashMap<String, VCPU> assignedVCPUMap) {
        this.assignedVCPUMap = assignedVCPUMap;
    }

    public int getConstrainedMode() {
        return constrainedMode;
    }

    public void setConstrainedMode(int constrainedMode) {
        this.constrainedMode = constrainedMode;
    }


    public HashMap<String, ComputeHost> getHostSet() {
        return hostSet;
    }

    public void setHostSet(HashMap<String, ComputeHost> hostSet) {
        this.hostSet = hostSet;
    }

    public long getTotalFunctionInstanceNum() {
        return totalFunctionInstanceNum;
    }

    public void setTotalFunctionInstanceNum(long totalFunctionInstanceNum) {
        this.totalFunctionInstanceNum = totalFunctionInstanceNum;
    }

    public HashMap<String, HostStatistics> getFairHostMap() {
        return fairHostMap;
    }

    public void setFairHostMap(HashMap<String, HostStatistics> fairHostMap) {
        this.fairHostMap = fairHostMap;
    }
}
