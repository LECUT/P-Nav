package com.ecut.pnav.ui.data;

import static com.ecut.pnav.ui.measure_util.resetobs;
import static com.rtklib.readobsnav.satSys.sat_sys;
import static com.rtklib.util.util1.time2epoch;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssStatus;
import android.os.Build;
import android.util.Log;

import com.ecut.pnav.ui.measure_util;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.obsd_t;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * RINEX 3.04 观测文件后处理写入器。
 *
 * 工作流程（参照 Calgary 大学 GNSSLOG2RINEX.cpp）：
 *   录制中 — bufferEpoch() 暂存历元原始数据
 *   停止后 — writeOutput() 一次性完成：信号发现 → 写头部 → 写全部历元
 *
 * 质量门限（RINEX 3.04）：
 *   - checkSyncState（CODE_LOCK + TOW_DECODED + !MSEC_AMBIGUOUS）
 *   - TOW 不确定度 ≤ 500 ns（Android 12+）
 *   - 伪距率不确定度 ≤ 10 m/s（Android 12+）
 */
public class Rinex304Writer {

    private static final String TAG = "Rinex304Writer";
    private static final double MAX_TOW_UNC_NS = 500.0;
    private static final double MAX_PRR_UNC_MPS = 10.0;

    private static final char[] SYS_ORDER = {'G', 'R', 'E', 'J', 'C'};

    /**
     * 各星座所有可能频点的观测码（与 measure_util.readobs() 槽位顺序严格一致）
     * GPS: slot0=L1(1C)  slot1=L2(2W)  slot2=L5(5Q)
     * GLO: slot0=G1(1C)  slot1=G2(2C)
     * GAL: slot0=E1(1C)  slot1=E5b(7Q) slot2=E5a(5Q)
     * QZS: slot0=L1(1C)  slot1=L2(2X)  slot2=L5(5Q)
     * BDS: slot0=B1I(2I) slot1=B1C(1P) slot2=B2b(7Z) slot3=B2a(5P)
     *
     * ⚠ BDS B2a 用 5P 而非 5Q：RINEX 3.04 中 B2a 的合法跟踪码只有 5D/5P/5X
     * （见 RTKLIB codepris 表 BDS 行 L5/E5a/B2a 列 "DPX"），5Q 是 GPS L5 /
     * Galileo E5a 的码。参考项目 Gnsslogger2RINEX.cpp 与 GNSSLOG2RINEX.cpp
     * 的 BDS B2a 分支均误用 "L5Q"，此处不作沿用。
     */
    static final String[][] ALL_OBS_CODES = {
        {"1C", "2W", "5Q"},
        {"1C", "2C"},
        {"1C", "7Q", "5Q"},
        {"1C", "2X", "5Q"},
        {"2I", "1P", "7Z", "5P"}
    };

    private static int sysIndex(char sys) {
        switch (sys) {
            case 'G': return 0;
            case 'R': return 1;
            case 'E': return 2;
            case 'J': return 3;
            case 'C': return 4;
            default:  return -1;
        }
    }

    static char satToSys(int sat) {
        if (sat >= 1   && sat <= 32)  return 'G';
        if (sat >= 33  && sat <= 59)  return 'R';
        if (sat >= 60  && sat <= 95)  return 'E';
        if (sat >= 96  && sat <= 105) return 'J';
        if (sat >= 106 && sat <= 168) return 'C';
        return '?';
    }

    // === 实例字段 ===

    private final obsd_t[] obs;
    private final List<CachedEpoch> epochBuffer = new ArrayList<>();
    private final Map<Character, boolean[]> activeSlots = new LinkedHashMap<>();
    private boolean supportCP = false;

    // === 头部所需的附加信息 ===

