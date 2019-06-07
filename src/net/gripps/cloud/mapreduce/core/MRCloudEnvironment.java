package net.gripps.cloud.mapreduce.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.environment.CPU;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Hidehiro Kanemitsu on 2019/03/31.
 */
public class MRCloudEnvironment extends CloudEnvironment {


    protected FSHost fsHost;

    public MRCloudEnvironment() {
        super();

    }

    public FSHost getFsHost() {
        return fsHost;
    }

    public void setFsHost(FSHost fsHost) {
        this.fsHost = fsHost;
    }

    /**
     * クラウドデータセンターの集合を生成するためのメソッドです．
     * protectedとすることにより，外部から呼ばれることを防ぎます．
     *
     * @return
     */
    public  HashMap<Long, Cloud> buildDCMap(){
        HashMap<Long, Cloud> retMap = new HashMap<Long, Cloud>();
        //DC数だけのループ
        for(int i = 0; i< CloudUtil.num_dc; i++){
            Cloud dc = new Cloud();
            dc.setId(new Long(i));

            long dc_bw= CloudUtil.genLong(CloudUtil.datacenter_externalbw_min, CloudUtil.datacenter_externalbw_max);
            dc.setBw(dc_bw);
            //ホスト数の生成
            long hostNum = CloudUtil.genLong(CloudUtil.host_num_foreachdc_min, CloudUtil.host_num_foreachdc_max);

            HashMap<Long, ComputeHost> hostMap = new HashMap<Long, ComputeHost>();

            //ホスト数分だけのループ
            for(int j=0;j<hostNum;j++) {
                //ComputeHostの生成
                //CPUソケット数
                int cpuNum = CloudUtil.genInt2(CloudUtil.host_cpu_num_min, CloudUtil.host_cpu_num_max, CloudUtil.dist_host_cpu_num, CloudUtil.dist_host_cpu_num_mu);
                //帯域幅
                long bw = CloudUtil.genLong2(CloudUtil.host_bw_min, CloudUtil.host_bw_max, CloudUtil.dist_host_bw, CloudUtil.dist_host_bw_mu);
                TreeMap<Long, CPU> cpuMap = new TreeMap<Long, CPU>();
                LinkedBlockingQueue<VCPU> vQueue = new LinkedBlockingQueue<VCPU>();
                boolean isMoreVM = true;

                //CPUソケット数だけのループ
                for (int k = 0; k < cpuNum; k++) {
                    //コア数
                    int coreNum = CloudUtil.genInt(CloudUtil.host_core_num_foreachcpu_min, CloudUtil.host_core_num_foreachcpu_max);
                    long mips = CloudUtil.genLong2(CloudUtil.host_mips_min, CloudUtil.host_mips_max, CloudUtil.dist_host_mips, CloudUtil.dist_host_mips_mu);
                    HashMap<Long, Core> coreMap = new HashMap<Long, Core>();

                    //コア数分だけのループ
                    for(int l=0;l < coreNum;l++){
                        double rate = Math.min(1.0, CloudUtil.genDouble(CloudUtil.core_mips_rate_min, CloudUtil.core_mips_rate_max));
                        HashMap<Long, VCPU> vcpuMap = new HashMap<Long, VCPU>();
                        String corePrefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + k + CloudUtil.DELIMITER + l;
                        //VCPUを生成．
                        for(int m = 0; m< CloudUtil.host_thread_num_foreeachcore; m++){
                            String prefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + k + CloudUtil.DELIMITER + l + CloudUtil.DELIMITER + m;
                            HashMap<String, Long> pMap = new HashMap<String, Long>();
                            pMap.put(CloudUtil.ID_DC, new Long(i));
                            pMap.put(CloudUtil.ID_HOST, new Long(j));
                            pMap.put(CloudUtil.ID_CPU, new Long(k));
                            pMap.put(CloudUtil.ID_CORE, new Long(l));
                            pMap.put(CloudUtil.ID_VCPU, new Long(m));
                            //  String cPrefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + k + CloudUtil.DELIMITER + l;
//変更箇所 START
                            VCPU vcpu = new MRVCPU(prefix, corePrefix,  pMap, null, (long)(mips*rate),  0);
//変更箇所 END
                            vcpuMap.put(new Long(m), vcpu);
                            String id = vcpu.getPrefix();
                            vQueue.offer(vcpu);
                            this.global_vcpuMap.put(prefix, vcpu);

                        /*    System.out.println("vCPU_Prefix:"+vcpu.getPrefix()+"/DataCenterID:"+CloudUtil.getInstance().getDCID(id)+"/HostID:"+CloudUtil.getInstance().getHostID(id)+
                                    "/CPUSocketID:"+CloudUtil.getInstance().getCPUID(id)+"/CoreID:"+CloudUtil.getInstance().getCoreID(id)+"/vCPUID:"+CloudUtil.getInstance().getVCPUID(vcpu.getPrefix()));
                     */
                        }

                        //System.out.println("core:"+corePrefix);
                        HashMap<String, Long> pMap = new HashMap<String, Long>();
                        pMap.put(CloudUtil.ID_DC, new Long(i));
                        pMap.put(CloudUtil.ID_HOST, new Long(j));
                        pMap.put(CloudUtil.ID_CPU, new Long(k));
                        pMap.put(CloudUtil.ID_CORE, new Long(l));
                        //コアの利用率上限値を設定する．
                        int maxUsage = NFVUtil.core_max_usage;
                        Core c = new Core(corePrefix, CloudUtil.host_thread_num_foreeachcore, (long)(mips*rate), new Long(l),  vcpuMap, maxUsage);
                        c.setPrefixMap(pMap);
                        coreMap.put(c.getCoreID(), c);
                        this.global_coreMap.put(corePrefix, c);


                    }


                    String cpuPrefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + k;
                    HashMap<String, Long> pMap = new HashMap<String, Long>();
                    pMap.put(CloudUtil.ID_DC, new Long(i));
                    pMap.put(CloudUtil.ID_HOST, new Long(j));
                    pMap.put(CloudUtil.ID_CPU, new Long(k));

                    CloudCPU cpu = new CloudCPU(new Long(k), mips, new Vector(), new Vector(),
                            mips, coreMap, cpuPrefix, pMap );

                    cpuMap.put(new Long(k), cpu);
                    this.global_cpuMap.put(cpuPrefix, cpu);
                }
                if((i == 0)&&(j==0)){
                    long fsbw = MRUtil.dfhost_bw;
                    int mode = MRUtil.dfs_transfer_mode;
                    boolean modeFlg = false;
                    if(mode == 0){
                        modeFlg = false;
                    }else{
                        modeFlg = true;
                    }
                    this.fsHost = new FSHost(
                            -1,new TreeMap<Long, CPU>(), 1, new HashMap<String, VM>(), new Long(0), null, fsbw, modeFlg);
                    //唯一のファイルシステムホストを最初のクラウドへセットする．
                    dc.setFsHost(this.fsHost);

                }
                String hostPrefix = i + CloudUtil.DELIMITER + j;
                ComputeHost host = new ComputeHost(new Long(j), cpuMap, cpuNum, new HashMap<String, VM>(), new Long(i), hostPrefix, bw);
                hostMap.put(new Long(j), host);
                dc.setComputeHostMap(hostMap);
                retMap.put(new Long(i), dc);
                this.global_hostMap.put(hostPrefix, host);


                //次に，VM数分だけのループ
                int vm_num = CloudUtil.genInt(CloudUtil.vm_num_foreachdc_min, CloudUtil.vm_num_foreachdc_max);
                //HashMap<String, VM> vmMap = new HashMap<String, VM>();
                for(int v=0;v<vm_num;v++){
                    if(!isMoreVM){
                        break;
                    }
                    //IDを生成．
                    String vmPrefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + v;
                    //String hostPrefix =  i + CloudUtil.DELIMITER + j;
                    long ramSize = CloudUtil.genLong(CloudUtil.vm_mem_min, CloudUtil.vm_mem_max);
                    int vcpu_num = CloudUtil.genInt2(CloudUtil.vm_vcpu_num_min, CloudUtil.vm_vcpu_num_max,
                            CloudUtil.dist_vm_vcpu_num, CloudUtil.dist_vm_vcpu_num_mu);
                    //     VM vm  = new VM(vmPrefix, hostPrefix,  new HashMap<String, VCPU>(), ramSize, vmPrefix);
                    HashMap<String, VCPU> vMap = new HashMap<String, VCPU>();

                    int realLen = Math.min(vcpu_num, vQueue.size());
                    for(int q=0;q<realLen;q++){
                        if(vQueue.isEmpty()){
                            isMoreVM = false;
                            break;
                        }
                        //先程追加したキューから，vcpuを取り出して入れる．
                        VCPU takenVCPU = vQueue.poll();
                        vMap.put(takenVCPU.getPrefix(), takenVCPU);

                    }
                    VM vm  = new VM(vmPrefix, hostPrefix,  vMap, ramSize, vmPrefix);
                    //VMを，ホストへ追加する．
                    host.getVmMap().put(vmPrefix, vm);
                    this.global_vmMap.put(vmPrefix, vm);
                }


            }

        }

        return retMap;
    }

}
