package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.nfv.sfc.VNFCluster;

import java.util.*;

//import org.apache.commons.collections.ArrayStack;

/* Created by Hidehiro Kanemitsu on 2018/12/02.

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

    public void mainProcess() {


        //SRC

        //

        ListIterator<HashMap<Long, VNF>> vnfMapIte = this.sfc.getLevelVNFSet().listIterator();

        //System.out.println("Level num:" + this.sfc.getLevelVNFSet().size());

        int Level_num_boys = this.sfc.getLevelVNFSet().size();

        int i = 0;

        int r = 0;

        int[] w_ave_str = new int[Level_num_boys];

        int[] leveler = new int[Level_num_boys];

        double gosa = 0.6;

        int g_num = 2;

        int str_point = 0;

        int b_point = (Level_num_boys / 2) - 1;

        int l_point = Level_num_boys - 1;

        List<VNF> VNF_List = new ArrayList<VNF>();

        List<Integer> VNF_Level_List = new ArrayList<Integer>();

        while (vnfMapIte.hasNext()) {

            HashMap<Long, VNF> map = vnfMapIte.next();

            System.out.println("Level:" + i + " /Num of VNFs:" + map.size());

            Iterator<VNF> vnfIte = map.values().iterator();

            while (vnfIte.hasNext()) {

                VNF vnf = vnfIte.next();

                System.out.print("   ");

                System.out.println("VNF_id:" + vnf.getIDVector().get(1) + "type:" + vnf.getType() + " w:" + vnf.getWorkLoad() +

                        "/# of OutDegree:" + vnf.getDsucList().size() + "/ # of InDegree:" + vnf.getDpredList().size() + "/ tlevel&blevel:" + vnf.getTlevel() + "&&" + vnf.getBlevel());

                System.out.print("");

                w_ave_str[i] += vnf.getWorkLoad();

                r++;

                VNF_List.add(vnf);

                VNF_Level_List.add(i);

            }

            w_ave_str[i] = w_ave_str[i] / r;

            leveler[i] = i;

            i++;

        }

        Iterator<VM> vmIte = this.env.getGlobal_vmMap().values().iterator();

        for (int l = 0; l < i; l++) {

            System.out.println("level" + l + ":" + w_ave_str[l]);

        }

        while (vmIte.hasNext()) {

            VM vm = vmIte.next();

            System.out.println(vm.getVMID());

            Iterator<VCPU> vIte = vm.getvCPUMap().values().iterator();

            //VMの特徴

            while (vIte.hasNext()) {

                VCPU vcpu = vIte.next();

                System.out.println("num of vCPUs:" + vm.getvCPUMap().size() + "/ getMips:" + vcpu.getMips());

            }

        }

        //System.out.println("Groups are:" + g_num);

        //System.out.println("VM_ums:" + VMfunction());

        //System.out.println("VM_POWER_AVE:"+VM_Power_Ave());

        //System.out.println("VNF_level_List:"+VNF_Level_List);

        //System.out.println("VM_FEE_AVE:"+VM_Fee_Ave());

        VMranking();

    }

    public void VMranking() {

        Iterator<VM> vmIte = this.env.getGlobal_vmMap().values().iterator();

        List<VM> VM_power_list = new ArrayList<VM>();

        while (vmIte.hasNext()) {

            VM_power_list.add(vmIte.next());

        }

        VM_2_VNF_Set_Function(VM_power_list);

    }

    public void VM_2_VNF_Set_Function(List<VM> pw_list) {

        pw_list.sort(Comparator.comparing(VM::getMIPS).reversed());

        //pw_list.sort(Comparator.comparing(VM::getvCPUnums).reversed());

        int len_pw = pw_list.size();

        List<VCPU> pw_vCPU_list = new ArrayList<VCPU>();

        for (int v = 0; v < len_pw; v++) {

            VM vm = pw_list.get(v);

            Iterator<VCPU> vIte = vm.getvCPUMap().values().iterator();

            while (vIte.hasNext()) {

                pw_vCPU_list.add(vIte.next());

            }

        }



        /*new */

        ListIterator<HashMap<Long, VNF>> vnfMapIte = this.sfc.getLevelVNFSet().listIterator();

        int vcpu_num = 0;

        while (vnfMapIte.hasNext()) {

            HashMap<Long, VNF> map = vnfMapIte.next();

            Iterator<VNFCluster> cIte = this.sfc.getVNFClusterMap().values().iterator();

            Iterator<VNF> vnfIte = map.values().iterator();

            while (cIte.hasNext()) {
                if (pw_vCPU_list.size() <= vcpu_num) {
                    break;
                }
                VCPU vcpu = pw_vCPU_list.get(vcpu_num);

                VNFCluster cluster = cIte.next();

                this.assignVCPU(cluster, vcpu);

                vcpu_num++;


            }

            vcpu_num = 0;

        }

        int seted_VNF = 0;

        while (!this.unScheduledVNFSet.isEmpty()) {

            VNF vnf = this.selectVNF();

            VCPU vcpu = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());

            HashMap<String, VCPU> map = new HashMap<String, VCPU>();

            map.put(vnf.getvCPUID(), vcpu);

            //クラスタリングの時点ですでに各クラスタの割当先vcpuは決まっているので，

            //単一vcpuから構成されるhashMapを第二引数とする．

            this.scheduleVNF(vnf, map);

            seted_VNF++;

        }

    }

    public VNF selectVNF() {

        Long retID = this.freeVNFSet.getList().getFirst();

        VNF selectedVNF = this.sfc.findVNFByLastID(retID);

        return selectedVNF;

    }

}


