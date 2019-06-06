package net.gripps.cloud.nfv.clustering;

import net.gripps.cloud.CloudUtil;
import org.apache.commons.math.random.RandomDataImpl;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/17.
 */
public class HUtil extends CloudUtil {

    /**
     * シングルトン（自分自身）
     */
    public static HUtil own;

    /**
     *  レベル(N+1)のVNF数/レベルNのVNF数 の比の最小値
     */
    public static double hclustering_vnf_num_rate_min;

    /**
     * # レベル(N+1)のVNF数/レベルNのVNF数 の比の最大値
     */
    public static double hclustering_vnf_num_rate_max;


    public static int dist_hclustering_vnf_num_rate;

    public static double dist_hclustering_vnf_num_rate_mu;

    /**
     * # レベル(N+1)のVNFの仕事量/レベルNのVNFの仕事量の最大値　の比の最小値
     */
    public static double hclustering_vnf_workload_rate_min;

    /**
     * # レベル(N+1)のVNFの仕事量/レベルNのVNFの仕事量の最大値　の比の最大値
     */
    public static double hclustering_vnf_workload_rate_max;

    public static int dist_hclustering_vnf_workload_rate;

    public static double dist_hclustering_vnf_workload_rate_mu;

    public static long hclustering_level_num;


    /**
     * インスタンスを返すためのメソッド
     * @return
     */
    public static HUtil getIns(){
        if(HUtil.own == null){
            HUtil.own = new HUtil();
        }else{

        }

        return HUtil.own;
    }

    /**
     * デフォルトコンストラクタ
     */
    private HUtil(){
        HUtil.rDataGen = new RandomDataImpl();

    }

    @Override
    public void initialize(String propName) {
        try{
            //設定情報
            HUtil.prop = new Properties();
            HUtil.prop.load(new FileInputStream(propName));

            //HUtil.sfc_multimode =  Integer.valueOf(prop.getProperty("sfc_multimode")).intValue();
            HUtil.hclustering_vnf_num_rate_min = Double.valueOf(prop.getProperty("hclustering_vnf_num_rate_min"));
            HUtil.hclustering_vnf_num_rate_max = Double.valueOf(prop.getProperty("hclustering_vnf_num_rate_max"));
            HUtil.dist_hclustering_vnf_num_rate =  Integer.valueOf(prop.getProperty("dist_hclustering_vnf_num_rate")).intValue();
            HUtil.dist_hclustering_vnf_num_rate_mu = Double.valueOf(prop.getProperty("dist_hclustering_vnf_num_rate_mu"));

            HUtil.hclustering_vnf_workload_rate_min = Double.valueOf(prop.getProperty("hclustering_vnf_workload_rate_min"));
            HUtil.hclustering_vnf_workload_rate_max = Double.valueOf(prop.getProperty("hclustering_vnf_workload_rate_max"));
            HUtil.dist_hclustering_vnf_workload_rate =  Integer.valueOf(prop.getProperty("dist_hclustering_vnf_workload_rate")).intValue();
            HUtil.dist_hclustering_vnf_workload_rate_mu = Double.valueOf(prop.getProperty("dist_hclustering_vnf_workload_rate_mu"));

            HUtil.hclustering_level_num = Long.valueOf(prop.getProperty("hclustering_level_num")).longValue();

        }catch(Exception e){
            e.printStackTrace();
        }


    }
}
