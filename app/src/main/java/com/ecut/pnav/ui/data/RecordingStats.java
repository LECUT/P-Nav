package com.ecut.pnav.ui.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集文件累积统计，反映已写入缓冲的历元数据内容。
 */
public class RecordingStats {

    /** 单频段统计 */
    public static class BandStats {
        public String code;      // "L1", "B1I", etc.
        public int satCount;     // 该频段卫星数
        public int avgCn0;       // 平均载噪比 dB-Hz

        public BandStats(String code, int satCount, int avgCn0) {
            this.code = code;
            this.satCount = satCount;
            this.avgCn0 = avgCn0;
        }
    }

    /** 单系统统计 */
    public static class SysStats {
        public String name;          // "GPS", "BDS", "GLO", "GAL"
        public int satCount;         // 本历元该系统的有效卫星数
        public int colorRes;         // 文字颜色 resource id
        public final List<BandStats> bands = new ArrayList<>();  // 各频段详情

        public SysStats(String name, int satCount, int colorRes) {
            this.name = name;
            this.satCount = satCount;
            this.colorRes = colorRes;
        }
    }

    /** 各系统统计（GPS/BDS/GLO/GAL） */
    public final List<SysStats> sysList = new ArrayList<>();

    /** 累计历元数 */
    public int epochCount;

    /** 当前采集文件名 */
    public String fileName;

    /** 文件所在目录 */
    public String fileDir;

    /** 文件大小（字节） */
    public long fileSize;

    /** 录制时长（秒） */
    public int durationSeconds;

    /** 已有载波相位数据 */
    public boolean hasCarrierPhase;
}
