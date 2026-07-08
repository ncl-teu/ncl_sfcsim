package net.gripps.cloud.nfv.main;

import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.NFVEnvironment;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.BaseVNFSchedulingAlgorithm;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * Export DAG visualization metadata for offline HTML rendering.
 */
public final class DAGMetadataExporter {

    private static final double EPS = 0.000001d;

    private DAGMetadataExporter() {
    }

    public static final class ExportContext {
        private final long seed;
        private final String timestamp;
        private final String seedTag;
        private final Path outputDir;

        private ExportContext(long seed, String timestamp, String seedTag, Path outputDir) {
            this.seed = seed;
            this.timestamp = timestamp;
            this.seedTag = seedTag;
            this.outputDir = outputDir;
        }

        public long getSeed() {
            return seed;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getSeedTag() {
            return seedTag;
        }

        public Path getOutputDir() {
            return outputDir;
        }
    }

    public static final class AlgorithmSnapshot {
        private final String algorithm;
        private final BaseVNFSchedulingAlgorithm scheduler;
        private final SFC scheduledSfc;
        private final NFVEnvironment scheduledEnv;
        private final long imageDLTotal;
        private final long imageDLFromRepo;
        private final long imageDLFromHost;

        private AlgorithmSnapshot(String algorithm,
                                  BaseVNFSchedulingAlgorithm scheduler,
                                  SFC scheduledSfc,
                                  NFVEnvironment scheduledEnv,
                                  long imageDLTotal,
                                  long imageDLFromRepo,
                                  long imageDLFromHost) {
            this.algorithm = algorithm;
            this.scheduler = scheduler;
            this.scheduledSfc = scheduledSfc;
            this.scheduledEnv = scheduledEnv;
            this.imageDLTotal = imageDLTotal;
            this.imageDLFromRepo = imageDLFromRepo;
            this.imageDLFromHost = imageDLFromHost;
        }
    }

    private static final class EdgeInfo {
        long fromVnfId;
        long toVnfId;
        long fromSfcId;
        long toSfcId;
        long dataSize;
    }

    public static ExportContext prepareExportContext(long seed) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String seedTag = "seed_" + seed + "_" + timestamp;
        Path outputDir = Paths.get("dag_outputs", seedTag);
        Files.createDirectories(outputDir);
        return new ExportContext(seed, timestamp, seedTag, outputDir);
    }

    public static Path copyPropertiesFile(String propertiesArg, ExportContext ctx) throws IOException {
        Path copiedPath = ctx.getOutputDir().resolve(ctx.getSeedTag() + ".properties");
        Path src = Paths.get(propertiesArg);
        if (!src.isAbsolute()) {
            src = Paths.get("").toAbsolutePath().resolve(src).normalize();
        }

        if (Files.exists(src)) {
            Files.copy(src, copiedPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            writeText(copiedPath,
                    "# WARNING: source properties file not found.\n"
                            + "# original_arg=" + propertiesArg + "\n");
        }
        return copiedPath;
    }

    public static AlgorithmSnapshot buildAlgorithmSnapshot(String algorithm,
                                                           BaseVNFSchedulingAlgorithm scheduler,
                                                           SFC scheduledSfc,
                                                           NFVEnvironment scheduledEnv,
                                                           long imageDLTotal,
                                                           long imageDLFromRepo,
                                                           long imageDLFromHost) {
        return new AlgorithmSnapshot(
                algorithm,
                scheduler,
                scheduledSfc,
                scheduledEnv,
                imageDLTotal,
                imageDLFromRepo,
                imageDLFromHost
        );
    }

    public static void exportAll(ExportContext ctx,
                                 String propertiesArg,
                                 Path copiedPropertiesPath,
                                 SFC topologySfc,
                                 NFVEnvironment topologyEnv,
                                 double ccrData,
                                 double idrImage,
                                 double nccrTotal,
                                 AlgorithmSnapshot... snapshots) throws IOException {
        if (ctx == null || topologySfc == null || topologyEnv == null) {
            return;
        }

        Path topologyPath = ctx.getOutputDir().resolve("dag_topology.json");
        writeTopologyJson(topologyPath, topologySfc);

        List<String> scheduleFileNames = new ArrayList<String>();
        if (snapshots != null) {
            for (AlgorithmSnapshot snapshot : snapshots) {
                if (snapshot == null || snapshot.algorithm == null) {
                    continue;
                }
                String fileName = "dag_schedule_" + snapshot.algorithm.toLowerCase(Locale.US) + ".json";
                Path schedulePath = ctx.getOutputDir().resolve(fileName);
                writeScheduleJson(schedulePath, snapshot);
                scheduleFileNames.add(fileName);
            }
        }

        Path metaPath = ctx.getOutputDir().resolve("meta.json");
        writeMetaJson(metaPath, ctx, propertiesArg, copiedPropertiesPath, ccrData, idrImage, nccrTotal, scheduleFileNames, snapshots);
    }

    private static void writeMetaJson(Path out,
                                      ExportContext ctx,
                                      String propertiesArg,
                                      Path copiedPropertiesPath,
                                      double ccrData,
                                      double idrImage,
                                      double nccrTotal,
                                      List<String> scheduleFiles,
                                      AlgorithmSnapshot... snapshots) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "schemaVersion", "1.0", true);
        appendJsonField(sb, 1, "generatedAt", ctx.getTimestamp(), true);
        appendJsonField(sb, 1, "seed", String.valueOf(ctx.getSeed()), false, true);
        appendJsonField(sb, 1, "propertiesArg", propertiesArg, true);
        appendJsonField(sb, 1, "copiedProperties", copiedPropertiesPath == null ? "" : copiedPropertiesPath.getFileName().toString(), true);

