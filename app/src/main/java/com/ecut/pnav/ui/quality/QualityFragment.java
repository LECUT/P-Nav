package com.ecut.pnav.ui.quality;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.ecut.pnav.R;
import com.ecut.pnav.MainActivity;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.measure_util;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.android.material.tabs.TabLayout;

public class QualityFragment extends Fragment implements GnssMeasurementListener {

    private static final String TAG = "QF";
    private static final int PERMISSION_REQUEST_CODE = 125;
    private static final long REPORT_INTERVAL_MS = 1000;
    private int mEpochCount;  // 诊断用历元计数
    private int mLastRefreshedEpoch = -1;  // 跳过重复 JSON 解析

    /* ── Native methods ─────────────────────────────────────────────── */
    static { System.loadLibrary("pnav"); }
    private static native void qualityInit();
    private static native void qualityProcessEpoch(
            int[] constellations, int[] svids,
            double[] freqs, double[] snrs, double[] prs,
            double[] adrs, int[] adrStates, float[] elevations,
            int n, long recvTimeNanos);
    private static native String qualityGetReport();
    private static native String qualityGetTimeSeries();
    private static native void qualityFree();

    /* ── UI ─────────────────────────────────────────────────────────── */
    private TextView statusSats, statusEpoch;
    private TextView statusGps, statusBds, statusGlo, statusGal;
    private LabeledScatterChart chartCn0Elev;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable reportUpdater;

    /* Charts */
    private LineChart chartIon, chartMp, chartTdPr, chartTdCp;
    /* 数据完整率 — 4 条横向进度条 */
    private View compFillGps, compFillBds, compFillGlo, compFillGal;
    private TextView compPctGps, compPctBds, compPctGlo, compPctGal;
    private LineChart chartSlipTs;
    /* 图表导航 Tab + 容器 */
    private TabLayout mQualityTabs;
    private android.widget.ViewFlipper mQualityFlipper;
    private boolean mAdrChecked = false;
    private boolean mAdrAvailable = true;
    private boolean chartsReady = false;
    private volatile boolean mViewDestroyed = false;

    /* ── C/N0 时序 + 卡片 ── */
    /* C/N0 时序 LineChart + 系统选择 chips */
    private LineChart chartCn0Ts;
    private TextView chipCn0G, chipCn0R, chipCn0C, chipCn0E;
    private int cn0ActiveTab = 0;  // 0=G, 1=R, 2=C, 3=E
    private TextView chipBandL1, chipBandL5;
    private int mBandFilter = 0;  // 0=全部, 1=L1, 2=L5
    /** C/N0 时序滑动窗口: key="G12_L1" → 时序 Entry 列表 */
    private final Map<String, List<Entry>> cn0TimelineData = new LinkedHashMap<>();
    private int cn0EpochCounter = 0;
    private static final int MAX_CN0_HISTORY = 40;
    private static final int MAX_CN0_KEYS = 48;  // 防止卫星轮换导致 key 无限增长
    private boolean mCn0AutoTabDone = false;
    private static final char[] CN0_PREFIXES = {'G', 'R', 'C', 'E'};

    /** 最近一个历元的 C/N0 快照 */
    private final List<Cn0Snapshot> mCn0Snapshots = new ArrayList<>();

    private final int clrGps = Color.rgb(0, 122, 255);
    private final int clrBds = Color.rgb(255, 59, 48);
    private final int clrGlo = Color.rgb(52, 199, 89);
    private final int clrGal = Color.rgb(255, 149, 0);

    /* Elevation cache — key = (constellation << 8) | svid */
    private final SparseArray<Float> elevationCache = new SparseArray<>();
    private LocationManager mLocationManager;
    private final GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            elevationCache.clear();