    /**
     * Android 原始测量的标准载波相位偏移 (cycles)。
     *
     * 来源：Geo++ RINEX Logger（Android 官方 RINEX 记录器）的 SYS / PHASE SHIFT 输出。
     * 偏移源于 Android getAccumulatedDeltaRangeMeters() 在各频点上的实现差异，
     * 与机型/芯片无关。RTKLIB 自身不解析该字段，但 Bernese / GAMIT 等高精度软件会用；
     * 漏写会导致 L5 / E1 出现 1/4 或 1/2 周的相位错位。
     *
     * 键为「系统字母 + 观测码」，未列出的频点偏移为 0。
     */
    private static final Map<String, Double> PHASE_SHIFT = new java.util.HashMap<>();
    static {
        PHASE_SHIFT.put("G L5Q", -0.25);
        PHASE_SHIFT.put("E L1C", +0.50);
        PHASE_SHIFT.put("E L5Q", -0.25);
        PHASE_SHIFT.put("J L5Q", -0.25);
    }

    /** 测站类型，按 Geo++ 惯例写 GEODETIC */
    private static final String MARKER_TYPE = "GEODETIC";

    /** 未标定的接收机/天线编号，按行业惯例写 NA（留空会被误认为漏填） */
    private static final String NA = "NA";

    /** 概略位置 (ECEF, m)。未设置时头部写 0 0 0，会导致 RTKLIB 报 "no position in rinex header" */
    private double[] approxPos = {0.0, 0.0, 0.0};

    /** 测站/接收机标识，写入 MARKER NAME */
    private String markerName = "PNAV";

    /** 首历元时刻，用于 TIME OF FIRST OBS */
    private gtime_t firstObsTime = null;

    public Rinex304Writer(obsd_t[] obs) {
        this.obs = obs;
        for (char sys : SYS_ORDER) {
            int idx = sysIndex(sys);
            activeSlots.put(sys, new boolean[ALL_OBS_CODES[idx].length]);
        }
    }

    /** 设置概略位置 (ECEF, m)。建议用首个定位结果的 ECEF 坐标。 */
    public void setApproxPosition(double x, double y, double z) {
        this.approxPos = new double[]{x, y, z};
    }

    /** 设置测站标识 (写入 MARKER NAME) */
    public void setMarkerName(String name) {
        if (name != null && !name.trim().isEmpty()) this.markerName = name.trim();
    }

    // === 公共接口 ===

    /** 录制期间：暂存一个历元的原始测量数据 */
    public void bufferEpoch(List<GnssMeasurement> measurements, GnssClock clock) {
        epochBuffer.add(new CachedEpoch(new ArrayList<>(measurements), clock));

        if (!supportCP) {
            for (GnssMeasurement m : measurements) {
                if ((m.getAccumulatedDeltaRangeState()
                        & GnssMeasurement.ADR_STATE_VALID) != 0) {
                    supportCP = true;
                    break;
                }
            }
        }
    }

    public int getBufferedEpochCount() {
        return epochBuffer.size();
    }

    /**
     * 停止录制后调用：信号发现 → 写头部 → 写全部历元。
     *
     * @param bw           输出流
     * @param deviceModel  设备型号
     * @param filter       星座过滤 [GPS, BDS, GLO, GAL]，null=全部通过
     * @param append       true=追加模式（头部已存在，仅追加历元数据）
     */
    public void writeOutput(BufferedWriter bw, String deviceModel,
                            boolean[] filter, boolean append) throws IOException {
        if (epochBuffer.isEmpty()) {
            Log.w(TAG, "无缓存历元，跳过输出");
            return;
        }

        if (!append) {
            // ====== 第一遍：信号发现 ======
            for (CachedEpoch ce : epochBuffer) {
                processMeasurements(ce, filter);
                scanActiveSlots();
                captureEpochTime();      // 记录首末历元时刻, 供 TIME OF FIRST/LAST OBS 使用
                resetobs(obs);
            }
            Log.d(TAG, "信号发现完成: " + getActiveSlotsSummary());
            writeHeader(bw, deviceModel);
        }

        // ====== 第二遍：格式化输出 ======
        int written = 0;
        for (CachedEpoch ce : epochBuffer) {
            processMeasurements(ce, filter);
            String epochStr = formatEpoch();
            if (!epochStr.isEmpty()) {
                bw.write(epochStr);
                written++;
            }
            resetobs(obs);
        }
        bw.flush();
        Log.d(TAG, "后处理完成: " + written + "/" + epochBuffer.size() + " 个历元已写入");
        epochBuffer.clear();
    }

