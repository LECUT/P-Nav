package com.ecut.pnav.ui;

import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;

/**
 * 统一分发 GNSS 数据的监听器接口。
 * Android 只支持一个 registerGnssMeasurementsCallback，
 * 由 MainActivity 持有唯一回调，通过此接口分发给所有订阅者。
 *
 * GnssStatus 和 NMEA 回调也经由 MainActivity 统一注册后，
 * 通过此接口的 default 方法分发给订阅者（向后兼容，已有实现者无需改动）。
 */
public interface GnssMeasurementListener {
    void onGnssMeasurementsReceived(GnssMeasurementsEvent event);

    /** GnssStatus 更新 — 由 MainActivity 统一分发 */
    default void onGnssStatusChanged(GnssStatus status) {}

    /** NMEA 语句 — 由 MainActivity 统一分发 */
    default void onNmeaReceived(long timestampMs, String nmea) {}
}
