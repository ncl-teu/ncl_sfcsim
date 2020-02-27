# ncl_sfcsim
SFC (Service Function Chaining) Simulator
## セットアップ
- Java1.8 (JDK8)で動作確認しています．それ以上のバージョンだと，保証できません．
- コンパイルはIDEがあればそのIDEの方法できますが，Antを使うと便利です（もちろん，antのbinディレクトリにpathを通しておいて下さい）．Antを使う場合は，`ant build`コマンドでビルドします．すると，classesディレクトリが生成されて，そこにclassファイル群が入ります．
- IDEを使う場合は，classpathとしてlib内にある全jarファイル＋classesディレクトリを追加してください．おすすめIDEは，IntelliJ IDEAです．
- 実行は，shファイル（Linuxの場合）やbatファイル（Windowsの場合）を使って下さい．
## 仕組み
- Mainメソッドは，`/src/net/gripps/cloud/nfv/main/`内にあるものを使います．特に，**NFVSchedulingTest.java**や，**NFVTest.java**を参照してください．
- 実行の際に，設定ファイルを読み込みます．
### シミュレータ上の処理環境（クラウド）について
- **Cloud(net.gripps.cloud.core.Cloud)**: 一番大きな単位であり，1ネットワークそのものと考えて下さい．
- **ComputeHost(net.gripps.cloud.core.ComputeHost)**: クラウド内にある物理計算機．
- **CloudCPU(net.gripps.cloud.core.CloudCPU)**: ComputeHost内に1つ以上あるCPUソケット．
- **CPUコア(net.gripps.cloud.core.Core)**:1つのCloudCPU内にある，1つ以上のCPUコア．
- **vCPU(net.gripps.cloud.core.VCPU)**: 1つのCPUコア内にある，1つor2つある仮想CPU．**SFは，このvCPUに対して割り当てることを想定している．**
- 通信帯域幅は，ComputeHostのNIC，及びCloudで設定します．つまりComputeHostはLAN内での帯域幅であり，Cloudは外部ネットワークへの帯域幅です．
