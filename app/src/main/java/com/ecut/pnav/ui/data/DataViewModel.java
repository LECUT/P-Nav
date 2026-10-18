package com.ecut.pnav.ui.data;

import static com.rtklib.bean.nav_t.getMaxSat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ecut.pnav.R;
import com.ecut.pnav.util.StorageUtil;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.measure_util;
import com.rtklib.bean.obsd_t;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataViewModel extends ViewModel implements GnssMeasurementListener {

    private static final String TAG = "DataViewModel";

    // --- Recording state ---
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private BufferedWriter bufferedWriter;
    private File currentLogFile;
    private long recordingStartTime;
    private final obsd_t[] obs;

    // --- CSV logger (GPSTest-style .txt output) ---
    private CsvFileLogger csvLogger;
    private File csvFile;

    // --- GNSS callback ---
    private LocationManager locationManager;
    private boolean callbackRegistered = false;
    private Context appContext;

    // --- 文件概览统计（反映缓冲中累积的采集内容） ---
    private final MutableLiveData<RecordingStats> recordingStats =
            new MutableLiveData<>();
    private boolean hasCarrierPhase = false;
    // 各系统累积已见 freq code 索引位图: [GPS,BDS,GLO,GAL]
    // 各系统累积已见频段名称（用于文件概览显示）
    private final java.util.Set<String>[] seenFreqBands = new java.util.Set[]{
        new java.util.LinkedHashSet<>(), // GPS
        new java.util.LinkedHashSet<>(), // BDS
        new java.util.LinkedHashSet<>(), // GLO
        new java.util.LinkedHashSet<>(), // GAL
    };
    // 各系统当前历元卫星数
    private int curGps, curBds, curGlo, curGal;

    // 各系统每频段 C/N0 累加值 [sysIdx][bandIdx]
    private final float[][] cn0BandSum = new float[4][6];
    private final int[][] cn0BandCount = new int[4][6];

    // 各系统每频段去重卫星集合 [sysIdx] → band → Set<svid>
    @SuppressWarnings("unchecked")
    private final java.util.Map<String, java.util.Set<Integer>>[] bandSvSets = new java.util.Map[]{
        new java.util.LinkedHashMap<>(),
        new java.util.LinkedHashMap<>(),
        new java.util.LinkedHashMap<>(),
        new java.util.LinkedHashMap<>(),
    };

    // --- Satellite elevation cache (from GnssStatus.Callback) ---
    private final Map<String, Float> satElevations = new HashMap<>();
    private GnssStatus.Callback gnssStatusCallback;
    private boolean statusCallbackRegistered = false;

    // --- NMEA + Location + Nav listeners (for CSV logging) ---
    private android.location.OnNmeaMessageListener nmeaListener;
    private LocationListener locationListener;
    private boolean nmeaRegistered = false;
    private boolean locationRegistered = false;

    // --- GNSS Navigation Message callback (for CSV Nav lines) ---
    private GnssNavigationMessage.Callback navMessageCallback;
    private boolean navMessageRegistered = false;

    // --- Recording type: 0=static, 1=dynamic ---
    private final MutableLiveData<Integer> recordingType = new MutableLiveData<>(0);

    // --- Elapsed timer ---
    private final MutableLiveData<Long> recordingElapsedMs = new MutableLiveData<>(0L);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable elapsedRunnable;

    // --- New: Constellation filter [GPS, BDS, GLO, GAL] ---
    private final MutableLiveData<boolean[]> constellationFilter =
            new MutableLiveData<>(new boolean[]{true, true, true, true});

    // --- New: Append mode ---
    private final MutableLiveData<Boolean> appendMode = new MutableLiveData<>(false);

    // --- RINEX 3.04 后处理写入器（录制中缓冲，停止时一次性输出）---
    private Rinex304Writer rinexWriter;

    /** 是否已用首个定位结果设置 RINEX 头部的 APPROX POSITION XYZ */
    private boolean approxPosSet = false;

    /** 经纬高(WGS84) -> ECEF 直角坐标 (m)，用于 RINEX 头部概略位置 */
    private static double[] llaToEcef(double latDeg, double lonDeg, double h) {
        final double A = 6378137.0;
        final double F = 1.0 / 298.257223563;
        final double E2 = F * (2.0 - F);
        double lat = Math.toRadians(latDeg), lon = Math.toRadians(lonDeg);
        double sinLat = Math.sin(lat), cosLat = Math.cos(lat);
        double sinLon = Math.sin(lon), cosLon = Math.cos(lon);
        double N = A / Math.sqrt(1.0 - E2 * sinLat * sinLat);
        return new double[]{
                (N + h) * cosLat * cosLon,
                (N + h) * cosLat * sinLon,
                (N * (1.0 - E2) + h) * sinLat
        };
    }

    public DataViewModel() {
        int maxSat;
        try {
            maxSat = getMaxSat();
            Log.d(TAG, "getMaxSat() returned: " + maxSat);
        } catch (Exception e) {
            Log.e(TAG, "getMaxSat() failed, using default 256", e);
            maxSat = 256;
        }
        if (maxSat < 200) {
            Log.w(TAG, "getMaxSat() returned " + maxSat + " < 200, using 256 instead");
            maxSat = 256;
        }
        obs = new obsd_t[maxSat];
        for (int i = 0; i < maxSat; i++) obs[i] = new obsd_t();
        Log.d(TAG, "DataViewModel initialized with maxSat=" + maxSat);
    }

    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // ==================== Recording ====================

    public LiveData<Boolean> getIsRecording() {
        return isRecording;
    }

    public File getCurrentLogFile() {
        return currentLogFile;
    }

    public long getRecordingStartTime() {
        return recordingStartTime;
    }

    /** 录制中已缓冲的历元数（后处理模式下文件未写入，用此替代文件大小） */
    public int getBufferedEpochCount() {
        return rinexWriter != null ? rinexWriter.getBufferedEpochCount() : 0;
    }

    public void startRecording() {
        if (Boolean.TRUE.equals(isRecording.getValue())) return;
        Log.d(TAG, "startRecording() called, type=" + recordingType.getValue());
        hasCarrierPhase = false;
        curGps = curBds = curGlo = curGal = 0;
        for (java.util.Set<String> bands : seenFreqBands) bands.clear();
        for (float[] row : cn0BandSum) java.util.Arrays.fill(row, 0);
        for (int[] row : cn0BandCount) java.util.Arrays.fill(row, 0);
        for (java.util.Map<String, java.util.Set<Integer>> m : bandSvSets) m.clear();
        writeLogHead();
        isRecording.postValue(true);
        recordingElapsedMs.postValue(0L);
        // 新采集开始，重置统计
        RecordingStats fresh = new RecordingStats();
        recordingStats.postValue(fresh);
        startElapsedTimer();
    }

    public void stopRecording() {
        if (Boolean.FALSE.equals(isRecording.getValue())) return;
        Log.d(TAG, "stopRecording() called");
        isRecording.postValue(false);

        // 后处理：信号发现 → 写 RINEX 头部 → 写全部历元
        if (rinexWriter != null && bufferedWriter != null) {
            synchronized (this) {
                try {
                    boolean append = Boolean.TRUE.equals(appendMode.getValue())
                            && currentLogFile != null && currentLogFile.exists()
                            && currentLogFile.length() > 0;
                    rinexWriter.writeOutput(bufferedWriter, Build.MODEL,
                            constellationFilter.getValue(), append);
                    Log.d(TAG, "RINEX 3.04 后处理完成，共缓冲 "
                            + rinexWriter.getBufferedEpochCount() + " 个历元");
                } catch (IOException e) {
                    Log.e(TAG, "RINEX writeOutput failed", e);
                }
            }
        }

        // 落盘后刷新最终文件信息到采集信息栏
        RecordingStats finalStats = new RecordingStats();
        if (currentLogFile != null) {
            finalStats.fileName = currentLogFile.getName();
            finalStats.fileDir = currentLogFile.getParent();
            finalStats.fileSize = currentLogFile.length();
        }
        finalStats.epochCount = rinexWriter != null ? rinexWriter.getBufferedEpochCount() : 0;
        finalStats.durationSeconds = recordingStartTime > 0
                ? (int)((System.currentTimeMillis() - recordingStartTime) / 1000) : 0;
        recordingStats.postValue(finalStats);

        closeWriter();
        stopElapsedTimer();
    }

    private void closeWriter() {
        synchronized (this) {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.close();
                    bufferedWriter = null;
                    currentLogFile = null;
                    Log.d(TAG, "bufferedWriter closed");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to close bufferedWriter", e);
                bufferedWriter = null;
                currentLogFile = null;
            }
        }
        if (csvLogger != null) {
            csvLogger.close();
            csvLogger = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopRecording();
        unregisterGnssCallback();
        stopElapsedTimer();
    }

    // ==================== Recording Type & Rate ====================

    public LiveData<Integer> getRecordingType() {
        return recordingType;
    }

    public LiveData<Long> getRecordingElapsedMs() {
        return recordingElapsedMs;
    }

    public void setRecordingType(int type) {
        recordingType.postValue(type);
        Log.d(TAG, "Recording type: " + (type == 1 ? "Dynamic" : "Static"));
    }

    public int getRecordingTypeValue() {
        Integer type = recordingType.getValue();
        return type != null ? type : 0;
    }

    private void startElapsedTimer() {
        stopElapsedTimer();
        elapsedRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - recordingStartTime;
                recordingElapsedMs.postValue(elapsed);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(elapsedRunnable);
    }

    private void stopElapsedTimer() {
        if (elapsedRunnable != null) {
            handler.removeCallbacks(elapsedRunnable);
            elapsedRunnable = null;
        }
    }

    // ==================== Constellation Filter ====================

    public LiveData<boolean[]> getConstellationFilter() {
        return constellationFilter;
    }

    public void toggleConstellation(int index, boolean on) {
        boolean[] filter = constellationFilter.getValue();
        if (filter != null && index >= 0 && index < filter.length) {
            filter[index] = on;
            constellationFilter.postValue(filter);
            Log.d(TAG, "Constellation filter [" + index + "] = " + on);
        }
    }

    /**
     * Check if the given constellation type is enabled in the filter.
     * Index mapping: 0=GPS(1), 1=BDS(5), 2=GLO(3), 3=GAL(6)
     */
    private boolean isConstellationEnabled(int constellationType) {
        boolean[] filter = constellationFilter.getValue();
        if (filter == null) return true;
        int idx;
        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:      idx = 0; break;
            case GnssStatus.CONSTELLATION_BEIDOU:    idx = 1; break;
            case GnssStatus.CONSTELLATION_GLONASS:   idx = 2; break;
            case GnssStatus.CONSTELLATION_GALILEO:   idx = 3; break;
            default: return true; // QZSS, SBAS, unknown always pass
        }
        return idx < filter.length && filter[idx];
    }

    // ==================== Append Mode ====================

    public LiveData<Boolean> getAppendMode() {
        return appendMode;
    }

    public void setAppendMode(boolean enabled) {
        appendMode.postValue(enabled);
        Log.d(TAG, "Append mode = " + enabled);
    }

    // ==================== 文件概览统计 ====================

    public LiveData<RecordingStats> getRecordingStats() {
        return recordingStats;
    }

    // ==================== GNSS Callback ====================

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
        // 仅在录制中处理数据，闲置态不消耗资源
        if (!Boolean.TRUE.equals(isRecording.getValue())) return;
        try {
            logMeasurements(eventArgs);
            if (csvLogger != null) {
                csvLogger.writeMeasurement(eventArgs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in GNSS callback", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void registerGnssCallback() {
        if (callbackRegistered || appContext == null) return;
        callbackRegistered = true;
        Log.d(TAG, "GNSS callback registered (via MainActivity dispatch)");

        try {
            if (locationManager == null) {
                locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
            }
            if (locationManager == null) return;

            // Also register GnssStatus callback for elevation data
            if (!statusCallbackRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssStatusCallback = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(GnssStatus status) {
                        synchronized (satElevations) {
                            satElevations.clear();
                            for (int i = 0; i < status.getSatelliteCount(); i++) {
                                String key = status.getConstellationType(i) + "_" + status.getSvid(i);
                                satElevations.put(key, status.getElevationDegrees(i));
                            }
                        }
                        // CSV: write status lines
                        if (csvLogger != null) {
                            csvLogger.writeStatus(status, System.currentTimeMillis());
                        }
                    }
                };
                try {
                    boolean sOk = locationManager.registerGnssStatusCallback(gnssStatusCallback);
                    statusCallbackRegistered = sOk;
                    Log.d(TAG, "GnssStatus callback registered: " + sOk);
                } catch (Exception e) {
                    Log.w(TAG, "GnssStatus callback not supported", e);
                }
            }

            // Register NMEA listener for CSV logging
            if (!nmeaRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                nmeaListener = (message, timestamp) -> {
                    if (csvLogger != null) {
                        csvLogger.writeNmea(timestamp, message);
                    }
                };
                try {
                    boolean nOk = locationManager.addNmeaListener(nmeaListener, null);
                    nmeaRegistered = nOk;
                    Log.d(TAG, "NMEA listener registered: " + nOk);
                } catch (Exception e) {
                    Log.w(TAG, "NMEA listener not supported", e);
                }
            }

            // Register GNSS Navigation Message callback for CSV Nav lines
            if (!navMessageRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                navMessageCallback = new GnssNavigationMessage.Callback() {
                    @Override
                    public void onGnssNavigationMessageReceived(GnssNavigationMessage message) {
                        if (csvLogger != null) {
                            csvLogger.writeNavigationMessage(message);
                        }
                    }

                    @Override
                    public void onStatusChanged(int status) {}
                };
                try {
                    boolean nmOk = locationManager.registerGnssNavigationMessageCallback(navMessageCallback);
                    navMessageRegistered = nmOk;
                    Log.d(TAG, "NavMessage callback registered: " + nmOk);
                } catch (Exception e) {
                    Log.w(TAG, "NavMessage callback not supported", e);
                }
            }

            // Register Location listener for CSV Fix lines
            if (!locationRegistered) {
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (csvLogger != null && LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
                            csvLogger.writeLocation(location);
                        }
                        // 用首个定位结果填 RINEX 头的 APPROX POSITION XYZ
                        // （全零会导致 RTKLIB 报 "no position in rinex header"）
                        if (rinexWriter != null && !approxPosSet
                                && LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
                            double[] ecef = llaToEcef(location.getLatitude(),
                                    location.getLongitude(), location.getAltitude());
                            rinexWriter.setApproxPosition(ecef[0], ecef[1], ecef[2]);
                            approxPosSet = true;
                            Log.d(TAG, "RINEX 概略位置已设置: " + Arrays.toString(ecef));
                        }
                    }
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                };
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
                    locationRegistered = true;
                    Log.d(TAG, "Location listener registered for CSV");
                } catch (Exception e) {
                    Log.w(TAG, "Location listener not supported", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register GNSS callback", e);
        }
    }

    public void unregisterGnssCallback() {
        callbackRegistered = false;
        Log.d(TAG, "GNSS callback unregistered (via MainActivity dispatch)");
        if (statusCallbackRegistered && locationManager != null) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
                statusCallbackRegistered = false;
                Log.d(TAG, "GnssStatus callback unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister GnssStatus callback", e);
            }
        }
        if (nmeaRegistered && locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                locationManager.removeNmeaListener(nmeaListener);
                nmeaRegistered = false;
                Log.d(TAG, "NMEA listener unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister NMEA listener", e);
            }
        }
        if (navMessageRegistered && locationManager != null) {
            try {
                locationManager.unregisterGnssNavigationMessageCallback(navMessageCallback);
                navMessageRegistered = false;
                Log.d(TAG, "NavMessage callback unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister NavMessage callback", e);
            }
        }
        if (locationRegistered && locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
                locationRegistered = false;
                Log.d(TAG, "Location listener unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister Location listener", e);
            }
        }
    }

    // ==================== File Writing ====================

    @SuppressLint("DefaultLocale")
    private void writeLogHead() {
        try {
            File folder = getStorageFolder();
            if (folder == null) {
                Log.e(TAG, "Cannot determine storage folder");
                return;
            }
            if (!folder.exists()) folder.mkdirs();

            // RINEX 命名: PNAV + DDD + F + .YYo，F=小时字母(hour a-x)，重复采集顺延小写字母
            LocalDateTime utcTime = LocalDateTime.now(ZoneOffset.UTC);
            int dayOfYear = utcTime.getDayOfYear();
            int hour = utcTime.getHour();
            int year = utcTime.getYear() % 100;
            char session = (char) ('a' + hour);
            String ext = String.format(".%02do", year);

            File outFile = new File(folder, String.format("PNAV%03d%c.%02do", dayOfYear, session, year));
            boolean append = Boolean.TRUE.equals(appendMode.getValue()) && outFile.exists();
            if (!append) {
                while (outFile.exists() && session < 'z') {
                    session++;
                    outFile = new File(folder, String.format("PNAV%03d%c.%02do", dayOfYear, session, year));
                }
            }

            currentLogFile = outFile;
            recordingStartTime = System.currentTimeMillis();
            Log.d(TAG, "Creating log file: " + outFile.getAbsolutePath()
                    + " (append=" + append + ")");

            bufferedWriter = new BufferedWriter(new FileWriter(outFile, append));

            // 初始化 RINEX 3.04 后处理写入器
            rinexWriter = new Rinex304Writer(obs);
            rinexWriter.setMarkerName(Build.MODEL);   // 写入 MARKER NAME / REC # / ANT #
            approxPosSet = false;                     // 新一次采集，重新取概略位置
            Log.d(TAG, "RINEX 3.04 writer initialized (append=" + append + ")");

            // Also start CSV logger
            csvLogger = new CsvFileLogger();
            String deviceModel = Build.MODEL;
            if (append && csvFile != null && csvFile.exists()) {
                csvLogger.openExisting(csvFile, deviceModel);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US);
                csvFile = new File(folder, "gnss_log_" + sdf.format(new Date()) + ".txt");
                csvLogger.startNewFile(folder, deviceModel);
            }
            Log.d(TAG, "CSV logger started: " + (csvLogger.getFile() != null ? csvLogger.getFile().getName() : "FAILED"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write log head: " + e.getMessage(), e);
            bufferedWriter = null;
        }
    }

    private void logMeasurements(GnssMeasurementsEvent eventArgs) {
        if (rinexWriter == null) return;

        try {
            // 每历元重置频段去重集合（CN0 均值仍累积跨历元）
            for (java.util.Map<String, java.util.Set<Integer>> m : bandSvSets) m.clear();

            List<GnssMeasurement> measurements = new ArrayList<>(eventArgs.getMeasurements());

            // Statistics for current epoch — 用 Set 按 SVID 去重
            java.util.Set<Integer> gpsSet = new java.util.HashSet<>(),
                    bdsSet = new java.util.HashSet<>(),
                    gloSet = new java.util.HashSet<>(),
                    galSet = new java.util.HashSet<>();

            for (GnssMeasurement m : measurements) {
                int constellation = m.getConstellationType();
                int sysIdx = constellationToIdx(constellation);

                // Per-system sat count (deduped by SVID)
                int svid = m.getSvid();
                switch (constellation) {
                    case GnssStatus.CONSTELLATION_GPS:
                        if (isConstellationEnabled(constellation)) gpsSet.add(svid);
                        break;
                    case GnssStatus.CONSTELLATION_BEIDOU:
                        if (isConstellationEnabled(constellation)) bdsSet.add(svid);
                        break;
                    case GnssStatus.CONSTELLATION_GLONASS:
                        if (isConstellationEnabled(constellation)) gloSet.add(svid);
                        break;
                    case GnssStatus.CONSTELLATION_GALILEO:
                        if (isConstellationEnabled(constellation)) galSet.add(svid);
                        break;
                }

                // CN0 - 按频段分别累计
                float cn0 = (float) m.getCn0DbHz();
                if (cn0 > 0 && sysIdx >= 0 && sysIdx < 4) {
                    String band = freqToBand(constellation, m.getCarrierFrequencyHz());
                    if (band != null) {
                        int bandIdx = bandIndex(sysIdx, band);
                        if (bandIdx >= 0) {
                            cn0BandSum[sysIdx][bandIdx] += cn0;
                            cn0BandCount[sysIdx][bandIdx]++;
                        }
                        // 频段去重卫星计数
                        java.util.Set<Integer> svSet = bandSvSets[sysIdx].get(band);
                        if (svSet == null) {
                            svSet = new java.util.HashSet<>();
                            bandSvSets[sysIdx].put(band, svSet);
                        }
                        svSet.add(m.getSvid());
                        seenFreqBands[sysIdx].add(band);
                    }
                }

                // 载波相位探测
                if (!hasCarrierPhase && (m.getAccumulatedDeltaRangeState()
                        & GnssMeasurement.ADR_STATE_VALID) != 0) {
                    hasCarrierPhase = true;
                }
            }

            curGps = gpsSet.size(); curBds = bdsSet.size(); curGlo = gloSet.size(); curGal = galSet.size();

            // Build stats with accumulated freq info
            postRecordingStats();

            // 缓冲历元数据供后处理使用（与 GPSTest 一致，每次回调都缓冲）
            if (rinexWriter != null) {
                rinexWriter.bufferEpoch(measurements, eventArgs.getClock());
            }
        } catch (Exception e) {
            Log.e(TAG, "logMeasurements unexpected error", e);
        }
    }

    /** 载波频率 → 频段简称 */
    private static String freqToBand(int constellation, float freqHz) {
        if (freqHz <= 0) return null;
        int mhz = Math.round(freqHz / 1e6f);
        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:
                if (mhz >= 1570) return "L1";
                if (mhz >= 1220) return "L2";
                if (mhz >= 1170) return "L5";
                return mhz + "M";
            case GnssStatus.CONSTELLATION_BEIDOU:
                if (mhz >= 1570) return "B1C";
                if (mhz >= 1560) return "B1I";
                if (mhz >= 1200) return "B2b";
                if (mhz >= 1170) return "B2a";
                if (mhz >= 1260) return "B3I";
                return mhz + "M";
            case GnssStatus.CONSTELLATION_GLONASS:
                if (mhz >= 1590) return "G1";
                if (mhz >= 1230) return "G2";
                return mhz + "M";
            case GnssStatus.CONSTELLATION_GALILEO:
                if (mhz >= 1570) return "E1";
                if (mhz >= 1200) return "E5b";
                if (mhz >= 1170) return "E5a";
                return mhz + "M";
            default:
                return mhz + "M";
        }
    }

    private static int constellationToIdx(int constellation) {
        switch (constellation) {
            case GnssStatus.CONSTELLATION_GPS:      return 0;
            case GnssStatus.CONSTELLATION_BEIDOU:    return 1;
            case GnssStatus.CONSTELLATION_GLONASS:   return 2;
            case GnssStatus.CONSTELLATION_GALILEO:   return 3;
            default:                                 return -1;
        }
    }

    private void postRecordingStats() {
        RecordingStats stats = new RecordingStats();
        stats.epochCount = rinexWriter != null ? rinexWriter.getBufferedEpochCount() : 0;
        stats.hasCarrierPhase = hasCarrierPhase;
        if (currentLogFile != null) {
            stats.fileName = currentLogFile.getName();
            stats.fileDir = currentLogFile.getParent();
            stats.fileSize = currentLogFile.length();
        }
        stats.durationSeconds = recordingStartTime > 0
                ? (int)((System.currentTimeMillis() - recordingStartTime) / 1000) : 0;

        String[][] allCodes = {
            {"L1", "L2", "L5"},
            {"B1I", "B1C", "B2b", "B2a", "B3I"},
            {"G1", "G2"},
            {"E1", "E5b", "E5a"},
        };
        String[] names = {"GPS", "BDS", "GLO", "GAL"};
        int[] counts = {curGps, curBds, curGlo, curGal};
        int[] colors = {R.color.gps_stats, R.color.bds_stats, R.color.glonass_stats, R.color.galileo_stats};

        for (int i = 0; i < 4; i++) {
            RecordingStats.SysStats sys = new RecordingStats.SysStats(names[i], counts[i], colors[i]);
            java.util.Set<String> bands = seenFreqBands[i];
            for (int j = 0; j < allCodes[i].length; j++) {
                String code = allCodes[i][j];
                if (bands.contains(code)) {
                    int avg = cn0BandCount[i][j] > 0
                            ? Math.round(cn0BandSum[i][j] / cn0BandCount[i][j]) : 0;
                    java.util.Set<Integer> svSet = bandSvSets[i].get(code);
                    int svCount = svSet != null ? svSet.size() : 0;
                    sys.bands.add(new RecordingStats.BandStats(code, svCount, avg));
                }
            }
            stats.sysList.add(sys);
        }

        recordingStats.postValue(stats);
    }

    /** 频段代码在对应系统 allCodes 中的索引 */
    private static int bandIndex(int sysIdx, String band) {
        String[][] allCodes = {
            {"L1", "L2", "L5"},
            {"B1I", "B1C", "B2b", "B2a", "B3I"},
            {"G1", "G2"},
            {"E1", "E5b", "E5a"},
        };
        if (sysIdx < 0 || sysIdx >= allCodes.length) return -1;
        String[] codes = allCodes[sysIdx];
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(band)) return i;
        }
        return -1;
    }

    // ==================== File Management ====================

    /**
     * 向后兼容的静态 RINEX 头部输出（供 DgnssViewModel/RtkViewModel 调用）。
     * 使用硬编码 OBS TYPES，版本 3.03。
     */
    @SuppressLint("DefaultLocale")
    public static void outLogHead(BufferedWriter bw, boolean supportCP) throws IOException {
        Date now = new Date();
        bw.write(String.format("     %3.2f           OBSERVATION DATA    M                   RINEX VERSION / TYPE", 3.04));
        bw.newLine();
        bw.write("G = GPS  R = GLONASS  E = GALILEO  J = QZSS  C = BDS        COMMENT             ");
        bw.newLine();
        bw.write("S = SBAS payload  M = Mixed                                 COMMENT             ");
        bw.newLine();
        bw.write(String.format("%-20s%-20s%-20s", "PNav Logger", Build.MODEL, now.getTime()) + "UTC PGM / RUN BY / DATE");
        bw.newLine();
        bw.write("                                                            MARKER NAME         ");
        bw.newLine();
        bw.write("                                                            MARKER NUMBER       ");
        bw.newLine();
        bw.write("                                                            OBSERVER / AGENCY   ");
        bw.newLine();
        bw.write("                                                            REC # / TYPE / VERS ");
        bw.newLine();
        bw.write("                                                            ANT # / TYPE        ");
        bw.newLine();
        bw.write(String.format("%14.4f%14.4f%14.4f                  ", 0.0, 0.0, 0.0) + "APPROX POSITION XYZ");
        bw.newLine();
        bw.write("        0.0000        0.0000        0.0000                  ANTENNA: DELTA H/E/N");
        bw.newLine();
        bw.write("     0                                                      RCV CLOCK OFFS APPL ");
        bw.newLine();

        bw.write(measure_util.buildObsTypesLine('G', new String[]{"1C", "2W", "5Q"}, supportCP));
        bw.newLine();
        bw.write(measure_util.buildObsTypesLine('R', new String[]{"1C", "2C"}, supportCP));
        bw.newLine();
        bw.write(measure_util.buildObsTypesLine('J', new String[]{"1C", "2X", "5Q"}, supportCP));
        bw.newLine();
        bw.write(measure_util.buildObsTypesLine('E', new String[]{"1C", "7Q", "5Q"}, supportCP));
        bw.newLine();
        bw.write(measure_util.buildObsTypesLine('C', new String[]{"2I", "1P", "7Z", "5Q", "6I"}, supportCP));
        bw.newLine();

        bw.write("                                                            END OF HEADER");
        bw.newLine();
        bw.flush();
    }

    public File getStorageFolder() {
        if (appContext != null) return StorageUtil.getDataDir(appContext);
        return null;
    }

}
