package net.gripps.cloud;

import org.apache.commons.math.random.RandomDataImpl;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Created by kanemih on 2018/10/17.
 */
public class CloudUtil {
    /**
     * 自身のインスタンス
     */
    protected  static CloudUtil own;

    /**
     * 設定情報プロパティ
     */
    public static Properties prop;

    public static int num_dc = 0;

    public static long datacenter_externalbw_min = 0;
    public static long datacenter_externalbw_max = 0;


    public static int host_num_foreachdc_min = 0;
    public static int host_num_foreachdc_max = 0;

    public static int vm_num_foreachdc_min = 0;
    public static int vm_num_foreachdc_max = 0;

    public static int num_host = 0;

    public static int dist_host_cpu_num=0;
    public static double dist_host_cpu_num_mu = 0.0d;
    public static int host_cpu_num_min=0;
    public static int host_cpu_num_max=0;
    public static int host_core_num_foreachcpu_min=0;
    public static int host_core_num_foreachcpu_max=0;

    public static int host_thread_num_foreeachcore=0;
    public static long vm_mem_min=0;
    public static long vm_mem_max=0;

    public static int dist_host_mips = 0;
    public static double dist_host_mips_mu = 0.0d;
    public static long host_mips_min=0;
    public static long host_mips_max=0;

    public static double core_mips_rate_min = 0.0;
    public static double core_mips_rate_max=0.0;

    public static int dist_host_bw =0;
    public static double dist_host_bw_mu = 0.0d;
    public static long host_bw_min=0;
    public static long host_bw_max=0;

    public static int vm_cpi=0;

    public static int dist_vm_vcpu_num=0;
    public static double dist_vm_vcpu_num_mu=0.0d;
    public static int vm_vcpu_num_min=0;
    public static int vm_vcpu_num_max=0;

    public static int mobile_device_num;
    public static int mobile_device_core_num_min;
    public static int mobile_device_core_num_max;
    public static long mobile_device_cpu_mips_min;
    public static long mobile_device_cpu_mips_max;

    public static long mobile_device_bw_min;
    public static long mobile_device_bw_max;

    public static   long MAXValue = (long)Double.POSITIVE_INFINITY;



    public static RandomDataImpl rDataGen =  new RandomDataImpl();

    public static String DELIMITER="^";

    public static String ID_DC = "id_dc";

    public static String ID_HOST = "id_host";

    public static String ID_CPU = "id_cpu";

    public static String ID_CORE = "id_core";

    public static String ID_VCPU = "id_vcpu";

    public static double mobile_device_power_min;

    public static double mobile_device_power_max;

    public static int dist_mobile_device_power;

    public static double dist_mobile_device_power_mu;

    public static double mobile_device_gain_min;

    public static double mobile_device_gain_max;

    public static int dist_mobile_device_gain;

    public static double dist_mobile_device_gain_mu;

    public static double mobile_device_back_noise;

    public static long mec_channel_num;

    public static double mobile_device_tau_min;

    public static double mobile_device_tau_max;

    public static int  dist_mobile_device_tau;

    public static double dist_mobile_device_tau_mu;

    public static long offload_program_datasize;





    public long getVCPUID(String prefix){
        StringTokenizer st = new StringTokenizer(prefix, CloudUtil.DELIMITER);
        long val = -1;
        String str = null;
        for(int i=0;i<5;i++){
            str = st.nextToken();

        }

        return Long.valueOf(str).longValue();


    }


    /**
     * コアIDを取得します．
     * DC^HOST^CPU^COREである．
     * @param prefix
     * @return
     */
    public long getCoreID(String prefix){
        StringTokenizer st = new StringTokenizer(prefix, CloudUtil.DELIMITER);
        long val = -1;
        String str = null;
        for(int i=0;i<4;i++){
            str = st.nextToken();

        }

        return Long.valueOf(str).longValue();


    }

    public long getCPUID(String prefix){
        StringTokenizer st = new StringTokenizer(prefix, CloudUtil.DELIMITER);
        long val = -1;
        String str = null;
        for(int i=0;i<3;i++){
            str = st.nextToken();

        }

        return Long.valueOf(str).longValue();
    }




    public long getHostID(String prefix){
        StringTokenizer st = new StringTokenizer(prefix, CloudUtil.DELIMITER);
        long val = -1;
        String str = null;
        for(int i=0;i<2;i++){
            str = st.nextToken();

        }

        return Long.valueOf(str).longValue();
    }

