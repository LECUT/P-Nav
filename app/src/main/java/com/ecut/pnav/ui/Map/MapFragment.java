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
package com.ecut.pnav.ui.Map;

import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;
import com.ecut.pnav.R;
import com.ecut.pnav.positiondatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapFragment extends Fragment {

    private MapViewModel mViewModel;
    MapView mMapView = null;
    private LocationClient mLocationClient;
    private boolean isFirstLoc = true;

    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(MapViewModel.class);

        if (isAdded() && getActivity() != null) {
            try {
                LocationClient.setAgreePrivacy(true);
                mLocationClient = new LocationClient(requireContext().getApplicationContext());
            } catch (Exception e) {
                Log.e("MapFragment", "Failed to init LocationClient", e);
            }
        }
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMapView = view.findViewById(R.id.bmapView);
        mViewModel.mBaiduMap = mMapView.getMap();
        mViewModel.mBaiduMap.setMyLocationEnabled(true);

        if (mLocationClient != null) {
            mLocationClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation location) {
                }
            });
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll");
            option.setFirstLocType(LocationClientOption.FirstLocType.SPEED_IN_FIRST_LOC);
            option.setScanSpan(1000);
            option.setOpenGnss(true);
            option.setLocationNotify(true);
            option.setIgnoreKillProcess(false);
            option.SetIgnoreCacheException(false);
            option.setWifiCacheTimeOut(5 * 60 * 1000);
            option.setEnableSimulateGnss(false);
            option.setNeedNewVersionRgc(true);
            mLocationClient.setLocOption(option);
            mLocationClient.start();
        }

        positiondatabase mydb = new positiondatabase(getContext());
        mViewModel.db = mydb.getReadableDatabase();
        mViewModel.cursor = mViewModel.db.rawQuery("SELECT * FROM my_table ORDER BY id DESC LIMIT 1", null);

        if (mViewModel.cursor.moveToFirst()) {
            updatePositionFromCursor();
            if (mViewModel.bd09llPoint != null) {
                MapStatus status = new MapStatus.Builder()
                        .target(mViewModel.bd09llPoint)
                        .zoom(19.0f)
                        .build();
                mViewModel.mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(status));
                isFirstLoc = false;
            }
        }

        // 加载历史轨迹
        loadTrajectoryFromDb();

        mViewModel.originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.position_icon);
        mViewModel.scaledBitmap = scaleBitmap(mViewModel.originalBitmap, 0.06f, 0.06f);
        mViewModel.bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(mViewModel.scaledBitmap);
        MyLocationConfiguration config = new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.FOLLOWING, false,
                mViewModel.bitmapDescriptor, mViewModel.accuracyCircleFillColor,
                mViewModel.accuracyCircleStrokeColor);
        mViewModel.mBaiduMap.setMyLocationConfiguration(config);

        mViewModel.timer = new Timer();
        mViewModel.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mViewModel.db == null || mMapView == null) return;
                mViewModel.cursor = mViewModel.db.rawQuery(
                        "SELECT * FROM my_table ORDER BY id DESC LIMIT 1", null);
                if (mViewModel.cursor != null && mViewModel.cursor.moveToFirst()) {
                    updatePositionFromCursor();
                    if (mViewModel.mBaiduMap != null && mViewModel.locData != null) {
                        mViewModel.mBaiduMap.setMyLocationData(mViewModel.locData);
                        if (isFirstLoc && mViewModel.bd09llPoint != null) {
                            isFirstLoc = false;
                            mMapView.post(() -> {
                                MapStatus status = new MapStatus.Builder()
                                        .target(mViewModel.bd09llPoint)
                                        .zoom(19.0f)
                                        .build();
                                mViewModel.mBaiduMap.animateMapStatus(
                                        MapStatusUpdateFactory.newMapStatus(status));
                            });
                        }
                    }
                }
                // 追加新增的轨迹点
                appendNewTrajectoryPoints();
            }
        }, 2000, 500);
    }

    @SuppressLint("Range")
    private void updatePositionFromCursor() {
        mViewModel.strdsm1 = mViewModel.cursor.getString(
                mViewModel.cursor.getColumnIndex("dsm1"));
        mViewModel.strdsm2 = mViewModel.cursor.getString(
                mViewModel.cursor.getColumnIndex("dsm2"));

        mViewModel.dsm1 = strtodouble(mViewModel.strdsm1);
        mViewModel.dsm2 = strtodouble(mViewModel.strdsm2);
        mViewModel.wgs84lat = mViewModel.dsm1[0] + mViewModel.dsm1[1] / 60.0 + mViewModel.dsm1[2] / 3600.0;
        mViewModel.wgs84lng = mViewModel.dsm2[0] + mViewModel.dsm2[1] / 60.0 + mViewModel.dsm2[2] / 3600.0;
        LatLng wgs84Point = new LatLng(mViewModel.wgs84lat, mViewModel.wgs84lng);
        CoordinateConverter converter = new CoordinateConverter();
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(wgs84Point);
        mViewModel.bd09llPoint = converter.convert();
        mViewModel.locData = new MyLocationData.Builder()
                .latitude(mViewModel.bd09llPoint.latitude)
                .longitude(mViewModel.bd09llPoint.longitude)
                .build();
    }

    // ==================== 轨迹折线 ====================

    /** 从 SQLite 加载全部历史位置点，转换为 BD09 坐标并绘制折线 */
    private void loadTrajectoryFromDb() {
        if (mViewModel.db == null || mViewModel.mBaiduMap == null) return;

        Cursor cursor = mViewModel.db.rawQuery(
                "SELECT * FROM my_table ORDER BY id ASC", null);
        if (cursor == null) return;

        List<LatLng> points = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") long id = cursor.getLong(cursor.getColumnIndex("id"));
                @SuppressLint("Range") String dsm1 = cursor.getString(cursor.getColumnIndex("dsm1"));
                @SuppressLint("Range") String dsm2 = cursor.getString(cursor.getColumnIndex("dsm2"));
                LatLng bd09 = dsmToBd09(dsm1, dsm2);
                if (bd09 != null) {
                    points.add(bd09);
                    mViewModel.lastTrajectoryId = Math.max(mViewModel.lastTrajectoryId, id);
                }
            }
        } finally {
            cursor.close();
        }

        mViewModel.trajectoryPoints.clear();
        mViewModel.trajectoryPoints.addAll(points);
        drawTrajectory();
    }

    /** 追加自上次查询以来新增的轨迹点 */
    private void appendNewTrajectoryPoints() {
        if (mViewModel.db == null || mViewModel.mBaiduMap == null) return;

        Cursor cursor = mViewModel.db.rawQuery(
                "SELECT * FROM my_table WHERE id > " + mViewModel.lastTrajectoryId
                        + " ORDER BY id ASC", null);
        if (cursor == null) return;

        boolean hasNew = false;
        try {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") long id = cursor.getLong(cursor.getColumnIndex("id"));
                @SuppressLint("Range") String dsm1 = cursor.getString(cursor.getColumnIndex("dsm1"));
                @SuppressLint("Range") String dsm2 = cursor.getString(cursor.getColumnIndex("dsm2"));
                LatLng bd09 = dsmToBd09(dsm1, dsm2);
                if (bd09 != null) {
                    mViewModel.trajectoryPoints.add(bd09);
                    mViewModel.lastTrajectoryId = Math.max(mViewModel.lastTrajectoryId, id);
                    hasNew = true;
                }
            }
        } finally {
            cursor.close();
        }

        if (hasNew) {
            drawTrajectory();
        }
    }

    /** 将度分秒字符串转换为 BD09 LatLng */
    private LatLng dsmToBd09(String dsm1, String dsm2) {
        try {
            double[] parts1 = strtodouble(dsm1);
            double[] parts2 = strtodouble(dsm2);
            double lat = parts1[0] + parts1[1] / 60.0 + parts1[2] / 3600.0;
            double lng = parts2[0] + parts2[1] / 60.0 + parts2[2] / 3600.0;
            LatLng wgs84 = new LatLng(lat, lng);
            CoordinateConverter converter = new CoordinateConverter();
            converter.from(CoordinateConverter.CoordType.GPS);
            converter.coord(wgs84);
            return converter.convert();
        } catch (Exception e) {
            return null;
        }
    }

    /** 在地图上绘制/刷新轨迹折线 */
    private void drawTrajectory() {
        if (mViewModel.mBaiduMap == null) return;
        List<LatLng> points = mViewModel.trajectoryPoints;
        if (points == null || points.size() < 2) return;

        mMapView.post(() -> {
            // 移除旧折线
            if (mViewModel.trajectoryPolyline != null) {
                mViewModel.trajectoryPolyline.remove();
            }
            // 画新折线
            PolylineOptions options = new PolylineOptions()
                    .points(new ArrayList<>(points))
                    .color(0xFF2196F3)   // 蓝色
                    .width(8);
            mViewModel.trajectoryPolyline = (Polyline) mViewModel.mBaiduMap.addOverlay(options);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    public void onPause() {
        if (mMapView != null) mMapView.onPause();
        if (mViewModel.timer != null) {
            mViewModel.timer.cancel();
            mViewModel.timer = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mViewModel.timer != null) {
            mViewModel.timer.cancel();
        }
        if (mViewModel.cursor != null) {
            mViewModel.cursor.close();
            mViewModel.cursor = null;
        }
        if (mViewModel.db != null) {
            mViewModel.db.close();
            mViewModel.db = null;
        }
        if (mMapView != null)
            mMapView.onDestroy();
        if (mLocationClient != null)
            mLocationClient.stop();
        if (mViewModel.mBaiduMap != null)
            mViewModel.mBaiduMap.setMyLocationEnabled(false);
    }

    private Bitmap scaleBitmap(Bitmap bitmap, float scaleX, float scaleY) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    static double[] strtodouble(String str) {
        String[] numberStrings = str.split(", ");
        double[] doubleArray = new double[numberStrings.length];
        for (int i = 0; i < numberStrings.length; i++) {
            doubleArray[i] = Double.parseDouble(numberStrings[i]);
        }
        return doubleArray;
    }
}
