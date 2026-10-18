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

import static com.ecut.pnav.ui.Map.MapFragment.strtodouble;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;

import androidx.lifecycle.ViewModel;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapViewModel extends ViewModel {
    TimerTask timerTask;
    double[] dsm1 = new double[3];
    double[] dsm2 = new double[3];
    double wgs84lat;
    double wgs84lng;
    String strdsm1;
    String strdsm2;
    Timer timer;
    LatLng bd09llPoint;
    Cursor cursor;
    BaiduMap mBaiduMap;
    BitmapDescriptor bitmapDescriptor;
    Bitmap originalBitmap;
    Bitmap scaledBitmap;
    SQLiteDatabase db;
    int accuracyCircleFillColor = 0xAAFFFF88;
    int accuracyCircleStrokeColor = 0xAA00FF00;
    MyLocationData locData;

    // 轨迹折线相关
    final List<LatLng> trajectoryPoints = new ArrayList<>();  // BD09 坐标点列表
    Polyline trajectoryPolyline;                                // 地图上的折线对象
    long lastTrajectoryId = 0;                                  // 已加载的最后一条记录 ID
    boolean trajectoryVisible = true;                           // 轨迹是否可见

    public MapViewModel() {
        timer = new Timer();
        timerTask = new TimerTask() {
            @SuppressLint("Range")
            @Override
            public void run() {
                if (db == null) return;
                cursor = db.rawQuery("SELECT * FROM my_table ORDER BY id DESC LIMIT 1", null);
                if (cursor.moveToFirst()) {
                    strdsm1 = cursor.getString(cursor.getColumnIndex("dsm1"));
                    strdsm2 = cursor.getString(cursor.getColumnIndex("dsm2"));

                    dsm1 = strtodouble(strdsm1);
                    dsm2 = strtodouble(strdsm2);
                    wgs84lat = dsm1[0] + dsm1[1] / 60.0 + dsm1[2] / 3600.0;
                    wgs84lng = dsm2[0] + dsm2[1] / 60.0 + dsm2[2] / 3600.0;
                }
            }
        };
    }
}
