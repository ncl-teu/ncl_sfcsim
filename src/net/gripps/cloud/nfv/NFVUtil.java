package net.gripps.cloud.nfv;

import net.gripps.cloud.CloudUtil;
import org.apache.commons.math.random.RandomDataImpl;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.Random;

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

    public static int vnf_type_min;
    public static int vnf_type_max;

    public static int sfc_multimode;

    public static int cmwsl_sched_area;


    //VNFのコンテナサイズの最小値、最大値

    public static long vnf_image_size_min;

    public static long vnf_image_size_max;

    public static long repository_bw;

    public static int cloud_container_dl_mode;
    // Optional debug flag for NHEFT verbose output (0: off, 1: on)
    public static int debug_nheft = 0;

    /**
     * Default disables resource-aware vCPU reuse and preserves the original
     * NHEFT policy, which always selects the candidate with minimum EFT.
     */
    public static final double DEFAULT_NHEFT_VCPU_EFT_TOLERANCE = 0.0d;

    /**
     * Relative EFT tolerance for preferring an already-used vCPU in NHEFT.
     * For example, 0.03 allows an already-used vCPU whose EFT is at most 3%
     * above the globally minimum EFT. A value of 0.0 keeps the original NHEFT
     * selection behavior completely unchanged.
     */
    public static double nheft_vcpu_eft_tolerance = DEFAULT_NHEFT_VCPU_EFT_TOLERANCE;

    /**
     * Default keeps the legacy NHEFT behavior. When enabled, opening a new
     * vCPU requires both earlier finish time and computation-time advantage
     * over the best used vCPU.
     */
    public static final int DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_COMP_ADVANTAGE = 0;

    /**
     * 0: disabled. 1: enabled.
     */
    public static int nheft_vcpu_open_requires_comp_advantage =
            DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_COMP_ADVANTAGE;

    /**
     * Default keeps the legacy NHEFT behavior. When enabled, opening a new
     * vCPU requires data-ready-time advantage over the best used vCPU.
     */
    public static final int DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_DRT_ADVANTAGE = 0;

    /**
     * 0: disabled. 1: enabled.
     */
    public static int nheft_vcpu_open_requires_drt_advantage =
            DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_DRT_ADVANTAGE;

    /**
     * Default keeps the legacy NHEFT behavior. When enabled, opening a new
     * vCPU requires image-ready-time advantage over the best used vCPU.
     */
    public static final int DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_IRT_ADVANTAGE = 0;

    /**
     * 0: disabled. 1: enabled.
     */
    public static int nheft_vcpu_open_requires_irt_advantage =
            DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_IRT_ADVANTAGE;

    /**
     * Gate-combination logic for enabled resource-opening advantages.
     * 0: all enabled gates must hold.
     * 1: any enabled gate may justify opening a new vCPU.
     */
    public static final int NHEFT_VCPU_OPEN_GATE_LOGIC_ALL = 0;
    public static final int NHEFT_VCPU_OPEN_GATE_LOGIC_ANY = 1;
    public static final int DEFAULT_NHEFT_VCPU_OPEN_GATE_LOGIC =
            NHEFT_VCPU_OPEN_GATE_LOGIC_ALL;

    /**
     * Default keeps the current strict behavior.
     */
    public static int nheft_vcpu_open_gate_logic =
            DEFAULT_NHEFT_VCPU_OPEN_GATE_LOGIC;


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
            String typeMinStr = prop.getProperty("vnf_type_min");
            if (typeMinStr == null || typeMinStr.trim().isEmpty()) {
                NFVUtil.vnf_type_min = 1;
            } else {
                NFVUtil.vnf_type_min = Integer.valueOf(typeMinStr.trim()).intValue();
            }
            NFVUtil.vnf_type_max = Integer.valueOf(prop.getProperty("vnf_type_max")).intValue();
            // Keep type range valid even when properties are misconfigured.
            if (NFVUtil.vnf_type_min < 1) {
                NFVUtil.vnf_type_min = 1;
            }
            if (NFVUtil.vnf_type_max < 1) {
                NFVUtil.vnf_type_max = 1;
            }
            if (NFVUtil.vnf_type_min > NFVUtil.vnf_type_max) {
                int tmp = NFVUtil.vnf_type_min;
                NFVUtil.vnf_type_min = NFVUtil.vnf_type_max;
                NFVUtil.vnf_type_max = tmp;
            }
            //NFVUtil.sfc_multimode =  Integer.valueOf(prop.getProperty("sfc_multimode")).intValue();

            //
            NFVUtil.vnf_image_size_min = Long.valueOf(prop.getProperty("vnf_image_size_min")).longValue();
            NFVUtil.vnf_image_size_max = Long.valueOf(prop.getProperty("vnf_image_size_max")).longValue();
            NFVUtil.repository_bw = Long.valueOf(prop.getProperty("repository_bw")).longValue();

            NFVUtil.cloud_container_dl_mode = Integer.valueOf(prop.getProperty("cloud_container_dl_mode")).intValue();
            try{
                String dbg = prop.getProperty("debug_nheft");
                if(dbg != null && dbg.trim().length() > 0){
                    NFVUtil.debug_nheft = Integer.valueOf(dbg.trim()).intValue();
                }
            }catch(Exception _e){
                // ignore
            }

            // Optional NHEFT resource-consolidation parameter. Reset the
            // default on every initialization so a missing property never
            // inherits a value loaded by an earlier experiment in this JVM.
            NFVUtil.nheft_vcpu_eft_tolerance = NFVUtil.DEFAULT_NHEFT_VCPU_EFT_TOLERANCE;
            String toleranceStr = prop.getProperty("nheft_vcpu_eft_tolerance");
            if (toleranceStr != null && toleranceStr.trim().length() > 0) {
                try {
                    double tolerance = Double.valueOf(toleranceStr.trim()).doubleValue();
                    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance < 0.0d) {
                        throw new NumberFormatException("not a finite non-negative ratio");
                    }
                    NFVUtil.nheft_vcpu_eft_tolerance = tolerance;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid nheft_vcpu_eft_tolerance='" + toleranceStr
                            + "'; using default " + NFVUtil.DEFAULT_NHEFT_VCPU_EFT_TOLERANCE);
                }
            }

            // Optional NHEFT resource-opening gates. These switches are off by
            // default, so experiments without the properties keep old behavior.
            NFVUtil.nheft_vcpu_open_requires_comp_advantage =
                    parseZeroOneSwitch(
                            "nheft_vcpu_open_requires_comp_advantage",
                            NFVUtil.DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_COMP_ADVANTAGE);
            NFVUtil.nheft_vcpu_open_requires_drt_advantage =
                    parseZeroOneSwitch(
                            "nheft_vcpu_open_requires_drt_advantage",
                            NFVUtil.DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_DRT_ADVANTAGE);
            NFVUtil.nheft_vcpu_open_requires_irt_advantage =
                    parseZeroOneSwitch(
                            "nheft_vcpu_open_requires_irt_advantage",
                            NFVUtil.DEFAULT_NHEFT_VCPU_OPEN_REQUIRES_IRT_ADVANTAGE);
            NFVUtil.nheft_vcpu_open_gate_logic =
                    parseGateLogic(
                            "nheft_vcpu_open_gate_logic",
                            NFVUtil.DEFAULT_NHEFT_VCPU_OPEN_GATE_LOGIC);

            // Optional global seed for reproducible SFC/environment generation.
            String seedStr = prop.getProperty("random_seed");
            if (seedStr != null && seedStr.trim().length() > 0) {
                long seed = Long.valueOf(seedStr.trim());
                CloudUtil.random_seed = seed;
                CloudUtil.rDataGen.reSeed(seed);
                CloudUtil.uniformRand = new Random(seed);
            }

        }catch(Exception e){
            e.printStackTrace();
        }


    }

    private static int parseZeroOneSwitch(String propName, int defaultValue) {
        String rawValue = prop.getProperty(propName);
        if (rawValue == null || rawValue.trim().length() == 0) {
            return defaultValue;
        }

        String value = rawValue.trim();
        try {
            if ("true".equalsIgnoreCase(value)) {
                return 1;
            }
            if ("false".equalsIgnoreCase(value)) {
                return 0;
            }
            int flag = Integer.valueOf(value).intValue();
            if (flag != 0 && flag != 1) {
                throw new NumberFormatException("not 0 or 1");
            }
            return flag;
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + propName + "='" + rawValue
                    + "'; using default " + defaultValue);
            return defaultValue;
        }
    }

    private static int parseGateLogic(String propName, int defaultValue) {
        String rawValue = prop.getProperty(propName);
        if (rawValue == null || rawValue.trim().length() == 0) {
            return defaultValue;
        }

        String value = rawValue.trim().toLowerCase();
        if ("all".equals(value) || "and".equals(value) || "strict".equals(value) || "0".equals(value)) {
            return NHEFT_VCPU_OPEN_GATE_LOGIC_ALL;
        }
        if ("any".equals(value) || "or".equals(value) || "relaxed".equals(value) || "1".equals(value)) {
            return NHEFT_VCPU_OPEN_GATE_LOGIC_ANY;
        }

        System.err.println("Invalid " + propName + "='" + rawValue
                + "'; using default " + describeNHEFTGateLogic(defaultValue));
        return defaultValue;
    }

    public static String describeNHEFTGateLogic(int gateLogic) {
        if (gateLogic == NHEFT_VCPU_OPEN_GATE_LOGIC_ANY) {
            return "any";
        }
        return "all";
    }
}
