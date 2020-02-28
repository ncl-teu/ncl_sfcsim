# ncl_sfcsim
SFC (Service Function Chaining) Simulator
## セットアップ
- Java8 (JRE1.8)で動作確認しています．JDKは1.8．それ以上のバージョンだと，保証できません．
- コンパイルはIDEがあればそのIDEの方法できますが，Antを使うと便利です（もちろん，antのbinディレクトリにpathを通しておいて下さい）．Antを使う場合は，`ant build`コマンドでビルドします．すると，classesディレクトリが生成されて，そこにclassファイル群が入ります．
- IDEを使う場合は，classpathとしてlib内にある全jarファイル＋classesディレクトリを追加してください．おすすめIDEは，IntelliJ IDEAです．
- 実行は，shファイル（Linuxの場合）やbatファイル（Windowsの場合）を使って下さい．
## 仕組み
- Mainメソッドは，`/src/net/gripps/cloud/nfv/main/`内にあるものを使います．特に，**NFVSchedulingTest.java**や，**NFVTest.java**を参照してください．
- 実行の際に，設定ファイルである`nfv.properties`を読み込みます．
### シミュレータ上の処理環境（クラウド）について
- **Cloud(net.gripps.cloud.core.Cloud)**: 一番大きな単位であり，1データセンターと考えて下さい．複数指定可能．
~~~
    /**
     * データセンター（クラウド）のマップで，(クラウドのID, クラウド）
     */
    protected HashMap<Long, Cloud> dcMap;
    
    /**
    *(文字列のID，ComputeHost)によるComputeHostのマップ
    */
    protected HashMap<String, ComputeHost> global_hostMap;
    
    /**
    * 直接アクセスするために用意された，CloudCPUのマップ．Stringは，CloudCPUのID
    */
    protected HashMap<String, CloudCPU> global_cpuMap;
    
    /**
    * 直接アクセスするために用意された，Coreのマップ．Stringは，CoreのID
    */
    protected HashMap<String, Core> global_coreMap;
    
    /**
    * 直接アクセスするために用意された，VMのマップ．Stringは，VMのID
    */
    protected HashMap<String, VM> global_vmMap;
    
    /**
    * 直接アクセスするために用意された，vCPUのマップ．Stringは，VCPUのID
    */
    protected HashMap<String, VCPU> global_vcpuMap;

    /**
     * データセンター間の帯域幅
     */
    protected long[][] dcLinkMatrix;
~~~
- **ComputeHost(net.gripps.cloud.core.ComputeHost)**: Cloud内にある物理計算機．Cloud内で複数指定可能．
~~~
    /**
     * VMのMapです．同一VMで複製した場合も，別個のVMとして扱います．
     * ただし，VM内に，「オリジナルVMID」を保持させているので，どのVMからの複製かは
     * わかります．
     */
    private HashMap<String, VM> vmMap;


    /**
     * 当該ホストが属するデータセンターID
     */
    private Long dcID;

    /**
     * このホストのprefix（文字列）
     */
    private String prefix;

    /**
     * このホストのIPアドレス
     */
    private String ipAddr;
    ~~~
- **CloudCPU(net.gripps.cloud.core.CloudCPU)**: ComputeHost内にあるCPUソケット．複数指定可能．
~~~
    /**
     * MIPS値．
     */
    private long mips;

    /**
     * このCloudCPUが持っているCoreのマップ．
     */
    private HashMap<Long, Core> coreMap;

    /**
     * このCloudCPUのID
     */
    private String prefix;
    ~~~
- **CPUコア(net.gripps.cloud.core.Core)**:1つのCloudCPU内にある，CPUコア．複数指定可能．
~~~
    /**
     * スレッド数．もしHyper Threadingがonであれば2．
     * そうでなければ1となる．
     */
    private int threadNum;

    /**
     * 想定される使用率の上限
     */
    private int maxUsage;

    /**
     * MIPS
     */
    private long mips;

    /**
     * コアのID
     */
    private Long coreID;

    /**
     * 帯域幅（使いみちなし？）
     */
    private long bw;

    /**
     * VCPUのMap
     */
    private HashMap<Long, VCPU> vCPUMap;

    /**
     * コアのprefix（文字列）
     */
    private String prefix;
    ~~~
- **vCPU(net.gripps.cloud.core.VCPU)**: 1つのCPUコア内にある，1つor2つある仮想CPU．**SFは，このvCPUに対して割り当てることを想定している．**
~~~
    /**
     * vCPUのIDです．dc_id^host_id^cpu_id^core_id^number
     * から構成されます．
     */
    private String prefix;

    /**
     * コアのPrefix
     */
    private String corePrefix;

    /**
     * このvCPUが所属するVMのID
     */
    private String VMID;

    /**
     * MIPSの定義
     */
    private long mips;

    /**
     * 占有されているMIPS
     */
    private long usedMips;
    ~~~
- 通信帯域幅は，ComputeHostのNIC，及びCloudで設定します．つまりComputeHostはLAN内での帯域幅であり，Cloudは外部ネットワークへの帯域幅です．
- これら計算機資源のネットワーク全体を管理するクラスは，`net.gripps.cloud.core.CloudEnvironment`になります．特に，このSFCで用いられているのは
`net.gripps.cloud.nfv.NFVEnvironment`で，CloudEnvironmentを継承しています．
- 以上の構成は，`nfv.properties`で設定します．