    public long getDCID(String prefix){
        StringTokenizer st = new StringTokenizer(prefix, CloudUtil.DELIMITER);
        long val = -1;
        String str = null;
        for(int i=0;i<1;i++){
            str = st.nextToken();

        }

        return Long.valueOf(str).longValue();
    }




    public static CloudUtil getInstance() {
        if (CloudUtil.own == null) {
            CloudUtil.own = new CloudUtil();
        }
        return CloudUtil.own;
    }

    public static double genDouble(double min, double max){
        return CloudUtil.getRoundedValue(min + (double) (Math.random() * (max - min + 1)));

    }

    public static long genLong(long min, long max) {

        return min + (long) (Math.random() * (max - min + 1));

    }

    public static int genInt(int  min, int max) {

        return min + (int) (Math.random() * (max - min + 1));

    }

    public static double getRoundedValue(double value1) {
        //  try{
        BigDecimal value2 = new BigDecimal(String.valueOf(value1));
        double retValue = value2.setScale(4, BigDecimal.ROUND_HALF_UP).doubleValue();
        return retValue;

    }


    /**
     * Int型の，一様／正規分布出力関数
     * @param min
     * @param max
     * @param dist
     * @param mu
     * @return
     */
    public static int genInt2(int min, int  max, int dist, double mu){
        max = Math.max(min, max);

        if(min == max){
            return min;
        }
        if(dist== 0){
            //一様分布
            return min + (int) (Math.random() * (max - min + 1));

        }else{
            //正規分布
            double meanValue2 = min + (max-min)* mu;
            double sig = (double)(Math.max((meanValue2-min), (max-meanValue2)))/3;
            double ran2 = CloudUtil.rDataGen.nextGaussian(meanValue2,sig);


            if(ran2 < min){
                ran2 =(int) min;
            }

            if(ran2 > max){
                ran2 = (int)max;
            }

            return (int) ran2;
        }

    }

    /**
     * Double型の，一様／正規分布出力関数
     * @param min
     * @param max
     * @param dist
     * @param mu
     * @return
     */
    public static double  genDouble2(double min, double max, int dist, double mu){
        if(min == max){
            return min;
        }
        if(dist== 0){
            //一様分布
            return min + (double) (Math.random() * (max - min + 1));

        }else{
            //正規分布
            double meanValue2 = min + (max-min)* mu;
            double sig = (double)(Math.max((meanValue2-min), (max-meanValue2)))/3;
            double ran2 = CloudUtil.getRoundedValue(CloudUtil.rDataGen.nextGaussian(meanValue2,sig));


            if(ran2 < min){
                ran2 =(double) min;
            }

            if(ran2 > max){
                ran2 = (double)max;
            }

            return (double) ran2;
        }

    }


    /**
     * Long型の一様・正規分布出力関数
     * @param min
     * @param max
     * @param dist
     * @param mu
     * @return
     */
    public static long genLong2(long min, long max, int dist, double mu){
        if(min == max){
            return min;
        }
        if(dist== 0){
            //一様分布
            return min + (long) (Math.random() * (max - min + 1));

        }else{
            //正規分布
            double meanValue2 = min + (max-min)* mu;
            double sig = (double)(Math.max((meanValue2-min), (max-meanValue2)))/3;
            double ran2 = CloudUtil.rDataGen.nextGaussian(meanValue2,sig);


            if(ran2 < min){
                ran2 =(double) min;
            }

            if(ran2 > max){
                ran2 = (double)max;
            }

            return (long) ran2;
        }

    }

