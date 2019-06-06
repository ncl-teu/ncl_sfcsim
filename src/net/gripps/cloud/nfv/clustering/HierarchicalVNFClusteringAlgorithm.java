package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNFCluster;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/02.
 * 階層型クラスタリングアルゴリズムです．上位階層が数は少ないが負荷が高くて仕事量が多いVNFと想定される．
 * したがって，上位階層では性能の高いvCPUの一方で，さほど数が無いようなVMを割り当てる．
 * 下位の階層では，性能は求められないがvCPU数が多いVMが必要．これらの要素が，上位／下位でそれぞれ
 * 別個のVMを割り当てる理由となる．かつ，同一ホストであればネットワーク遅延が発生しないので嬉しい．
 * 1. SFCを階層化してトータルでNレベルとして，作成する(依存関係の無いものどうしが同一階層）
 * 2. Nコのレベルから，VNFの仕事量の合計が均一となるようなグループを決める．決め方は↓．
 *      A. まず，半分のレベル（=N/2）のレベルで切る．上半分のVNFの仕事量合計(W1)と下半分のVNFの仕事量合計(W2)を求める．
 *      B. もしW1 >> W2であれば，W1をさらに半分にする．この時点で3つの「VNFグループ」の仕事量の合計（W11, W12, W2)がわかっていることになる．
 *      C. さらにW11 >> W12なら，W11の領域を半分のレベルで切って・・・・を，仕事量の合計が均一 OR のこり1レベルになるまで繰り返す．
 *      D. レベルの中で「VNFグループ1~G」までできたものとする．次に「VMグループ1～G」にVMを入れる処理にうつる．
 *      E. VNFグループ1～Gまでのループをして，
 *          E-1. VMグループiにいれるための各属性の重み(周波数a1，vCPU数: : a2，メモリ: a3，BW: a4）を考慮した評価関数を定義する．
 *                  評価関数F = a1 * 周波数 + a2 * vCPU数 + メモリ* a3 + BW * a4 とする．
 *                  たとえばiが小さければ，a1は大きくとるべきで，逆にa2は小さくすべき．
 *          E-2. Fが大きい順にVMをソートして，先頭から順に，(VNFグループi内のVNF数 = VMグループ内のvCPU数の合計）となるまで入れる．
 *                  これを，i == Gとなったらループを抜ける．この時点で，VMグループにはVM集合が入っている．
 *      F. あとは，各VNFグループ単位で，VNFの割当先を探す．(VNFグループi内のVNFの割当先は，VMグループiのVMから探す）．
 *
 */
public class HierarchicalVNFClusteringAlgorithm extends AbstractVNFClusteringAlgorithm {

    public HierarchicalVNFClusteringAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
    }

    @Override
    public VNFCluster selectVNFCluster() {
        return null;
    }

    @Override
    public VNFCluster processVNFCluster(VNFCluster cluster) {
        return null;
    }
}
