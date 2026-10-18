package com.ecut.pnav.ui.data;

import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

/**
 * Utilities for injecting GNSS assist data (time + PSDS/XTRA) into the GNSS chipset.
 * Inspired by GPSTest's IOUtils.forceTimeInjection() and forcePsdsInjection().
 */
public class GpsAssistUtil {

    private static final String TAG = "GpsAssistUtil";

    /**
     * Attempts to force a GNSS assistance time injection (NTP time).
     * This helps the GNSS receiver acquire a faster and more accurate time fix.
     *
     * @param locationManager the system LocationManager
     * @return true if the command was sent successfully
     */
    public static boolean forceTimeInjection(LocationManager locationManager) {
        if (locationManager == null) {
            Log.w(TAG, "forceTimeInjection: LocationManager is null");
            return false;
        }
        try {
            boolean result = locationManager.sendExtraCommand(
                    LocationManager.GPS_PROVIDER, "force_time_injection", null);
            Log.d(TAG, "forceTimeInjection result: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "forceTimeInjection failed", e);
            return false;
        }
    }

    /**
     * Attempts to force a GNSS PSDS (Predicted Satellite Data Service) injection.
     * On API 29+, uses "force_psds_injection". On older versions, uses "force_xtra_injection".
     *
     * @param locationManager the system LocationManager
     * @return true if the command was sent successfully
     */
    public static boolean forcePsdsInjection(LocationManager locationManager) {
        if (locationManager == null) {
            Log.w(TAG, "forcePsdsInjection: LocationManager is null");
            return false;
        }
        try {
            String command;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                command = "force_psds_injection";
            } else {
                command = "force_xtra_injection";
            }
            boolean result = locationManager.sendExtraCommand(
                    LocationManager.GPS_PROVIDER, command, null);
            Log.d(TAG, "forcePsdsInjection (" + command + ") result: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "forcePsdsInjection failed", e);
            return false;
        }
    }

    /**
     * Inject both time and PSDS assist data. Called once at the start of each recording session.
     *
     * @param locationManager the system LocationManager
     */
    public static void injectAssistData(LocationManager locationManager) {
        Log.d(TAG, "Injecting assist data...");
        forceTimeInjection(locationManager);
        forcePsdsInjection(locationManager);
    }
}