        sb.append(indent(1)).append("\"metrics\": {\n");
        appendJsonField(sb, 2, "CCR_data", fmtDouble(ccrData), false, true);
        appendJsonField(sb, 2, "IDR_image", fmtDouble(idrImage), false, true);
        appendJsonField(sb, 2, "NCCR_total", fmtDouble(nccrTotal), false, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"files\": {\n");
        appendJsonField(sb, 2, "topology", "dag_topology.json", true);

        sb.append(indent(2)).append("\"schedules\": [\n");
        for (int i = 0; i < scheduleFiles.size(); i++) {
            sb.append(indent(3)).append("\"").append(escapeJson(scheduleFiles.get(i))).append("\"");
            if (i < scheduleFiles.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(2)).append("]\n");
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"algorithms\": [\n");
        if (snapshots != null) {
            int validCount = 0;
            for (AlgorithmSnapshot snapshot : snapshots) {
                if (snapshot != null && snapshot.scheduler != null) {
                    validCount++;
                }
            }

            int seen = 0;
            for (AlgorithmSnapshot snapshot : snapshots) {
                if (snapshot == null || snapshot.scheduler == null) {
                    continue;
                }
                seen++;
                sb.append(indent(2)).append("{\n");
                appendJsonField(sb, 3, "name", snapshot.algorithm, true);
                appendJsonField(sb, 3, "makespan", fmtDouble(snapshot.scheduler.getMakeSpan()), false, true);
                appendJsonField(sb, 3, "slr", fmtDouble(NFVUtil.getRoundedValue(snapshot.scheduler.getMakeSpan() / snapshot.scheduler.getTotalCPProcTimeAtMaxSpeed())), false, true);
                appendJsonField(sb, 3, "assignedVcpuNum", String.valueOf(snapshot.scheduler.getAssignedVCPUMap().size()), false, true);
                appendJsonField(sb, 3, "hostNum", String.valueOf(snapshot.scheduler.getHostSet().size()), false, true);
                appendJsonField(sb, 3, "instanceNum", String.valueOf(snapshot.scheduler.calcTotalFunctionInstanceNum()), false, true);

                sb.append(indent(3)).append("\"imageDownloads\": {\n");
                appendJsonField(sb, 4, "total", String.valueOf(snapshot.imageDLTotal), false, true);
                appendJsonField(sb, 4, "fromRepo", String.valueOf(snapshot.imageDLFromRepo), false, true);
                appendJsonField(sb, 4, "fromHost", String.valueOf(snapshot.imageDLFromHost), false, false);
                sb.append(indent(3)).append("}\n");

                sb.append(indent(2)).append("}");
                if (seen < validCount) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        }
        sb.append(indent(1)).append("]\n");
        sb.append("}\n");

        writeText(out, sb.toString());
    }

