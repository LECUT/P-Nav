/*
 * Derived from the open-source myGNSS application.
 *
 *     Copyright (c) 2024, z1Yang-1, All rights reserved.
 *     Licensed under the BSD 2-Clause License.
 *     https://github.com/z1Yang-1/myGNSS
 *
 * Substantially modified and extended for P-Nav.
 * See THIRD-PARTY-NOTICES.md for details.
 */
package com.ecut.pnav.ui.status;

import static android.location.GnssStatus.CONSTELLATION_BEIDOU;
import static android.location.GnssStatus.CONSTELLATION_GPS;

import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class HomeViewModel extends ViewModel {
    StringBuilder sb1 = new StringBuilder("卫星类型").append('\n').append('\n');
    StringBuilder sb2 = new StringBuilder("可见卫星数目").append('\n').append('\n');
    public static boolean support_CarrierPhase = false;
    private static boolean carrierPhaseChecked = false;
    private static int carrierPhaseEpochCount = 0;
    private final MutableLiveData<Boolean> carrierPhaseWarning;
    int bds_sat_b1i=0, bds_sat_b1c=0, bds_sat_b2a=0, bds_sat_b2b=0;
    int gps_sat_l1=0, gps_sat_l5=0, glonass_sat_g1=0, glonass_sat_g2=0;
    int galileo_sat_e1=0, galileo_sat_e5a=0, qzss_sat_l1=0, qzss_sat_l5=0, irnss_sat_l5=0, irnss_sat_s=0;
    private final MutableLiveData<String> bds_sat_count, gps_sat_count, glonass_sat_count, galileo_sat_count, qzss_sat_count, irnss_sat_count;
    private final MutableLiveData<String> bds_band_detail, gps_band_detail, glonass_band_detail, galileo_band_detail, qzss_band_detail, irnss_band_detail;
    private final MutableLiveData<List<Cn0Info>> cn0Data;
    boolean firstpaint;
    private final java.util.HashMap<String, Float> cn0Map = new java.util.HashMap<>();
    private final java.util.HashMap<String, Double> freqMap = new java.util.HashMap<>();
    private final MutableLiveData<List<SatelliteDetail>> satelliteDetails;
    private final MutableLiveData<String> mText;

    // Location info (13 fields)
    private final MutableLiveData<String> locLat, locLon, locAlt;
    private final MutableLiveData<String> locSpeed, locSpeedAcc, locPdop;
    private final MutableLiveData<String> locTime, locTtff, locHvAcc;
    private final MutableLiveData<String> locNumSats, locBearing, locBearingAcc, locHvDop;
    private float nmeaPdop = -1, nmeaHdop = -1, nmeaVdop = -1;
    private double nmeaMslAlt = Double.NaN;
    private final MutableLiveData<String> dualFreqStatus;
    private final MutableLiveData<Integer> dualFreqColor;
    private final MutableLiveData<List<DopSnapshot>> dopHistory;
    private final LinkedList<DopSnapshot> dopBuffer = new LinkedList<>();
    private int satelliteTotalCount = 0, satelliteUsedCount = 0;
    private String ttffStr = "";

    public HomeViewModel() {
        mText = new MutableLiveData<>(); mText.setValue("home fragment");
        firstpaint = true;
        bds_sat_count = new MutableLiveData<>(); gps_sat_count = new MutableLiveData<>();
        glonass_sat_count = new MutableLiveData<>(); galileo_sat_count = new MutableLiveData<>();
        qzss_sat_count = new MutableLiveData<>(); irnss_sat_count = new MutableLiveData<>();
        bds_band_detail = new MutableLiveData<>(); gps_band_detail = new MutableLiveData<>();
        glonass_band_detail = new MutableLiveData<>(); galileo_band_detail = new MutableLiveData<>();
        qzss_band_detail = new MutableLiveData<>(); irnss_band_detail = new MutableLiveData<>();
        cn0Data = new MutableLiveData<>(); cn0Data.setValue(new ArrayList<>());
        satelliteDetails = new MutableLiveData<>(); satelliteDetails.setValue(new ArrayList<>());
        locLat = new MutableLiveData<>(); locLon = new MutableLiveData<>();
        locAlt = new MutableLiveData<>();
        locSpeed = new MutableLiveData<>(); locSpeedAcc = new MutableLiveData<>();
        locPdop = new MutableLiveData<>();
        locTime = new MutableLiveData<>(); locTtff = new MutableLiveData<>();
        locHvAcc = new MutableLiveData<>(); locNumSats = new MutableLiveData<>();
        locBearing = new MutableLiveData<>(); locBearingAcc = new MutableLiveData<>();
        locHvDop = new MutableLiveData<>();
        carrierPhaseWarning = new MutableLiveData<>();
        dualFreqStatus = new MutableLiveData<>(); dualFreqStatus.setValue("");
        dualFreqColor = new MutableLiveData<>(); dualFreqColor.setValue(0xFF8E8E93);
        dopHistory = new MutableLiveData<>(); dopHistory.setValue(new ArrayList<>());
    }

    // ==================== Location/NMEA methods ====================

    /** 最新手机 GNSS 定位（十进制度），供 NTRIP GGA 自动填入 */
    public static volatile double[] latestGnssLatLon = {0, 0};

    public void updateLocation(Location location) {
        if (location == null) return;
        latestGnssLatLon[0] = location.getLatitude();
        latestGnssLatLon[1] = location.getLongitude();
        locLat.postValue(String.format("%.7f°", location.getLatitude()));
        locLon.postValue(String.format("%.7f°", location.getLongitude()));
        locAlt.postValue(String.format("%.1f m", location.getAltitude()));
        locSpeed.postValue(location.hasSpeed() ? String.format("%.1f m/s", location.getSpeed()) : "—");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy())
            locSpeedAcc.postValue(String.format("%.1f m/s", location.getSpeedAccuracyMetersPerSecond()));
        else locSpeedAcc.postValue("—");
        locPdop.postValue(nmeaPdop > 0 ? String.format("%.1f", nmeaPdop) : "—");
        if (location.getTime() > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            locTime.postValue(sdf.format(new Date(location.getTime())));
        } else locTime.postValue("—");
        if (!ttffStr.isEmpty()) locTtff.postValue(ttffStr);
        else locTtff.postValue("—");
        String hvAcc;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy())
            hvAcc = String.format("%.1f / %.1f m", location.getAccuracy(), location.getVerticalAccuracyMeters());
        else hvAcc = String.format("%.1f m", location.getAccuracy());
        locHvAcc.postValue(hvAcc);
        locNumSats.postValue(satelliteUsedCount + "/" + satelliteTotalCount);
        locBearing.postValue(location.hasBearing() ? String.format("%.1f°", location.getBearing()) : "—");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy())
            locBearingAcc.postValue(String.format("%.1f°", location.getBearingAccuracyDegrees()));
        else locBearingAcc.postValue("—");
        if (nmeaHdop > 0 && nmeaVdop > 0) locHvDop.postValue(String.format("%.1f / %.1f", nmeaHdop, nmeaVdop));
        else if (nmeaHdop > 0) locHvDop.postValue(String.format("%.1f / —", nmeaHdop));
        else locHvDop.postValue("—");
    }

    public void updateSatelliteCount(int total, int used) {
        satelliteTotalCount = total; satelliteUsedCount = used;
        locNumSats.postValue(used + "/" + total);
    }
    public void updateTtff(int ttffMillis) {
        if (ttffMillis < 1000) ttffStr = ttffMillis + " ms";
        else if (ttffMillis < 60000) ttffStr = String.format("%.1f sec", ttffMillis / 1000.0);
        else { int min = ttffMillis / 60000; int sec = (ttffMillis % 60000) / 1000; ttffStr = min + " min " + sec + " sec"; }
        locTtff.postValue(ttffStr);
    }
    public void processNmea(String nmea) {
        if (nmea.startsWith("$GPGSA") || nmea.startsWith("$GNGSA")) {
            String[] p = nmea.split(",");
            if (p.length > 15) try { nmeaPdop = Float.parseFloat(p[15]); } catch (Exception ignored) {}
            if (p.length > 16) try { nmeaHdop = Float.parseFloat(p[16]); } catch (Exception ignored) {}
            if (p.length > 17) try { nmeaVdop = Float.parseFloat(p[17].split("\\*")[0]); } catch (Exception ignored) {}
        }
        if (nmea.startsWith("$GPGGA") || nmea.startsWith("$GNGNS") || nmea.startsWith("$GNGGA")) {
            String[] p = nmea.split(",");
            if (p.length > 9 && !p[9].isEmpty()) try { nmeaMslAlt = Double.parseDouble(p[9]); } catch (Exception ignored) {}
        }
        updateDopHistory();
    }

    private void updateDopHistory() {
        if (nmeaPdop <= 0 && nmeaHdop <= 0 && nmeaVdop <= 0) return;
        dopBuffer.addLast(new DopSnapshot(System.currentTimeMillis(), nmeaPdop, nmeaHdop, nmeaVdop));
        // 保持 60 秒窗口
        long cutoff = System.currentTimeMillis() - 60_000;
        while (!dopBuffer.isEmpty() && dopBuffer.getFirst().timestampMillis < cutoff) {
            dopBuffer.removeFirst();
        }
        dopHistory.postValue(new ArrayList<>(dopBuffer));
    }

    // --- Getters ---
    public LiveData<String> getLocationLat() { return locLat; }
    public LiveData<String> getLocationLon() { return locLon; }
    public LiveData<String> getLocationAlt() { return locAlt; }
    public LiveData<String> getLocationSpeed() { return locSpeed; }
    public LiveData<String> getLocationSpeedAcc() { return locSpeedAcc; }
    public LiveData<String> getLocationPdop() { return locPdop; }
    public LiveData<String> getLocationTime() { return locTime; }
    public LiveData<String> getLocationTtff() { return locTtff; }
    public LiveData<String> getLocationHvAcc() { return locHvAcc; }
    public LiveData<String> getLocationNumSats() { return locNumSats; }
    public LiveData<String> getLocationBearing() { return locBearing; }
    public LiveData<String> getLocationBearingAcc() { return locBearingAcc; }
    public LiveData<String> getLocationHvDop() { return locHvDop; }
    public LiveData<Boolean> getCarrierPhaseWarning() { return carrierPhaseWarning; }
    public LiveData<String> getDualFreqStatus() { return dualFreqStatus; }
    public LiveData<Integer> getDualFreqColor() { return dualFreqColor; }

    public static String Gtypetostring(int ct) {
        switch (ct) {
            case CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_SBAS: return "SBAS";
            case GnssStatus.CONSTELLATION_GLONASS: return "GLONASS";
            case CONSTELLATION_BEIDOU: return "BeiDou";
            case GnssStatus.CONSTELLATION_GALILEO: return "Galileo";
            case GnssStatus.CONSTELLATION_QZSS: return "QZSS";
            case GnssStatus.CONSTELLATION_IRNSS: return "IRNSS";
            default: return "Unknown";
        }
    }

    void processGnssMeasurements(GnssMeasurementsEvent eventArgs){
        List<GnssMeasurement> measurements = new ArrayList<>(eventArgs.getMeasurements());
        // 无原始测量数据时不清零已有的 GnssStatus 计数
        if (measurements.isEmpty()) return;
        // Detect carrier phase support from actual measurements
        if (!carrierPhaseChecked) {
            for (GnssMeasurement m : measurements) {
                if ((m.getAccumulatedDeltaRangeState() & GnssMeasurement.ADR_STATE_VALID) != 0) {
                    support_CarrierPhase = true;
                    carrierPhaseChecked = true;
                    break;
                }
            }
            if (!carrierPhaseChecked) {
                carrierPhaseEpochCount++;
                if (carrierPhaseEpochCount >= 5) {
                    carrierPhaseChecked = true;
                    carrierPhaseWarning.postValue(false);
                }
            } else {
                carrierPhaseWarning.postValue(true);
            }
        }

        // 按 SVID 去重统计各频段卫星数（一颗星多 measurement 只算一次）
        java.util.Set<Integer> gpsL1Set = new java.util.HashSet<>(), gpsL5Set = new java.util.HashSet<>();
        java.util.Set<Integer> bdsB1iSet = new java.util.HashSet<>(), bdsB1cSet = new java.util.HashSet<>();
        java.util.Set<Integer> bdsB2aSet = new java.util.HashSet<>(), bdsB2bSet = new java.util.HashSet<>();
        java.util.Set<Integer> gloG1Set = new java.util.HashSet<>(), gloG2Set = new java.util.HashSet<>();
        java.util.Set<Integer> galE1Set = new java.util.HashSet<>(), galE5aSet = new java.util.HashSet<>();
        java.util.Set<Integer> qzssL1Set = new java.util.HashSet<>(), qzssL5Set = new java.util.HashSet<>();
        java.util.Set<Integer> irnssL5Set = new java.util.HashSet<>(), irnssSSet = new java.util.HashSet<>();
        // 每星座独立卫星总数集（去重，避免一颗卫星多频段被重复计数）
        java.util.Set<Integer> gpsAllSet = new java.util.HashSet<>(), bdsAllSet = new java.util.HashSet<>();
        java.util.Set<Integer> gloAllSet = new java.util.HashSet<>(), galAllSet = new java.util.HashSet<>();
        java.util.Set<Integer> qzssAllSet = new java.util.HashSet<>(), irnssAllSet = new java.util.HashSet<>();

        cn0Map.clear(); freqMap.clear();
        List<Cn0Info> cn0List = new ArrayList<>();
        for (GnssMeasurement m : measurements) {
            int type = m.getConstellationType(); double carrier = m.getCarrierFrequencyHz();
            int svid = m.getSvid(); float cn0 = (float) m.getCn0DbHz();
            cn0List.add(new Cn0Info(type, svid, cn0, carrier));
            // 频率相关 key，避免同卫星多频率互相覆盖
            String freqKey = type + "_" + svid + "_" + freqSlot(carrier, type);
            cn0Map.put(freqKey, cn0);
            if (carrier > 0) freqMap.put(freqKey, carrier);

            if(type==CONSTELLATION_GPS&&freqMatch(carrier, 1.57542e9)) gpsL1Set.add(svid);
            else if(type==CONSTELLATION_GPS&&freqMatch(carrier, 1.17645e9)) gpsL5Set.add(svid);
            else if(type==CONSTELLATION_BEIDOU&&freqMatch(carrier, 1.561098e9)) bdsB1iSet.add(svid);
            else if(type==CONSTELLATION_BEIDOU&&freqMatch(carrier, 1.57542e9)) bdsB1cSet.add(svid);
            else if(type==CONSTELLATION_BEIDOU&&freqMatch(carrier, 1.17645e9)) bdsB2aSet.add(svid);
            else if(type==CONSTELLATION_BEIDOU&&freqMatch(carrier, 1.20714e9)) bdsB2bSet.add(svid);
            else if(type==GnssStatus.CONSTELLATION_GLONASS&&Math.abs(carrier - 1.602e9) < 8e6) gloG1Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_GLONASS&&Math.abs(carrier - 1.246e9) < 8e6) gloG2Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_GALILEO&&freqMatch(carrier, 1.57542e9)) galE1Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_GALILEO&&freqMatch(carrier, 1.17645e9)) galE5aSet.add(svid);
            else if(type==GnssStatus.CONSTELLATION_QZSS&&freqMatch(carrier, 1.57542e9)) qzssL1Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_QZSS&&freqMatch(carrier, 1.17645e9)) qzssL5Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_IRNSS&&freqMatch(carrier, 1.17645e9)) irnssL5Set.add(svid);
            else if(type==GnssStatus.CONSTELLATION_IRNSS&&freqMatch(carrier, 2.492028e9)) irnssSSet.add(svid);
            // 无论哪个频段，卫星都计入该星座独立总数
            if (type == CONSTELLATION_GPS) gpsAllSet.add(svid);
            else if (type == CONSTELLATION_BEIDOU) bdsAllSet.add(svid);
            else if (type == GnssStatus.CONSTELLATION_GLONASS) gloAllSet.add(svid);
            else if (type == GnssStatus.CONSTELLATION_GALILEO) galAllSet.add(svid);
            else if (type == GnssStatus.CONSTELLATION_QZSS) qzssAllSet.add(svid);
            else if (type == GnssStatus.CONSTELLATION_IRNSS) irnssAllSet.add(svid);
        }

        bds_sat_b1i = bdsB1iSet.size(); bds_sat_b1c = bdsB1cSet.size();
        bds_sat_b2a = bdsB2aSet.size(); bds_sat_b2b = bdsB2bSet.size();
        gps_sat_l1 = gpsL1Set.size(); gps_sat_l5 = gpsL5Set.size();
        glonass_sat_g1 = gloG1Set.size(); glonass_sat_g2 = gloG2Set.size();
        galileo_sat_e1 = galE1Set.size(); galileo_sat_e5a = galE5aSet.size();
        qzss_sat_l1 = qzssL1Set.size(); qzss_sat_l5 = qzssL5Set.size();
        irnss_sat_l5 = irnssL5Set.size(); irnss_sat_s = irnssSSet.size();

        // 总卫星数（大字）—— 用独立卫星并集，避免一颗卫星多频段重复计数
        gps_sat_count.postValue(String.valueOf(gpsAllSet.size()));
        bds_sat_count.postValue(String.valueOf(bdsAllSet.size()));
        glonass_sat_count.postValue(String.valueOf(gloAllSet.size()));
        galileo_sat_count.postValue(String.valueOf(galAllSet.size()));
        qzss_sat_count.postValue(String.valueOf(qzssAllSet.size()));
        irnss_sat_count.postValue(String.valueOf(irnssAllSet.size()));

        // 频段细分（小字副行）—— 只显示有卫星的频段，统一两行
        gps_band_detail.postValue(buildBandDetail2Row(
            new String[]{"L1", "L5"}, new int[]{gps_sat_l1, gps_sat_l5}, 1));
        bds_band_detail.postValue(buildBandDetail2Row(
            new String[]{"B1I", "B1C", "B2a", "B2b"}, new int[]{bds_sat_b1i, bds_sat_b1c, bds_sat_b2a, bds_sat_b2b}, 2));
        glonass_band_detail.postValue(buildBandDetail2Row(
            new String[]{"G1", "G2"}, new int[]{glonass_sat_g1, glonass_sat_g2}, 1));
        galileo_band_detail.postValue(buildBandDetail2Row(
            new String[]{"E1", "E5a"}, new int[]{galileo_sat_e1, galileo_sat_e5a}, 1));
        qzss_band_detail.postValue(buildBandDetail2Row(
            new String[]{"L1", "L5"}, new int[]{qzss_sat_l1, qzss_sat_l5}, 1));
        irnss_band_detail.postValue(buildBandDetail2Row(
            new String[]{"L5", "S"}, new int[]{irnss_sat_l5, irnss_sat_s}, 1));
        cn0Data.postValue(cn0List);

        // 双频检测：统计有多少颗卫星在多个频率上有信号
        java.util.HashMap<String, Integer> satFreqCount = new java.util.HashMap<>();
        for (String key : cn0Map.keySet()) {
            // key 格式：type_svid_slot，取 type_svid 作为卫星标识
            int lastUnderscore = key.lastIndexOf('_');
            if (lastUnderscore > 0) {
                String satKey = key.substring(0, lastUnderscore);
                satFreqCount.put(satKey, satFreqCount.getOrDefault(satKey, 0) + 1);
            }
        }
        int dualFreqSats = 0;
        for (int count : satFreqCount.values()) {
            if (count >= 2) dualFreqSats++;
        }
        int totalSatsWithSignal = satFreqCount.size();
        if (totalSatsWithSignal > 0) {
            dualFreqStatus.postValue(dualFreqSats + "/" + totalSatsWithSignal);
        } else {
            dualFreqStatus.postValue("-");
        }
        if (dualFreqSats > 0) {
            dualFreqColor.postValue(0xFF34C759); // 绿色：双频可用
        } else if (totalSatsWithSignal > 0) {
            dualFreqColor.postValue(0xFFFF9500); // 橙色：有信号但无双频
        } else {
            dualFreqColor.postValue(0xFF8E8E93); // 灰色：无信号
        }
        // 注意：卫星总数由 GnssStatus 回调权威提供（updateSatelliteCount），此处不再覆盖
    }

    void processDetailedStatus(android.location.GnssStatus status) {
        int count = status.getSatelliteCount();
        List<SatelliteDetail> detailList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int svid = status.getSvid(i), type = status.getConstellationType(i);
            float elev = status.getElevationDegrees(i), azim = status.getAzimuthDegrees(i);
            boolean used = status.usedInFix(i), hasEph = status.hasEphemerisData(i);
            // 每个有信号的频率都单独一行
            boolean hasAny = false;
            for (int slot = 0; slot < 5; slot++) {
                String fk = type + "_" + svid + "_" + slot;
                Float cn0 = cn0Map.get(fk);
                if (cn0 != null && cn0 > 0) {
                    Double freq = freqMap.get(fk);
                    detailList.add(new SatelliteDetail(svid, type, elev, azim, cn0, used, hasEph, freq != null ? freq : 0));
                    hasAny = true;
                }
            }
            // 如果 cn0Map 中没有任何频率数据，显示一行占位
            if (!hasAny) {
                detailList.add(new SatelliteDetail(svid, type, elev, azim, 0, used, hasEph, 0));
            }
        }
        java.util.Collections.sort(detailList, (a, b) -> {
            if (a.usedInFix != b.usedInFix) return a.usedInFix ? -1 : 1;
            if (a.constellationType != b.constellationType) return a.constellationType - b.constellationType;
            return a.svid - b.svid;
        });
        satelliteDetails.postValue(detailList);
    }

    void processSatelliteStatus(android.location.GnssStatus status) {
        int gps_n=0,sbas_n=0,glo_n=0,bds_n=0,qzss_n=0,irnss_n=0;
        for (int i=0;i<status.getSatelliteCount();i++) {
            switch (Gtypetostring(status.getConstellationType(i))) {
                case "GPS": gps_n++; break; case "SBAS": sbas_n++; break;
                case "GLONASS": glo_n++; break; case "BeiDou": bds_n++; break;
                case "QZSS": qzss_n++; break; case "IRNSS": irnss_n++; break;
            }
        }
        if(gps_n>0){sb1.append("GPS").append('\n').append('\n');sb2.append(gps_n).append('\n').append('\n');}
        if(sbas_n>0){sb1.append("SBAS").append('\n').append('\n');sb2.append(sbas_n).append('\n').append('\n');}
        if(glo_n>0){sb1.append("GLONASS").append('\n').append('\n');sb2.append(glo_n).append('\n').append('\n');}
        if(bds_n>0){sb1.append("BeiDou").append('\n').append('\n');sb2.append(bds_n).append('\n').append('\n');}
        if(qzss_n>0){sb1.append("QZSS").append('\n').append('\n');sb2.append(qzss_n).append('\n').append('\n');}
        if(irnss_n>0){sb1.append("IRNSS").append('\n').append('\n');sb2.append(irnss_n).append('\n').append('\n');}
    }

    public LiveData<String> getText() { return mText; }
    public LiveData<String> getbds() { return bds_sat_count; }
    public LiveData<String> getgps() { return gps_sat_count; }
    public LiveData<String> getglonass() { return glonass_sat_count; }
    public LiveData<String> getgalileo() { return galileo_sat_count; }
    public LiveData<String> getqzss() { return qzss_sat_count; }
    public LiveData<String> getirnss() { return irnss_sat_count; }
    public LiveData<String> getGpsBandDetail() { return gps_band_detail; }
    public LiveData<String> getBdsBandDetail() { return bds_band_detail; }
    public LiveData<String> getGlonassBandDetail() { return glonass_band_detail; }
    public LiveData<String> getGalileoBandDetail() { return galileo_band_detail; }
    public LiveData<String> getQzssBandDetail() { return qzss_band_detail; }
    public LiveData<String> getIrnssBandDetail() { return irnss_band_detail; }
    public LiveData<List<Cn0Info>> getCn0Data() { return cn0Data; }
    public LiveData<List<SatelliteDetail>> getSatelliteDetails() { return satelliteDetails; }
    public LiveData<List<DopSnapshot>> getDopHistory() { return dopHistory; }
    public float getCn0ForSatellite(int type, int svid) {
        float best = 0f;
        for (int slot = 0; slot < 5; slot++) {
            Float cn0 = cn0Map.get(type + "_" + svid + "_" + slot);
            if (cn0 != null && cn0 > best) best = cn0;
        }
        return best;
    }

    /** 构建频段详情，每行 colsPerRow 个频段，换行分隔，只显示 count>0 的频段 */
    private static String buildBandDetail2Row(String[] labels, int[] counts, int colsPerRow) {
        // 先收集非零频段
        StringBuilder sb = new StringBuilder();
        int col = 0;
        for (int i = 0; i < labels.length; i++) {
            if (counts[i] > 0) {
                if (col > 0 && col % colsPerRow == 0) sb.append('\n');
                else if (col > 0) sb.append("  ");
                sb.append(labels[i]).append(":").append(counts[i]);
                col++;
            }
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    /** 容差频率比较，±1MHz */
    private static boolean freqMatch(double actual, double expected) {
        return Math.abs(actual - expected) < 1e6;
    }

    /** 将载波频率映射为 0-4 的 slot 编号 */
    private static int freqSlot(double freqHz, int type) {
        if (freqHz <= 0) return 0;
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS:
                if (freqMatch(freqHz, 1.57542e9)) return 0; // L1
                if (freqMatch(freqHz, 1.22760e9)) return 1; // L2
                if (freqMatch(freqHz, 1.17645e9)) return 2; // L5
                return 0;
            case GnssStatus.CONSTELLATION_BEIDOU:
                if (freqMatch(freqHz, 1.561098e9)) return 0; // B1I
                if (freqMatch(freqHz, 1.57542e9))  return 1; // B1C
                if (freqMatch(freqHz, 1.20714e9))  return 2; // B2b
                if (freqMatch(freqHz, 1.17645e9))  return 3; // B2a
                return 0;
            case GnssStatus.CONSTELLATION_GALILEO:
                if (freqMatch(freqHz, 1.57542e9)) return 0; // E1
                if (freqMatch(freqHz, 1.20714e9)) return 1; // E5b
                if (freqMatch(freqHz, 1.17645e9)) return 2; // E5a
                return 0;
            case GnssStatus.CONSTELLATION_GLONASS:
                if (Math.abs(freqHz - 1.602e9) < 8e6)  return 0; // G1
                if (Math.abs(freqHz - 1.246e9) < 8e6)  return 1; // G2
                return 0;
            default:
                return 0;
        }
    }

    /** 一次 DOP 快照，用于趋势图绘制 */
    public static class DopSnapshot {
        public final long timestampMillis;
        public final float pdop, hdop, vdop;

        public DopSnapshot(long timestampMillis, float pdop, float hdop, float vdop) {
            this.timestampMillis = timestampMillis;
            this.pdop = pdop;
            this.hdop = hdop;
            this.vdop = vdop;
        }
    }
}
