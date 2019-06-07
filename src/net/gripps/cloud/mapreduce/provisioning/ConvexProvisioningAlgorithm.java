package net.gripps.cloud.mapreduce.provisioning;

import net.gripps.cloud.mapreduce.MRMgr;
import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.core.FSHost;
import net.gripps.cloud.mapreduce.core.MRCloudEnvironment;
import net.gripps.cloud.mapreduce.core.MRVCPU;
import net.gripps.cloud.mapreduce.datamodel.InputFile;

import java.util.HashMap;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/18.
 */
public class ConvexProvisioningAlgorithm extends BaseProvisioningAlgorithm {

    protected long mapperNum;

    protected long keyNum;

    protected long reducerNum;

    protected long allNum;



    public ConvexProvisioningAlgorithm(MRCloudEnvironment in_env, long mapperNum,
                                       long reducerNum, HashMap<String, MRVCPU> mapperMap,
                                       HashMap<String, MRVCPU> reducerMap) {
        super(in_env, mapperNum, reducerNum, mapperMap, reducerMap);
    }

    public ConvexProvisioningAlgorithm(MRCloudEnvironment in_env) {
        super(in_env);
    }


    @Override
    public long calcReducerNum() {
        long num = Math.min(this.keyNum, this.allNum-this.mapperNum);
        this.reducerNum = num;
        return num;
    }

    @Override
    public long calcMapperNum() {
        return this.calcOptimalMapperNum();
    }

    public long  calcOptimalMapperNum(){
        double  k = 1.0;
        //outputレコード数@I_i = InputRecord
        //今はinputレコード数と同じ．
        long num_out_rec = (long)MRUtil.getRoundedValue(MRUtil.size_of_inputsplit / (double)MRUtil.size_of_inputrecord);
        //1 spillファイル内のバッファレコード数(1キーで1spillファイルとしている）．

        double L_ave_dc = MRUtil.getRoundedValue((MRUtil.datacenter_externalbw_min + MRUtil.datacenter_externalbw_max) /(double)2);
        double L_ave_host = MRUtil.getRoundedValue((MRUtil.datacenter_externalbw_min + MRUtil.datacenter_externalbw_max) / (double)2);

        double  L = Math.min(MRUtil.dfhost_bw, L_ave_dc);
        L = Math.min(L, L_ave_host);

        double w_out = MRUtil.getRoundedValue(MRUtil.split_out_in_rate * MRUtil.size_of_inputrecord);

        //spillファイルサイズを算出する
        // outputsplit(=inputsplit size * ρ)/キー数
        double spillFileSize = MRUtil.getRoundedValue(MRUtil.size_of_inputsplit * MRUtil.split_out_in_rate / (double)MRMgr.getIns().getKeyKindsNum());

        double  n_spillb =MRUtil.getRoundedValue(spillFileSize / w_out);
        double n_outrecord = MRUtil.getRoundedValue(MRUtil.size_of_inputsplit * MRUtil.split_out_in_rate/w_out);
        long keyNum = MRMgr.getIns().getKeyKindsNum();
        this.keyNum = keyNum;

        double d_key=spillFileSize;
        double alpha_r = MRUtil.getRoundedValue((MRUtil.host_mips_min + MRUtil.host_mips_max)/(double)2);
        double alpha_w = alpha_r;
        //Bの値を計算する．
        double B = MRUtil.getRoundedValue(Math.ceil(MRUtil.getRoundedValue(n_outrecord/n_spillb))*MRUtil.getRoundedValue(spillFileSize/L)+keyNum*d_key*(
                MRUtil.getRoundedValue(1/alpha_r) + MRUtil.getRoundedValue(2/alpha_w)) + keyNum *
                        (MRUtil.getRoundedValue(n_outrecord/(double)keyNum) * MRUtil.getRoundedValue(MRUtil.out_record_workload_perMB/alpha_w))
                );
        MRCloudEnvironment env = MRMgr.getIns().getEnv();
        FSHost fsHost = env.getFsHost();
        InputFile file = fsHost.getIfList().get(0);

        double inputFileSize = file.getSize();
        double D = MRUtil.reduce_out_in_rate * inputFileSize * (MRUtil.getRoundedValue(1/alpha_r) + MRUtil.getRoundedValue(1/alpha_w))
                +MRUtil.getRoundedValue(keyNum * MRUtil.merge_workload_permerge/alpha_w);

        double E =(B + (2*D + inputFileSize * (MRUtil.getRoundedValue((1/alpha_r) + MRUtil.out_record_workload_perMB/(w_out*alpha_w))
        )+MRUtil.getRoundedValue(inputFileSize*MRUtil.reduce_out_in_rate/L)));

        double G = inputFileSize * (MRUtil.getRoundedValue(1/alpha_r)+
                MRUtil.getRoundedValue(keyNum*MRUtil.split_out_in_rate*(MRUtil.workload_partitioning_per_key +
                        MRUtil.out_record_workload_perMB)/(alpha_w * MRUtil.size_of_inputrecord))+MRUtil.getRoundedValue(MRUtil.split_out_in_rate/MRUtil.size_of_inputrecord)*
                (MRUtil.getRoundedValue(MRUtil.workload_partitioning_per_key*keyNum/alpha_r)*MRUtil.log2(n_spillb)+w_out/alpha_w+
                        w_out*(MRUtil.getRoundedValue(1/alpha_r) + MRUtil.getRoundedValue(1/alpha_w)) + MRUtil.getRoundedValue(MRUtil.reduce_out_in_rate*
                        MRUtil.merge_workload_permerge/alpha_w) ));
        //long A = env.getGlobal_vcpuMap().size();
        //long A =(long) Math.min(this.env.getGlobal_vcpuMap().size(),  Math.ceil(MRUtil.getRoundedValue(inputFileSize / (double)MRUtil.size_of_inputsplit)));
            long A =  (long)Math.ceil(MRUtil.getRoundedValue(inputFileSize / (double)MRUtil.size_of_inputsplit));

        //Mapper数を求める．
        double m = MRUtil.getRoundedValue((-1*A*G + Math.sqrt(Math.pow(A,3) * B * G *k + Math.pow(A,2) * D * G*Math.pow(k,2) + Math.pow(A,3)* G * E*k))/((-1)*G +
                k*A*B + D*Math.pow(k,2) + A*E*Math.pow(k,2)) );
        long realNum = (long)Math.ceil(m);
        this.mapperNum = realNum;
        this.allNum = A;

        return this.mapperNum;
    }


}