    private static void writeTopologyJson(Path out, SFC sfc) throws IOException {
        List<VNF> nodes = sortedVnfs(sfc);
        List<EdgeInfo> edges = collectEdges(nodes);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "nodeCount", String.valueOf(nodes.size()), false, true);
        appendJsonField(sb, 1, "edgeCount", String.valueOf(edges.size()), false, true);

        sb.append(indent(1)).append("\"nodes\": [\n");
        for (int i = 0; i < nodes.size(); i++) {
            VNF v = nodes.get(i);
            sb.append(indent(2)).append("{\n");
            appendCommonNodeFields(sb, v, 3, false);
            sb.append(indent(2)).append("}");
            if (i < nodes.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("],\n");

        sb.append(indent(1)).append("\"edges\": [\n");
        for (int i = 0; i < edges.size(); i++) {
            EdgeInfo e = edges.get(i);
            sb.append(indent(2)).append("{\n");
            appendJsonField(sb, 3, "fromVnfId", String.valueOf(e.fromVnfId), false, true);
            appendJsonField(sb, 3, "toVnfId", String.valueOf(e.toVnfId), false, true);
            appendJsonField(sb, 3, "fromSfcId", String.valueOf(e.fromSfcId), false, true);
            appendJsonField(sb, 3, "toSfcId", String.valueOf(e.toSfcId), false, true);
            appendJsonField(sb, 3, "dataSize", String.valueOf(e.dataSize), false, false);
            sb.append(indent(2)).append("}");
            if (i < edges.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("]\n");
        sb.append("}\n");

        writeText(out, sb.toString());
    }

    private static void writeScheduleJson(Path out, AlgorithmSnapshot snapshot) throws IOException {
        List<VNF> nodes = sortedVnfs(snapshot.scheduledSfc);
        sortByExecution(nodes);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, 1, "algorithm", snapshot.algorithm, true);
        appendJsonField(sb, 1, "nodeCount", String.valueOf(nodes.size()), false, true);

        sb.append(indent(1)).append("\"imageDownloads\": {\n");
        appendJsonField(sb, 2, "total", String.valueOf(snapshot.imageDLTotal), false, true);
        appendJsonField(sb, 2, "fromRepo", String.valueOf(snapshot.imageDLFromRepo), false, true);
        appendJsonField(sb, 2, "fromHost", String.valueOf(snapshot.imageDLFromHost), false, false);
        sb.append(indent(1)).append("},\n");

        sb.append(indent(1)).append("\"nodes\": [\n");
        for (int i = 0; i < nodes.size(); i++) {
            VNF v = nodes.get(i);
            sb.append(indent(2)).append("{\n");
            appendCommonNodeFields(sb, v, 3, true);
            appendScheduleFields(
                    sb,
                    v,
                    snapshot.scheduledEnv,
                    snapshot.scheduler,
                    snapshot.imageDLFromRepo,
                    snapshot.imageDLFromHost,
                    3,
                    false
            );
            sb.append(indent(2)).append("}");
            if (i < nodes.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent(1)).append("]\n");
        sb.append("}\n");

        writeText(out, sb.toString());
    }

    private static void appendCommonNodeFields(StringBuilder sb, VNF v, int level, boolean withTrailingComma) {
        long sfcId = getSfcId(v);
        long vnfId = getVnfId(v);
        appendJsonField(sb, level, "sfcId", String.valueOf(sfcId), false, true);
        appendJsonField(sb, level, "vnfId", String.valueOf(vnfId), false, true);
        appendJsonField(sb, level, "type", String.valueOf(v.getType()), false, true);
        appendJsonField(sb, level, "workload", String.valueOf(v.getWorkLoad()), false, true);
        appendJsonField(sb, level, "imageSize", String.valueOf(v.getImageSize()), false, true);
        appendJsonField(sb, level, "depth", String.valueOf(v.getDepth()), false, true);
        appendJsonField(sb, level, "predNum", String.valueOf(v.getDpredList().size()), false, true);
        appendJsonField(sb, level, "sucNum", String.valueOf(v.getDsucList().size()), false, withTrailingComma);
    }

    private static void appendScheduleFields(StringBuilder sb,
                                             VNF v,
                                             NFVEnvironment env,
                                             BaseVNFSchedulingAlgorithm scheduler,
                                             long imageDLFromRepoCount,
                                             long imageDLFromHostCount,
                                             int level,
                                             boolean last) {
        double st = v.getStartTime();
        double ft = v.getFinishTime();
        double dlSt = v.getDlStartTime();
        double dlFt = v.getDlFinishTime();

        double exec = -1.0d;
        if (st >= 0.0d && ft >= 0.0d) {
            exec = Math.max(0.0d, ft - st);
        }

        double dl = -1.0d;
        if (dlSt >= 0.0d && dlFt >= 0.0d) {
            dl = Math.max(0.0d, dlFt - dlSt);
        }

        boolean hasDownload = (dl >= EPS);
        String downloadSource = resolveDownloadSource(v, scheduler, hasDownload, imageDLFromRepoCount, imageDLFromHostCount);

        String vcpuId = v.getvCPUID();
        String vmId = null;
        String hostId = null;
        String hostPrefix = null;
        Long dcId = null;
        Long hostBw = null;
        Long dcBw = null;
        Long machineId = null;

        if (vcpuId != null && env != null) {
            VCPU vcpu = env.getGlobal_vcpuMap().get(vcpuId);
            if (vcpu != null) {
                vmId = vcpu.getVMID();
            }
            if (vmId != null && env.getGlobal_vmMap() != null) {
                VM vm = env.getGlobal_vmMap().get(vmId);
                if (vm != null) {
                    hostId = vm.getHostID();
                }
            }
            if (hostId != null && env.getGlobal_hostMap() != null) {
                ComputeHost host = env.getGlobal_hostMap().get(hostId);
                if (host != null) {
                    hostPrefix = host.getPrefix();
                    dcId = host.getDcID();
                    hostBw = host.getBw();
                    machineId = host.getMachineID();
                    if (dcId != null && env.getDcMap() != null && env.getDcMap().get(dcId) != null) {
                        dcBw = env.getDcMap().get(dcId).getBw();
                    }
                }
            }
        }

        appendJsonField(sb, level, "startTime", fmtDouble(st), false, true);
        appendJsonField(sb, level, "finishTime", fmtDouble(ft), false, true);
        appendJsonField(sb, level, "execTime", fmtDouble(exec), false, true);
        appendJsonField(sb, level, "dlStartTime", fmtDouble(dlSt), false, true);
        appendJsonField(sb, level, "dlFinishTime", fmtDouble(dlFt), false, true);
        appendJsonField(sb, level, "dlTime", fmtDouble(dl), false, true);
        appendJsonField(sb, level, "hasDownload", String.valueOf(hasDownload), false, true);
        appendJsonField(sb, level, "downloadSource", downloadSource, true);
        appendJsonField(sb, level, "vCpuId", nullToEmpty(vcpuId), true);
        appendJsonField(sb, level, "vmId", nullToEmpty(vmId), true);
        appendJsonField(sb, level, "hostId", nullToEmpty(hostId), true);
        appendJsonField(sb, level, "hostPrefix", nullToEmpty(hostPrefix), true);
        appendJsonField(sb, level, "hostBw", hostBw == null ? "-1" : String.valueOf(hostBw.longValue()), false, true);
        appendJsonField(sb, level, "dcBw", dcBw == null ? "-1" : String.valueOf(dcBw.longValue()), false, true);
        appendJsonField(sb, level, "machineId", machineId == null ? "-1" : String.valueOf(machineId.longValue()), false, true);
        appendJsonField(sb, level, "dcId", dcId == null ? "-1" : String.valueOf(dcId.longValue()), false, last);
    }

    private static String resolveDownloadSource(VNF v,
                                                BaseVNFSchedulingAlgorithm scheduler,
                                                boolean hasDownload,
                                                long imageDLFromRepoCount,
                                                long imageDLFromHostCount) {
        if (!hasDownload) {
            return "none";
        }

        if (scheduler != null) {
            String src = scheduler.getImageDownloadSourceForVNF(v);
            if (src != null) {
                String trimmed = src.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }

        // Conservative fallback when per-VNF source is unavailable.
        if (imageDLFromHostCount <= 0L && imageDLFromRepoCount > 0L) {
            return "repo";
        }
        if (imageDLFromRepoCount <= 0L && imageDLFromHostCount > 0L) {
            return "host";
        }
        return "unknown";
    }

    private static List<VNF> sortedVnfs(SFC sfc) {
        if (sfc == null || sfc.getVnfMap() == null) {
            return Collections.emptyList();
        }
        List<VNF> list = new ArrayList<VNF>(sfc.getVnfMap().values());
        Collections.sort(list, new Comparator<VNF>() {
            @Override
            public int compare(VNF o1, VNF o2) {
                long a = getVnfId(o1);
                long b = getVnfId(o2);
                if (a < b) {
                    return -1;
                }
                if (a > b) {
                    return 1;
                }
                return 0;
            }
        });
        return list;
    }

    private static void sortByExecution(List<VNF> list) {
        Collections.sort(list, new Comparator<VNF>() {
            @Override
            public int compare(VNF a, VNF b) {
                double sa = normalizeMissingTime(a.getStartTime());
                double sb = normalizeMissingTime(b.getStartTime());
                if (sa < sb) {
                    return -1;
                }
                if (sa > sb) {
                    return 1;
                }
                double fa = normalizeMissingTime(a.getFinishTime());
                double fb = normalizeMissingTime(b.getFinishTime());
                if (fa < fb) {
                    return -1;
                }
                if (fa > fb) {
                    return 1;
                }
                long ia = getVnfId(a);
                long ib = getVnfId(b);
                if (ia < ib) {
                    return -1;
                }
                if (ia > ib) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private static List<EdgeInfo> collectEdges(List<VNF> nodes) {
        List<EdgeInfo> edges = new ArrayList<EdgeInfo>();
        Iterator<VNF> vIte = nodes.iterator();
        while (vIte.hasNext()) {
            VNF v = vIte.next();
            if (v == null || v.getDsucList() == null) {
                continue;
            }
            Iterator<DataDependence> dsucIte = v.getDsucList().iterator();
            while (dsucIte.hasNext()) {
                DataDependence dd = dsucIte.next();
                if (dd == null || dd.getFromID() == null || dd.getToID() == null) {
                    continue;
                }
                EdgeInfo e = new EdgeInfo();
                Vector<Long> from = dd.getFromID();
                Vector<Long> to = dd.getToID();
                e.fromSfcId = from.size() > 0 ? from.get(0).longValue() : -1L;
                e.fromVnfId = from.size() > 1 ? from.get(1).longValue() : -1L;
                e.toSfcId = to.size() > 0 ? to.get(0).longValue() : -1L;
                e.toVnfId = to.size() > 1 ? to.get(1).longValue() : -1L;
                e.dataSize = dd.getMaxDataSize();
                edges.add(e);
            }
        }

        Collections.sort(edges, new Comparator<EdgeInfo>() {
            @Override
            public int compare(EdgeInfo a, EdgeInfo b) {
                if (a.fromVnfId < b.fromVnfId) {
                    return -1;
                }
                if (a.fromVnfId > b.fromVnfId) {
                    return 1;
                }
                if (a.toVnfId < b.toVnfId) {
                    return -1;
                }
                if (a.toVnfId > b.toVnfId) {
                    return 1;
                }
                return 0;
            }
        });

        return edges;
    }

    private static long getSfcId(VNF vnf) {
        if (vnf == null || vnf.getIDVector() == null || vnf.getIDVector().isEmpty()) {
            return -1L;
        }
        return vnf.getIDVector().get(0).longValue();
    }

    private static long getVnfId(VNF vnf) {
        if (vnf == null || vnf.getIDVector() == null || vnf.getIDVector().size() < 2) {
            return -1L;
        }
        return vnf.getIDVector().get(1).longValue();
    }

    private static double normalizeMissingTime(double v) {
        if (v < 0.0d) {
            return Double.MAX_VALUE;
        }
        return v;
    }

    private static String fmtDouble(double v) {
        if (v < 0.0d) {
            return "-1";
        }
        return String.valueOf(NFVUtil.getRoundedValue(v));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void writeText(Path out, String text) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write(text);
        }
    }

    private static void appendJsonField(StringBuilder sb,
                                        int indentLevel,
                                        String key,
                                        String value,
                                        boolean isString) {
        appendJsonField(sb, indentLevel, key, value, isString, true);
    }

    private static void appendJsonField(StringBuilder sb,
                                        int indentLevel,
                                        String key,
                                        String value,
                                        boolean isString,
                                        boolean trailingComma) {
        sb.append(indent(indentLevel)).append("\"").append(escapeJson(key)).append("\": ");
        if (isString) {
            sb.append("\"").append(escapeJson(value)).append("\"");
        } else {
            sb.append(value);
        }
        if (trailingComma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private static String escapeJson(String in) {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
