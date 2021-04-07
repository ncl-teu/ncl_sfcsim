package net.gripps.cloud.nfv;

import net.gripps.cloud.CloudUtil;
import org.apache.commons.math.random.RandomDataImpl;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Created by Hidehiro Kanemitsu on 2018/11/26.
 */
public class NFVUtil extends CloudUtil {

    public static long vnf_weight_min;
    public static long vnf_weight_max;

    public static int VNF_TYPE_VSTART = 111;

    public static int VNF_TYPE_VEND =  999;

    public static int dist_vnf_weight;
    public static double dist_vnf_weight_mu;
    public static long vnf_datasize_min;
    public static long vnf_datasize_max;

    public static int dist_vnf_datasize;
    public static double dist_vnf_datasize_mu;

    public static long sfc_vnf_num;
 //   public static int sfc_vnf_indegree_min;
 ///   public static int sfc_vnf_indegree_max;
    public static int sfc_vnf_outdegree_min;
    public static int sfc_vnf_outdegree_max;

    public static int multiple_sfc_num;
    public static long multiple_sfc_vnf_num_min;
    public static long multiple_sfc_vnf_num_max;
    public static int dist_multiple_sfc_vnf_num;
    public static double dist_multiple_sfc_vnf_num_mu;

    public static double startNumRate;
    public static int depth_alpha;


    public static int CALCMODE_LEVEL_MAX = 1;
    public static int CALCMODE_LEVEL_MIN = 2;

    public static int CALCMODE_LEVEL_AVE = 0;


    public static int calcmode_level;

    public static double nfv_fairness_weight_overlap;

    public static int core_max_usage;

    public static int cloud_constrained_mode;

    public static int vnf_usage_min;

    public static int vnf_usage_max;

    public static int dist_vnf_usage;

    public static double dist_vnf_usage_mu;

    public static int vnf_type_max;

    public static int sfc_multimode;

    public static int cmwsl_sched_area;


    //VNFのコンテナサイズの最小値、最大値

    public static long vnf_image_size_min;

    public static long vnf_image_size_max;

    public static long repository_bw;


   // public static double nfv_fairness_weight_rt;


    public static NFVUtil own;

    public static NFVUtil getIns(){
        if(NFVUtil.own == null){
            NFVUtil.own = new NFVUtil();
        }else{

        }

        return NFVUtil.own;
    }

    /**
     * デフォルトコンストラクタ
     */
    private NFVUtil(){
        NFVUtil.rDataGen = new RandomDataImpl();

    }

    @Override
    public void initialize(String propName) {
        try{
            //設定情報
            NFVUtil.prop = new Properties();
            NFVUtil.prop.load(new FileInputStream(propName));
            NFVUtil.vnf_weight_min = Long.valueOf( CloudUtil.prop.getProperty("vnf_weight_min"));
            NFVUtil.vnf_weight_max = Long.valueOf( CloudUtil.prop.getProperty("vnf_weight_max"));
            NFVUtil.dist_vnf_weight = Integer.valueOf( CloudUtil.prop.getProperty("dist_vnf_weight"));
            NFVUtil.dist_vnf_weight_mu = Double.valueOf( CloudUtil.prop.getProperty("dist_vnf_weight_mu"));
            NFVUtil.vnf_datasize_min = Long.valueOf( CloudUtil.prop.getProperty("vnf_datasize_min"));
            NFVUtil.vnf_datasize_max = Long.valueOf( CloudUtil.prop.getProperty("vnf_datasize_max"));
            NFVUtil.dist_vnf_datasize = Integer.valueOf( CloudUtil.prop.getProperty("dist_vnf_datasize"));
            NFVUtil.dist_vnf_datasize_mu = Double.valueOf( CloudUtil.prop.getProperty("dist_vnf_datasize_mu"));
            NFVUtil.sfc_vnf_num = Long.valueOf( CloudUtil.prop.getProperty("sfc_vnf_num"));
           // NFVUtil.sfc_vnf_indegree_min = Integer.valueOf( CloudUtil.prop.getProperty("sfc_vnf_indegree_min"));
           // NFVUtil.sfc_vnf_indegree_max = Integer.valueOf( CloudUtil.prop.getProperty("sfc_vnf_indegree_max"));
            NFVUtil.sfc_vnf_outdegree_min = Integer.valueOf( CloudUtil.prop.getProperty("sfc_vnf_outdegree_min"));
            NFVUtil.sfc_vnf_outdegree_max = Integer.valueOf( CloudUtil.prop.getProperty("sfc_vnf_outdegree_max"));
            NFVUtil.multiple_sfc_num = Integer.valueOf( CloudUtil.prop.getProperty("multiple_sfc_num"));
            NFVUtil.multiple_sfc_vnf_num_min = Long.valueOf( CloudUtil.prop.getProperty("multiple_sfc_vnf_num_min"));
            NFVUtil.multiple_sfc_vnf_num_max = Long.valueOf( CloudUtil.prop.getProperty("multiple_sfc_vnf_num_max"));
            NFVUtil.dist_multiple_sfc_vnf_num = Integer.valueOf( CloudUtil.prop.getProperty("dist_multiple_sfc_vnf_num"));
            NFVUtil.dist_multiple_sfc_vnf_num_mu = Double.valueOf( CloudUtil.prop.getProperty("dist_multiple_sfc_vnf_num_mu"));
            NFVUtil.startNumRate = Double.valueOf(prop.getProperty("sfc_vnf_startnumrate")).doubleValue();
            NFVUtil.depth_alpha =Integer.valueOf(prop.getProperty("sfc_vnf_deapthalpha")).intValue();
            NFVUtil.calcmode_level = Integer.valueOf(prop.getProperty("calcmode_level")).intValue();
            NFVUtil.nfv_fairness_weight_overlap =  Double.valueOf( CloudUtil.prop.getProperty("nfv_fairness_weight_overlap"));
           // NFVUtil.nfv_fairness_weight_rt = Double.valueOf( CloudUtil.prop.getProperty("nfv_fairness_weight_rt"));

            NFVUtil.core_max_usage = Integer.valueOf(prop.getProperty("core_max_usage")).intValue();
            NFVUtil.cloud_constrained_mode =  Integer.valueOf(prop.getProperty("cloud_constrained_mode")).intValue();

            NFVUtil.vnf_usage_min =  Integer.valueOf(prop.getProperty("vnf_usage_min")).intValue();
            NFVUtil.vnf_usage_max = Integer.valueOf(prop.getProperty("vnf_usage_max")).intValue();

            NFVUtil.dist_vnf_usage =  Integer.valueOf(prop.getProperty("dist_vnf_usage")).intValue();
            NFVUtil.dist_vnf_usage_mu = Double.valueOf(prop.getProperty("dist_vnf_usage_mu")).doubleValue();
            NFVUtil.cmwsl_sched_area = Integer.valueOf(prop.getProperty("cmwsl_sched_area")).intValue();
            NFVUtil.vnf_type_max = Integer.valueOf(prop.getProperty("vnf_type_max")).intValue();
            //NFVUtil.sfc_multimode =  Integer.valueOf(prop.getProperty("sfc_multimode")).intValue();

            //
            NFVUtil.vnf_image_size_min = Long.valueOf(prop.getProperty("vnf_image_size_min")).longValue();
            NFVUtil.vnf_image_size_max = Long.valueOf(prop.getProperty("vnf_image_size_max")).longValue();
            NFVUtil.repository_bw = Long.valueOf(prop.getProperty("repository_bw")).longValue();


        }catch(Exception e){
            e.printStackTrace();
        }


    }
}
