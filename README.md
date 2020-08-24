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
- **CPUコア(net.gripps.cloud.core.Core)**:A CPU core in a CloudCPU. 
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
- **vCPU(net.gripps.cloud.core.VCPU)**: A vCPU in a CPU core. **We assume that each SF is allocated to a vCPU. **
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
### SFCの構造について
- SFC: `net.gripps.cloud.nfv.sfc.SFC`. In this class, the following SF map is defined. 
~~~
    /**
     * VNFのMap
     */
    private HashMap<Long, VNF> vnfMap;
~~~
where the map of (VNF ID, SF) is defined. 
- VNF: a Task/SF(Service function)．please refer to `net.gripps.cloud.nfv.sfc.VNF`. 
- VNFのIDは，`private Vector<Long> IDVector`となっており，**Index 0: SFCのID, Index1: VNFのID**です．つまり，VNFのIDを知りたければインデックス1の値を見ることになります．
- VNFには，` protected String vCPUID`そして，VNFの割当先vCPUのIDが定義されていますので，適宜，セットしてください．
#### 新規にSFCスケジューリングアルゴリズムを作成する方法（listスケジューリングの場合）
1. BaseVNFSchedulingAlgorithmを継承する．
~~~
public class 新規アルゴリズムのクラス名 extends BaseVNFSchedulingAlgorithm 
....
~~~
2. コンストラクタでsuper(env, sfc)を呼び出す．
~~~
    public 新規アルゴリズムのクラス名(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

~~~
この時点で，スケジューリングするための初期設定がなされます．

3. 優先度に従ってSFを選択する(vnfとする）．そして，vnfをスケジュールする．例えば以下のようにしてください．
~~~
    public void mainProcess() {
        //未スケジュールなVNFが残っている間，行うループ
        while (!this.getUnScheduledVNFSet().isEmpty()) {
            VNF vnf = this.selectVNF();
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
~~~
このうち，`selectVNF()`は自身で実装してください．また，this.scheduleVNF(vnf, this.vcpuMap)は，superクラスで実装済みなので呼び出せばOKです．
this.scheduleVNF(vnf, this.vcpuMap)の引数は(スケジュール対象のVNF, VNFの割り当て候補のvCPU集合）という意味です．

4. 外部のmainメソッドから，下記のように呼び出してください．詳しくは**NFVtest.javaかNFVSchedulingTest.java**を参照．
~~~
        新規アルゴリズムクラス alg = new 新規アルゴリズムクラス(env, sfc);
        alg.mainProcess();
        System.out.println("makespan[新規のアルゴリズム名]:"+alg.getMakeSpan()+" / # of vCPUs: "+alg.getAssignedVCPUMap().size()+ "/ # of Hosts:"+alg.getHostSet().size());
~~~
#### 新規にSFCスケジューリングアルゴリズムを作成する方法（クラスタリングアルゴリズムの場合）
1. `net.gripps.cloud.nfv.clustering.AbstractVNFClusteringAlgorithm`クラスを継承した新規クラスを作る．
そして，コンストラクタでsuper(env, sfc)を呼ぶ．これで，スケジューリング及びクラスタリングのための初期設定がなされます．
また，最も大事なのはクラスタ同士をマージするメソッドである`public VNFCluster clustering(VNFCluster fromCluster, VNFCluster toCluster)`です．これは，2つのクラスタをマージして一つのクラスタとする処理です．新規クラス側ではこれをcallすればよいです．
~~~
public class 新規クラス名 extends AbstractVNFClusteringAlgorithm {

    public 新規クラス名(CloudEnvironment env, SFC sfc){
        super(env, sfc);
    }
    .....
}
~~~
2. AbstractVNFClusteringAlgorithmでabstract宣言されているメソッドを実装する．
これらは，クラスタ選択，及びクラスタリングに関する処理を実装してください．
~~~
    /**
     * 何らかの基準でVNFClusterを選択します．
     * 実装クラス側で実装してください．
     * @return
     */
    public abstract VNFCluster selectVNFCluster();


    /**
     * 指定クラスタに対して何らかの処理を行います．
     * 例えば，clusterを他クラスタとマージするなど，です．
     * @param cluster
     */
    public abstract VNFCluster processVNFCluster(VNFCluster cluster);
~~~
3. その後は，scheduleVNFメソッドによって各SFに対するスケジュールをしてもよいですし，
他の方法で各リソースの時間スロットへSFを割り当ててもよいです．
