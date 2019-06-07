package net.gripps.cloud.mapreduce;
import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.mapreduce.core.FSHost;
import net.gripps.cloud.mapreduce.core.MRCloudEnvironment;
import net.gripps.cloud.mapreduce.core.MRVCPU;
import net.gripps.cloud.mapreduce.datamodel.InputSplit;
import net.gripps.cloud.mapreduce.datamodel.ShuffleFileSplit;
import org.apache.commons.math.random.RandomDataImpl;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01
 */
public class MRUtil extends CloudUtil {

    /**
     * シングルトン
     */
    public static MRUtil own;

    /**
     * 入力ファイルのサイズ
     */
    public static long size_of_inputfile_min;

    public static long size_of_inputfile_max;


    /**
     * InputSplitのサイズ
     */
    public static long  size_of_inputsplit;

    /**
     * 逐次モードか，ダイナミックモードか
     */
    public static int is_size_dynamic;

    /**
     * InputSplit生成のための仕事量
     */
    public static long is_gen_workload_ave;

    /**
     * ファイルホストの処理速度
     */
    public static long dfhost_mips;

    /**
     * ファイルホストの帯域幅(MB)
     */
    public static long dfhost_bw;

    /**
     * Mapper数
     */
  //  public static long mapper_num;

    /**
     * Reducer数
     */
    //public static long reducer_num;
    
    public static int dfs_transfer_mode;

    public static double  size_of_inputrecord;

    public static long num_of_inputfiles;

    public static long in_record_num_of_kinds_keys_min;
    public static long in_record_num_of_kinds_keys_max;
    public static int dist_in_record_num_of_kinds_keys;
    public static double dist_in_record_num_of_kinds_mu;

    public static long key_weight_min;
    public static long key_weight_max;
    public static int  dist_key_weight;
    public static double  dist_key_weight_mu;

    public static double in_record_workload_perMB;

    public static double  num_mapper_rate;

    public static double time_speed_rate;

    //Mapperの状態定義
    public  static final  int  STATE_IN_RECEIVED=1;
    public static final int  STATE_MAP_START=2;
    public static  final int  STATE_MAP_FIN=3;
    public static  final int  STATE_COLLECT_START=4;
    public static  final int  STATE_COLLECT_FNIN=5;
    public static  final int  STATE_SPILL_START=6;
    public static  final int  STATE_SPILL_FIN=7;
    public static  final int  STATE_MERGE_START=8;
    public static  final int  STATE_MERGE_FIN=9;
    public static  final int  STATE_SHUFFLE_SEND=10;

    public static double split_out_in_rate;

    public static int mr_algorithm_scheduling_num;
    public static int mr_algorithm_scheduling_using;
    public static int mr_algorithm_provisioning_num;

    public static int mr_algorithm_provisioning_using;


    public static long workload_partitioning_per_key;


    public static double out_record_workload_perMB;

    public static double spill_read_workload_perMB;

    public static double merge_workload_permerge;

    public static double reduce_out_in_rate;

    public static double key_rate;

    public static double num_reducer_rate_to_mapper;



    public static MRUtil getIns(){
        if(MRUtil.own == null){
            MRUtil.own = new MRUtil();
        }else{

        }

        return MRUtil.own;
    }

    /**
     * デフォルトコンストラクタ
     */
    private MRUtil(){
        MRUtil.rDataGen = new RandomDataImpl();
    }

