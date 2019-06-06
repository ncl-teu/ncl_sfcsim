package net.gripps.cloud.nfv.fairscheduling;

public class FairnessInfo {

    /**
     * コンピュートホストのprefix
     */
    private String hostPrefix;

    /**
     * 重なり具合のa値
     */
    private double val_ovarlap;

    /**
     * ホスト内の処理時間の合計（各VCPUの処理時間を
     * 合計したもの）
     */
    private double total_execTime;

    /**
     * 応答時間のa値
     */
    private double val_rt;

    /**
     * 現在の完了時刻
     */
    private double currentCT;

    /**
     * 現在の開始時刻
     */
    private double currentST;

    public FairnessInfo(String hostPrefix) {
        this.hostPrefix = hostPrefix;
    }

    public String getHostPrefix() {
        return hostPrefix;
    }

    public void setHostPrefix(String hostPrefix) {
        this.hostPrefix = hostPrefix;
    }

    public double getVal_ovarlap() {
        return val_ovarlap;
    }

    public void setVal_ovarlap(double val_ovarlap) {
        this.val_ovarlap = val_ovarlap;
    }

    public double getVal_rt() {
        return val_rt;
    }

    public void setVal_rt(double val_rt) {
        this.val_rt = val_rt;
    }

    public double getCurrentCT() {
        return currentCT;
    }

    public void setCurrentCT(double currentCT) {
        this.currentCT = currentCT;
    }

    public double getCurrentST() {
        return currentST;
    }

    public void setCurrentST(double currentST) {
        this.currentST = currentST;
    }

    public double getTotal_execTime() {
        return total_execTime;
    }

    public void setTotal_execTime(double total_execTime) {
        this.total_execTime = total_execTime;
    }
}
