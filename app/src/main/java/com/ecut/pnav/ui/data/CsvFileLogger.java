package com.ecut.pnav.ui.data;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GnssLogger-style CSV/TXT logger — writes Raw / Fix / NMEA / Nav / Status lines to a .txt file.
 * 表头与列顺序对齐 Google GnssLogger v3.0.3.1，以便 GnssAnalysis App 能直接读取。
 * Runs in parallel with the RINEX observation file writer in DataViewModel.
 */
public class CsvFileLogger {

    private static final String TAG = "CsvFileLogger";
    private static final String FILE_PREFIX = "gnss_log";
    private static final String COMMENT_PREFIX = "# ";
    private static final char DELIM = ',';

    /**
     * 声称的 GnssLogger 版本号——本 logger 的列布局与官方 v3.0.3.1 导出对齐。
     * GnssAnalysis App 要求日志版本 >= v1.4.0.0，否则报
     * "This version of GnssAnalysis supports Gnss log files v1.4.0.0 onwards"。
     */
    private static final String GNSSLOGGER_COMPAT_VERSION = "v3.0.3.1";

    /**
     * 列定义行必须顶格（只留 "# " 前缀）。GnssAnalysis 按 "# Raw," 前缀精确匹配，
     * 多一个空格就匹配不到，报 "No raw measurements read from this file"。
     */
    private static final String RAW_HEADER =
            "Raw,utcTimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,"
                    + "BiasNanos,BiasUncertaintyNanos,DriftNanosPerSecond,"
                    + "DriftUncertaintyNanosPerSecond,HardwareClockDiscontinuityCount,"
                    + "Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos,"
                    + "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond,"
                    + "PseudorangeRateUncertaintyMetersPerSecond,AccumulatedDeltaRangeState,"
                    + "AccumulatedDeltaRangeMeters,AccumulatedDeltaRangeUncertaintyMeters,"
                    + "CarrierFrequencyHz,CarrierCycles,CarrierPhase,CarrierPhaseUncertainty,"
                    + "MultipathIndicator,SnrInDb,ConstellationType,AgcDb,BasebandCn0DbHz,"
                    + "FullInterSignalBiasNanos,FullInterSignalBiasUncertaintyNanos,"
                    + "SatelliteInterSignalBiasNanos,SatelliteInterSignalBiasUncertaintyNanos,"
                    + "CodeType,ChipsetElapsedRealtimeNanos";

    private static final String FIX_HEADER =
            "Fix,Provider,LatitudeDegrees,LongitudeDegrees,AltitudeMeters,"
                    + "SpeedMps,AccuracyMeters,BearingDegrees,UnixTimeMillis,"
                    + "SpeedAccuracyMps,BearingAccuracyDegrees,elapsedRealtimeNanos,"
                    + "VerticalAccuracyMeters";

    private static final String NAV_HEADER =
            "Nav,Svid,Type,Status,MessageId,Sub-messageId,Data(Bytes)";

    private static final String STATUS_HEADER =
            "Status,UnixTimeMillis,SignalCount,SignalIndex,ConstellationType,"
                    + "Svid,CarrierFrequencyHz,Cn0DbHz,AzimuthDegrees,ElevationDegrees,"
                    + "UsedInFix,HasAlmanacData,HasEphemerisData,BasebandCn0DbHz";

    private BufferedWriter writer;
    private File file;

    public CsvFileLogger() {}

    /** Returns the underlying file, or null if not started. */
    public File getFile() { return file; }

    /**
     * Opens a new CSV log file in the given folder.
     * @return true on success
     */
    public boolean startNewFile(File folder, String deviceModel) {
        if (writer != null) close();

        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(new Date());
        file = new File(folder, FILE_PREFIX + "_" + timestamp + ".txt");

        try {
            writer = new BufferedWriter(new FileWriter(file, false));
            writeHeader(deviceModel);
            Log.d(TAG, "Started CSV log: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to create CSV file", e);
            file = null;
            return false;
        }
    }