    public void initialize(String propName){
        try{
            //設定情報
            CloudUtil.prop = new Properties();
            CloudUtil.prop.load(new FileInputStream(propName));
            //データセンター数
            CloudUtil.num_dc = Integer.valueOf( CloudUtil.prop.getProperty("datacenter_num"));
            CloudUtil.datacenter_externalbw_min = Long.valueOf( CloudUtil.prop.getProperty("datacenter_externalbw_min"));
            CloudUtil.datacenter_externalbw_max = Long.valueOf( CloudUtil.prop.getProperty("datacenter_externalbw_max"));

            //VM数@クラウド
            CloudUtil.vm_num_foreachdc_min = Integer.valueOf( CloudUtil.prop.getProperty("vm_num_foreachdc_min"));
            CloudUtil.vm_num_foreachdc_max = Integer.valueOf( CloudUtil.prop.getProperty("vm_num_foreachdc_max"));

            //ホスト数@クラウド
            CloudUtil.host_num_foreachdc_min = Integer.valueOf( CloudUtil.prop.getProperty("host_num_foreachdc_min"));
            CloudUtil.host_num_foreachdc_max = Integer.valueOf( CloudUtil.prop.getProperty("host_num_foreachdc_max"));


            CloudUtil.dist_host_cpu_num = Integer.valueOf( CloudUtil.prop.getProperty("dist_host_cpu_num"));
            CloudUtil.dist_host_cpu_num_mu = Double.valueOf(prop.getProperty("dist_host_cpu_num_mu")).doubleValue();
            CloudUtil.host_cpu_num_min=Integer.valueOf( CloudUtil.prop.getProperty("host_cpu_num_min"));
            CloudUtil.host_cpu_num_max=Integer.valueOf( CloudUtil.prop.getProperty("host_cpu_num_max"));

            CloudUtil.host_core_num_foreachcpu_min=Integer.valueOf( CloudUtil.prop.getProperty("host_core_num_foreachcpu_min"));
            CloudUtil.host_core_num_foreachcpu_max=Integer.valueOf( CloudUtil.prop.getProperty("host_core_num_foreachcpu_max"));

            CloudUtil.host_thread_num_foreeachcore=Integer.valueOf( CloudUtil.prop.getProperty("host_thread_num_foreeachcore"));
            CloudUtil.vm_mem_min=Long.valueOf( CloudUtil.prop.getProperty("vm_mem_min"));
            CloudUtil.vm_mem_max=Long.valueOf( CloudUtil.prop.getProperty("vm_mem_max"));

            CloudUtil.dist_host_mips = Integer.valueOf( CloudUtil.prop.getProperty("dist_host_mips"));
            CloudUtil.dist_host_mips_mu = Double.valueOf(prop.getProperty("dist_host_mips_mu")).doubleValue();
            CloudUtil.host_mips_min=Long.valueOf( CloudUtil.prop.getProperty("host_mips_min"));
            CloudUtil.host_mips_max=Long.valueOf( CloudUtil.prop.getProperty("host_mips_max"));

            CloudUtil.core_mips_rate_min = Double.valueOf(prop.getProperty("core_mips_rate_min")).doubleValue();
            CloudUtil.core_mips_rate_max = Double.valueOf(prop.getProperty("core_mips_rate_max")).doubleValue();

            CloudUtil.dist_host_bw = Integer.valueOf( CloudUtil.prop.getProperty("dist_host_bw"));
            CloudUtil.dist_host_bw_mu = Double.valueOf( CloudUtil.prop.getProperty("dist_host_bw_mu")).doubleValue();
            CloudUtil.dist_host_mips_mu = Double.valueOf(prop.getProperty("dist_host_bw_mu")).doubleValue();
            CloudUtil.host_bw_min=Long.valueOf( CloudUtil.prop.getProperty("host_bw_min"));
            CloudUtil.host_bw_max=Long.valueOf( CloudUtil.prop.getProperty("host_bw_max"));
            CloudUtil.vm_cpi=Integer.valueOf( CloudUtil.prop.getProperty("vm_cpi"));

            CloudUtil.dist_vm_vcpu_num = Integer.valueOf( CloudUtil.prop.getProperty("dist_vm_vcpu_num"));
            CloudUtil.dist_vm_vcpu_num_mu=Double.valueOf(prop.getProperty("dist_vm_vcpu_num_mu")).doubleValue();
            CloudUtil.vm_vcpu_num_min=Integer.valueOf( CloudUtil.prop.getProperty("vm_vcpu_num_min"));
            CloudUtil.vm_vcpu_num_max=Integer.valueOf( CloudUtil.prop.getProperty("vm_vcpu_num_max"));

            CloudUtil.mobile_device_num = Integer.valueOf( CloudUtil.prop.getProperty("mobile_device_num"));
            CloudUtil.mobile_device_core_num_min = Integer.valueOf( CloudUtil.prop.getProperty("mobile_device_core_num_min"));
            CloudUtil.mobile_device_core_num_max = Integer.valueOf( CloudUtil.prop.getProperty("mobile_device_core_num_max"));
            CloudUtil.mobile_device_cpu_mips_min = Long.valueOf( CloudUtil.prop.getProperty("mobile_device_cpu_mips_min"));
            CloudUtil.mobile_device_cpu_mips_max = Long.valueOf( CloudUtil.prop.getProperty("mobile_device_cpu_mips_max"));

            CloudUtil.mobile_device_bw_min = Long.valueOf( CloudUtil.prop.getProperty("mobile_device_bw_min"));
            CloudUtil.mobile_device_bw_max = Long.valueOf( CloudUtil.prop.getProperty("mobile_device_bw_max"));

            CloudUtil.mobile_device_power_min = Double.valueOf(prop.getProperty("mobile_device_power_min")).doubleValue();
            CloudUtil.mobile_device_power_max = Double.valueOf(prop.getProperty("mobile_device_power_max")).doubleValue();
            CloudUtil.dist_mobile_device_power = Integer.valueOf( CloudUtil.prop.getProperty("dist_mobile_device_power"));
            CloudUtil.dist_mobile_device_power_mu = Double.valueOf(prop.getProperty("dist_mobile_device_power_mu")).doubleValue();

            CloudUtil.mobile_device_gain_min = Double.valueOf(prop.getProperty("mobile_device_gain_min")).doubleValue();
            CloudUtil.mobile_device_gain_max = Double.valueOf(prop.getProperty("mobile_device_gain_max")).doubleValue();
            CloudUtil.dist_mobile_device_gain = Integer.valueOf( CloudUtil.prop.getProperty("dist_mobile_device_gain"));
            CloudUtil.dist_mobile_device_gain_mu = Double.valueOf(prop.getProperty("dist_mobile_device_gain_mu")).doubleValue();
            CloudUtil.mobile_device_back_noise = Double.valueOf(prop.getProperty("mobile_device_back_noise")).doubleValue();
            CloudUtil.mobile_device_back_noise =  Double.valueOf( CloudUtil.prop.getProperty("mobile_device_back_noise")).doubleValue();
            CloudUtil.mec_channel_num = Long.valueOf( CloudUtil.prop.getProperty("mec_channel_num"));

            CloudUtil.mobile_device_tau_min =  Double.valueOf( CloudUtil.prop.getProperty("mobile_device_tau_min")).doubleValue();
            CloudUtil.mobile_device_tau_max =  Double.valueOf( CloudUtil.prop.getProperty("mobile_device_tau_max")).doubleValue();
            CloudUtil.dist_mobile_device_tau = Integer.valueOf( CloudUtil.prop.getProperty("dist_mobile_device_tau"));
            CloudUtil.dist_mobile_device_tau_mu =  Double.valueOf( CloudUtil.prop.getProperty("dist_mobile_device_tau_mu")).doubleValue();

            CloudUtil.offload_program_datasize = Long.valueOf( CloudUtil.prop.getProperty("offload_program_datasize"));

            CloudUtil.rDataGen = new RandomDataImpl();


        }catch(Exception e){
            e.printStackTrace();
        }

        //Hostの設定
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
     * 指定ユーザID, VM数のCondorVMリストを作成する．
     * @param userId
     * @param vms number of vms to be created
     * @return
     */
    /**
    public List<CondorVM> createVM(int userId, int vms) {

        //Creates a container to store VMs. This list is passed to the broker later
        LinkedList<CondorVM> list = new LinkedList<CondorVM>();

        //VM Parameters
        long size = 10000; //image size (MB)
        int ram = CloudUtil.genInt(CloudUtil.vm_mem_min,CloudUtil.vm_mem_max);
        int mips = CloudUtil.genInt2(CloudUtil.host_mips_min,CloudUtil.host_mips_max, CloudUtil.dist_host_mips, CloudUtil.dist_host_bw_mu);
        long bw = CloudUtil.genLong2(CloudUtil.host_bw_min, CloudUtil.host_bw_max, CloudUtil.dist_host_bw, CloudUtil.dist_host_bw_mu);

        int pesNumber = 1; //number of cpus
        String vmm = "Xen"; //VMM name

        //create VMs
        CCNRouterVM[] vm = new CCNRouterVM[vms];

        for (int i = 0; i < vms; i++) {
            double ratio = 1.0;
            vm[i] = new CCNRouterVM(i, userId, mips * ratio, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared());
            list.add(vm[i]);
        }

        return list;
    }
     */
}