    public boolean isSupportCP() {
        return supportCP;
    }

    // === 头部输出 ===

    /** 生成标准 80 字符 RINEX 头行：内容占 1-60 列，标签占 61-80 列 */
    private static String hdrLine(String content, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append(content);
        while (sb.length() < 60) sb.append(' ');
        if (sb.length() > 60) sb.setLength(60);      // 内容超长则截断，绝不侵占标签区
        sb.append(String.format(Locale.US, "%-20s", label));
        return sb.toString();
    }

    private void writeHeader(BufferedWriter bw, String deviceModel) throws IOException {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmmss", Locale.US);

        // ===== 表头（对照 Geo++ RINEX Logger 样式）=====
        // 设计原则：
        //   1. 只保留有实际内容的行，不写空占位行
        //      （Geo++ 无 MARKER NUMBER / OBSERVER / INTERVAL / TIME OF LAST OBS）
        //   2. 未标定的字段写 NA，不写空（空会被误认为漏填）
        //   3. MARKER TYPE 按惯例写 GEODETIC
        //   4. 相位偏移逐频点输出，零值留空

        // --- RINEX VERSION / TYPE (M) ---
        bw.write(hdrLine(String.format(Locale.US,
                "     %4.2f           OBSERVATION DATA    M: Mixed", 3.04),
                "RINEX VERSION / TYPE"));
        bw.newLine();

        // --- PGM / RUN BY / DATE (R) ---
        bw.write(hdrLine(String.format(Locale.US, "%-20s%-20s%s",
                "PNav Logger", "PNav", sdf.format(now) + " UTC"),
                "PGM / RUN BY / DATE"));
        bw.newLine();

        // --- MARKER NAME (M) / MARKER TYPE (M) ---
        bw.write(hdrLine(markerName, "MARKER NAME"));
        bw.newLine();
        bw.write(hdrLine(MARKER_TYPE, "MARKER TYPE"));
        bw.newLine();

        // --- REC # / TYPE / VERS (R)：接收机编号未知用 NA ---
        bw.write(hdrLine(String.format(Locale.US, "%-20s%-20s%-20s",
                NA, deviceModel == null ? NA : deviceModel, "1.0"),
                "REC # / TYPE / VERS"));
        bw.newLine();

        // --- ANT # / TYPE (R)：手机天线无型号，用 NA ---
        bw.write(hdrLine(String.format(Locale.US, "%-20s%-20s", NA, NA),
                "ANT # / TYPE"));
        bw.newLine();

        // --- APPROX POSITION XYZ / ANTENNA: DELTA H/E/N (M) ---
        bw.write(hdrLine(String.format(Locale.US, "%14.4f%14.4f%14.4f",
                approxPos[0], approxPos[1], approxPos[2]), "APPROX POSITION XYZ"));
        bw.newLine();
        bw.write(hdrLine(String.format(Locale.US, "%14.4f%14.4f%14.4f",
                0.0, 0.0, 0.0), "ANTENNA: DELTA H/E/N"));
        bw.newLine();

        // --- SYS / # / OBS TYPES (M) ---
        for (char sys : SYS_ORDER) {
            String[] codes = getActiveObsCodes(sys);
            if (codes.length > 0) {
                bw.write(measure_util.buildObsTypesLine(sys, codes, supportCP));
                bw.newLine();
            }
        }

        // --- TIME OF FIRST OBS (M) ---
        if (firstObsTime != null) {
            bw.write(hdrLine(epochTimeField(firstObsTime), "TIME OF FIRST OBS"));
            bw.newLine();
        }

        // --- SYS / PHASE SHIFT (C)：逐频点输出，零偏移留空 ---
        for (char sys : SYS_ORDER) {
            String[] codes = getActiveObsCodes(sys);
            for (String code : codes) {
                if (!code.startsWith("L")) continue;      // 只处理载波相位
                Double v = PHASE_SHIFT.get(sys + " " + code);
                String field = (v == null || v == 0.0)
                        ? "" : String.format(Locale.US, "%+8.5f", v);
                bw.write(hdrLine(String.format(Locale.US, "%c %3s %s", sys, code, field),
                        "SYS / PHASE SHIFT"));
                bw.newLine();
            }
        }

        // --- GLONASS SLOT / FRQ # + COD/PHS/BIS (C)：含 GLONASS 时必填 ---
        if (hasActiveSystem('R')) {
            writeGloSlotLines(bw);
            bw.write(hdrLine(" C1C    0.000 C1P    0.000 C2C    0.000 C2P    0.000",
                    "GLONASS COD/PHS/BIS"));
            bw.newLine();
        }

        bw.write(hdrLine("", "END OF HEADER"));
        bw.newLine();
        bw.flush();
        Log.d(TAG, "RINEX 3.04 头部已写入（Geo++ 样式）");
    }

