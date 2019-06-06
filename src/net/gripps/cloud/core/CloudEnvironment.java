package net.gripps.cloud.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.environment.CPU;
import net.gripps.environment.Environment;

import java.io.Serializable;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

public class CloudEnvironment extends Environment implements Serializable, Cloneable {



    /**
     * データセンターのマップ
     */
    protected HashMap<Long, Cloud> dcMap;

    protected HashMap<String, ComputeHost> global_hostMap;

    protected HashMap<String, CloudCPU> global_cpuMap;

    protected HashMap<String, Core> global_coreMap;

    protected HashMap<String, VM> global_vmMap;

    protected HashMap<String, VCPU> global_vcpuMap;


    /**
     * データセンター間の帯域幅
     */
    protected long[][] dcLinkMatrix;

    /**
     * コンストラクタ
     */
    public CloudEnvironment(){
        this.dcLinkMatrix = new long[CloudUtil.num_dc][CloudUtil.num_dc];

       // this.mobileMap = new HashMap<Long, MobileTerminal>();
        this.global_hostMap = new HashMap<String, ComputeHost>();
        this.global_cpuMap = new HashMap<String, CloudCPU>();
        this.global_coreMap = new HashMap<String, Core>();
        this.global_vmMap = new HashMap<String, VM>();
        this.global_vcpuMap = new HashMap<String, VCPU>();
        this.dcMap =  this.buildDCMap();
    }




    /**
     * 設定ファイルから，モバイル端末の情報を読み込みます．
     * @return
     */
    public HashMap<Long, MobileTerminal> buildMobileMap(){
        return null;
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
                            VCPU vcpu = new VCPU(prefix, corePrefix,  pMap, null, (long)(mips*rate),  0);
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
                    //int realLen = vcpu_num;

                    for(int q=0;q<realLen;q++){
                        if(vQueue.isEmpty()){
                            isMoreVM = false;
                            break;
                        }
                        //先程追加したキューから，vcpuを取り出して入れる．
                        VCPU takenVCPU = vQueue.poll();
                        takenVCPU.setVMID(vmPrefix);
                        vMap.put(takenVCPU.getPrefix(), takenVCPU);

                    }
                    if(vMap.isEmpty()){
                        break;
                    }
                    VM vm  = new VM(vmPrefix, hostPrefix,  vMap, ramSize, vmPrefix);
                    if(vMap.isEmpty()){
                        System.out.println("Empty!!");
                    }
                    //VMを，ホストへ追加する．
                    host.getVmMap().put(vmPrefix, vm);
                    this.global_vmMap.put(vmPrefix, vm);
                }


            }

        }
        //this.setDcMap(retMap);


        return retMap;
    }





    public HashMap<Long, Cloud> getDcMap() {
        return dcMap;
    }

    public void setDcMap(HashMap<Long, Cloud> dcMap) {
        this.dcMap = dcMap;
    }

    public long[][] getDcLinkMatrix() {
        return dcLinkMatrix;
    }

    public void setDcLinkMatrix(long[][] dcLinkMatrix) {
        this.dcLinkMatrix = dcLinkMatrix;
    }

    public HashMap<String, ComputeHost> getGlobal_hostMap() {
        return global_hostMap;
    }

    public void setGlobal_hostMap(HashMap<String, ComputeHost> global_hostMap) {
        this.global_hostMap = global_hostMap;
    }

    public HashMap<String, CloudCPU> getGlobal_cpuMap() {
        return global_cpuMap;
    }

    public void setGlobal_cpuMap(HashMap<String, CloudCPU> global_cpuMap) {
        this.global_cpuMap = global_cpuMap;
    }

    public HashMap<String, Core> getGlobal_coreMap() {
        return global_coreMap;
    }

    public void setGlobal_coreMap(HashMap<String, Core> global_coreMap) {
        this.global_coreMap = global_coreMap;
    }

    public HashMap<String, VM> getGlobal_vmMap() {
        return global_vmMap;
    }

    public void setGlobal_vmMap(HashMap<String, VM> global_vmMap) {
        this.global_vmMap = global_vmMap;
    }

    public HashMap<String, VCPU> getGlobal_vcpuMap() {
        return global_vcpuMap;
    }

    public void setGlobal_vcpuMap(HashMap<String, VCPU> global_vcpuMap) {
        this.global_vcpuMap = global_vcpuMap;
    }
}