    /**
     * 初期化処理
     * @param fileName
     */
    public void initialize(String fileName) {
        try {
            MRUtil.prop = new Properties();
            MRUtil.prop.load(new FileInputStream(fileName));

            MRUtil.size_of_inputfile_min = Long.valueOf(MRUtil.prop.getProperty("size_of_inputfile_min"));
            MRUtil.size_of_inputfile_max = Long.valueOf(MRUtil.prop.getProperty("size_of_inputfile_max"));

            MRUtil.size_of_inputsplit = Long.valueOf(MRUtil.prop.getProperty("size_of_inputsplit"));
            MRUtil.is_size_dynamic = Integer.valueOf(MRUtil.prop.getProperty("is_size_dynamic"));
            MRUtil.is_gen_workload_ave = Long.valueOf(MRUtil.prop.getProperty("is_gen_workload_ave"));
           // MRUtil.mapper_num = Long.valueOf(MRUtil.prop.getProperty("mapper_num"));
          //  MRUtil.reducer_num = Long.valueOf(MRUtil.prop.getProperty("reducer_num"));

            MRUtil.dfhost_mips = Long.valueOf(MRUtil.prop.getProperty("dfhost_mips"));
            MRUtil.dfhost_bw = Long.valueOf(MRUtil.prop.getProperty("dfhost_bw"));
            MRUtil.dfs_transfer_mode = Integer.valueOf(MRUtil.prop.getProperty("dfs_transfer_mode"));

            MRUtil.size_of_inputrecord = Double.valueOf(MRUtil.prop.getProperty("size_of_inputrecord"));

            MRUtil.num_of_inputfiles = Long.valueOf(MRUtil.prop.getProperty("num_of_inputfiles"));

            MRUtil.in_record_num_of_kinds_keys_min = Long.valueOf(MRUtil.prop.getProperty("in_record_num_of_kinds_keys_min"));
            MRUtil.in_record_num_of_kinds_keys_max = Long.valueOf(MRUtil.prop.getProperty("in_record_num_of_kinds_keys_max"));
            MRUtil.dist_in_record_num_of_kinds_keys =  Integer.valueOf(MRUtil.prop.getProperty("dist_in_record_num_of_kinds_keys"));
            MRUtil.dist_in_record_num_of_kinds_mu = Double.valueOf(MRUtil.prop.getProperty("dist_in_record_num_of_kinds_mu"));

            MRUtil.key_weight_min = Long.valueOf(MRUtil.prop.getProperty("key_weight_min"));
            MRUtil.key_weight_max = Long.valueOf(MRUtil.prop.getProperty("key_weight_max"));
            MRUtil.dist_key_weight = Integer.valueOf(MRUtil.prop.getProperty("dist_key_weight"));
            MRUtil.dist_key_weight_mu = Double.valueOf(MRUtil.prop.getProperty("dist_key_weight_mu"));
            MRUtil.in_record_workload_perMB = Double.valueOf(MRUtil.prop.getProperty("in_record_workload_perMB"));
            MRUtil.num_mapper_rate = Double.valueOf(MRUtil.prop.getProperty("num_mapper_rate"));
            MRUtil.time_speed_rate = Double.valueOf(MRUtil.prop.getProperty("time_speed_rate"));

            MRUtil.split_out_in_rate = Double.valueOf(MRUtil.prop.getProperty("split_out_in_rate"));

            MRUtil.mr_algorithm_scheduling_num = Integer.valueOf(MRUtil.prop.getProperty("mr_algorithm_scheduling_num"));
            MRUtil.mr_algorithm_scheduling_using = Integer.valueOf(MRUtil.prop.getProperty("mr_algorithm_scheduling_using"));

            MRUtil.mr_algorithm_provisioning_num = Integer.valueOf(MRUtil.prop.getProperty("mr_algorithm_provisioning_num"));

            MRUtil.mr_algorithm_provisioning_using = Integer.valueOf(MRUtil.prop.getProperty("mr_algorithm_provisioning_using"));
            MRUtil.workload_partitioning_per_key = Long.valueOf(MRUtil.prop.getProperty("workload_partitioning_per_key"));
            MRUtil.out_record_workload_perMB =  Double.valueOf(MRUtil.prop.getProperty("out_record_workload_perMB"));
            MRUtil.spill_read_workload_perMB = Double.valueOf(MRUtil.prop.getProperty("spill_read_workload_perMB"));
            MRUtil.merge_workload_permerge = Double.valueOf(MRUtil.prop.getProperty("merge_workload_permerge"));
            MRUtil.reduce_out_in_rate = Double.valueOf(MRUtil.prop.getProperty("reduce_out_in_rate"));
            MRUtil.key_rate = Double.valueOf(MRUtil.prop.getProperty("key_rate"));

            MRUtil.num_reducer_rate_to_mapper =  Double.valueOf(MRUtil.prop.getProperty("num_reducer_rate_to_mapper"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double log2(double x) {
        // 特殊な値
        if (Double.isNaN(x) || x < 0.0) return Double.NaN;
        if (x == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY;
        if (x == 0.0) return Double.NEGATIVE_INFINITY;
        // ここから
        int k = Math.getExponent(x);
        if (k < Double.MIN_EXPONENT) {
            // 非正規化数は取扱い注意！
            k = Math.getExponent(x * 0x1.0p52) - 52;
        }
        if (k < 0) {
            k++;
        }
        double s = Math.scalb(x, -k);
        final double LOG2_E = 1.4426950408889634;
        return k + LOG2_E * Math.log(s);
    }

    /**
     * 与えられたデータに対し，通信時間を求めます．
     *
     * @param dataSize
     * @param fromVCPU
     * @param toVCPU
     * @return
     */
    public static double calcComTime(double  dataSize, MRVCPU fromVCPU, MRVCPU toVCPU) {
        //DCの情報．
        Long fromDCID = MRUtil.getInstance().getDCID(fromVCPU.getPrefix());
        Long toDCID = MRUtil.getInstance().getDCID(toVCPU.getPrefix());
        long dcBW = MRUtil.MAXValue;
        MRCloudEnvironment env = MRMgr.getIns().getEnv();

        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);

        //同一クラウド内であれば，DC間の通信は考慮しなくて良い．
        if (fromDCID.longValue() == toDCID.longValue()) {
        } else {
            //DCが異なれば，DC間の通信も考慮スべき．
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());

        }
        Long fromHostID = MRUtil.getInstance().getHostID(fromVCPU.getPrefix());
        Long toHostID = MRUtil.getInstance().getHostID(toVCPU.getPrefix());

        //後は，ホスト間での通信
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = toCloud.getComputeHostMap().get(toHostID);
        long hostBW = MRUtil.MAXValue;
        if (fromHostID == toHostID) {
            //同一ホストなら，0を返す．
            return 0;
        } else {
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());
        }

        long realBW = Math.min(dcBW, hostBW);

        double time = MRUtil.getRoundedValue((double) dataSize / (double) realBW);

        return time;

    }


    public static double calcComTimeParallel(double dataSize, long mapperNum, long reducerNum, MRVCPU fromVCPU, MRVCPU toVCPU) {
        //DCの情報．
        Long fromDCID = MRUtil.getInstance().getDCID(fromVCPU.getPrefix());
        Long toDCID = MRUtil.getInstance().getDCID(toVCPU.getPrefix());
        long dcBW = MRUtil.MAXValue;
        MRCloudEnvironment env = MRMgr.getIns().getEnv();

        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);

        //同一クラウド内であれば，DC間の通信は考慮しなくて良い．
        if (fromDCID.longValue() == toDCID.longValue()) {
        } else {
            //DCが異なれば，DC間の通信も考慮スべき．
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());

        }
        Long fromHostID = MRUtil.getInstance().getHostID(fromVCPU.getPrefix());
        Long toHostID = MRUtil.getInstance().getHostID(toVCPU.getPrefix());

        //後は，ホスト間での通信
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = toCloud.getComputeHostMap().get(toHostID);
        long hostBW = MRUtil.MAXValue;
        if (fromHostID == toHostID) {
            //同一ホストなら，0を返す．
            return 0;
        } else {
            hostBW = (long)Math.min(MRUtil.getRoundedValue(fromHost.getBw()/(double)reducerNum),
                    MRUtil.getRoundedValue(toHost.getBw()/(double)mapperNum));
        }

        long realBW = Math.min(dcBW, hostBW);

        double time = MRUtil.getRoundedValue((double) dataSize / (double) realBW);

        return time;

    }

