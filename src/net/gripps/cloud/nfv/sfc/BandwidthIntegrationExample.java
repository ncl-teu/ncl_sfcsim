package net.gripps.cloud.nfv.sfc;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;

/**
 * 示例：如何在 BaseVNFSchedulingAlgorithm 中使用带宽控制
 * 这是一个参考实现，展示了如何集成 BandwidthTimeSlot 和 BandwidthResourceManager
 */
public class BandwidthIntegrationExample {

    /**
     * 方案1：修改 calcImageComTime，考虑实时可用带宽
     *
     * 这是对原方法的增强版本，在计算下载时间时考虑当前可用的带宽资源
     */
    public static double calcImageComTime_WithBandwidthCheck(
            long dataSize,
            VCPU vcpu,
            CloudEnvironment env,
            BandwidthResourceManager bandwidthManager) {

        // 获取源和目的地DC ID
        Long fromDCID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        NFVEnvironment nEnv = (NFVEnvironment) env;
        Long toDCID = nEnv.getDockerRepository().getDcID();

        // 获取云资源
        long dcBW = NFVUtil.MAXValue;
        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);
        boolean isSameDC = false;

        // DC 间的带宽计算
        if (fromDCID.longValue() == toDCID.longValue()) {
            isSameDC = true;
        } else {
            // 原有的DC间BW计算
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());