            for (int i = 0; i < status.getSatelliteCount(); i++) {
                int key = (status.getConstellationType(i) << 8) | status.getSvid(i);
                float elev = status.getElevationDegrees(i);
                elevationCache.put(key, elev);
            }
        }
    };

    /* ── GnssMeasurementListener 实现 ── */
    private int epochCount = 0;
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
        epochCount++;
        if (epochCount % 30 == 1) Log.d(TAG, "epoch #" + epochCount + " measurements=" + eventArgs.getMeasurements().size());
        try { processEpochNative(eventArgs); }
        catch (Exception e) { Log.e(TAG, "epoch error", e); }
    }

    /** GnssStatus 由 MainActivity 统一分发，复用原有 gnssStatusCallback 逻辑 */
    @Override
    public void onGnssStatusChanged(@NonNull GnssStatus status) {
        gnssStatusCallback.onSatelliteStatusChanged(status);
    }

    /* ── 通过 MainActivity 统一回调获取测量数据 ── */
    private boolean measurementsRegistered = false;
    private boolean permissionGrantedPending = false;

    private void registerMeasurements() {
        if (measurementsRegistered) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addMeasurementListener(this);
        }
        // GnssStatus 回调现在由 MainActivity 统一注册并分发
        measurementsRegistered = true;
    }

    /* ── Lifecycle ──────────────────────────────────────────────────── */

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView start");
        mViewDestroyed = false;  // ViewPager 回切时重置
        try { qualityInit(); } catch (Exception e) { Log.e(TAG, "qualityInit", e); }
        mLocationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);

        View root = inflater.inflate(R.layout.fragment_quality, container, false);

        /* ── 顶部状态栏 ── */
        statusSats = root.findViewById(R.id.status_sats);
        statusGps = root.findViewById(R.id.status_gps);
        statusBds = root.findViewById(R.id.status_bds);
        statusGlo = root.findViewById(R.id.status_glo);
        statusGal = root.findViewById(R.id.status_gal);

        /* ── 侧栏完整率 ── */
        compFillGps = root.findViewById(R.id.comp_fill_gps);
        compFillBds = root.findViewById(R.id.comp_fill_bds);
        compFillGlo = root.findViewById(R.id.comp_fill_glo);
        compFillGal = root.findViewById(R.id.comp_fill_gal);
        compPctGps = root.findViewById(R.id.comp_pct_gps);
        compPctBds = root.findViewById(R.id.comp_pct_bds);
        compPctGlo = root.findViewById(R.id.comp_pct_glo);
        compPctGal = root.findViewById(R.id.comp_pct_gal);

        /* C/N0 时序 */
        chartCn0Ts = root.findViewById(R.id.chart_cn0_ts);
        chipCn0G = root.findViewById(R.id.chip_cn0_g);
        chipCn0R = root.findViewById(R.id.chip_cn0_r);
        chipCn0C = root.findViewById(R.id.chip_cn0_c);
        chipCn0E = root.findViewById(R.id.chip_cn0_e);
        chipBandL1 = root.findViewById(R.id.chip_band_l1);
        chipBandL5 = root.findViewById(R.id.chip_band_l5);
        chartCn0Elev = root.findViewById(R.id.chart_cn0_elev);

        /* ── 误差图表 ── */
        chartIon = root.findViewById(R.id.chart_ion);
        chartMp = root.findViewById(R.id.chart_mp);
        chartTdPr = root.findViewById(R.id.chart_td_pr);
        chartTdCp = root.findViewById(R.id.chart_td_cp);

        /* ── 周跳 ── */
        chartSlipTs = root.findViewById(R.id.chart_slip_ts);

        statusEpoch = root.findViewById(R.id.status_epoch);

        /* ── 图表导航 Tab + ViewFlipper ── */
        mQualityTabs = root.findViewById(R.id.quality_tabs);
        mQualityFlipper = root.findViewById(R.id.quality_flipper);
        if (mQualityTabs != null && mQualityFlipper != null) {
            // 8 个 Tab 对应 ViewFlipper 的 8 个子视图
            int[] tabLabels = {
                    R.string.cn0_ts_title, R.string.cn0_elev_title, R.string.ion_rate_title,
                    R.string.mp_title, R.string.td_pr_title, R.string.td_cp_title,
                    R.string.slip_stats_title
            };
            for (int res : tabLabels) {
                mQualityTabs.addTab(mQualityTabs.newTab().setText(getString(res)));
            }
            mQualityTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    if (mQualityFlipper != null && tab.getPosition() < mQualityFlipper.getChildCount()) {
                        mQualityFlipper.setDisplayedChild(tab.getPosition());
                    }
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        /* Defer chart setup to after layout pass */
        root.post(() -> {
            try {
                setupLineChart(chartIon);
                styleLineChart(chartIon);
                setupLineChart(chartMp);
                styleLineChart(chartMp);
                setupLineChart(chartTdPr);
                styleLineChart(chartTdPr);
                setupLineChart(chartTdCp);
                styleLineChart(chartTdCp);
                setupLineChart(chartSlipTs);
                styleLineChart(chartSlipTs);
                chartSlipTs.getAxisLeft().setAxisMinimum(0f);
                chartSlipTs.getAxisLeft().setGranularity(1f);
                chartSlipTs.getAxisLeft().setGranularityEnabled(true);
                chartSlipTs.getAxisLeft().setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        if (value < 0) return "";
                        return String.valueOf((int) value);
                    }
                });
                setupLineChart(chartCn0Ts);
                chartCn0Ts.setDrawGridBackground(true);
                chartCn0Ts.setGridBackgroundColor(0xFFF5F5F5);
                chartCn0Ts.setHardwareAccelerationEnabled(true);
                chartCn0Ts.getLegend().setEnabled(true);
                chartCn0Ts.getLegend().setTextSize(9f);
                chartCn0Ts.getLegend().setFormSize(7f);
                chartCn0Ts.getLegend().setXEntrySpace(5f);
                chartCn0Ts.getLegend().setFormToTextSpace(3f);
                chartCn0Ts.getLegend().setOrientation(Legend.LegendOrientation.HORIZONTAL);
                chartCn0Ts.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
                chartCn0Ts.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
                chartCn0Ts.getLegend().setDrawInside(false);
                chartCn0Ts.getLegend().setWordWrapEnabled(true);
                chartCn0Ts.getLegend().setMaxSizePercent(1.0f);
                chartCn0Ts.setVisibleXRangeMaximum(MAX_CN0_HISTORY);
                chartCn0Ts.setExtraBottomOffset(6f);
                chartCn0Ts.getAxisLeft().setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
                chartCn0Ts.getAxisLeft().setAxisMinimum(20f);
                chartCn0Ts.getAxisLeft().setAxisMaximum(55f);
                chartCn0Ts.getAxisLeft().setGranularity(5f);
                chartCn0Ts.getAxisLeft().setGranularityEnabled(true);
                chartCn0Ts.getAxisLeft().setDrawGridLines(true);
                chartCn0Ts.getAxisLeft().setGridColor(0xFFE8E8E8);
                chartCn0Ts.getAxisLeft().setTextSize(9f);
                // 坐标轴边框线
                chartCn0Ts.getAxisLeft().setDrawAxisLine(true);
                chartCn0Ts.getAxisLeft().setAxisLineColor(0xFFBDBDBD);
                chartCn0Ts.getAxisLeft().setAxisLineWidth(1f);
                // X轴 — 和散点图统一风格
                chartCn0Ts.getXAxis().setTextColor(0xFF555555);
                chartCn0Ts.getXAxis().setTextSize(10f);
                chartCn0Ts.getXAxis().setDrawGridLines(true);
                chartCn0Ts.getXAxis().setGridColor(0xFFE8E8E8);
                chartCn0Ts.getXAxis().setDrawAxisLine(true);
                chartCn0Ts.getXAxis().setAxisLineColor(0xFFBDBDBD);
                chartCn0Ts.getXAxis().setAxisLineWidth(1f);
                chartCn0Ts.getXAxis().setGranularity(1f);
                chartCn0Ts.getXAxis().setLabelCount(5, true);
                chartCn0Ts.getXAxis().setAxisMinimum(0f);
                chartCn0Ts.getXAxis().setAxisMaximum(MAX_CN0_HISTORY);
                chartCn0Ts.getXAxis().setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        return String.valueOf((int) value);
                    }
                });
                chartCn0Ts.setMarker(new Cn0MarkerView(getContext(), R.layout.marker_cn0_value));
                chartCn0Ts.setData(new LineData());
                chartCn0Ts.setNoDataText("");
                setupScatterChart(chartCn0Elev);
                chartsReady = true;
                Log.d(TAG, "charts ready");
            } catch (Exception e) { Log.e(TAG, "chart setup", e); }
        });

        /* C/N0 时序星座 Tab 切换 */
        setupCn0Tabs();
        // 清理可能残留的 NaN 条目（旧版断线代码遗留）
        for (List<Entry> series : cn0TimelineData.values()) {
            series.removeIf(e -> Float.isNaN(e.getY()));
        }
        cn0TimelineData.entrySet().removeIf(e -> e.getValue().isEmpty());
        switchCn0Tab(0);  // 初始选中 GPS，同时重置 mBandFilter=0

        /* 频段筛选 Chip */
        if (chipBandL1 != null) chipBandL1.setOnClickListener(v -> {
            mBandFilter = (mBandFilter == 1) ? 0 : 1;
            updateBandChipVisibility();
            updateCn0Timeline();
        });
        if (chipBandL5 != null) chipBandL5.setOnClickListener(v -> {
            mBandFilter = (mBandFilter == 2) ? 0 : 2;
            updateBandChipVisibility();
            updateCn0Timeline();
        });

        /* 周跳系统筛选 Chip — 多选勾选模式 */

        reportUpdater = () -> {
            if (mViewDestroyed || !isAdded()) return;
            try { refreshUIFromNative(); }
            catch (Exception e) { Log.e(TAG, "refresh: " + e.getMessage(), e); }
            uiHandler.postDelayed(reportUpdater, REPORT_INTERVAL_MS);
        };
        uiHandler.postDelayed(reportUpdater, REPORT_INTERVAL_MS + 500);

        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            registerMeasurements();
        }
        // 权限在 onCreateView 之前已授予，现在补注册
        if (permissionGrantedPending) {
            permissionGrantedPending = false;
            registerMeasurements();
        }
        // 注意：不再在此处调用 requestPermissions()，统一由 MainActivity 管理
        Log.d(TAG, "onCreateView done");
        return root;
    }

    @Override
    public void onDestroyView() {
        mViewDestroyed = true;  // 立刻阻断所有后续 UI 刷新
        super.onDestroyView();
        try { uiHandler.removeCallbacks(reportUpdater); } catch (Exception ignored) {}
        try { unregisterMeasurements(); } catch (Exception ignored) {}
        try { qualityFree(); } catch (Exception ignored) {}
        // 清理累积数据，Fragment 重建时从零开始
        synchronized (cn0TimelineData) { cn0TimelineData.clear(); }
        mCn0AutoTabDone = false;
        cn0EpochCounter = 0;
        mLastRefreshedEpoch = -1;
        mAdrChecked = false;
        mAdrAvailable = true;
        chartsReady = false;
    }

    private void unregisterMeasurements() {
        measurementsRegistered = false;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).removeMeasurementListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (isAdded() && ActivityCompat.checkSelfPermission(requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                registerMeasurements();
            }
        } catch (Exception e) { Log.e(TAG, "onResume error", e); }
    }

    /** 由 MainActivity 在位置权限授予后调用 */
    public void onLocationPermissionGranted() {
        if (mLocationManager == null) {
            permissionGrantedPending = true;
            return;
        }
        registerMeasurements();
    }

    /* ── Chart setup ────────────────────────────────────────────────── */

    private void setupLineChart(LineChart chart) {
        if (chart == null) return;
        chart.setNoDataText(getString(R.string.quality_no_data));
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        chart.getLegend().setTextSize(10f);
    }

    /** 统一白底网格样式 — 电离层/多径/TD/周跳图表共用 */
    private void styleLineChart(LineChart chart) {
        chart.setDrawGridBackground(true);
        chart.setGridBackgroundColor(0xFFF5F5F5);
        chart.getAxisLeft().setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(0xFFE8E8E8);
        chart.getAxisLeft().setTextSize(9f);
        // 坐标轴边框线（左边框 + 底边框）
        chart.getAxisLeft().setDrawAxisLine(true);
        chart.getAxisLeft().setAxisLineColor(0xFFBDBDBD);
        chart.getAxisLeft().setAxisLineWidth(1f);
        chart.getXAxis().setDrawGridLines(true);
        chart.getXAxis().setGridColor(0xFFE8E8E8);
        chart.getXAxis().setTextColor(0xFF555555);
        chart.getXAxis().setTextSize(10f);
        chart.getXAxis().setDrawAxisLine(true);
        chart.getXAxis().setAxisLineColor(0xFFBDBDBD);
        chart.getXAxis().setAxisLineWidth(1f);
        // 图例右上角，避免遮挡数据
        chart.getLegend().setTextSize(9f);
        chart.getLegend().setFormSize(7f);
        chart.getLegend().setXEntrySpace(5f);
        chart.getLegend().setFormToTextSpace(3f);
        chart.getLegend().setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        chart.getLegend().setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        chart.getLegend().setOrientation(Legend.LegendOrientation.HORIZONTAL);
        chart.getLegend().setDrawInside(false);
    }

    private void setupScatterChart(ScatterChart chart) {
        if (chart == null) return;
        chart.setNoDataText(getString(R.string.corr_waiting));
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(true);
        chart.setGridBackgroundColor(0xFFF5F5F5);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        // Y轴：内部刻度 + 网格线
        chart.getAxisLeft().setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        chart.getAxisLeft().setTextSize(9f);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(60f);
        chart.getAxisLeft().setGranularity(10f);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(0xFFE0E0E0);
        chart.getAxisLeft().setDrawAxisLine(true);
        chart.getAxisLeft().setAxisLineColor(0xFFBDBDBD);
        chart.getAxisLeft().setAxisLineWidth(1f);
        // X轴：底部 + 网格线
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(90f);
        chart.getXAxis().setLabelCount(4, true);
        chart.getXAxis().setDrawGridLines(true);
        chart.getXAxis().setGridColor(0xFFE0E0E0);
        chart.getXAxis().setDrawAxisLine(true);
        chart.getXAxis().setAxisLineColor(0xFFBDBDBD);
        chart.getXAxis().setAxisLineWidth(1f);
        chart.getLegend().setTextSize(9f);
        chart.getLegend().setForm(Legend.LegendForm.CIRCLE);
        chart.getLegend().setFormSize(6f);
        chart.setExtraOffsets(0f, 0f, 0f, 8f);
    }

    /* ── Native processing ──────────────────────────────────────────── */

    private static final double C = 299792458.0;
    private static final int MAX_MEAS = 256;
    private final int[] tmpCons = new int[MAX_MEAS];
    private final int[] tmpSvids = new int[MAX_MEAS];
    private final double[] tmpFreqs = new double[MAX_MEAS];
    private final double[] tmpSnrs = new double[MAX_MEAS];
    private final double[] tmpPrs = new double[MAX_MEAS];
    private final double[] tmpAdrs = new double[MAX_MEAS];
    private final int[] tmpAdrStates = new int[MAX_MEAS];
    private final float[] tmpElevs = new float[MAX_MEAS];

    @SuppressWarnings("deprecation")
    private void processEpochNative(GnssMeasurementsEvent eventArgs) {
        if (mViewDestroyed) return;  // Fragment 已销毁，native 已 free
        GnssClock clock = eventArgs.getClock();
        if (clock == null) return;

        long timeNanos = clock.getTimeNanos();
        int count = eventArgs.getMeasurements().size();
        if (count > MAX_MEAS) count = MAX_MEAS;

        /* FullBiasNanos 绝对伪距 — gnss_lib_py (Stanford NAV Lab) 方案。
           接收时刻 GPS 周内秒: tRx = (TimeNanos - FullBiasNanos - BiasNanos - GPS周起点) * 1e-9
           发射时刻 GPS 周内秒: tTx = ReceivedSvTimeNanos * 1e-9
           伪距: PR = (tRx - tTx) * C

           历元间 tRx/tTx 均按真实 GPS 时间变化 → ΔP 包含真实几何变化 (~800 m/s)，
           与 ΔADR 同量级，时间差分 MP 公式中几何项正确抵消。 */
        final double WEEKSEC = 604800.0;
        final long WEEK_NANOS = 604800000000000L;
        long fullBiasNanos = clock.getFullBiasNanos();
        // 用整数运算避免 double 精度损失 (FullBiasNanos 是 61-bit 整数, double 仅 53-bit 尾数)
        long posFullBias = -fullBiasNanos;
        long gpsWeekNanos = (posFullBias / WEEK_NANOS) * WEEK_NANOS;
        double biasNanos = clock.getBiasNanos();
        long gpsTimeNs = timeNanos - fullBiasNanos - (long)biasNanos;
        double tRxSecs = (gpsTimeNs - gpsWeekNanos) * 1e-9;
        boolean tRxValid = tRxSecs > 0 && tRxSecs < WEEKSEC;

        int idx = 0;
        for (GnssMeasurement m : eventArgs.getMeasurements()) {
            if (idx >= MAX_MEAS) break;
            tmpCons[idx] = m.getConstellationType();
            tmpSvids[idx] = m.getSvid();
            double freqHz = m.getCarrierFrequencyHz();
            tmpFreqs[idx] = freqHz > 0 ? freqHz : Double.NaN;
            double cn0 = m.getCn0DbHz();
            tmpSnrs[idx] = cn0 > 0 ? cn0 : Double.NaN;

            /* 绝对伪距 = (tRx_secs - tTx_secs) * C */
            long tTx = m.getReceivedSvTimeNanos();
            int cons = m.getConstellationType();
            // BDS: BDT → GPST (+14s); GLONASS 暂不支持绝对法（TOD 非 GPS 周对齐）
            long tTxNs = (cons == GnssStatus.CONSTELLATION_BEIDOU) ? tTx + 14_000_000_000L : tTx;
            if (tTx > 0 && tRxValid && cons != GnssStatus.CONSTELLATION_GLONASS) {
                double pr = (tRxSecs - tTxNs * 1e-9) * C;
                tmpPrs[idx] = (pr >= 1e6 && pr <= 1e8) ? pr : Double.NaN;
            } else {
                tmpPrs[idx] = Double.NaN;
            }

            double rawAdr = m.getAccumulatedDeltaRangeMeters();
            int adrState = m.getAccumulatedDeltaRangeState();
            // 某些 HAL (如 Broadcom BCM47755 + Android 9) 不设置 ADR_STATE_VALID
            // 但实际提供了有效的载波相位数值 → 放宽检查，非零有限值也视为有效
            boolean adrUsable = (adrState & GnssMeasurement.ADR_STATE_VALID) != 0
                    || (Double.isFinite(rawAdr) && rawAdr != 0.0);
            tmpAdrs[idx] = adrUsable ? rawAdr : Double.NaN;

            tmpAdrStates[idx] = adrState;
            /* Look up elevation from GnssStatus cache */
            int elevKey = (tmpCons[idx] << 8) | tmpSvids[idx];
            Float elev = elevationCache.get(elevKey);
            tmpElevs[idx] = elev != null ? elev : Float.NaN;
            idx++;
        }

        /* 第二遍：同星 L1→L5 伪距借用。
           手机 L5 信号常缺 CODE_LOCK，ReceivedSvTimeNanos 无效 → PR=NaN，
           但同一卫星的 L1/L5 几何距离相同，可用 L1 PR 填补 L5 PR。 */
        for (int i = 0; i < idx; i++) {
            if (!Double.isNaN(tmpPrs[i])) continue;
            // 只处理 L5/E5a/B2a 频段 (~1176 MHz)
            double fi = tmpFreqs[i];
            if (Double.isNaN(fi) || fi > 1.3e9 || fi < 1.1e9) continue;
            // 找同星同星座 L1 频段的有效 PR
            for (int j = 0; j < idx; j++) {
                if (tmpCons[j] != tmpCons[i] || tmpSvids[j] != tmpSvids[i]) continue;
                double fj = tmpFreqs[j];
                if (Double.isNaN(fj) || fj < 1.5e9 || fj > 1.7e9) continue;
                if (Double.isFinite(tmpPrs[j])) {
                    tmpPrs[i] = tmpPrs[j];
                    break;
                }
            }
        }
        // 保存 C/N0 快照供箱线图 + 时序图使用
        synchronized (mCn0Snapshots) {
            mCn0Snapshots.clear();
            for (int i = 0; i < idx; i++) {
                if (Double.isFinite(tmpSnrs[i]) && tmpSnrs[i] > 0) {
                    mCn0Snapshots.add(new Cn0Snapshot(
                            tmpCons[i], tmpSvids[i], (float) tmpSnrs[i],
                            Double.isFinite(tmpFreqs[i]) ? tmpFreqs[i] : 0));
                }
            }
        }
        // 推送 C/N0 到时序滑动窗口
        pushCn0ToTimeline();
        qualityProcessEpoch(tmpCons, tmpSvids, tmpFreqs, tmpSnrs,
                tmpPrs, tmpAdrs, tmpAdrStates, tmpElevs, idx, timeNanos);
        mEpochCount++;
        // 精简日志：仅输出测量数和有 PR/ADR 的卫星数
        if (mEpochCount % 30 == 1) {
            int nPr = 0, nAdr = 0;
            for (int i = 0; i < idx; i++) {
                if (Double.isFinite(tmpPrs[i])) nPr++;
                if (Double.isFinite(tmpAdrs[i])) nAdr++;
            }
            Log.d(TAG, "epoch #" + mEpochCount + " meas=" + idx + " pr=" + nPr + " adr=" + nAdr);
        }
    }

    /* ── UI refresh ─────────────────────────────────────────────────── */

    private void refreshUIFromNative() {
        if (mViewDestroyed || !chartsReady) return;

        // 无新历元数据时跳过 JSON 解析和图表重建
        if (mEpochCount == mLastRefreshedEpoch) return;
        mLastRefreshedEpoch = mEpochCount;

        /* Time-series line charts */
        try {
            String tsJson = qualityGetTimeSeries();
            if (tsJson != null && tsJson.length() > 10 && !"{}".equals(tsJson)) {
                updateTimeSeriesCharts(tsJson);
            }
        } catch (Exception e) { Log.e(TAG, "timeseries: " + e.getMessage(), e); }

        /* Snapshot charts + status */
        try {
            String json = qualityGetReport();
            if (json == null || json.length() < 10) return;

            JSONObject r = new JSONObject(json);
            int epoch = r.optInt("epoch_count", 0);
            int hasAdr = r.optInt("has_adr", 1);

            if (!mAdrChecked && epoch >= 5) {
                mAdrChecked = true;
                if (hasAdr == 0) {
                    mAdrAvailable = false;
                    Log.w(TAG, "ADR unavailable — disabling carrier-phase-dependent charts");
                    disableAdrCharts();
                }
            }

            updateStatusHeader(r);
        } catch (Exception e) { Log.e(TAG, "report: " + e.getMessage(), e); }

        /* C/N0 散点图 — 实时快照，独立于时序图的星座筛选 */
        updateCn0ElevChart();
    }

    /* ── Time-series charts ─────────────────────────────────────────── */

    private void updateTimeSeriesCharts(String tsJson) {
        try {
            JSONObject ts = new JSONObject(tsJson);
            JSONArray epochs = ts.optJSONArray("epochs");
            if (epochs == null) return;
            int n = epochs.length();
            if (n == 0) return;

            updateLine4(chartSlipTs, ts, "slip", "Slips", n);
            updateCompBar(ts, n);
            updateLine4(chartTdPr, ts, "td_pr", "TD-PR", n);

            /* 以下图表依赖载波相位，ADR 不可用时跳过 */
            if (mAdrAvailable) {
                updateIonCharts(ts, n);
                updateMpChart(ts, n);
                updateLine4(chartTdCp, ts, "td_cp", "TD-CP", n);
            }
        } catch (Exception e) { Log.e(TAG, "ts parse: " + e.getMessage(), e); }
    }

    private void updateLine4(LineChart chart, JSONObject ts,
                             String prefix, String labelSuffix, int n) {
        if (chart == null) return;
        try {
            JSONArray a1 = ts.optJSONArray(prefix + "_gps");
            JSONArray a2 = ts.optJSONArray(prefix + "_glo");
            JSONArray a3 = ts.optJSONArray(prefix + "_bds");
            JSONArray a4 = ts.optJSONArray(prefix + "_gal");
            int l1 = a1 != null ? a1.length() : 0;
            int l2 = a2 != null ? a2.length() : 0;
            int l3 = a3 != null ? a3.length() : 0;
            int l4 = a4 != null ? a4.length() : 0;

            List<Entry> e1 = new ArrayList<>(), e2 = new ArrayList<>();
            List<Entry> e3 = new ArrayList<>(), e4 = new ArrayList<>();
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                if (i < l1) { float v = (float) a1.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) { e1.add(new Entry(i, v)); if (v < min) min = v; if (v > max) max = v; } }
                if (i < l2) { float v = (float) a2.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) { e2.add(new Entry(i, v)); if (v < min) min = v; if (v > max) max = v; } }
                if (i < l3) { float v = (float) a3.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) { e3.add(new Entry(i, v)); if (v < min) min = v; if (v > max) max = v; } }
                if (i < l4) { float v = (float) a4.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) { e4.add(new Entry(i, v)); if (v < min) min = v; if (v > max) max = v; } }
            }
            if (e1.isEmpty() && e2.isEmpty() && e3.isEmpty() && e4.isEmpty()) return;

            List<ILineDataSet> datasets = new ArrayList<>();
            if (!e1.isEmpty()) { LineDataSet d = new LineDataSet(e1, "G"); d.setColor(clrGps); applyLineStyle(d); datasets.add(d); }
            if (!e2.isEmpty()) { LineDataSet d = new LineDataSet(e2, "R"); d.setColor(clrGlo); applyLineStyle(d); datasets.add(d); }
            if (!e3.isEmpty()) { LineDataSet d = new LineDataSet(e3, "C"); d.setColor(clrBds); applyLineStyle(d); datasets.add(d); }
            if (!e4.isEmpty()) { LineDataSet d = new LineDataSet(e4, "E"); d.setColor(clrGal); applyLineStyle(d); datasets.add(d); }

            chart.setData(new LineData(datasets));
            if (Float.isFinite(min) && Float.isFinite(max) && max > min) {
                float pad = (max - min) * 0.15f + 0.1f;
                chart.getAxisLeft().setAxisMinimum(min - pad);
                chart.getAxisLeft().setAxisMaximum(max + pad);
            }
            chart.setVisibleXRangeMaximum(60);
            chart.moveViewToX(Math.max(0, n - 1));
            chart.invalidate();
        } catch (Exception e) { Log.e(TAG, "line4 " + prefix, e); }
    }

    /** 电离层延迟率 L1(实线)+L5(虚线)，四星座着色同一张图 */
    private void updateIonCharts(JSONObject ts, int n) {
        if (chartIon == null) return;
        try {
            String[] sysKeys = {"gps", "glo", "bds", "gal"};
            String[] sysLabels = {"G", "R", "C", "E"};
            String[][] bandLabels = {{"L1","L5"}, {"G1","G2"}, {"B1","B2"}, {"E1","E5a"}};
            int[] sysColors = {clrGps, clrGlo, clrBds, clrGal};

            List<ILineDataSet> datasets = new ArrayList<>();
            float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;

            for (int s = 0; s < 4; s++) {
                JSONArray k1Arr = ts.optJSONArray("ion_" + sysKeys[s]);
                JSONArray k2Arr = ts.optJSONArray("ion_k2_" + sysKeys[s]);

                // 第一频段实线
                List<Entry> e1 = new ArrayList<>();
                int len1 = k1Arr != null ? k1Arr.length() : 0;
                for (int i = 0; i < Math.min(n, len1); i++) {
                    float v = (float) k1Arr.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) {
                        e1.add(new Entry(i, v));
                        if (v < yMin) yMin = v; if (v > yMax) yMax = v;
                    }
                }
                if (!e1.isEmpty()) {
                    LineDataSet d = new LineDataSet(e1, sysLabels[s] + " " + bandLabels[s][0]);
                    d.setColor(sysColors[s]); d.setLineWidth(2.5f);
                    d.setDrawCircles(false); d.setDrawValues(false);
                    d.setMode(LineDataSet.Mode.LINEAR);
                    d.setForm(Legend.LegendForm.LINE); d.setFormSize(10f); d.setFormLineWidth(2.5f);
                    datasets.add(d);
                }

                // 第二频段虚线
                List<Entry> e2 = new ArrayList<>();
                int len2 = k2Arr != null ? k2Arr.length() : 0;
                for (int i = 0; i < Math.min(n, len2); i++) {
                    float v = (float) k2Arr.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) {
                        e2.add(new Entry(i, v));
                        if (v < yMin) yMin = v; if (v > yMax) yMax = v;
                    }
                }
                if (!e2.isEmpty()) {
                    LineDataSet d = new LineDataSet(e2, sysLabels[s] + " " + bandLabels[s][1]);
                    d.setColor(sysColors[s]); d.setLineWidth(2.0f);
                    d.setDrawCircles(false); d.setDrawValues(false);
                    d.setMode(LineDataSet.Mode.LINEAR);
                    d.enableDashedLine(8f, 4f, 0f);
                    d.setForm(Legend.LegendForm.LINE); d.setFormSize(10f); d.setFormLineWidth(2.0f);
                    d.setFormLineDashEffect(new DashPathEffect(new float[]{8f, 4f}, 0));
                    datasets.add(d);
                }
            }

            if (datasets.isEmpty()) return;

            chartIon.setData(new LineData(datasets));
            if (Float.isFinite(yMin) && Float.isFinite(yMax) && yMax > yMin) {
                float pad = (yMax - yMin) * 0.15f + 0.1f;
                chartIon.getAxisLeft().setAxisMinimum(yMin - pad);
                chartIon.getAxisLeft().setAxisMaximum(yMax + pad);
            }
            chartIon.setVisibleXRangeMaximum(60);
            chartIon.moveViewToX(Math.max(0, n - 1));
            chartIon.invalidate();
        } catch (Exception e) { Log.e(TAG, "ionCharts", e); }
    }

    /** 多路径组合 MP1(实线) + MP2/MP5(虚线)，四星座着色 */
    private void updateMpChart(JSONObject ts, int n) {
        if (chartMp == null) return;
        try {
            String[] sysKeys = {"gps", "glo", "bds", "gal"};
            String[] sysLabels = {"G", "R", "C", "E"};
            int[] sysColors = {clrGps, clrGlo, clrBds, clrGal};

            List<ILineDataSet> datasets = new ArrayList<>();
            float yMin = Float.MAX_VALUE, yMax = -Float.MAX_VALUE;

            for (int s = 0; s < 4; s++) {
                JSONArray mp1Arr = ts.optJSONArray("mp1_" + sysKeys[s]);
                JSONArray mp2Arr = ts.optJSONArray("mp2_" + sysKeys[s]);
                JSONArray mp5Arr = ts.optJSONArray("mp5_" + sysKeys[s]);

                // MP1 实线
                List<Entry> e1 = new ArrayList<>();
                int len = mp1Arr != null ? mp1Arr.length() : 0;
                for (int i = 0; i < Math.min(n, len); i++) {
                    float v = (float) mp1Arr.optDouble(i, Double.NaN);
                    if (Float.isFinite(v)) {
                        e1.add(new Entry(i, v));
                        if (v < yMin) yMin = v; if (v > yMax) yMax = v;
                    }
                }
                if (!e1.isEmpty()) {
                    LineDataSet d = new LineDataSet(e1, sysLabels[s] + " MP1");
                    d.setColor(sysColors[s]); d.setLineWidth(2.5f);
                    d.setDrawCircles(false); d.setDrawValues(false);
                    d.setMode(LineDataSet.Mode.LINEAR);
                    d.setForm(Legend.LegendForm.LINE); d.setFormSize(10f); d.setFormLineWidth(2.5f);
                    datasets.add(d);
                }

                // 第二频率：MP2(非L5) + MP5(L5) 合并后虚线
                List<Entry> e2 = new ArrayList<>();
                int len2 = mp2Arr != null ? mp2Arr.length() : 0;
                int len5 = mp5Arr != null ? mp5Arr.length() : 0;
                for (int i = 0; i < Math.min(n, Math.max(len2, len5)); i++) {
                    float v2 = (i < len2) ? (float) mp2Arr.optDouble(i, Double.NaN) : Float.NaN;
                    float v5 = (i < len5) ? (float) mp5Arr.optDouble(i, Double.NaN) : Float.NaN;
                    float v;
                    if (Float.isFinite(v2) && Float.isFinite(v5)) v = (v2 + v5) / 2f;
                    else if (Float.isFinite(v2)) v = v2;
                    else if (Float.isFinite(v5)) v = v5;
                    else continue;
                    e2.add(new Entry(i, v));
                    if (v < yMin) yMin = v; if (v > yMax) yMax = v;
                }
                if (!e2.isEmpty()) {
                    LineDataSet d = new LineDataSet(e2, sysLabels[s] + " MP2/5");
                    d.setColor(sysColors[s]); d.setLineWidth(2.0f);
                    d.setDrawCircles(false); d.setDrawValues(false);
                    d.setMode(LineDataSet.Mode.LINEAR);
                    d.enableDashedLine(8f, 4f, 0f);
                    d.setForm(Legend.LegendForm.LINE); d.setFormSize(10f); d.setFormLineWidth(2.0f);
                    d.setFormLineDashEffect(new DashPathEffect(new float[]{8f, 4f}, 0));
                    datasets.add(d);
                }
            }

            if (datasets.isEmpty()) return;

            chartMp.setData(new LineData(datasets));
            if (Float.isFinite(yMin) && Float.isFinite(yMax) && yMax > yMin) {
                float pad = (yMax - yMin) * 0.15f + 0.1f;
                chartMp.getAxisLeft().setAxisMinimum(yMin - pad);
                chartMp.getAxisLeft().setAxisMaximum(yMax + pad);
            }
            chartMp.setVisibleXRangeMaximum(60);
            chartMp.moveViewToX(Math.max(0, n - 1));
            chartMp.invalidate();
        } catch (Exception e) { Log.e(TAG, "mpChart", e); }
    }

    private void applyLineStyle(LineDataSet ds) {
        ds.setLineWidth(2f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.LINEAR);
        // 图例线型
        ds.setForm(Legend.LegendForm.LINE);
        ds.setFormSize(10f);
        ds.setFormLineWidth(2f);
    }

    /* ── Data Completeness — 横向进度条 ──────────────────────────────── */

    private void updateCompBar(JSONObject ts, int n) {
        if (compFillGps == null || n == 0) return;
        try {
            String[] keys = {"comp_gps", "comp_bds", "comp_glo", "comp_gal"};
            View[] fills = {compFillGps, compFillBds, compFillGlo, compFillGal};
            TextView[] pcts = {compPctGps, compPctBds, compPctGlo, compPctGal};
            int[] tintColors = {clrGps, clrBds, clrGlo, clrGal};

            // 读取百分比 + 是否有数据标记
            float[] vals = new float[4];
            boolean[] hasData = new boolean[4];
            for (int i = 0; i < 4; i++) {
                JSONArray arr = ts.optJSONArray(keys[i]);
                hasData[i] = (arr != null && arr.length() > 0);
                if (hasData[i]) {
                    vals[i] = (float) arr.optDouble(arr.length() - 1, 0.0);
                }
                vals[i] = Math.max(0f, Math.min(100f, vals[i]));
            }

            // 立即更新百分比文本
            for (int i = 0; i < 4; i++) {
                if (pcts[i] != null) {
                    pcts[i].setText(hasData[i] ? String.format(Locale.US, "%.0f%%", vals[i]) : "-");
                }
            }

            // 设置进度条宽度 — post 确保 layout 已完成后再操作
            fills[0].post(() -> {
                if (mViewDestroyed) return;
                for (int i = 0; i < 4; i++) {
                    View fill = fills[i];
                    if (fill == null) continue;
                    ViewGroup parent = (ViewGroup) fill.getParent();
                    if (parent == null) continue;
                    int pw = Math.max(parent.getWidth(), parent.getMeasuredWidth());
                    if (pw <= 0) continue;

                    ViewGroup.LayoutParams lp = fill.getLayoutParams();
                    if (lp == null) continue;
                    lp.width = (int) (pw * vals[i] / 100f);
                    fill.setLayoutParams(lp);

                    // mutate() 防止同一 drawable 资源被多 View 共享造成 tint 相互覆盖
                    android.graphics.drawable.Drawable bg = fill.getBackground();
                    if (bg != null) {
                        bg.mutate().setTint(tintColors[i]);
                    }
                }
            });
            // 触发一次绘制以确保 post 中的 runnable 在下一帧执行
            fills[0].invalidate();
        } catch (Exception e) { Log.e(TAG, "compBar", e); }
    }

    /* ── 星座前缀提取 ────────────────────────────────────────────── */

    /** 星座类型 → 单字符前缀 */
    private static char constellationPrefix(int cons) {
        switch (cons) {
            case 1: return 'G';  // GPS
            case 3: return 'R';  // GLONASS
            case 5: return 'C';  // BDS
            case 6: return 'E';  // GALILEO
            default: return '?';
        }
    }

    /* ── Status ─────────────────────────────────────────────────────── */

    /* 当设备不支持载波相位时，灰度所有依赖 ADR 的图表 */

    private void disableAdrCharts() {
        String noAdrText = getString(R.string.quality_adr_required);
        LineChart[] adrCharts = { chartIon, chartMp, chartTdCp };
        for (LineChart c : adrCharts) {
            if (c == null) continue;
            c.setNoDataText(noAdrText);
            c.setAlpha(0.35f);
            c.setTouchEnabled(false);
            c.clear();
            c.invalidate();
        }
        if (chartSlipTs != null) {
            chartSlipTs.setNoDataText(noAdrText);
            chartSlipTs.setAlpha(0.35f);
            chartSlipTs.setTouchEnabled(false);
            chartSlipTs.clear();
            chartSlipTs.invalidate();
        }
    }

    private void updateStatusHeader(JSONObject r) {
        int epoch = r.optInt("epoch_count", 0);
        int satTotal = r.optInt("sat_total", 0);

        if (statusEpoch != null) {
            statusEpoch.setText(epoch > 0 ? "EP" + epoch : "--");
        }

        if (statusSats != null) {
            statusSats.setText(satTotal + " Sats");
        }
        if (statusGps != null) statusGps.setText(String.valueOf(r.optInt("sat_gps", 0)));
        if (statusBds != null) statusBds.setText(String.valueOf(r.optInt("sat_bds", 0)));
        if (statusGlo != null) statusGlo.setText(String.valueOf(r.optInt("sat_glo", 0)));
        if (statusGal != null) statusGal.setText(String.valueOf(r.optInt("sat_gal", 0)));
    }

    /* ── C/N0 时序滑动窗口 ────────────────────────────────────────── */

    /** 从当前历元的 mCn0Snapshots 推入 cn0TimelineData 滑动窗口。
     *  只收集当前选中系统的数据，切换系统时清空重来，避免4系统并行处理吃满运存。 */
    private void pushCn0ToTimeline() {
        synchronized (mCn0Snapshots) {
            if (mCn0Snapshots.isEmpty()) return;
            cn0EpochCounter++;
            char activePrefix = CN0_PREFIXES[cn0ActiveTab];
            synchronized (cn0TimelineData) {
                for (Cn0Snapshot s : mCn0Snapshots) {
                    if (constellationPrefix(s.constellationType) != activePrefix) continue;
                    String key = constellationPrefix(s.constellationType)
                            + String.format("%02d", s.svid)
                            + "_" + freqLabel(s.constellationType, s.freqHz);
                    List<Entry> series = cn0TimelineData.computeIfAbsent(key, k -> new ArrayList<>());
                    series.add(new Entry(cn0EpochCounter, s.cn0));
                }
                // 每历元清理过期数据
                int cutoff = cn0EpochCounter - MAX_CN0_HISTORY;
                for (List<Entry> series : cn0TimelineData.values())
                    series.removeIf(e -> e.getX() < cutoff);
                cn0TimelineData.entrySet().removeIf(e -> e.getValue().isEmpty());

                // key 数量上限：超出时淘汰最久未更新的 key
                while (cn0TimelineData.size() > MAX_CN0_KEYS) {
                    String oldest = null;
                    float oldestX = Float.MAX_VALUE;
                    for (Map.Entry<String, List<Entry>> e : cn0TimelineData.entrySet()) {
                        List<Entry> v = e.getValue();
                        if (v.isEmpty()) { oldest = e.getKey(); break; }
                        float lastX = v.get(v.size() - 1).getX();
                        if (lastX < oldestX) { oldestX = lastX; oldest = e.getKey(); }
                    }
                    if (oldest != null) cn0TimelineData.remove(oldest);
                    else break;
                }
            }
        }

        // 每历元刷新 C/N0 时序图（Fragment 存活时）
        if (!mViewDestroyed) uiHandler.post(() -> updateCn0Timeline());
    }

    /* ── C/N0 时序 LineChart ───────────────────────────────────────── */

    private void setupCn0Tabs() {
        TextView[] chips = {chipCn0G, chipCn0R, chipCn0C, chipCn0E};
        for (int i = 0; i < chips.length; i++) {
            final int tab = i;
            if (chips[i] != null) {
                chips[i].setOnClickListener(v -> switchCn0Tab(tab));
            }
        }
    }

    private void switchCn0Tab(int tab) {
        cn0ActiveTab = tab;
        mBandFilter = 0;
        // 切换系统时清空窗口重新开始，只处理当前系统数据
        synchronized (cn0TimelineData) {
            cn0TimelineData.clear();
            cn0EpochCounter = 0;
        }
        updateBandChipVisibility();
        updateBandLabels(tab);
        int[] colors = {clrGps, clrGlo, clrBds, clrGal};
        TextView[] chips = {chipCn0G, chipCn0R, chipCn0C, chipCn0E};
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            if (i == tab) {
                chips[i].setTextColor(Color.WHITE);
                chips[i].setAlpha(1.0f);
                if (chips[i].getBackground() != null) {
                    chips[i].getBackground().mutate().setTint(colors[i]);
                }
            } else {
                chips[i].setTextColor(0xFF8E8E93);
                chips[i].setAlpha(0.55f);
                if (chips[i].getBackground() != null) {
                    chips[i].getBackground().mutate().setTintList(null);
                }
            }
        }
        updateCn0Timeline();
    }

    /** 根据当前星座切换频段 chip 文字 */
    private void updateBandLabels(int tab) {
        // tab: 0=GPS, 1=GLO, 2=BDS, 3=GAL
        String[][] labels = {
            {"L1", "L5"},       // GPS
            {"G1", "G2"},       // GLO
            {"B1", "B2"},       // BDS
            {"E1", "E5a"},      // GAL
        };
        if (chipBandL1 != null) chipBandL1.setText(labels[tab][0]);
        if (chipBandL5 != null) chipBandL5.setText(labels[tab][1]);
    }

    private void updateBandChipVisibility() {
        if (chipBandL1 != null) chipBandL1.setSelected(mBandFilter == 1);
        if (chipBandL5 != null) chipBandL5.setSelected(mBandFilter == 2);
    }

    private void updateCn0Timeline() {
        if (mViewDestroyed || !isAdded()) return;
        if (chartCn0Ts == null || !chartsReady) return;

        char prefix = CN0_PREFIXES[cn0ActiveTab];
        String prefixStr = String.valueOf(prefix);

        // 线程安全：拷贝数据到 UI 线程再迭代
        List<Map.Entry<String, List<Entry>>> filtered = new ArrayList<>();
        synchronized (cn0TimelineData) {
            for (Map.Entry<String, List<Entry>> e : cn0TimelineData.entrySet()) {
                if (!e.getKey().startsWith(prefixStr)) continue;
                if (mBandFilter == 1 && !e.getKey().contains("_L1") && !e.getKey().contains("_E1")
                        && !e.getKey().contains("_B1") && !e.getKey().contains("_G1")) continue;
                if (mBandFilter == 2 && !e.getKey().contains("_L5") && !e.getKey().contains("_E5")) continue;
                // 深拷贝 Entry 列表避免并发修改
                List<Entry> copy = new ArrayList<>(e.getValue());
                filtered.add(new AbstractMap.SimpleEntry<>(e.getKey(), copy));
            }
        }
        if (filtered.isEmpty()) {
            // 空数据：清除高亮 + setData(null)，避免 marker drawMarkers 对空 dataset 触发 NPE
            chartCn0Ts.highlightValues(null);
            chartCn0Ts.setData(null);
            chartCn0Ts.getXAxis().setAxisMinimum(0f);
            chartCn0Ts.getXAxis().setAxisMaximum(MAX_CN0_HISTORY);
            chartCn0Ts.invalidate();
            return;
        }

        filtered.sort((a, b) -> a.getKey().compareTo(b.getKey()));

        List<ILineDataSet> datasets = new ArrayList<>();
        float globalMin = Float.MAX_VALUE, globalMax = -Float.MAX_VALUE;
        float xMin = Float.MAX_VALUE, xMax = -Float.MAX_VALUE;

        for (Map.Entry<String, List<Entry>> e : filtered) {
            String key = e.getKey();
            List<Entry> raw = e.getValue();
            if (raw.isEmpty()) continue;

            int svid = 0;
            try { svid = Integer.parseInt(key.substring(1, 3)); } catch (Exception ignored) {}

            List<Entry> indexed = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                float x = raw.get(i).getX();
                float v = raw.get(i).getY();
                indexed.add(new Entry(x, v));
                if (x < xMin) xMin = x;
                if (x > xMax) xMax = x;
                if (v < globalMin) globalMin = v;
                if (v > globalMax) globalMax = v;
            }

            // 从 key 提取频段标签，如 "G12_L2" → label="L2"
            int sep = key.lastIndexOf('_');
            String band = sep >= 0 ? key.substring(sep + 1) : key;
            LineDataSet ds = new LineDataSet(indexed, prefix + String.valueOf(svid) + " " + band);
            ds.setColor(cn0LineColor(prefix, svid));
            ds.setLineWidth(2.0f);
            ds.setDrawCircles(false);
            ds.setDrawValues(false);
            ds.setMode(LineDataSet.Mode.LINEAR);
            ds.setHighlightEnabled(true);
            ds.setForm(Legend.LegendForm.LINE); ds.setFormSize(10f); ds.setFormLineWidth(2.0f);

            // 第二频率用虚线
            if (!band.equals("L1") && !band.equals("G1") && !band.equals("B1I")
                    && !band.equals("B1C") && !band.equals("E1")) {
                ds.enableDashedLine(10f, 5f, 0f);
                ds.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0));
            }
            datasets.add(ds);
        }

        if (datasets.isEmpty()) {
            // 清除高亮，避免 marker 引用已替换的 dataSet 触发 NPE
            chartCn0Ts.highlightValues(null);
            chartCn0Ts.setData(new LineData());
            chartCn0Ts.getXAxis().setAxisMinimum(0f);
            chartCn0Ts.getXAxis().setAxisMaximum(MAX_CN0_HISTORY);
            chartCn0Ts.invalidate();
            return;
        }

        // 数据刷新前清除旧高亮，防止 drawMarkers 对已替换 dataSet 崩溃
        chartCn0Ts.highlightValues(null);
        chartCn0Ts.setData(new LineData(datasets));

        if (globalMin <= globalMax) {
            float yMin = ((int)(globalMin / 5)) * 5f;
            if (yMin > globalMin) yMin -= 5f;
            yMin = Math.max(0f, yMin);
            float yMax = ((int)(globalMax / 5) + 1) * 5f;
            if (yMax - yMin < 10f) { yMin -= 5f; yMax += 5f; }
            yMin = Math.max(0f, yMin);
            chartCn0Ts.getAxisLeft().setAxisMinimum(yMin);
            chartCn0Ts.getAxisLeft().setAxisMaximum(yMax);
        }

        chartCn0Ts.getXAxis().setAxisMinimum(xMin);
        chartCn0Ts.getXAxis().setAxisMaximum(xMax);
        chartCn0Ts.setVisibleXRangeMaximum(MAX_CN0_HISTORY);
        if (Float.isFinite(xMax)) chartCn0Ts.moveViewToX(xMax);
        chartCn0Ts.invalidate();
    }

    /** C/N0 时序卫星颜色 — 按星座偏移+卫星号展开色相，32颗/星座不撞色 */
    private static int cn0LineColor(char prefix, int svid) {
        // 每个星座基础色相不同，卫星号按黄金角偏移避免相邻号同色
        float baseHue;
        switch (prefix) {
            case 'G': baseHue = 210f; break; // GPS 蓝区
            case 'R': baseHue = 120f; break; // GLO 绿区
            case 'C': baseHue =   0f; break; // BDS 红区
            case 'E': baseHue =  36f; break; // GAL 橙区
            default:  baseHue =   0f; break;
        }
        float hue = (baseHue + svid * 27.5f) % 360f;
        return Color.HSVToColor(new float[]{hue, 0.78f, 0.90f});
    }

    /** C/N0 快照 — 一个历元中单颗卫星的一个频率测量 */

    /* ── C/N0 vs 高度角散点图 ───────────────────────────────────── */

    private ScatterDataSet makeScatterSet(List<Entry> entries, String label, int color, ScatterChart.ScatterShape shape) {
        ScatterDataSet ds = new ScatterDataSet(entries, label);
        ds.setColor(color);
        ds.setScatterShape(shape);
        ds.setScatterShapeSize(12f);
        ds.setDrawValues(false);
        return ds;
    }

    private void updateCn0ElevChart() {
        if (chartCn0Elev == null) return;

        List<Cn0Snapshot> snaps;
        synchronized (mCn0Snapshots) {
            if (mCn0Snapshots.isEmpty()) return;
            snaps = new ArrayList<>(mCn0Snapshots);
        }

        char[] prefixes = {'G', 'R', 'C', 'E'};
        int[] colors = {clrGps, clrGlo, clrBds, clrGal};

        List<IScatterDataSet> datasets = new ArrayList<>();
        for (int ci = 0; ci < 4; ci++) {
            char prefix = prefixes[ci];
            List<Entry> l1Entries = new ArrayList<>();
            List<Entry> l5Entries = new ArrayList<>();
            for (Cn0Snapshot s : snaps) {
                if (constellationPrefix(s.constellationType) != prefix) continue;
                if (!Float.isFinite(s.cn0) || s.cn0 <= 0) continue;
                String fl = freqLabel(s.constellationType, s.freqHz);
                int key = (s.constellationType << 8) | s.svid;
                Float elev = elevationCache.get(key);
                if (elev == null || !Float.isFinite(elev)) continue;

                Entry e = new Entry(elev, s.cn0);
                e.setData(prefix + String.valueOf(s.svid));

                if (fl.equals("L1") || fl.equals("E1") || fl.equals("B1I") || fl.equals("B1C") || fl.equals("G1")) {
                    l1Entries.add(e);
                } else if (fl.equals("L5") || fl.equals("E5a") || fl.equals("B2a")) {
                    Entry e2 = new Entry(elev, s.cn0);
                    e2.setData(prefix + s.svid);
                    l5Entries.add(e2);
                }
            }
            if (!l1Entries.isEmpty()) datasets.add(makeScatterSet(l1Entries, prefix + " L1", colors[ci], ScatterChart.ScatterShape.CIRCLE));
            if (!l5Entries.isEmpty()) datasets.add(makeScatterSet(l5Entries, prefix + " L5", colors[ci], ScatterChart.ScatterShape.SQUARE));
        }

        if (datasets.isEmpty()) return;

        ScatterData data = new ScatterData(datasets);
        data.setDrawValues(false);  // 彻底禁止 MPAndroidChart 画数值
        chartCn0Elev.setData(data);
        // setData 会重置坐标轴，强制设回 0-60
        chartCn0Elev.getAxisLeft().setAxisMinimum(0f);
        chartCn0Elev.getAxisLeft().setAxisMaximum(60f);
        chartCn0Elev.getXAxis().setAxisMinimum(0f);
        chartCn0Elev.getXAxis().setAxisMaximum(90f);
        chartCn0Elev.invalidate();
    }

    private static class Cn0Snapshot {
        final int constellationType;
        final int svid;
        final float cn0;
        final double freqHz;

        Cn0Snapshot(int type, int svid, float cn0, double freqHz) {
            this.constellationType = type;
            this.svid = svid;
            this.cn0 = cn0;
            this.freqHz = freqHz;
        }
    }

    /** 频率短标签，如 "L1", "B1I" */
    private static String freqLabel(int type, double freqHz) {
        if (freqHz <= 0) return "";
        final double TOL = 10e6;
        double f = freqHz;
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS:
                if (Math.abs(f - 1.57542e9) < TOL) return "L1";
                if (Math.abs(f - 1.22760e9) < TOL) return "L2";
                if (Math.abs(f - 1.17645e9) < TOL) return "L5";
                return "";
            case GnssStatus.CONSTELLATION_GLONASS:
                if (f >= 1598.0625e6 - TOL && f <= 1605.375e6 + TOL) return "G1";
                if (f >= 1242.9375e6 - TOL && f <= 1248.625e6 + TOL) return "G2";
                return "G1";
            case GnssStatus.CONSTELLATION_BEIDOU:
                if (Math.abs(f - 1.561098e9) < TOL) return "B1I";
                if (Math.abs(f - 1.57542e9) < TOL) return "B1C";
                if (Math.abs(f - 1.17645e9) < TOL) return "B2a";
                if (Math.abs(f - 1.20714e9) < TOL) return "B2b";
                return "";
            case GnssStatus.CONSTELLATION_GALILEO:
                if (Math.abs(f - 1.57542e9) < TOL) return "E1";
                if (Math.abs(f - 1.17645e9) < TOL) return "E5a";
                if (Math.abs(f - 1.20714e9) < TOL) return "E5b";
                if (Math.abs(f - 1.27875e9) < TOL) return "E6";
                return "";
            default:
                return "";
        }
    }

    /* ── FDE: 逐卫星 CMC 残差异常检测 ── */

    /** 星座名称缩写映射 */
    private static String consAbbr(int cons) {
        switch (cons) {
            case 1: return "G";
            case 3: return "R";
            case 5: return "C";
            case 6: return "E";
            default: return "?";
        }
    }

    /* ── C/N0 时序图 MarkerView: 点击显示精确值 ──────────────────────── */

    private static class Cn0MarkerView extends MarkerView {
        private final TextView tv;

        Cn0MarkerView(Context context, int layoutResource) {
            super(context, layoutResource);
            tv = (TextView) getChildAt(0);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            tv.setText(String.format("%.0f", e.getY()));
            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
        }
    }
}