    public  static double calcComTimeReducerToFSHost(MRVCPU mapper, FSHost fsHost, ShuffleFileSplit sfs){
        double retBW = -1L;
        long dcID = MRUtil.getIns().getDCID(mapper.getPrefix());
        long hostID = MRUtil.getIns().getHostID(mapper.getPrefix());

        Cloud cloud = MRMgr.getIns().getEnv().getDcMap().get(dcID);
        double  cloudBW = cloud.getBw();
        ComputeHost host = cloud.getComputeHostMap().get(hostID);
       // FSHost fsHost = MRMgr.getIns().getEnv().getFsHost();
        long fsDCID =fsHost.getDcID();
        Cloud fsCloud = MRMgr.getIns().getEnv().getDcMap().get(fsDCID);

        retBW = Math.min(fsHost.getBw(), host.getBw());

        //同一クラウド内であれば，ホスト間のBW vs FSのBWということになる．
        if(fsDCID == dcID){
            // retBW = Math.min(fsHost.getBw(), host.getBw());
        }else{
            retBW = Math.min(retBW, fsCloud.getBw());
            retBW = Math.min(retBW, cloud.getBw());
        }
        retBW = Math.min(retBW, fsHost.getBw());
        double comTime = MRUtil.getRoundedValue((double)sfs.getSize() / (double) retBW);

        return MRUtil.getRoundedValue(comTime);

    }



}