            // *** 新增：使用带宽管理器检查DC间的实际可用带宽 ***
            String dcLinkKey = "DC^" + fromDCID + "^" + toDCID;
            long availableDCBW = bandwidthManager.getAvailableBandwidth(dcLinkKey);
            if (availableDCBW > 0) {
                dcBW = Math.min(dcBW, availableDCBW);
            }
        }

        // Host 间的带宽计算
        Long fromHostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = nEnv.getDockerRepository();
        long hostBW = NFVUtil.MAXValue;

        if (isSameDC) {
            if (fromHost.getMachineID() == toHost.getMachineID()) {
                return 0; // 同一主机，无需下载
            } else {
                hostBW = Math.min(fromHost.getBw(), toHost.getBw());

                // *** 新增：检查Host间的实际可用带宽 ***
                String hostLinkKey = "Host^" + fromHost.getPrefix() + "^" + toHost.getPrefix();
                long availableHostBW = bandwidthManager.getAvailableBandwidth(hostLinkKey);
                if (availableHostBW > 0) {
                    hostBW = Math.min(hostBW, availableHostBW);
                }
            }
        } else {
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());

            // *** 新增：检查Host间的实际可用带宽 ***
            String hostLinkKey = "Host^" + fromHost.getPrefix() + "^" + toHost.getPrefix();
            long availableHostBW = bandwidthManager.getAvailableBandwidth(hostLinkKey);
            if (availableHostBW > 0) {
                hostBW = Math.min(hostBW, availableHostBW);
            }
        }

        long realBW = Math.min(dcBW, hostBW);
        if (realBW <= 0) {
            realBW = 1; // 避免除以零
        }

        double time = CloudUtil.getRoundedValue((double) dataSize / (double) realBW);
        return time;
    }

    /**
     * 方案2：使用 BandwidthTimeSlot 进行精确的时间段预留
     *
     * 这个方法在调度时主动预留带宽，并查询何时可以开始下载
     */
    public static double calcImageComTime_WithTimeSlotReservation(
            long dataSize,
            VCPU vcpu,
            CloudEnvironment env,
            BandwidthTimeSlot dcTimeSlot,
            double currentTime) {

        // 获取源和目的地DC ID
        Long fromDCID = CloudUtil.getInstance().getDCID(vcpu.getPrefix());
        NFVEnvironment nEnv = (NFVEnvironment) env;
        Long toDCID = nEnv.getDockerRepository().getDcID();

        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);
        boolean isSameDC = (fromDCID.longValue() == toDCID.longValue());

        // 基础带宽计算（不考虑时间槽）
        long dcBW = NFVUtil.MAXValue;
        if (!isSameDC) {
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());
        }

        Long fromHostID = CloudUtil.getInstance().getHostID(vcpu.getPrefix());
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = nEnv.getDockerRepository();
        long hostBW = NFVUtil.MAXValue;

        if (isSameDC) {
            if (fromHost.getMachineID() == toHost.getMachineID()) {
                return 0;
            }
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());
        } else {
            hostBW = Math.min(fromHost.getBw(), toHost.getBw());
        }

        long baselineBW = Math.min(dcBW, hostBW);

        // *** 新增：使用时间槽查询可用带宽和预留 ***
        long availableBW = dcTimeSlot.getAvailableBW(currentTime, currentTime + 1000);
        long actualBW = Math.min(baselineBW, availableBW);

        if (actualBW <= 0) {
            actualBW = 1;
        }

        double downloadTime = CloudUtil.getRoundedValue((double) dataSize / (double) actualBW);

        return downloadTime;
    }

    /**
     * 方案3：智能调度 - 如果当前带宽不足，查询下次可用时刻
     *
     * 返回 [是否可以立即开始, 建议开始时刻, 预期下载时间]
     */
    public static class ImageDownloadSchedule {
        public boolean canStartImmediately;
        public double recommendedStartTime;
        public double estimatedDownloadTime;

        public ImageDownloadSchedule(boolean canStart, double startTime, double duration) {
            this.canStartImmediately = canStart;
            this.recommendedStartTime = startTime;
            this.estimatedDownloadTime = duration;
        }
    }

    public static ImageDownloadSchedule scheduleImageDownload(
            long dataSize,
            VCPU targetVCPU,
            CloudEnvironment env,
            BandwidthTimeSlot dcTimeSlot,
            double currentTime,
            long minimumRequiredBW) {

        // 获取基础带宽
        Long fromDCID = CloudUtil.getInstance().getDCID(targetVCPU.getPrefix());
        NFVEnvironment nEnv = (NFVEnvironment) env;
        Long toDCID = nEnv.getDockerRepository().getDcID();

        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Cloud toCloud = env.getDcMap().get(toDCID);

        long dcBW = NFVUtil.MAXValue;
        if (fromDCID.longValue() != toDCID.longValue()) {
            dcBW = Math.min(fromCloud.getBw(), toCloud.getBw());
        }

        Long fromHostID = CloudUtil.getInstance().getHostID(targetVCPU.getPrefix());
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        ComputeHost toHost = nEnv.getDockerRepository();
        long hostBW = Math.min(fromHost.getBw(), toHost.getBw());

        long baselineBW = Math.min(dcBW, hostBW);

        // 检查当前的可用带宽
        long availableBW = dcTimeSlot.getAvailableBW(currentTime, currentTime + 100);

        if (availableBW >= minimumRequiredBW) {
            // 可以立即开始
            long actualBW = Math.min(baselineBW, availableBW);
            double downloadTime = CloudUtil.getRoundedValue((double) dataSize / (double) actualBW);
            return new ImageDownloadSchedule(true, currentTime, downloadTime);
        } else {
            // 带宽不足，查询下次可用时刻
            double nextAvailableTime = dcTimeSlot.getNextAvailableTime(currentTime, minimumRequiredBW);
            long actualBW = Math.min(baselineBW, minimumRequiredBW);
            double downloadTime = CloudUtil.getRoundedValue((double) dataSize / (double) actualBW);
            return new ImageDownloadSchedule(false, nextAvailableTime, downloadTime);
        }
    }

    /**
     * 方案4：简化版本 - 仅在调度前后占用/释放带宽
     */
    public static void reserveBandwidthForImageDownload(
            String vnfId,
            double startTime,
            double endTime,
            long requiredBW,
            BandwidthTimeSlot timeSlot) {

        // 预留带宽
        String taskId = "IMG_Download_" + vnfId;
        boolean reserved = timeSlot.reserveBW(startTime, endTime, requiredBW, taskId);

        if (reserved) {
            System.out.println("成功预留带宽: " + taskId +
                             " [" + startTime + ", " + endTime + "] BW=" + requiredBW);
        } else {
            System.out.println("预留带宽失败: " + taskId +
                             " 需要 " + requiredBW + " Mbps");
            // 查询下次可用时刻
            double nextTime = timeSlot.getNextAvailableTime(startTime, requiredBW);
            System.out.println("建议在 " + nextTime + " 重试");
        }
    }

    public static void releaseBandwidthForImageDownload(String vnfId, BandwidthTimeSlot timeSlot) {
        String taskId = "IMG_Download_" + vnfId;
        timeSlot.releaseBW(taskId);
        System.out.println("已释放带宽: " + taskId);
    }
}