    /** 生成 TIME OF FIRST/LAST OBS 的 60 列内容区 (与 RTKLIB time2epoch 输出一致) */
    private static String epochTimeField(gtime_t t) {
        double[] ep = new double[6];
        time2epoch(t, ep);
        return String.format(Locale.US, "  %4d    %2d    %2d    %2d    %2d   %10.7f     GPS",
                (int) ep[0], (int) ep[1], (int) ep[2],
                (int) ep[3], (int) ep[4], ep[5]);
    }

    /**
     * 输出 GLONASS SLOT / FRQ #。每行最多 8 组，每组 7 字符 " R%02d %2d"，
     * 首行前 3 字符为卫星总数。格式取自 RTKLIB outrnx_glo_fcn()。
     */
    private void writeGloSlotLines(BufferedWriter bw) throws IOException {
        TreeMap<Integer, Integer> slots = collectGloSlots();
        int n = slots.size();
        if (n == 0) {
            // 无可用槽位信息时也要给出合法的空行（数量 0）
            bw.write(hdrLine("  0", "GLONASS SLOT / FRQ #"));
            bw.newLine();
            return;
        }
        List<Integer> prns = new ArrayList<>(slots.keySet());
        for (int i = 0, j = 0; i < (n - 1) / 8 + 1; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(i == 0 ? String.format(Locale.US, "%3d", n) : "   ");
            int k = 0;
            for (; k < 8 && j < n; k++, j++) {
                sb.append(String.format(Locale.US, " R%02d %2d", prns.get(j), slots.get(prns.get(j))));
            }
            // 补齐到 60 列
            while (sb.length() < 60) sb.append(' ');
            bw.write(hdrLine(sb.toString(), "GLONASS SLOT / FRQ #"));
            bw.newLine();
        }
    }

    /**
     * 从缓存的原始测量中提取 GLONASS 卫星槽位(即 Android SVID)与频率通道号。
     * RINEX 的 GLONASS PRN 就是槽位号 1-24；通道号 k 由 G1 频率反推：
     *   f = 1602 MHz + k * 562.5 kHz   →   k = round((f - 1602e6) / 562.5e3)
     */
    private TreeMap<Integer, Integer> collectGloSlots() {
        TreeMap<Integer, Integer> map = new TreeMap<>();
        for (CachedEpoch ce : epochBuffer) {
            for (GnssMeasurement m : ce.measurements) {
                if (m.getConstellationType() != GnssStatus.CONSTELLATION_GLONASS) continue;
                int prn = m.getSvid();
                if (prn < 1 || prn > 24) continue;
                if (!m.hasCarrierFrequencyHz()) continue;
                int ch = freqToGloChannel(m.getCarrierFrequencyHz());
                if (ch == Integer.MIN_VALUE) continue;
                map.put(prn, ch);        // 同一卫星多次出现取最后值
            }
        }
        return map;
    }

