package net.gripps.cloud.mapreduce.scheduling;

import net.gripps.cloud.mapreduce.MRUtil;
import net.gripps.cloud.mapreduce.core.FSHost;
import net.gripps.cloud.mapreduce.core.MRVCPU;
import net.gripps.cloud.mapreduce.datamodel.InputFile;
import net.gripps.cloud.mapreduce.datamodel.InputSplit;
import net.gripps.cloud.mapreduce.datamodel.KeyElement;
import net.gripps.cloud.mapreduce.datamodel.MergedFileSplit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/09.
 */
public class BaseMRScheduling implements IMRScheduling {


    /**
     * InputSplitを送信するためのスケジューリング
     * 性能を無視して，順番に送っているだけ．
     * @param fsHost
     * @param mapperList
     */
    @Override
    public void sendInputSplits(FSHost fsHost, ArrayList< MRVCPU> mapperList) {
        Iterator<InputFile> ifIte = fsHost.getIfList().iterator();
        int len = mapperList.size();
        int idx = 0;
        //入力ファイル毎のループ
        while(ifIte.hasNext()){
            InputFile file = ifIte.next();
            //InputSplitごとのループ
            Iterator<InputSplit> splitIte = file.getSplitList().iterator();
            int splitNum = file.getSplitList().size();
            double maxComTime = -1d;
            double dividedBW = MRUtil.getRoundedValue(fsHost.getBw() / (double)splitNum);
            long slen = file.getSplitList().size();
            long mlen = mapperList.size();

            while(splitIte.hasNext()){
                InputSplit split = splitIte.next();
                //当該Splitを指定のノードへ送る
               /* if((idx >= len-1)||(!splitIte.hasNext())){
                    //mapperのインデックスが最後に達した場合は
                    //最初に戻る．
                    idx = 0;
                    if(MRUtil.dfs_transfer_mode == 0){
                        //また，並列転送モードのときは，最大時間分，待つ．
                        fsHost.process(maxComTime);
                    }
                    //そして，maxComTimeを初期化する．
                    maxComTime = -1d;

                }

                */
               int   amari =(int) (idx % mlen);
                MRVCPU target = mapperList.get(amari);
                if(MRUtil.dfs_transfer_mode == 0){
                    double val = fsHost.calcComTime(dividedBW, target, split);
//よく考える必要あり．mapper側で待たせないと，FSHostで全部止まってしまう．
                    split.setComTime(val);
                  //  fsHost.process(val);
                    //そして，splitを入れる．
                    target.getIsQueue().offer(split);
                    if(val >= maxComTime){
                        maxComTime = val;
                    }
                }else{
                    //targetに対して毎回，split送信時間分，待つ．
                    fsHost.sendInputSplitSeq(file, split, target);
                }

                //ystem.out.println(split.getIsID());
                idx++;
            }

        }
        //System.out.println("!tst");

    }

    @Override
    public LinkedList<HashMap<Long, KeyElement>> divideMergedFile(MergedFileSplit mfs, long reducerNum) {
        LinkedList<HashMap<Long, KeyElement>> retList = new LinkedList<HashMap<Long, KeyElement>>();
        long totalLen = mfs.getKeyElementMap().size();
        //1MPあたりのkey数を算出する．
        long  key_num_mp = (totalLen / reducerNum)+1;
        Iterator<KeyElement> kIte = mfs.getKeyElementMap().values().iterator();
        long currentIDX = 0;
        while (kIte.hasNext()) {
            long tmpIdx = 0;
       /*     if(totalLen - currentIDX-1<key_num_mp){
                tmpIdx = totalLen - currentIDX-1;
            }else{
                tmpIdx = key_num_mp;
            }

        */
            HashMap<Long, KeyElement> mp = new HashMap<Long, KeyElement>();
            for(int i=0;i<key_num_mp;i++){
                KeyElement ke = kIte.next();
                currentIDX++;
                mp.put(ke.getKey(), ke);
                if(currentIDX >= totalLen){
                    break;
                }
            }
            retList.add(mp);
            if(currentIDX >= totalLen){
                break;
            }


        }
        return retList;
    }
}
