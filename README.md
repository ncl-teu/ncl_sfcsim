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
~~~
- **ComputeHost(net.gripps.cloud.core.ComputeHost)**: Cloud内にある物理計算機．Cloud内で複数指定可能．
- **CloudCPU(net.gripps.cloud.core.CloudCPU)**: ComputeHost内にあるCPUソケット．複数指定可能．
- **CPUコア(net.gripps.cloud.core.Core)**:1つのCloudCPU内にある，CPUコア．複数指定可能．
- **vCPU(net.gripps.cloud.core.VCPU)**: 1つのCPUコア内にある，1つor2つある仮想CPU．**SFは，このvCPUに対して割り当てることを想定している．**
- 通信帯域幅は，ComputeHostのNIC，及びCloudで設定します．つまりComputeHostはLAN内での帯域幅であり，Cloudは外部ネットワークへの帯域幅です．
- これら計算機資源のネットワーク全体を管理するクラスは，`net.gripps.cloud.core.CloudEnvironment`になります．特に，このSFCで用いられているのは
`net.gripps.cloud.nfv.NFVEnvironment`で，CloudEnvironmentを継承しています．
- 以上の構成は，`nfv.properties`で設定します．
