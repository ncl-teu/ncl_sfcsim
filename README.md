# ncl_sfcsim
SFC (Service Function Chaining) Simulator
## セットアップ
- Java1.8 (JDK8)で動作確認しています．それ以上のバージョンだと，保証できません．
- コンパイルはIDEがあればそのIDEの方法できますが，Antを使うと便利です（もちろん，antのbinディレクトリにpathを通しておいて下さい）．Antを使う場合は，`ant build`コマンドでビルドします．
- IDEを使う場合は，classpathとしてlib内にある全jarファイルを追加してください．おすすめIDEは，IntelliJ IDEAです．
- 実行は，shファイル（Linuxの場合）やbatファイル（Windowsの場合）を使って下さい．
## 仕組み
- Mainメソッドは，`/src/net/gripps/cloud/nfv/main/`内にあるものを使います．特に，**NFVSchedulingTest.java**や，**NFVTest.java**を参照してください．
- 実行の際に，設定ファイルを読み込みます．