    /** Appends to an existing file (for append-mode recording). */
    public boolean openExisting(File existingFile, String deviceModel) {
        if (writer != null) close();
        file = existingFile;
        try {
            writer = new BufferedWriter(new FileWriter(existingFile, true));
            Log.d(TAG, "Opened existing CSV log: " + existingFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to open existing CSV file", e);
            file = null;
            return false;
        }
    }

    public synchronized void close() {
        if (writer != null) {
            try { writer.flush(); writer.close(); } catch (IOException ignored) {}
            writer = null;
        }
    }

    // ==================== Header ====================

    private void writeHeader(String deviceModel) throws IOException {
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + "Header Description:");
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        // 官方格式用空格分隔，且版本号必须是可解析的 v<数字>
        writer.write(COMMENT_PREFIX + "Version: " + GNSSLOGGER_COMPAT_VERSION
                + " Platform: " + Build.VERSION.RELEASE
                + " Manufacturer: " + Build.MANUFACTURER
                + " Model: " + deviceModel);
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + RAW_HEADER);
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + FIX_HEADER);
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + "NMEA,[NMEA sentence],(UTC)TimeInMs");
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + NAV_HEADER);
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.write(COMMENT_PREFIX + STATUS_HEADER);
        writer.newLine();
        writer.write(COMMENT_PREFIX);
        writer.newLine();
        writer.flush();
    }

    // ==================== Raw GNSS Measurement ====================

    public synchronized void writeMeasurement(GnssMeasurementsEvent event) {
        if (writer == null) return;
        GnssClock clock = event.getClock();
        // 芯片组 elapsedRealtime 的纳秒值，写在行尾（对应官方 ChipsetElapsedRealtimeNanos 列）。
        // 原先它紧跟在 utcTimeMillis 之后、但头部没为它声明列名，导致其后所有字段错位一位。
        long elapsedNs = SystemClock.elapsedRealtimeNanos();

        for (GnssMeasurement m : event.getMeasurements()) {
            try {
                StringBuilder sb = new StringBuilder("Raw");
                sb.append(DELIM).append(System.currentTimeMillis());
                sb.append(DELIM).append(clock.getTimeNanos());
                sb.append(DELIM).append(clock.hasLeapSecond() ? clock.getLeapSecond() : "");
                sb.append(DELIM).append(clock.hasTimeUncertaintyNanos() ? clock.getTimeUncertaintyNanos() : "");
                sb.append(DELIM).append(clock.getFullBiasNanos());
                sb.append(DELIM).append(clock.hasBiasNanos() ? clock.getBiasNanos() : "");
                sb.append(DELIM).append(clock.hasBiasUncertaintyNanos() ? clock.getBiasUncertaintyNanos() : "");
                sb.append(DELIM).append(clock.hasDriftNanosPerSecond() ? clock.getDriftNanosPerSecond() : "");
                sb.append(DELIM).append(clock.hasDriftUncertaintyNanosPerSecond() ? clock.getDriftUncertaintyNanosPerSecond() : "");
                sb.append(DELIM).append(clock.getHardwareClockDiscontinuityCount());

                sb.append(DELIM).append(m.getSvid());
                sb.append(DELIM).append(m.getTimeOffsetNanos());
                sb.append(DELIM).append(m.getState());
                sb.append(DELIM).append(m.getReceivedSvTimeNanos());
                sb.append(DELIM).append(m.getReceivedSvTimeUncertaintyNanos());
                sb.append(DELIM).append(m.getCn0DbHz());
                sb.append(DELIM).append(m.getPseudorangeRateMetersPerSecond());
                sb.append(DELIM).append(m.getPseudorangeRateUncertaintyMetersPerSecond());
                sb.append(DELIM).append(m.getAccumulatedDeltaRangeState());
                sb.append(DELIM).append(m.getAccumulatedDeltaRangeMeters());
                sb.append(DELIM).append(m.getAccumulatedDeltaRangeUncertaintyMeters());
                sb.append(DELIM).append(m.hasCarrierFrequencyHz() ? m.getCarrierFrequencyHz() : "");
                sb.append(DELIM).append(m.hasCarrierCycles() ? m.getCarrierCycles() : "");
                sb.append(DELIM).append(m.hasCarrierPhase() ? m.getCarrierPhase() : "");
                sb.append(DELIM).append(m.hasCarrierPhaseUncertainty() ? m.getCarrierPhaseUncertainty() : "");
                sb.append(DELIM).append(m.getMultipathIndicator());
                sb.append(DELIM).append(m.hasSnrInDb() ? m.getSnrInDb() : "");
                sb.append(DELIM).append(m.getConstellationType());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sb.append(DELIM).append(m.hasAutomaticGainControlLevelDb() ? m.getAutomaticGainControlLevelDb() : "");
                } else {
                    sb.append(DELIM);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sb.append(DELIM).append(m.hasBasebandCn0DbHz() ? m.getBasebandCn0DbHz() : "");
                } else {
                    sb.append(DELIM);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sb.append(DELIM).append(m.hasFullInterSignalBiasNanos() ? m.getFullInterSignalBiasNanos() : "");
                    sb.append(DELIM).append(m.hasFullInterSignalBiasUncertaintyNanos() ? m.getFullInterSignalBiasUncertaintyNanos() : "");
                    sb.append(DELIM).append(m.hasSatelliteInterSignalBiasNanos() ? m.getSatelliteInterSignalBiasNanos() : "");
                    sb.append(DELIM).append(m.hasSatelliteInterSignalBiasUncertaintyNanos() ? m.getSatelliteInterSignalBiasUncertaintyNanos() : "");
                } else {
                    sb.append(DELIM).append(DELIM).append(DELIM).append(DELIM);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    sb.append(DELIM).append(m.getCodeType());
                } else {
                    sb.append(DELIM);
                }
                sb.append(DELIM).append(elapsedNs);

                writer.write(sb.toString());
                writer.newLine();
            } catch (IOException e) {
                Log.e(TAG, "Error writing measurement", e);
            }
        }
    }

    // ==================== Location Fix ====================

    public synchronized void writeLocation(Location location) {
        if (writer == null) return;
        try {
            StringBuilder sb = new StringBuilder("Fix");
            sb.append(DELIM).append(location.getProvider());
            sb.append(DELIM).append(location.getLatitude());
            sb.append(DELIM).append(location.getLongitude());
            sb.append(DELIM).append(location.hasAltitude() ? location.getAltitude() : "");
            sb.append(DELIM).append(location.hasSpeed() ? location.getSpeed() : "");
            sb.append(DELIM).append(location.hasAccuracy() ? location.getAccuracy() : "");
            sb.append(DELIM).append(location.hasBearing() ? location.getBearing() : "");
            sb.append(DELIM).append(location.getTime());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append(DELIM).append(location.hasSpeedAccuracy() ? location.getSpeedAccuracyMetersPerSecond() : "");
            } else {
                sb.append(DELIM);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append(DELIM).append(location.hasBearingAccuracy() ? location.getBearingAccuracyDegrees() : "");
            } else {
                sb.append(DELIM);
            }
            sb.append(DELIM).append(location.getElapsedRealtimeNanos());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append(DELIM).append(location.hasVerticalAccuracy() ? location.getVerticalAccuracyMeters() : "");
            } else {
                sb.append(DELIM);
            }

            writer.write(sb.toString());
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Error writing location", e);
        }
    }

    // ==================== NMEA ====================

    public synchronized void writeNmea(long timestampMs, String nmea) {
        if (writer == null) return;
        try {
            writer.write("NMEA" + DELIM + nmea.trim() + DELIM + timestampMs);
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Error writing NMEA", e);
        }
    }

    // ==================== Nav (Navigation Message) ====================

    public synchronized void writeNavigationMessage(GnssNavigationMessage msg) {
        if (writer == null) return;
        try {
            StringBuilder sb = new StringBuilder("Nav");
            sb.append(DELIM).append(msg.getSvid());
            sb.append(DELIM).append(msg.getType());
            sb.append(DELIM).append(msg.getStatus());
            sb.append(DELIM).append(msg.getMessageId());
            sb.append(DELIM).append(msg.getSubmessageId());
            byte[] data = msg.getData();
            for (byte b : data) {
                sb.append(DELIM).append(b);
            }
            writer.write(sb.toString());
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Error writing Nav message", e);
        }
    }

    // ==================== GnssStatus ====================

    public synchronized void writeStatus(GnssStatus status, long unixTimeMs) {
        if (writer == null) return;
        int count = status.getSatelliteCount();
        for (int i = 0; i < count; i++) {
            try {
                StringBuilder sb = new StringBuilder("Status");
                sb.append(DELIM).append(unixTimeMs);
                sb.append(DELIM).append(count);
                sb.append(DELIM).append(i);
                sb.append(DELIM).append(status.getConstellationType(i));
                sb.append(DELIM).append(status.getSvid(i));
                sb.append(DELIM).append(status.hasCarrierFrequencyHz(i) ? status.getCarrierFrequencyHz(i) : "");
                sb.append(DELIM).append(status.getCn0DbHz(i));
                sb.append(DELIM).append(status.getAzimuthDegrees(i));
                sb.append(DELIM).append(status.getElevationDegrees(i));
                sb.append(DELIM).append(status.usedInFix(i) ? 1 : 0);
                sb.append(DELIM).append(status.hasAlmanacData(i) ? 1 : 0);
                sb.append(DELIM).append(status.hasEphemerisData(i) ? 1 : 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    sb.append(DELIM).append(status.hasBasebandCn0DbHz(i) ? status.getBasebandCn0DbHz(i) : "");
                } else {
                    sb.append(DELIM);
                }

                writer.write(sb.toString());
                writer.newLine();
            } catch (IOException e) {
                Log.e(TAG, "Error writing status", e);
            }
        }
    }

    // ==================== Flush ====================

    public synchronized void flush() {
        if (writer != null) {
            try { writer.flush(); } catch (IOException ignored) {}
        }
    }
}
