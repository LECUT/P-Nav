package com.ecut.pnav.ui.data;

import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

/**
 * Debug output utilities for logging GNSS data to Android Studio Logcat.
 * Inspired by GPSTest's IOUtils.writeMeasurementToLogcat() etc.
 */
public class LogcatLogger {

    private static final String TAG = "LogcatLogger";
    private static final String NMEA_OUTPUT_TAG = "GpsOutputNmea";
    private static final String MEASURE_OUTPUT_TAG = "GpsOutputMeasure";
    private static final String NAV_OUTPUT_TAG = "GpsOutputNav";

    private static boolean logcatEnabled = false;
    private static boolean logcatNmeaEnabled = false;
    private static boolean logcatMeasureEnabled = false;
    private static boolean logcatNavEnabled = false;

    public static void setEnabled(boolean enabled) {
        logcatEnabled = enabled;
    }

    public static void setNmeaEnabled(boolean enabled) {
        logcatNmeaEnabled = enabled;
    }

    public static void setMeasureEnabled(boolean enabled) {
        logcatMeasureEnabled = enabled;
    }

    public static void setNavEnabled(boolean enabled) {
        logcatNavEnabled = enabled;
    }

    public static boolean isEnabled() {
        return logcatEnabled;
    }

    /**
     * Output NMEA sentence to Logcat with optional timestamp.
     */
    public static void writeNmea(long timestamp, String nmea) {
        if (!logcatEnabled || !logcatNmeaEnabled) return;
        if (timestamp > 0) {
            Log.d(NMEA_OUTPUT_TAG, timestamp + "," + nmea);
        } else {
            Log.d(NMEA_OUTPUT_TAG, nmea);
        }
    }

    /**
     * Output GNSS measurement to Logcat.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void writeMeasurement(GnssMeasurement measurement) {
        if (!logcatEnabled || !logcatMeasureEnabled) return;
        Log.d(MEASURE_OUTPUT_TAG, measurement.toString());
    }

    /**
     * Output all measurements from a GnssMeasurementsEvent to Logcat.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void writeMeasurements(GnssMeasurementsEvent event) {
        if (!logcatEnabled || !logcatMeasureEnabled) return;
        for (GnssMeasurement m : event.getMeasurements()) {
            Log.d(MEASURE_OUTPUT_TAG, m.toString());
        }
    }

    /**
     * Output GNSS navigation message to Logcat.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void writeNavMessage(GnssNavigationMessage message) {
        if (!logcatEnabled || !logcatNavEnabled) return;
        Log.d(NAV_OUTPUT_TAG, message.toString());
    }
}
