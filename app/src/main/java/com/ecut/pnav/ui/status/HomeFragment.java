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


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.ecut.pnav.R;
import com.ecut.pnav.MainActivity;
import com.ecut.pnav.databinding.FragmentHomeBinding;
import com.ecut.pnav.ui.GnssMeasurementListener;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements GnssMeasurementListener {

    private TextView mTextView;
    private FragmentHomeBinding binding;
    HomeViewModel homeViewModel;
    private GnssSkyView gnssSkyView;
    private GnssSatelliteDetailView gnssSatelliteDetailView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometerSensor;
    private Sensor magneticFieldSensor;
    private float[] rotationMatrix = new float[9];
    private float[] orientationValues = new float[3];
    private float[] accelerometerValues = new float[3];
    private float[] magneticValues = new float[3];
    private boolean useRotationVector = true;

    public static final int PERMISSION_REQUEST_CODE = 123;
    LocationManager mLocationManager;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // 初始化 LocationManager 对象
        mLocationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        // 检查是否具有位置权限
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // 如果已经有位置权限，则注册 GNSS 测量回调监听器
            RegisterMeasurements();
            RegisterGnssStatus();
            registerLocationListener();
        }
        // 注意：不再在此处调用 requestPermissions()，统一由 MainActivity 管理
        final TextView showbds = root.findViewById(R.id.bdssat_count);
        final TextView showgps = root.findViewById(R.id.gpssat_count);
        final TextView showglonass = root.findViewById(R.id.glonasssat_count);
        final TextView showgalileo = root.findViewById(R.id.galileosat_count);
        final TextView showqzss = root.findViewById(R.id.qzsssat_count);
        final TextView showirnss = root.findViewById(R.id.irnsssat_count);
        // 找到GnssSkyView视图
        gnssSkyView = root.findViewById(R.id.gnssSkyView);
        gnssSkyView.setOnSatelliteClickListener(this::showSatelliteDetailPopup);
        // 找到卫星详细状态视图
        gnssSatelliteDetailView = root.findViewById(R.id.gnssSatelliteDetailView);

        // 初始化方向传感器（用于天空图旋转）
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationSensor == null) {
                // 无陀螺仪设备：使用加速度计 + 磁力计组合
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                useRotationVector = false;
            }
        }
        // 注册方向传感器（在初始化之后）
        if (sensorManager != null) {
            if (useRotationVector && rotationSensor != null) {
                sensorManager.registerListener(orientationListener, rotationSensor, SensorManager.SENSOR_DELAY_UI);
            } else if (accelerometerSensor != null && magneticFieldSensor != null) {
                sensorManager.registerListener(orientationListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
                sensorManager.registerListener(orientationListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }

//        final TextView textView = binding.textHome;
        homeViewModel.getbds().observe(getViewLifecycleOwner(), showbds::setText);
        homeViewModel.getgps().observe(getViewLifecycleOwner(), showgps::setText);
        homeViewModel.getglonass().observe(getViewLifecycleOwner(), showglonass::setText);
        homeViewModel.getgalileo().observe(getViewLifecycleOwner(), showgalileo::setText);
        homeViewModel.getqzss().observe(getViewLifecycleOwner(), showqzss::setText);
        homeViewModel.getirnss().observe(getViewLifecycleOwner(), showirnss::setText);

        // 频段细分
        final TextView bdsDetail = root.findViewById(R.id.bds_band_detail);
        final TextView gpsDetail = root.findViewById(R.id.gps_band_detail);
        final TextView glonassDetail = root.findViewById(R.id.glonass_band_detail);
        final TextView galileoDetail = root.findViewById(R.id.galileo_band_detail);
        final TextView qzssDetail = root.findViewById(R.id.qzss_band_detail);
        final TextView irnssDetail = root.findViewById(R.id.irnss_band_detail);
        homeViewModel.getBdsBandDetail().observe(getViewLifecycleOwner(), bdsDetail::setText);
        homeViewModel.getGpsBandDetail().observe(getViewLifecycleOwner(), gpsDetail::setText);
        homeViewModel.getGlonassBandDetail().observe(getViewLifecycleOwner(), glonassDetail::setText);
        homeViewModel.getGalileoBandDetail().observe(getViewLifecycleOwner(), galileoDetail::setText);
        homeViewModel.getQzssBandDetail().observe(getViewLifecycleOwner(), qzssDetail::setText);
        homeViewModel.getIrnssBandDetail().observe(getViewLifecycleOwner(), irnssDetail::setText);
        homeViewModel.getSatelliteDetails().observe(getViewLifecycleOwner(), data -> {
            if (gnssSatelliteDetailView != null) {
                gnssSatelliteDetailView.setSatelliteDetails(data);
            }
        });

        // Location info observers (14 fields)
        final TextView locLat = root.findViewById(R.id.location_lat_value);
        final TextView locLon = root.findViewById(R.id.location_lon_value);
        final TextView locAlt = root.findViewById(R.id.location_alt_value);
        final TextView locSpeed = root.findViewById(R.id.location_speed_value);
        final TextView locSpeedAcc = root.findViewById(R.id.location_speed_acc_value);
        final TextView locPdop = root.findViewById(R.id.location_pdop_value);
        final TextView locTime = root.findViewById(R.id.location_time_value);
        final TextView locTtff = root.findViewById(R.id.location_ttff_value);
        final TextView locHvAcc = root.findViewById(R.id.location_hv_acc_value);
        final TextView locNumSats = root.findViewById(R.id.location_num_sats_value);
        final TextView locBearing = root.findViewById(R.id.location_bearing_value);
        final TextView locBearingAcc = root.findViewById(R.id.location_bearing_acc_value);
        final TextView locHvDop = root.findViewById(R.id.location_hv_dop_value);
        homeViewModel.getLocationLat().observe(getViewLifecycleOwner(), locLat::setText);
        homeViewModel.getLocationLon().observe(getViewLifecycleOwner(), locLon::setText);
        homeViewModel.getLocationAlt().observe(getViewLifecycleOwner(), locAlt::setText);
        homeViewModel.getLocationSpeed().observe(getViewLifecycleOwner(), locSpeed::setText);
        homeViewModel.getLocationSpeedAcc().observe(getViewLifecycleOwner(), locSpeedAcc::setText);
        homeViewModel.getLocationPdop().observe(getViewLifecycleOwner(), locPdop::setText);
        homeViewModel.getLocationTime().observe(getViewLifecycleOwner(), locTime::setText);
        homeViewModel.getLocationTtff().observe(getViewLifecycleOwner(), locTtff::setText);
        homeViewModel.getLocationHvAcc().observe(getViewLifecycleOwner(), locHvAcc::setText);
        homeViewModel.getLocationNumSats().observe(getViewLifecycleOwner(), locNumSats::setText);
        homeViewModel.getLocationBearing().observe(getViewLifecycleOwner(), locBearing::setText);
        homeViewModel.getLocationBearingAcc().observe(getViewLifecycleOwner(), locBearingAcc::setText);
        homeViewModel.getLocationHvDop().observe(getViewLifecycleOwner(), locHvDop::setText);
        homeViewModel.getCarrierPhaseWarning().observe(getViewLifecycleOwner(), supported -> {
            if (supported != null && !supported) {
                Toast.makeText(requireContext(), R.string.no_carrier_phase_warning, Toast.LENGTH_LONG).show();
            }
        });

        // 双频状态指示
        final TextView dualFreqValue = root.findViewById(R.id.dual_freq_value);
        final View dualFreqDot = root.findViewById(R.id.dual_freq_dot);
        homeViewModel.getDualFreqStatus().observe(getViewLifecycleOwner(), status -> {
            if (dualFreqValue != null) dualFreqValue.setText(status);
        });
        homeViewModel.getDualFreqColor().observe(getViewLifecycleOwner(), color -> {
            if (dualFreqDot != null && color != null) {
                dualFreqDot.getBackground().setTint(color);
            }
        });

        // 权限在 onCreateView 之前已授予，现在补注册
        if (permissionGrantedPending) {
            permissionGrantedPending = false;
            RegisterMeasurements();
            RegisterGnssStatus();
            registerLocationListener();
        }

        return root;
    }
    /* ── GnssMeasurementListener 实现 ── */
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
        Log.d("status", "measurement called");
        homeViewModel.processGnssMeasurements(eventArgs);
    }
    private final android.location.GnssStatus.Callback gnssStatusCallback =
            new android.location.GnssStatus.Callback() {
                @Override
                public void onStarted() {
                    super.onStarted();
                    Log.d("GnssStatus", "onStarted() called");
                }

                @Override
                public void onStopped() {
                    super.onStopped();
                    Log.d("GnssStatus", "onStopped() called");
                }

                @Override
                public void onFirstFix(int ttffMillis) {
                    super.onFirstFix(ttffMillis);
                    Log.d("GnssStatus", "onFirstFix() called with ttffMillis: " + ttffMillis);
                    homeViewModel.updateTtff(ttffMillis);
                }

                @Override
                public void onSatelliteStatusChanged(@NonNull android.location.GnssStatus status) {
                    super.onSatelliteStatusChanged(status);
                    List<SatelliteInfo> satelliteInfoList = new ArrayList<>();
                    int usedCount = 0;
                    int totalCount = 0;
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        int constellationType = status.getConstellationType(i);
                        // 过滤增强系统(QZSS/IRNSS/SBAS)，天空视图只显示四大系统
                        if (constellationType == android.location.GnssStatus.CONSTELLATION_QZSS
                                || constellationType == android.location.GnssStatus.CONSTELLATION_IRNSS
                                || constellationType == android.location.GnssStatus.CONSTELLATION_SBAS) {
                            continue;
                        }
                        float elevation = status.getElevationDegrees(i);
                        float azimuth = status.getAzimuthDegrees(i);
                        int prn = status.getSvid(i);
                        boolean used = status.usedInFix(i);
                        if (used) usedCount++;
                        totalCount++;
                        float cn0 = homeViewModel.getCn0ForSatellite(constellationType, prn);
                        satelliteInfoList.add(new SatelliteInfo(elevation, azimuth, prn, constellationType, cn0, used));
                    }
                    gnssSkyView.setSatelliteInfo(satelliteInfoList);
                    homeViewModel.processDetailedStatus(status);
                    homeViewModel.updateSatelliteCount(totalCount, usedCount);
                }
            };

    private final SensorEventListener orientationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (useRotationVector && event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationValues);
                updateAzimuth(orientationValues[0]);
            } else if (!useRotationVector) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, accelerometerValues, 0, 3);
                }
                if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, magneticValues, 0, 3);
                }
                if (accelerometerValues[0] != 0 && magneticValues[0] != 0) {
                    float[] R = new float[9];
                    if (SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticValues)) {
                        SensorManager.getOrientation(R, orientationValues);
                        updateAzimuth(orientationValues[0]);
                    }
                }
            }
        }

        private void updateAzimuth(float rad) {
            float azimuth = (float) Math.toDegrees(rad);
            if (azimuth < 0) azimuth += 360;
            if (gnssSkyView != null) {
                gnssSkyView.setOrientation(azimuth);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private boolean gnssStatusRegistered = false;
    private boolean measurementsRegistered = false;
    private boolean permissionGrantedPending = false;  // 权限可能在 onCreateView 之前授予

    private void RegisterGnssStatus() {
        if (gnssStatusRegistered) return;
        if (mLocationManager == null) {
            // onCreateView 还未执行，等 permissionGrantedPending 标志在 onViewCreated 中触发
            return;
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        boolean is_register_success =
                mLocationManager.registerGnssStatusCallback(gnssStatusCallback);
        if (is_register_success) {
            gnssStatusRegistered = true;
            Log.d("HomeFragment", "GnssStatus callback registered OK");
        } else {
            Toast.makeText(requireContext(), "Failed to register GNSS measurement callback listener", Toast.LENGTH_SHORT).show();
        }
    }
    /* ── 通过 MainActivity 统一回调获取测量数据 ── */
    public void RegisterMeasurements() {
        if (measurementsRegistered) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addMeasurementListener(this);
        }
        measurementsRegistered = true;
    }

    public void unRegisterMeasurements() {
        measurementsRegistered = false;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeMeasurementListener(this);
        }
        if (locationListener != null) {
            mLocationManager.removeUpdates(locationListener);
        }
    }

    private LocationListener locationListener;
    private android.location.OnNmeaMessageListener nmeaListener;

    private void registerLocationListener() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                homeViewModel.updateLocation(location);
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) { }

            @Override
            public void onProviderDisabled(@NonNull String provider) { }
        };
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);

        nmeaListener = (message, timestamp) -> homeViewModel.processNmea(message);
        mLocationManager.addNmeaListener(nmeaListener, null);
    }
    public void unRegisterGNSSstatus(){
        gnssStatusRegistered = false;
        mLocationManager.unregisterGnssStatusCallback(gnssStatusCallback);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        unRegisterMeasurements();
        unRegisterGNSSstatus();
        if (locationListener != null) {
            mLocationManager.removeUpdates(locationListener);
        }
        if (nmeaListener != null) {
            mLocationManager.removeNmeaListener(nmeaListener);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(orientationListener);
        }
    }

    /** 由 MainActivity 在位置权限授予后调用 */
    public void onLocationPermissionGranted() {
        if (mLocationManager == null) {
            // onCreateView 还没跑完，先记下状态，等 onCreateView 末尾处理
            permissionGrantedPending = true;
            return;
        }
        RegisterMeasurements();
        RegisterGnssStatus();
        registerLocationListener();
    }

    private void showSatelliteDetailPopup(SatelliteInfo info, float anchorX, float anchorY) {
        View popupView = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_satellite_detail, null);

        String sysName = HomeViewModel.Gtypetostring(info.constellationType);
        ((TextView) popupView.findViewById(R.id.popup_sat_prn))
                .setText(sysName + " " + info.prn);
        ((TextView) popupView.findViewById(R.id.popup_sat_system)).setText(sysName);
        ((TextView) popupView.findViewById(R.id.popup_sat_cn0))
                .setText(String.format("%.1f dB-Hz", info.cn0));
        ((TextView) popupView.findViewById(R.id.popup_sat_elevation))
                .setText(String.format("%.0f°", info.elevation));
        ((TextView) popupView.findViewById(R.id.popup_sat_azimuth))
                .setText(String.format("%.0f°", info.azimuth));
        ((TextView) popupView.findViewById(R.id.popup_sat_used))
                .setText(info.usedInFix ? R.string.label_yes : R.string.label_no);

        PopupWindow popup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);

        // 偏移使浮窗显示在点击的卫星旁边
        int offsetX = dpToPx(16);
        int offsetY = dpToPx(8);
        popup.showAsDropDown(gnssSkyView, (int) anchorX + offsetX, (int) anchorY + offsetY - gnssSkyView.getHeight());
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}