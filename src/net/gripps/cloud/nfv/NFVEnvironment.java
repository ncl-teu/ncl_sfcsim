package net.gripps.cloud.nfv;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.Cloud;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.DockerRepository;

public class NFVEnvironment extends CloudEnvironment {

    /**
     * Dockerリポジトリ
     */
    //protected DockerRepository dHost;

    private ComputeHost dockerRepository;

    public ComputeHost getDockerRepository() {
        return dockerRepository;
    }

    public void setDockerRepository(ComputeHost dockerRepository) {
        this.dockerRepository = dockerRepository;
    }



    public NFVEnvironment() {
        super();
        //DataCenterのIDを決定
        Cloud cloud = this.getDcMap().get(new Long(0));
        int len = cloud.getComputeHostMap().size();

        String hostPrefix = 0 + CloudUtil.DELIMITER + len;

        //Dockerリポジトリを作成する
        this.dockerRepository = new ComputeHost( len, null, 0, null, (long)1, hostPrefix, NFVUtil.repository_bw);


    }


}
