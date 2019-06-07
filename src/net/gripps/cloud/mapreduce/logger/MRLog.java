package net.gripps.cloud.mapreduce.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by Hidehiro Kanemitsu on 2019/04/01
 */
public class MRLog {

    //getLoggerの引数はロガー名を指定する。
    //log4j2では、ロガー名の指定が省略可能になった。
    private static Logger logger;

    public static MRLog own;

    public static MRLog getIns(){
        if(MRLog.own == null){
            MRLog.own = new MRLog();
        }
        return MRLog.own;
    }

    private  MRLog(){
        MRLog.logger = LogManager.getLogger();
    }

    /**
     * ログ出力する．
     * @param m
     */
    public void log(String m){
        logger.info(m);
    }

    /**
     public void runSample() {

     logger.trace("Start"); //2017/01/21 06:02:17.154 [main] TRACE  test1.Sample Start

     int a = 1;
     int b = 2;
     String c = null;

     logger.info("this is a "+a);
     logger.debug("debug"); //2017/01/21 06:02:17.157 [main] DEBUG  test1.Sample debug
     logger.info("info={}",a); //2017/01/21 06:02:17.159 [main] INFO   test1.Sample info=1
     logger.warn("warn={},={}" ,a,b); //2017/01/21 06:02:17.159 [main] WARN   test1.Sample warn=1,=2
     logger.error("error={}",c); //2017/01/21 06:02:17.171 [main] ERROR  test1.Sample error=null

     logger.trace("End"); //2017/01/21 06:02:17.172 [main] TRACE  test1.Sample End
     }
     **/
}
