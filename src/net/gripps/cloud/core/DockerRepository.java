package net.gripps.cloud.core;

import net.gripps.environment.CPU;

import java.io.Serializable;
import java.util.HashMap;
import java.util.TreeMap;

public class DockerRepository extends ComputeHost implements Serializable {


    public DockerRepository(long machineID, TreeMap<Long, CPU> cpuMap, int num, HashMap<String, VM> vmMap, Long dcID, String p, long bw) {
        super(machineID, cpuMap, num, vmMap, dcID, p, bw);
    }



}
