package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.core.*;
import java.util.*;

/**
 * 全局带宽资源管理器 - 简化版本
 * 跟踪DC/Host之间的带宽占用情况
 * 这是一个基础实现，用于理解带宽约束对调度的影响
 */
public class BandwidthResourceManager {

    /**
     * 简单的带宽占用记录
     * key: "source^destination", value: 当前占用的带宽
     */
    private final Map<String, Long> bandwidthUsage = new HashMap<>();

    /**
     * 带宽最大容量
     * key: "source^destination", value: 最大带宽
     */
    private final Map<String, Long> bandwidthCapacity = new HashMap<>();

    private final CloudEnvironment env;

    public BandwidthResourceManager(CloudEnvironment env) {
        this.env = env;
        this.initialize();
    }

    /**
     * 初始化带宽容量
     * 这里简化处理：每条线路的容量设为该线路上较小的BW值
     */
    private void initialize() {
        // 初始化所有DC间的带宽容量
        // 使用DC的BW作为容量
        for (Cloud cloud : env.getDcMap().values()) {
            String key = "DC^" + cloud.getId();
            bandwidthCapacity.put(key, cloud.getBw());
            bandwidthUsage.put(key, 0L);
        }

        // 初始化所有Host的带宽容量
        for (Cloud cloud : env.getDcMap().values()) {
            for (ComputeHost host : cloud.getComputeHostMap().values()) {
                String key = "Host^" + host.getPrefix();
                bandwidthCapacity.put(key, host.getBw());
                bandwidthUsage.put(key, 0L);
            }
        }
    }

    /**
     * 获取某条线路的剩余可用带宽
     */
    public long getAvailableBandwidth(String linkKey) {
        Long capacity = bandwidthCapacity.get(linkKey);
        Long usage = bandwidthUsage.get(linkKey);

        if (capacity == null || usage == null) {
            return 0;
        }

        return capacity - usage;
    }

    /**
     * 占用带宽
     */
    public void useBandwidth(String linkKey, long bandwidthNeeded) {
        Long current = bandwidthUsage.get(linkKey);
        if (current != null) {
            bandwidthUsage.put(linkKey, Math.min(current + bandwidthNeeded,
                bandwidthCapacity.get(linkKey)));
        }
    }

    /**
     * 释放带宽
     */
    public void releaseBandwidth(String linkKey, long bandwidthToRelease) {
        Long current = bandwidthUsage.get(linkKey);
        if (current != null) {
            bandwidthUsage.put(linkKey, Math.max(0, current - bandwidthToRelease));
        }
    }

    /**
     * 获取带宽容量
     */
    public long getBandwidthCapacity(String linkKey) {
        Long capacity = bandwidthCapacity.get(linkKey);
        return capacity != null ? capacity : 0;
    }

    /**
     * 获取带宽占用情况
     */
    public long getBandwidthUsage(String linkKey) {
        Long usage = bandwidthUsage.get(linkKey);
        return usage != null ? usage : 0;
    }

    /**
     * 获取带宽利用率（0-100）
     */
    public double getBandwidthUtilization(String linkKey) {
        Long capacity = bandwidthCapacity.get(linkKey);
        Long usage = bandwidthUsage.get(linkKey);

        if (capacity == null || capacity <= 0) {
            return 0;
        }

        return ((double) usage / (double) capacity) * 100;
    }
}