    /** G1 频率 -> GLONASS 频率通道号 (-7..6)；非 G1 频段返回 Integer.MIN_VALUE */
    private static int freqToGloChannel(double fHz) {
        if (fHz <= 0) return Integer.MIN_VALUE;
        if (fHz > 1585e6 && fHz < 1615e6) {       // G1 频段 1598.0625~1605.375 MHz
            int k = (int) Math.round((fHz - 1602.0e6) / 562.5e3);
            if (k >= -7 && k <= 6) return k;
        }
        return Integer.MIN_VALUE;
    }

    /** 记录首历元时刻（用于 TIME OF FIRST OBS） */
    private void captureEpochTime() {
        if (firstObsTime != null) return;          // 只需首历元
        for (obsd_t ob : obs) {
            if (ob == null || ob.sat[0] == 0) continue;
            if (ob.time == null || ob.time.time == 0.0) continue;   // gtime_t 初值为 0
            firstObsTime = ob.time;
            return;
        }
    }

    private boolean hasActiveSystem(char sys) {
        return getActiveObsCodes(sys).length > 0;
    }

    // === 信号发现 ===

    private String[] getActiveObsCodes(char sys) {
        int idx = sysIndex(sys);
        if (idx < 0) return new String[0];
        boolean[] slots = activeSlots.get(sys);
        if (slots == null) return new String[0];
        List<String> active = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i]) active.add(ALL_OBS_CODES[idx][i]);
        }
        return active.toArray(new String[0]);
    }

    private void scanActiveSlots() {
        for (obsd_t ob : obs) {
            if (ob == null || ob.sat[0] == 0) continue;
            char sys = satToSys(ob.sat[0]);
            if (sys == '?') continue;
            boolean[] slots = activeSlots.get(sys);
            if (slots == null) continue;
            for (int i = 0; i < slots.length && i < ob.code.length; i++) {
                if (ob.code[i] != 0) slots[i] = true;
            }
        }
    }

    private String getActiveSlotsSummary() {
        StringBuilder sb = new StringBuilder();
        for (char sys : SYS_ORDER) {
            String[] codes = getActiveObsCodes(sys);
            if (codes.length > 0) sb.append(sys).append(":").append(Arrays.toString(codes)).append(" ");
        }
        return sb.toString();
    }

    // === 历元格式化 ===

    private void processMeasurements(CachedEpoch ce, boolean[] filter) {
        measure_util.prepareEpochLargestTows(ce.measurements);
        for (GnssMeasurement m : ce.measurements) {
            if (!passesQualityFilter(m)) continue;
            if (!isConstellationEnabled(m.getConstellationType(), filter)) continue;
            try {
                measure_util.readobs(m, ce.clock, obs);
            } catch (Exception e) {
                Log.e(TAG, "readobs failed svid=" + m.getSvid(), e);
            }
        }
    }

    private String formatEpoch() {
        int numSat = 0;
        gtime_t firstObsTime = null;
        for (obsd_t ob : obs) {
            if (ob != null && ob.sat[0] != 0) {
                numSat++;
                if (firstObsTime == null) firstObsTime = ob.time;
            }
        }
        if (firstObsTime == null || numSat == 0) return "";

        StringBuilder sb = new StringBuilder();
        double[] ep = new double[6];
        time2epoch(firstObsTime, ep);
        sb.append(String.format(Locale.US, "> %4d %2d %2d %2d %2d%11.7f  0 %2d\n",
                (int) ep[0], (int) ep[1], (int) ep[2],
                (int) ep[3], (int) ep[4], ep[5], numSat));

        for (obsd_t ob : obs) {
            if (ob == null || ob.sat[0] == 0) continue;
            int sat = ob.sat[0];
            int[] prn = new int[1];
            try {
                sat_sys(sat, prn);
            } catch (Exception e) {
                continue;
            }
            char sys = satToSys(sat);
            StringBuilder line = new StringBuilder(
                    String.format(Locale.US, "%c%02d", sys, prn[0]));
            appendObsSlots(line, ob, sys);
            line.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private void appendObsSlots(StringBuilder line, obsd_t ob, char sys) {
        boolean[] slots = activeSlots.get(sys);
        if (slots == null) return;
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i]) continue;
            boolean valid = (i < ob.code.length && ob.code[i] != 0);
            if (valid) {
                line.append(fmtObs(ob.P[i]));
                if (supportCP) {
                    if (Math.abs(ob.L[i]) > 1e-9)
                        line.append(fmtObs(ob.L[i]));
                    else
                        line.append(BLANK_FIELD);
                }
                line.append(fmtObs(ob.D[i]));
                line.append(fmtObs(ob.SNR[i]));
            } else {
                int fields = supportCP ? 4 : 3;
                for (int f = 0; f < fields; f++) line.append(BLANK_FIELD);
            }
        }
    }

    /** 单个观测值占 16 字符 = F14.3 数值(14) + LLI(1) + 信号强度(1)，后两位留空 */
    private static final String BLANK_FIELD = "                "; // 16 空格

    /**
     * 严格 RINEX 3.04 观测值格式化：每字段 16 字符。
     *
     * 规范要求 F14.3 + I1(LLI) + I1(信号强度)；RTKLIB 解析时以固定 16 字符步长推进
     * （见 rinex.c decode_obsdata: for (i=0,j=3; ...; i++, j+=16)）。
     * 原实现只产出 14 字符，会逐字段累积错位，从第 7 个观测量起溢出读取窗口。
     */
    private static String fmtObs(double val) {
        String s = String.format(Locale.US, "%14.3f", val);
        if (s.length() > 14) {
            // 超宽时回绕模 10^10（原逻辑保留）
            double frac = val - Math.floor(val);
            long ip = (long) Math.floor(val);
            long wrapped = ip % 10_000_000_000L;
            if (wrapped < 0) wrapped += 10_000_000_000L;
            s = String.format(Locale.US, "%14.3f", (double) wrapped + frac);
        }
        return s + "  ";   // LLI 与信号强度两个字符位留空
    }

    // === 质量过滤 ===

    private boolean passesQualityFilter(GnssMeasurement m) {
        if (!measure_util.checkSyncState(m, false)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            double towUnc = m.getReceivedSvTimeUncertaintyNanos();
            if (towUnc > 0 && towUnc > MAX_TOW_UNC_NS) return false;
            double prrUnc = m.getPseudorangeRateUncertaintyMetersPerSecond();
            if (prrUnc > 0 && prrUnc > MAX_PRR_UNC_MPS) return false;
        }
        return true;
    }

    private static boolean isConstellationEnabled(int constellationType, boolean[] filter) {
        if (filter == null) return true;
        int idx;
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:      idx = 0; break;
            case GnssStatus.CONSTELLATION_BEIDOU:    idx = 1; break;
            case GnssStatus.CONSTELLATION_GLONASS:   idx = 2; break;
            case GnssStatus.CONSTELLATION_GALILEO:   idx = 3; break;
            default: return true;
        }
        return idx < filter.length && filter[idx];
    }

    // === 内部类 ===

    private static class CachedEpoch {
        final List<GnssMeasurement> measurements;
        final GnssClock clock;

        CachedEpoch(List<GnssMeasurement> measurements, GnssClock clock) {
            this.measurements = measurements;
            this.clock = clock;
        }
    }
}
