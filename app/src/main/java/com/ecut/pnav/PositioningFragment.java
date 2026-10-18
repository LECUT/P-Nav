package com.ecut.pnav;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMapOptions;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.DGNSS.DgnssFragment;
import com.ecut.pnav.ui.PPP.PppFragment;
import com.ecut.pnav.ui.RTK.RtkFragment;
import com.ecut.pnav.ui.Spp.SppFragment;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.SolutionMetrics;
import com.ecut.pnav.util.SessionLogger;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PositioningFragment extends Fragment {

    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private SQLiteDatabase mDb;

    // ── 模式选择器 ──
    private Chip[] mModeChips;
    private String mCurrentMode = "SPP";
    private boolean mModeSelectorReady = false;

    // ── 底部指标 ──
    private TextView mTvHAcc, mTvVAcc, mTvSpeed, mTvSat, mTvMode;

    // ── 顶部信息栏 ──
    private View mInfoEphSection;
    private View mInfoEphDot;
    private TextView mInfoEphStatus, mInfoEphName;
    private View mInfoCorsDot;
    private TextView mInfoCorsStatus, mInfoCorsSub;
    private View mInfoRtcmDot;
    private TextView mInfoRtcmStatus, mInfoRtcmBytes;
    // ── 星历详情展开 ──
    private View mEphDetail;
    private TextView mEphDetailText, mEphNextUpdate;
    private boolean mEphDetailExpanded = false;
    private boolean mEphDetailFromNav = false; // SPP getEphDetail() 已填充 nav 级详情时跳过文件级覆盖

    // ── CORS 详情展开 ──
    private View mInfoCorsSection;
    private View mCorsDetail;
    private TextView mCorsDetailText;
    private boolean mCorsDetailExpanded = false;

    // ── RTCM 详情展开 ──
    private View mInfoRtcmSection;
    private View mRtcmDetail;
    private TextView mRtcmDetailText;
    private boolean mRtcmDetailExpanded = false;

    // ── 底部抽屉面板 ──
    private TextView mSheetTitle;
    private TextView mSheetLonText;
    private ImageButton mSheetCloseBtn;
    private View mSheetFragment;
    private Fragment mCurrentDetailFragment;
    private boolean mSheetExpanded = false;
    private final List<LatLng> mTrajectoryPoints = new ArrayList<>();
    private Polyline mTrajectoryPolyline;
    private LatLng mLastTrajectoryPoint;
    private static final double MIN_TRAJECTORY_DIST = 2.0; // 米，最小间距过滤
    private long mLastTrajectoryId = 0;

    // ── 速度计算（位置差 / 时间差） ──
    private double mLastLat = Double.NaN, mLastLon = Double.NaN;
    private double mLastBd09Lat = Double.NaN, mLastBd09Lon = Double.NaN;
    private long mLastPosTimeMs = 0;

    // ── 定时刷新 ──
    private Handler mRefreshHandler;
    private Runnable mRefreshRunnable;
    private static final int REFRESH_INTERVAL = 3000;

    // ── 地图定位图标 ──
    private Bitmap mScaledBitmap;
    private BitmapDescriptor mBitmapDescriptor;

    private boolean mSatelliteMode = false;

    // ── RTCM 广播 ──
    private final BroadcastReceiver mRtcmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshRtcmInfo();
        }
    };

    private final BroadcastReceiver mRtcmDiagReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra("diagMessage");
            if (msg != null && mInfoRtcmBytes != null) {
                mInfoRtcmBytes.setText("RTCM " + msg);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_positioning, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── 程序化创建 MapView ──
        FrameLayout mapContainer = view.findViewById(R.id.map_container);
        try {
            BaiduMapOptions options = new BaiduMapOptions()
                    .mapStatus(new MapStatus.Builder().zoom(18.0f).build())
                    .zoomControlsEnabled(false)
                    .scaleControlEnabled(false)
                    .rotateGesturesEnabled(true)
                    .overlookingGesturesEnabled(true);
            mMapView = new MapView(requireContext(), options);
            mapContainer.addView(mMapView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mBaiduMap = mMapView.getMap();
        } catch (Exception e) {
            Log.e("PositioningFragment", "百度地图初始化失败", e);
            Toast.makeText(requireContext(), R.string.pos_map_load_fail, Toast.LENGTH_LONG).show();
            mMapView = null;
            mBaiduMap = null;
        }
        if (mBaiduMap != null) {
            mBaiduMap.setMyLocationEnabled(true);
            Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.position_icon);
            mScaledBitmap = scaleBitmap(original, 0.06f, 0.06f);
            mBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(mScaledBitmap);
            MyLocationConfiguration config = new MyLocationConfiguration(
                    MyLocationConfiguration.LocationMode.FOLLOWING, false,
                    mBitmapDescriptor, 0xAAFFFF88, 0xAA00FF00);
            mBaiduMap.setMyLocationConfiguration(config);
            mBaiduMap.setViewPadding(0, (int) dpToPx(56), 0, (int) dpToPx(140));
        }

        // ── 数据库 ──
        positiondatabase helper = new positiondatabase(getContext());
        mDb = helper.getReadableDatabase();
        // 初始化到最新ID，跳过历史记录不画到地图上
        Cursor initCursor = null;
        try {
            initCursor = mDb.rawQuery("SELECT MAX(id) FROM my_table", null);
            if (initCursor != null && initCursor.moveToFirst()) {
                mLastTrajectoryId = initCursor.getLong(0);
            }
        } finally {
            if (initCursor != null) initCursor.close();
        }

        // 已通过 mLastTrajectoryId 跳到最新，不再全量加载历史

        // ── 初始化 UI ──
        initModeSelector(view);
        initMetrics(view);
        initFabs(view);
        initInfoBar(view);
        initBottomSheet(view);
        initRefreshTimer();

        // ── 解算精度指标：直接从活跃 ViewModel 观察，不再绕道数据库 ──
        SolutionMetrics.liveData.observe(getViewLifecycleOwner(), entry -> {
            if (entry == null) return;
            if (mTvHAcc != null)
                mTvHAcc.setText(entry.hAcc > 0 ? String.format(Locale.US, "%.2f", entry.hAcc) : "-");
            if (mTvVAcc != null)
                mTvVAcc.setText(entry.vAcc > 0 ? String.format(Locale.US, "%.2f", entry.vAcc) : "-");
            if (mTvSat != null)
                mTvSat.setText(entry.satCount > 0 ? String.valueOf(entry.satCount) : "-");
            if (mTvMode != null)
                mTvMode.setText(entry.mode != null ? entry.mode : mCurrentMode);
            // 把手标题：仅收起态显示"解算模式 + 经纬度"，展开态保持"收起详情"不被覆盖
            if (mSheetTitle != null && !mSheetExpanded) {
                // 收起态把手：左侧纬度，右侧经度
                boolean hasLat = !Double.isNaN(entry.lat) && entry.lat != 0;
                boolean hasLon = !Double.isNaN(entry.lon) && entry.lon != 0;
                mSheetTitle.setText(hasLat ? String.format(Locale.US, getString(R.string.pos_lat_label), entry.lat)
                        : getString(R.string.pos_slide_up));
                if (mSheetLonText != null) {
                    mSheetLonText.setText(hasLon ? String.format(Locale.US, getString(R.string.pos_lon_label), entry.lon) : "--");
                    mSheetLonText.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── 星历详情 ──
        com.ecut.pnav.ui.measure_util.ephDetail.observe(getViewLifecycleOwner(), detail -> {
            if (mEphDetailText != null && detail != null) {
                mEphDetailText.setText(detail);
                mEphDetailFromNav = detail.length() > 10; // nav 级详情非空即标记
            }
        });
        com.ecut.pnav.ui.measure_util.ephSummary.observe(getViewLifecycleOwner(), summary -> {
            if (mInfoEphStatus != null && summary != null) {
                mInfoEphStatus.setText(summary);
            }
            // ephSummary 更新意味着星历已加载/刷新，同步更新文件名行
            refreshEphInfo();
        });

        // ── RTCM 广播 ──
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(requireContext());
        lbm.registerReceiver(mRtcmReceiver, new IntentFilter("com.ecut.pnav.RTCM_DATA_RECEIVED"));
        lbm.registerReceiver(mRtcmDiagReceiver, new IntentFilter("com.ecut.pnav.RTCM_DIAG"));

        // ── 返回键处理 ──
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (mCurrentDetailFragment != null && mSheetFragment != null
                                && mSheetFragment.getVisibility() == View.VISIBLE) {
                            closeDetailSheet();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    // ═══════════════════ 模式选择器 ═══════════════════

    private void initModeSelector(View root) {
        Chip spp = root.findViewById(R.id.mode_spp);
        Chip rtk = root.findViewById(R.id.mode_rtk);
        Chip dgnss = root.findViewById(R.id.mode_dgnss);
        Chip ppp = root.findViewById(R.id.mode_ppp);
        mModeChips = new Chip[]{spp, rtk, dgnss, ppp};

        for (Chip chip : mModeChips) {
            chip.setOnClickListener(v -> {
                if (!mModeSelectorReady) return;
                Chip clicked = (Chip) v;
                // 取消其他 Chip 选中
                for (Chip c : mModeChips) {
                    if (c != clicked) c.setChecked(false);
                }
                clicked.setChecked(true);

                int id = clicked.getId();
                if (id == R.id.mode_spp) {
                    mCurrentMode = "SPP";
                    showDetailInSheet(getString(R.string.mode_spp), new SppFragment());
                } else if (id == R.id.mode_rtk) {
                    mCurrentMode = "RTK";
                    launchRTK();
                } else if (id == R.id.mode_dgnss) {
                    mCurrentMode = "DGNSS";
                    launchDGNSS();
                } else if (id == R.id.mode_ppp) {
                    mCurrentMode = "PPP";
                    showDetailInSheet(getString(R.string.mode_ppp), new PppFragment());
                }
            });
        }
        // 延迟启用，跳过初始化选中回调
        mModeChips[0].post(() -> mModeSelectorReady = true);
    }

    // ═══════════════════ 底部指标 ═══════════════════

    private void initMetrics(View root) {
        mTvHAcc = root.findViewById(R.id.metric_val_hacc);
        mTvVAcc = root.findViewById(R.id.metric_val_vacc);
        mTvSpeed = root.findViewById(R.id.metric_val_speed);
        mTvSat = root.findViewById(R.id.metric_val_sat);
        mTvMode = root.findViewById(R.id.metric_val_mode);
    }

    // ═══════════════════ FAB 按钮 ═══════════════════

    private void initFabs(View root) {
        ImageButton btnZoomIn = root.findViewById(R.id.btn_zoom_in);
        ImageButton btnZoomOut = root.findViewById(R.id.btn_zoom_out);
        ImageButton btnLocate = root.findViewById(R.id.btn_locate);
        ImageButton btnMapLayer = root.findViewById(R.id.btn_map_layer);

        if (btnZoomIn != null) btnZoomIn.setOnClickListener(v -> {
            if (mBaiduMap != null) mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomIn());
        });
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> {
            if (mBaiduMap != null) mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomOut());
        });
        if (btnLocate != null) btnLocate.setOnClickListener(v -> {
            if (mBaiduMap == null || mBitmapDescriptor == null) return;
            // 设置跟随模式
            mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(
                    MyLocationConfiguration.LocationMode.FOLLOWING, false,
                    mBitmapDescriptor, 0xAAFFFF88, 0xAA00FF00));
            // 动画飞到当前位置
            if (!Double.isNaN(mLastBd09Lat) && !Double.isNaN(mLastBd09Lon)) {
                MapStatus status = new MapStatus.Builder()
                        .target(new LatLng(mLastBd09Lat, mLastBd09Lon))
                        .zoom(mBaiduMap.getMapStatus() != null
                                ? mBaiduMap.getMapStatus().zoom : 18.0f)
                        .build();
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(status), 500);
                Toast.makeText(getContext(), R.string.pos_located, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.pos_no_location, Toast.LENGTH_SHORT).show();
            }
        });
        if (btnMapLayer != null) btnMapLayer.setOnClickListener(v -> {
            if (mBaiduMap == null) return;
            mSatelliteMode = !mSatelliteMode;
            mBaiduMap.setMapType(mSatelliteMode
                    ? BaiduMap.MAP_TYPE_SATELLITE : BaiduMap.MAP_TYPE_NORMAL);
            Toast.makeText(getContext(),
                    mSatelliteMode ? getString(R.string.pos_sat_view) : getString(R.string.pos_std_map), Toast.LENGTH_SHORT).show();
        });

        // ── 轨迹按钮已移除 ──
    }

    // ═══════════════════ 顶部信息栏 ═══════════════════

    private void initInfoBar(View root) {
        mInfoEphSection = root.findViewById(R.id.info_eph_section);
        mInfoEphDot = root.findViewById(R.id.info_eph_dot);
        mInfoEphStatus = root.findViewById(R.id.info_eph_status);
        mInfoEphName = root.findViewById(R.id.info_eph_name);
        mEphDetail = root.findViewById(R.id.eph_detail);
        mEphDetailText = root.findViewById(R.id.eph_detail_text);
        mEphNextUpdate = root.findViewById(R.id.eph_next_update);
        mInfoCorsDot = root.findViewById(R.id.info_cors_dot);
        mInfoCorsStatus = root.findViewById(R.id.info_cors_status);
        mInfoCorsSub = root.findViewById(R.id.info_cors_sub);
        mInfoRtcmDot = root.findViewById(R.id.info_rtcm_dot);
        mInfoRtcmStatus = root.findViewById(R.id.info_rtcm_status);
        mInfoRtcmBytes = root.findViewById(R.id.info_rtcm_bytes);
        mCorsDetail = root.findViewById(R.id.cors_detail);
        mCorsDetailText = root.findViewById(R.id.cors_detail_text);
        mRtcmDetail = root.findViewById(R.id.rtcm_detail);
        mRtcmDetailText = root.findViewById(R.id.rtcm_detail_text);

        // 点击星历段切换展开/收起详情
        if (mInfoEphSection != null) {
            mInfoEphSection.setOnClickListener(v -> toggleEphDetail());
        }

        mInfoCorsSection = root.findViewById(R.id.info_cors_section);
        if (mInfoCorsSection != null) {
            mInfoCorsSection.setOnClickListener(v -> toggleCorsDetail());
        }

        mInfoRtcmSection = root.findViewById(R.id.info_rtcm_section);
        if (mInfoRtcmSection != null) {
            mInfoRtcmSection.setOnClickListener(v -> toggleRtcmDetail());
        }

        // ── 星历模式 Chip 已移至解算配置Tab ──
    }

    // ═══════════════════ 底部抽屉面板 ═══════════════════

    private void initBottomSheet(View root) {
        mSheetTitle = root.findViewById(R.id.sheet_title);
        mSheetLonText = root.findViewById(R.id.sheet_lon_text);
        mSheetCloseBtn = root.findViewById(R.id.sheet_close_btn);
        mSheetFragment = root.findViewById(R.id.sheet_fragment);

        if (mSheetCloseBtn != null) {
            mSheetCloseBtn.setOnClickListener(v -> closeDetailSheet());
        }

        // 点击把手 → 就地展开/收起解算详情（与顶部星历/CORS卡片展开一致）
        View toolbar = root.findViewById(R.id.sheet_toolbar);
        if (toolbar != null) {
            toolbar.setOnClickListener(v -> toggleDetailCard());
        }
    }

    /** 详情可展开的最大高度（屏幕约 70%） */
    private int mMaxDetailH;

    /** 就地展开解算详情（卡片向上长高，顶部对齐上方信息卡下边缘，底部不动） */
    private void expandDetailCard() {
        if (mSheetFragment == null) return;
        mSheetExpanded = true;
        if (mCurrentDetailFragment == null) loadCurrentModeDetail();

        mSheetFragment.setVisibility(View.VISIBLE);
        if (mSheetCloseBtn != null) mSheetCloseBtn.setVisibility(View.VISIBLE);
        if (mSheetLonText != null) mSheetLonText.setVisibility(View.GONE);
        if (mSheetTitle != null) mSheetTitle.setText(getString(R.string.pos_collapse));

        // 等布局完成后再测量，详情顶部对齐上方解算模式卡片下边缘
        mSheetFragment.post(() -> {
            View topCard = getView() != null ? getView().findViewById(R.id.top_card) : null;
            View metricsCard = getView() != null ? getView().findViewById(R.id.bottom_sheet) : null;
            int screenH = getResources().getDisplayMetrics().heightPixels;

            int topBottom = 0;
            if (topCard != null) {
                int[] loc = new int[2];
                topCard.getLocationOnScreen(loc);
                topBottom = loc[1] + topCard.getHeight();
            }
            int metricsH = (metricsCard != null && metricsCard.getHeight() > 0)
                    ? metricsCard.getHeight() : (int) dpToPx(90);
            // 详情顶部在解算模式卡片下方再下移一段距离
            int maxH = screenH - (topBottom + (int) dpToPx(48)) - metricsH;
            if (maxH <= 0) maxH = (int) (screenH * 0.40f);
            mMaxDetailH = maxH;

            ViewGroup.LayoutParams initLp = mSheetFragment.getLayoutParams();
            initLp.height = 0;
            mSheetFragment.setLayoutParams(initLp);
            animateDetailHeight(0, mMaxDetailH);
        });
    }

    /** 就地收起解算详情 */
    private void collapseDetailCard() {
        if (mSheetFragment == null) return;
        mSheetExpanded = false;
        if (mSheetCloseBtn != null) mSheetCloseBtn.setVisibility(View.GONE);
        // 收起后立即把手标题刷新回"解算信息"，不残留"收起详情"
        refreshCollapsedSheetTitle();
        int from = mSheetFragment.getHeight();
        animateDetailHeight(from > 0 ? from : 0, 0);
    }

    /** 用最近一次解算信息刷新收起态把手标题（无数据则显示"上滑查看详情"） */
    private void refreshCollapsedSheetTitle() {
        if (mSheetTitle == null) return;
        SolutionMetrics.Entry entry = SolutionMetrics.liveData.getValue();
        boolean hasLat = entry != null && !Double.isNaN(entry.lat) && entry.lat != 0;
        mSheetTitle.setText(hasLat
                ? String.format(Locale.US, getString(R.string.pos_lat_label), entry.lat)
                : getString(R.string.pos_slide_up));
        if (mSheetLonText != null) {
            boolean hasLon = entry != null && !Double.isNaN(entry.lon) && entry.lon != 0;
            mSheetLonText.setText(hasLon
                    ? String.format(Locale.US, getString(R.string.pos_lon_label), entry.lon) : "--");
            mSheetLonText.setVisibility(View.VISIBLE);
        }
    }

    /** 高度动画（收起后 GONE），使用丝滑的快速出入缓动 */
    private void animateDetailHeight(int from, int to) {
        if (mSheetFragment == null) return;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(from, to);
        anim.setDuration(320);
        anim.setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = mSheetFragment.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            mSheetFragment.setLayoutParams(lp);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (to == 0) {
                    mSheetFragment.setVisibility(View.GONE);
                }
            }
        });
        anim.start();
    }

    private void toggleDetailCard() {
        if (mSheetFragment != null && mSheetFragment.getVisibility() == View.VISIBLE) {
            collapseDetailCard();
        } else {
            expandDetailCard();
        }
    }

    private void showDetailInSheet(String title, Fragment fragment) {
        String modeTag = title.replaceAll("\\s+", "");
        SessionLogger.ui("Sheet打开: " + modeTag);
        mCurrentMode = modeTag;

        getChildFragmentManager().beginTransaction()
                .replace(R.id.sheet_fragment_container, fragment)
                .commit();

        if (mSheetTitle != null) mSheetTitle.setText(title);
        mCurrentDetailFragment = fragment;
        expandDetailCard();
    }

    private void loadCurrentModeDetail() {
        switch (mCurrentMode) {
            case "SPP":
                showDetailInSheet(getString(R.string.mode_spp), new SppFragment());
                break;
            case "RTK":
                launchRTK();
                break;
            case "DGNSS":
                launchDGNSS();
                break;
            case "PPP":
                showDetailInSheet(getString(R.string.mode_ppp), new PppFragment());
                break;
        }
    }

    private void closeDetailSheet() {
        SessionLogger.ui("Sheet关闭");
        // 收起态标题保留解算信息（"▲ 模式 经纬度"），不覆盖为"上滑查看详情"
        if (mSheetCloseBtn != null) mSheetCloseBtn.setVisibility(View.GONE);

        if (mCurrentDetailFragment != null) {
            getChildFragmentManager().beginTransaction()
                    .remove(mCurrentDetailFragment).commit();
            mCurrentDetailFragment = null;
        }
        collapseDetailCard();
    }

    private void launchRTK() {
        if (isMyServiceRunning() && MainActivity.if_setting) {
            showDetailInSheet(getString(R.string.mode_rtk), new RtkFragment());
        } else if (!isMyServiceRunning()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.cors_status).setMessage(R.string.cors_not_enabled)
                    .setPositiveButton(R.string.yes, null).show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.unset).setMessage(R.string.please_set_up)
                    .setPositiveButton(R.string.yes, null).show();
        }
    }

    private void launchDGNSS() {
        if (isMyServiceRunning() && MainActivity.if_setting) {
            showDetailInSheet(getString(R.string.mode_dgnss), new DgnssFragment());
        } else if (!isMyServiceRunning()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.cors_status).setMessage(R.string.cors_not_enabled)
                    .setPositiveButton(R.string.yes, null).show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.unset).setMessage(R.string.please_set_up)
                    .setPositiveButton(R.string.yes, null).show();
        }
    }

    // ═══════════════════ 数据刷新 ═══════════════════

    @SuppressLint("Range")
    private void refreshSolutionInfo() {
        if (mDb == null || !mDb.isOpen()) return;
        Cursor cursor = null;
        try {
            cursor = mDb.rawQuery("SELECT * FROM my_table ORDER BY id DESC LIMIT 1", null);
            if (cursor != null && cursor.moveToFirst()) {
                String dsm1 = cursor.getString(cursor.getColumnIndex("dsm1"));
                String dsm2 = cursor.getString(cursor.getColumnIndex("dsm2"));

                double[] p1 = strtodouble(dsm1);
                double[] p2 = strtodouble(dsm2);
                double lat = p1[0] + p1[1] / 60.0 + p1[2] / 3600.0;
                double lon = p2[0] + p2[1] / 60.0 + p2[2] / 3600.0;

                // 速度 = 位置差 / 时间差 (km/h)
                double speed = -1;
                long nowMs = System.currentTimeMillis();
                if (!Double.isNaN(mLastLat) && mLastPosTimeMs > 0
                        && Math.abs(lat - mLastLat) < 0.01
                        && Math.abs(lon - mLastLon) < 0.01) {
                    double dist = haversine(mLastLat, mLastLon, lat, lon);
                    double dtSec = (nowMs - mLastPosTimeMs) / 1000.0;
                    if (dtSec > 0.5 && dist > 0) {
                        speed = dist / dtSec * 3.6;
                    }
                }
                mLastLat = lat;
                mLastLon = lon;
                mLastPosTimeMs = nowMs;

                if (mTvSpeed != null)
                    mTvSpeed.setText(speed > 0 ? String.format(Locale.US, "%.1f", speed) : "-");
                updateMapLocation(dsm1, dsm2);
            }
        } catch (Exception e) {
            // 静默处理
        } finally {
            if (cursor != null) cursor.close();
        }

        refreshEphInfo();
        refreshCorsInfo();
        refreshRtcmInfo();
    }

    /** 星历 — 收起态只显示简短摘要，同时生成文件级详情填充展开区（SPP 读取 nav 后会覆盖为完整详情） */
    private void refreshEphInfo() {
        if (mInfoEphDot == null) return;

        // 先检查精密星历
        String precSp3 = (MainActivity.outputFilePath != null) ? MainActivity.outputFilePath[1] : null;
        String[] navPaths = com.ecut.pnav.MainActivity.navFilePaths;

        if (precSp3 != null && new File(precSp3).exists()) {
            mInfoEphDot.getBackground().setTint(0xFF34C759);
            buildFileEphDetail(navPaths, precSp3);
            return;
        }
        int existCount = 0;
        for (String fp : navPaths) {
            if (fp != null && new File(fp).exists()) existCount++;
        }

        if (existCount > 0) {
            mInfoEphDot.getBackground().setTint(0xFF34C759);
            // 收起态只显示最近更新时间
            if (mInfoEphName != null) {
                long latest = 0;
                for (String fp : navPaths) {
                    if (fp != null) { File f = new File(fp); if (f.exists() && f.lastModified() > latest) latest = f.lastModified(); }
                }
                String ds = latest > 0 ? new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(latest)) : "";
                mInfoEphName.setText(ds.isEmpty() ? getString(R.string.pos_eph_loaded)
                        : getString(R.string.pos_eph_updated, ds));
            }
            // ★ 展开区：文件级详情（SPP 读 nav 后会覆盖为系统计数+TOE 详情）
            buildFileEphDetail(navPaths, precSp3);
        } else {
            if (mInfoEphName != null) mInfoEphName.setText(getString(R.string.eph_not_loaded));
            mInfoEphDot.getBackground().setTint(0xFFFF9500);
            if (mEphDetailText != null) mEphDetailText.setText(getString(R.string.pos_no_eph));
        }
    }

    /** 基于文件信息构建展开区详情（不需要 nav_t 解析）。SPP 已填充 nav 级详情时跳过。 */
    private void buildFileEphDetail(String[] navPaths, String precSp3) {
        if (mEphDetailText == null || mEphDetailFromNav) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.pos_broadcast_eph)).append("\n");

        String[] sysLabels = {"GPS", "GLO", "BDS", "GAL"};
        for (int i = 0; i < navPaths.length && i < sysLabels.length; i++) {
            String fp = navPaths[i];
            if (fp == null) continue;
            File f = new File(fp);
            if (f.exists()) {
                sb.append("  ").append(sysLabels[i]).append("  ")
                  .append(f.length() / 1024).append("KB  ")
                  .append(sdf.format(new Date(f.lastModified()))).append("\n");
            } else {
                sb.append("  ").append(sysLabels[i]).append("  ").append(getString(R.string.pos_not_loaded)).append("\n");
            }
        }

        // 精密星历
        if (precSp3 != null) {
            File f = new File(precSp3);
            if (f.exists()) {
                sb.append("\n").append(getString(R.string.pos_precise_eph)).append("\n  SP3  ").append(f.length() / 1024).append("KB  ")
                  .append(sdf.format(new Date(f.lastModified())));
            }
        }

        mEphDetailText.setText(sb.toString().trim());
    }

    private void toggleEphDetail() {
        if (mEphDetail == null) return;
        CloseOthers("eph");
        mEphDetailExpanded = !mEphDetailExpanded;
        mEphDetail.setVisibility(mEphDetailExpanded ? View.VISIBLE : View.GONE);
        if (mEphDetailExpanded && mEphNextUpdate != null) {
            long now = System.currentTimeMillis();
            long nextCheck = ((now / 1800_000) + 1) * 1800_000;
            long remainMin = (nextCheck - now) / 60_000;
            mEphNextUpdate.setText(getString(R.string.pos_next_check, remainMin));
        }
    }

    private void toggleCorsDetail() {
        if (mCorsDetail == null) return;
        CloseOthers("cors");
        mCorsDetailExpanded = !mCorsDetailExpanded;
        mCorsDetail.setVisibility(mCorsDetailExpanded ? View.VISIBLE : View.GONE);
        if (mCorsDetailExpanded) refreshCorsDetailText();
    }

    private void toggleRtcmDetail() {
        if (mRtcmDetail == null) return;
        CloseOthers("rtcm");
        mRtcmDetailExpanded = !mRtcmDetailExpanded;
        mRtcmDetail.setVisibility(mRtcmDetailExpanded ? View.VISIBLE : View.GONE);
        if (mRtcmDetailExpanded) refreshRtcmDetailText();
    }

    private void CloseOthers(String except) {
        if (!"eph".equals(except)) {
            mEphDetailExpanded = false;
            if (mEphDetail != null) mEphDetail.setVisibility(View.GONE);
        }
        if (!"cors".equals(except)) {
            mCorsDetailExpanded = false;
            if (mCorsDetail != null) mCorsDetail.setVisibility(View.GONE);
        }
        if (!"rtcm".equals(except)) {
            mRtcmDetailExpanded = false;
            if (mRtcmDetail != null) mRtcmDetail.setVisibility(View.GONE);
        }
    }

    private void refreshCorsDetailText() {
        if (mCorsDetailText == null || getContext() == null) return;
        StringBuilder sb = new StringBuilder();
        boolean running = isMyServiceRunning();
        sb.append(getString(R.string.pos_ntrip_conn, running ? getString(R.string.pos_ntrip_connected2) : getString(R.string.pos_ntrip_disconnected))).append("\n");

        android.content.SharedPreferences sp = getContext().getSharedPreferences(
                getContext().getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE);
        String ip = sp.getString("User_ip", "");
        String port = sp.getString("User_port", "");
        String mp = sp.getString("Mount_point", "");
        String user = sp.getString("User_name", "");
        sb.append(getString(R.string.pos_server)).append(": ").append(ip.isEmpty() ? "-" : ip).append(":").append(port.isEmpty() ? "-" : port).append("\n");
        sb.append(getString(R.string.pos_mountpoint)).append(": ").append(mp.isEmpty() ? "-" : mp).append("\n");
        sb.append(getString(R.string.pos_user)).append(": ").append(user.isEmpty() ? "-" : user).append("\n");

        // RTCM解码统计
        if (running) {
            com.rtklib.bean.nav_t corsNav = com.ecut.pnav.ui.Ntrip.NtripFragment.extractRtcmNav();
            if (corsNav != null) {
                sb.append(getString(R.string.pos_decoded_groups, corsNav.n, corsNav.ng));
            } else {
                sb.append(getString(R.string.pos_waiting_eph));
            }
        }
        mCorsDetailText.setText(sb.toString().trim());
    }

    private void refreshRtcmDetailText() {
        if (mRtcmDetailText == null) return;
        StringBuilder sb = new StringBuilder();
        boolean running = isMyServiceRunning();
        sb.append(getString(R.string.pos_rtcm_flow, running ? getString(R.string.pos_rtcm_receiving) : getString(R.string.pos_rtcm_not_receiving))).append("\n");

        if (running) {
            long b = MyService.rtcmTotalBytes;
            String bs;
            if (b >= 1024 * 1024) bs = String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
            else if (b >= 1024) bs = String.format(Locale.US, "%.1f KB", b / 1024.0);
            else bs = b + " B";
            sb.append(getString(R.string.pos_rtcm_total)).append(": ").append(bs).append("\n");
            sb.append(getString(R.string.pos_rtcm_obs)).append(": ").append(getString(R.string.pos_count_units, MyService.rtcmCountObs)).append("\n");
            sb.append(getString(R.string.pos_rtcm_sta)).append(": ").append(getString(R.string.pos_count_units, MyService.rtcmCountSta)).append("\n");
            sb.append(getString(R.string.pos_rtcm_other)).append(": ").append(getString(R.string.pos_count_units, MyService.rtcmCountOther)).append("\n");

            // 消息类型分布
            int total = MyService.rtcmCountObs + MyService.rtcmCountSta + MyService.rtcmCountOther;
            if (total > 0) {
                sb.append(getString(R.string.pos_rtcm_obs_ratio)).append(": ").append(String.format(Locale.US, "%.0f%%", 100.0 * MyService.rtcmCountObs / total));
            }
        }
        mRtcmDetailText.setText(sb.toString().trim());
    }

    /** CORS / NTRIP */
    private void refreshCorsInfo() {
        if (mInfoCorsDot == null) return;
        boolean running = isMyServiceRunning();
        if (running) {
            mInfoCorsDot.getBackground().setTint(0xFF34C759);
            if (mInfoCorsSub != null && getContext() != null) {
                android.content.SharedPreferences sp = getContext().getSharedPreferences(
                        getContext().getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE);
                String ip = sp.getString("User_ip", "");
                String port = sp.getString("User_port", "");
                String mp = sp.getString("Mount_point", "");
                if (!ip.isEmpty()) {
                    mInfoCorsSub.setText(getString(R.string.cors_connected) + " · " + ip + ":" + port + " / " + mp);
                } else {
                    mInfoCorsSub.setText(getString(R.string.cors_connected));
                }
            }
        } else {
            mInfoCorsDot.getBackground().setTint(0xFF8E8E93);
            if (mInfoCorsSub != null) mInfoCorsSub.setText(getString(R.string.cors_disconnected));
        }
        if (mCorsDetailExpanded) refreshCorsDetailText();
    }

    /** RTCM */
    private void refreshRtcmInfo() {
        if (mInfoRtcmDot == null) return;
        boolean running = isMyServiceRunning();
        if (running && MyService.rtcmTotalBytes > 0) {
            mInfoRtcmDot.getBackground().setTint(0xFF34C759);
            long b = MyService.rtcmTotalBytes;
            String bs;
            if (b >= 1024 * 1024) bs = String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
            else if (b >= 1024) bs = String.format(Locale.US, "%.1f KB", b / 1024.0);
            else bs = b + " B";
            if (mInfoRtcmBytes != null) {
                mInfoRtcmBytes.setText(bs + " · obs=" + MyService.rtcmCountObs
                        + " sta=" + MyService.rtcmCountSta + " other=" + MyService.rtcmCountOther);
            }
        } else if (running) {
            mInfoRtcmDot.getBackground().setTint(0xFFFF9500);
            if (mInfoRtcmBytes != null) mInfoRtcmBytes.setText(getString(R.string.rtcm_waiting));
        } else {
            mInfoRtcmDot.getBackground().setTint(0xFF8E8E93);
            if (mInfoRtcmBytes != null) mInfoRtcmBytes.setText(getString(R.string.rtcm_disconnected));
        }
        if (mRtcmDetailExpanded) refreshRtcmDetailText();
    }

    private void updateMapLocation(String dsm1, String dsm2) {
        if (mBaiduMap == null) return;
        try {
            double[] p1 = strtodouble(dsm1);
            double[] p2 = strtodouble(dsm2);
            double lat = p1[0] + p1[1] / 60.0 + p1[2] / 3600.0;
            double lon = p2[0] + p2[1] / 60.0 + p2[2] / 3600.0;
            CoordinateConverter conv = new CoordinateConverter();
            conv.from(CoordinateConverter.CoordType.GPS);
            conv.coord(new LatLng(lat, lon));
            LatLng bd09 = conv.convert();
            mLastBd09Lat = bd09.latitude;
            mLastBd09Lon = bd09.longitude;
            mBaiduMap.setMyLocationData(new com.baidu.mapapi.map.MyLocationData.Builder()
                    .latitude(bd09.latitude).longitude(bd09.longitude).build());
        } catch (Exception ignored) {}
    }

    private boolean isMyServiceRunning() {
        return MyService.isServiceRunning;
    }

    // ═══════════════════ 轨迹 ═══════════════════

    private void loadTrajectoryFromDb() {
        if (mDb == null || mBaiduMap == null) return;
        Cursor cursor = null;
        try {
            cursor = mDb.rawQuery("SELECT * FROM my_table ORDER BY id ASC", null);
            if (cursor == null) return;
            mTrajectoryPoints.clear();
            mLastTrajectoryId = 0;
            while (cursor.moveToNext()) {
                @SuppressLint("Range") long id = cursor.getLong(cursor.getColumnIndex("id"));
                @SuppressLint("Range") LatLng bd09 = dsmToBd09(
                        cursor.getString(cursor.getColumnIndex("dsm1")),
                        cursor.getString(cursor.getColumnIndex("dsm2")));
                if (bd09 != null) {
                    // 距离过滤 — 跳过与上一点太近的
                    if (mLastTrajectoryPoint != null
                            && distanceBetween(mLastTrajectoryPoint, bd09) < MIN_TRAJECTORY_DIST) {
                        mLastTrajectoryId = Math.max(mLastTrajectoryId, id);
                        continue;
                    }
                    mTrajectoryPoints.add(bd09);
                    mLastTrajectoryPoint = bd09;
                    mLastTrajectoryId = Math.max(mLastTrajectoryId, id);
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        drawTrajectory();
    }

    private void appendNewTrajectoryPoints() {
        if (mDb == null || mBaiduMap == null) return;
        Cursor cursor = null;
        boolean hasNew = false;
        try {
            cursor = mDb.rawQuery("SELECT * FROM my_table WHERE id > " + mLastTrajectoryId + " ORDER BY id ASC", null);
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                @SuppressLint("Range") long id = cursor.getLong(cursor.getColumnIndex("id"));
                @SuppressLint("Range") LatLng bd09 = dsmToBd09(
                        cursor.getString(cursor.getColumnIndex("dsm1")),
                        cursor.getString(cursor.getColumnIndex("dsm2")));
                if (bd09 != null) {
                    // 距离过滤
                    if (mLastTrajectoryPoint != null
                            && distanceBetween(mLastTrajectoryPoint, bd09) < MIN_TRAJECTORY_DIST) {
                        mLastTrajectoryId = Math.max(mLastTrajectoryId, id);
                        continue;
                    }
                    mTrajectoryPoints.add(bd09);
                    mLastTrajectoryPoint = bd09;
                    mLastTrajectoryId = Math.max(mLastTrajectoryId, id);
                    hasNew = true;
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        if (hasNew) drawTrajectory();
    }

    private LatLng dsmToBd09(String dsm1, String dsm2) {
        try {
            double[] p1 = strtodouble(dsm1);
            double[] p2 = strtodouble(dsm2);
            double lat = p1[0] + p1[1] / 60.0 + p1[2] / 3600.0;
            double lon = p2[0] + p2[1] / 60.0 + p2[2] / 3600.0;
            CoordinateConverter conv = new CoordinateConverter();
            conv.from(CoordinateConverter.CoordType.GPS);
            conv.coord(new LatLng(lat, lon));
            return conv.convert();
        } catch (Exception e) { return null; }
    }

    private void drawTrajectory() {
        // 轨迹线已移除，不再在地图上绘制
    }

    /** 渐变颜色 — 三段式等距插值: c1 → c2 → c3 */
    private static List<Integer> generateGradientColors(int count,
                                                         int c1, int c2, int c3) {
        List<Integer> list = new ArrayList<>(count);
        if (count <= 0) return list;
        int mid = count / 2;
        for (int i = 0; i < count; i++) {
            float t;
            int from, to;
            if (i <= mid) {
                t = mid == 0 ? 0f : (float) i / mid;
                from = c1;
                to = c2;
            } else {
                t = (count - 1 == mid) ? 0f : (float) (i - mid) / (count - 1 - mid);
                from = c2;
                to = c3;
            }
            list.add(lerpColor(from, to, t));
        }
        return list;
    }

    /** RGB 线性插值 */
    private static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 更新轨迹按钮状态 — 圆点颜色 + 文字标签（按钮已移除，保留空方法） */
    private void updateTrajDots() {
        boolean rec = MainActivity.if_record_trajectory;
        if (rec) {
            // 轨迹记录状态仍由 MainActivity 维护
        }
    }

    /** 清除轨迹数据 */
    private void clearTrajectory() {
        mTrajectoryPoints.clear();
        mLastTrajectoryPoint = null;

        if (mMapView != null) {
            mMapView.post(() -> {
                if (mTrajectoryPolyline != null) { mTrajectoryPolyline.remove(); mTrajectoryPolyline = null; }
            });
        }

        if (mDb != null && mDb.isOpen()) {
            Cursor cursor = null;
            try {
                cursor = mDb.rawQuery("SELECT MAX(id) FROM my_table", null);
                if (cursor != null && cursor.moveToFirst()) {
                    mLastTrajectoryId = cursor.getLong(0);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        Toast.makeText(getContext(), R.string.traj_cleared, Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════ 定时刷新 ═══════════════════

    private void initRefreshTimer() {
        mRefreshHandler = new Handler(Looper.getMainLooper());
        mRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshSolutionInfo();
                appendNewTrajectoryPoints();
                mRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        mRefreshHandler.postDelayed(mRefreshRunnable, REFRESH_INTERVAL);
    }

    // ═══════════════════ 工具方法 ═══════════════════

    private float dpToPx(float dp) { return dp * getResources().getDisplayMetrics().density; }

    private Bitmap scaleBitmap(Bitmap b, float sx, float sy) {
        Matrix m = new Matrix();
        m.postScale(sx, sy);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    static double[] strtodouble(String s) {
        if (s == null) return new double[]{0, 0, 0};
        String[] parts = s.split(", ");
        double[] r = new double[parts.length];
        for (int i = 0; i < parts.length; i++) r[i] = Double.parseDouble(parts[i]);
        return r;
    }

    /** Haversine 距离 (m) — 用于速度估算 */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 地球半径 (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 两坐标点间距离 (m) — 用于轨迹过滤 */
    private static double distanceBetween(LatLng a, LatLng b) {
        return haversine(a.latitude, a.longitude, b.latitude, b.longitude);
    }

    // ═══════════════════ 生命周期 ═══════════════════

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() instanceof MainActivity) {
            if (hidden) {
                // 被切走（如切到数据采集页），释放优先级给其他模块
                ((MainActivity) getActivity()).clearPriorityListener(null);
            } else if (mCurrentDetailFragment instanceof GnssMeasurementListener) {
                // 切回定位页，恢复当前解算模块的优先级
                ((MainActivity) getActivity()).setPriorityListener((GnssMeasurementListener) mCurrentDetailFragment);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
        if (mRefreshHandler != null && mRefreshRunnable != null) {
            mRefreshHandler.postDelayed(mRefreshRunnable, REFRESH_INTERVAL);
        }
        refreshSolutionInfo();
        appendNewTrajectoryPoints();
    }

    @Override
    public void onPause() {
        if (mMapView != null) mMapView.onPause();
        if (mRefreshHandler != null && mRefreshRunnable != null) {
            mRefreshHandler.removeCallbacks(mRefreshRunnable);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRefreshHandler != null && mRefreshRunnable != null) {
            mRefreshHandler.removeCallbacks(mRefreshRunnable);
        }
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mRtcmReceiver);
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mRtcmDiagReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBaiduMap != null) mBaiduMap.setMyLocationEnabled(false);
        if (mMapView != null) mMapView.onDestroy();
        if (mBitmapDescriptor != null) mBitmapDescriptor.recycle();
        if (mScaledBitmap != null && !mScaledBitmap.isRecycled()) mScaledBitmap.recycle();
        if (mDb != null && mDb.isOpen()) { mDb.close(); mDb = null; }
    }
}
