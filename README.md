# ncl_sfcsim
SFC (Service Function Chaining) Simulator
- For README in Japanese, see [here](https://github.com/ncl-teu/ncl_sfcsim/blob/master/README_JP.md). 
## Setup
- We confirmed the simulator runs on Java8(JRE1.8).
- Build: please run ant by `ant build`. 
- Run: plsease run by `nfvrun.bat` for windows or `./nfvrun.sh` for Linux. 
## Structure of the simulator. 
- The main class is located in `/src/net/gripps/cloud/nfv/main/`. In particular, please refer to **NFVSchedulingTest.java** or **NFVTest.java** in the directory. 
- This simulator loade the config. file named `nfv.properties` before the execution. Pelase set parameters in **nfv.properties** as needed. 
![1](https://user-images.githubusercontent.com/4952618/78368539-be1dfa00-75fe-11ea-9900-f474a58acdf0.jpg)
### Assumed environment
- The class that covers the whole network is `net.gripps.cloud.core.CloudEnvironment`. In this class, the following fields are defined. 
The actual used environmental class is `net.gripps.cloud.nfv.NFVEnvironment`, that extends CloudEnvironment.
~~~
    /**
     * Map object as a data center (cloud): <cloudID, cloud>
     */
    protected HashMap<Long, Cloud> dcMap;
    
    /**
    * The map of compute hosts: <prefix, computehost>
    */
    protected HashMap<String, ComputeHost> global_hostMap;
    
    /**
    * The map of cpus in a compute host: <cloudCPU's prefix, cpu> 
    */
    protected HashMap<String, CloudCPU> global_cpuMap;
    
    /**
    * The map of CPU cores in a cloudCPU: <Core prefix, core>
    */
    protected HashMap<String, Core> global_coreMap;
    
    /**
    * The map of VMs: <VM prefix, VM>
    */
    protected HashMap<String, VM> global_vmMap;
    
    /**
    * The map of vCPUs: <vCPU prefix, vCPU> 
    */
    protected HashMap<String, VCPU> global_vcpuMap;

    /**
     * Bandwidth among data centers (i.e., external BWs)
     */
    protected long[][] dcLinkMatrix;
~~~
- **Cloud(net.gripps.cloud.core.Cloud)**: A data center. 
~~~
    /**
     * Cloud ID
     */
    protected  Long id;

    /**
     * The map of ComputeHosts. 
     */
    protected HashMap<Long, ComputeHost> computeHostMap;

    /**
     * The external BW of the cloud. 
     */
    protected long bw;

~~~
- **ComputeHost(net.gripps.cloud.core.ComputeHost)**: A physical computer in a cloud. This class extends Machine class, having a CPU map `private TreeMap<Long, CPU> cpuMap;`. 
~~~
    /**
     * VM Map. 
     */
    private HashMap<String, VM> vmMap;


    /**
     * Data center ID to which this host belongs.
     */
    private Long dcID;

    /**
     * Prefix of this host.
     */
    private String prefix;

    /**
     * IP address
     */
    private String ipAddr;
~~~
- **CloudCPU(net.gripps.cloud.core.CloudCPU)**: A CPU socket in a ComputeHost. 
~~~
    /**
     * MIPS
     */
    private long mips;

    /**
     * CPU Core map of this CloudCPU. 
     */
    private HashMap<Long, Core> coreMap;

    /**
     * Cloud CPU ID
     */
    private String prefix;
~~~
- **CPU Core(net.gripps.cloud.core.Core)**:A CPU core in a CloudCPU. 
~~~
    /**
     * # of threads. If Hyper-Threading is enable, this value is set as 2. otherwise, it is 1. 
     */
    private int threadNum;

    /**
     * The threshold of usage (%)
     */
    private int maxUsage;

    /**
     * MIPS
     */
    private long mips;

    /**
     * CPU core ID
     */
    private Long coreID;

    /**
     * BW
     */
    private long bw;

    /**
     * VCPU Map
     */
    private HashMap<Long, VCPU> vCPUMap;

    /**
     * prefix of the core. 
     */
    private String prefix;
~~~
- **vCPU(net.gripps.cloud.core.VCPU)**: A vCPU in a CPU core. **We assume that each SF is allocated to a vCPU.**
~~~
    /**
     * vCPU ID, that is composed of dc_id^host_id^cpu_id^core_id^number. 
     * 
     */
    private String prefix;

    /**
     * CPU Core Prefix
     */
    private String corePrefix;

    /**
     * VM ID to which this vCPU belongs.
     */
    private String VMID;

    /**
     * MIPS
     */
    private long mips;

    /**
     * occupied mips. 
     */
    private long usedMips;
~~~
- Bandwidth is set from ComputeHostのNIC and Cloud. That is, BW of each ComputeHost is the one in LAN, and the one of Cloud is an external BW. 
- Above configuration is defined in`nfv.properties`. 
### SFC scheduling algorithm
- Under the **net.gripps.cloud.nfv** package, each scheduling algorithm is located in packages of `clustering`，`fairscheduling`，`listscheduling`, `optimization`. 
- **Clustering**: Algorithms that clusterings each SF (Service Function) and ordering by **SF-CUV (SF-Clustering for Utilizing vCPUs）** and **HClustering(HierarchicalVNFClusteringAlgorithm by Lee Tesu**) are implemented. 
- **listscheduling**: HEFT, PEFT, FWS，Random algorithms are included. 
- **optimization**: CoordVNFAlgorithm is included. 
### SFC structure (workflow/DAG)
- SFC: `net.gripps.cloud.nfv.sfc.SFC`. In this class, the following SF map is defined. 
~~~
    /**
     * VNF Map
     */
    private HashMap<Long, VNF> vnfMap;
~~~
where the map of (VNF ID, SF) is defined. 
- VNF: a Task/SF(Service function)．please refer to `net.gripps.cloud.nfv.sfc.VNF`. 
- VNF ID is，`private Vector<Long> IDVector`, **Index 0: SFC ID, Index1: VNF ID**．i.e., VNF ID exists at index 1.
- In VNF，` protected String vCPUID` and the allocation target vCPU ID are defined. 
#### How to create a new SFC schduling algorithm for list-based scheduling. 
1. Extends `BaseVNFSchedulingAlgorithm`. 
~~~
public class NEW_CLASS extends BaseVNFSchedulingAlgorithm 
....
~~~
2. Call super(env, sfc) in the constructor. 
~~~
    public NEW_CLASS(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

~~~
The initialization procedure is performed at this stage. 

3. Please implement the SF selection according to a specific priority. Then schedule the chosen SF as follows: 
~~~
    public void mainProcess() {
        //Loop during un-scheduled VNF exists. 
        while (!this.getUnScheduledVNFSet().isEmpty()) {
            VNF vnf = this.selectVNF();
            //Select the SF allocation target vCPU. 
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
        //Set the makespan. 
        this.makeSpan = val;
    }
~~~
Please implement `selectVNF()` by your own．The method for the SF allcation, i.e., this.scheduleVNF(vnf, this.vcpuMap) is already implemented in the super class. Thus, you have only call the method for the SF allcation. 
The arguments of this.scheduleVNF(vnf, this.vcpuMap) are: (The SF to be scheduled, set of vCPUs for the SF allocation). 

4. From an external method (e.g., main method)，Please call as follows: For more details, refer to **NFVtest.java or NFVSchedulingTest.java**. 
~~~
        NEW_CLASS alg = new NEW_CLASS(env, sfc);
        alg.mainProcess();
        System.out.println("makespan[NEW_CLASS]:"+alg.getMakeSpan()+" / # of vCPUs: "+alg.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg.getHostSet().size());
~~~
#### How to create a new SFC scheduling algorithm (SF clustering algorithm)
1. Please create a new clsss that extends `net.gripps.cloud.nfv.clustering.AbstractVNFClusteringAlgorithm`.
Then call super(env, sfc) in the constructor. Then the initialization process for the SF scheduling is finished. 
The critical part of a SF clustering algorithm is `public VNFCluster clustering(VNFCluster fromCluster, VNFCluster toCluster)`. 
This method implements the SF cluster merging. You have only to call this method from an external class. 
~~~
public class NEW_CLASS extends AbstractVNFClusteringAlgorithm {

    public NEW_CLASS(CloudEnvironment env, SFC sfc){
        super(env, sfc);
    }
    .....
}
~~~
2. Please implement abstract methods defined in AbstractVNFClusteringAlgorithm. 
These abstract methods requires to impelment how to select a SF cluster, and how to clustering them. 
~~~
    /**
     * SF cluster selection. 
     * @return
     */
    public abstract VNFCluster selectVNFCluster();


    /**
     * Any procedures can be implemented. For instnce, two SF clusters are merged in this method. 
     * @param cluster
     */
    public abstract VNFCluster processVNFCluster(VNFCluster cluster);
~~~
3. Then you can call scheduleVNF method to schdule each SF, or you can implement an SF scheduling by other policies. 
．
# Copyright

see [LICENSE](https://github.com/ncl-teu/ncl_sfcsim/blob/master/LICENSE)

Copyright (c) 2019 Hidehiro Kanemitsu <kanemitsuh@stf.teu.ac.jp>
