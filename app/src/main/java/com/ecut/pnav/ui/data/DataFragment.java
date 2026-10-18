package com.ecut.pnav.ui.data;

import android.animation.ValueAnimator;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ecut.pnav.MainActivity;
import com.ecut.pnav.R;
import com.ecut.pnav.RawFilesFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataFragment extends Fragment {

    private static final int PERMISSION_REQUEST_CODE = 126;
    private static final String PREFS_NAME = "recording_prefs";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";

    // Header
    private ImageView statusDot;
    private TextView recordingStatus;
    // Timer capsule
    private TextView tvRecordingTime;
    private TextView tvRecordingSize;

    // Timer capsule
    private View timerCapsule;
    private View capsuleIdleContent;
    private View capsuleRecordingContent;

    // 信号强度卡片
    private View sigCard;
    private View sigGroupGps, sigGroupBds, sigGroupGlo, sigGroupGal;
    private LinearLayout sigGpsRows, sigBdsRows, sigGloRows, sigGalRows;

    // 预建的频段行（所有可能的频段，初始化时创建，运行时只更新值和可见性）
    private final Map<String, List<LinearLayout>> preBuiltSigRows = new HashMap<>();
    private static final String[][] ALL_BAND_CODES = {
        {"L1", "L2", "L5"},                         // GPS
        {"B1I", "B1C", "B2b", "B2a", "B3I"},       // BDS
        {"G1", "G2"},                                // GLO
        {"E1", "E5b", "E5a"},                        // GAL
    };
    private static final String[] SYS_NAMES = {"GPS", "BDS", "GLO", "GAL"};

    // 四大系统 tile 卡片
    private View tileGps, tileBds, tileGlo, tileGal;
    private TextView tileGpsCount, tileBdsCount, tileGloCount, tileGalCount;

    // 采集信息卡片
    private View fileOverviewCard;
    private TextView ovFileName, ovFileDir, ovFileSize, ovDuration;
    private View ovCarrierBadge, ovCarrierDot;
    private TextView ovCarrierStatus;

    // Bottom bar
    private TextView tvRecordButton;
    private TextView btnKeepScreenOn;
    private boolean keepScreenOn;

    // Animation & timers
    private ValueAnimator pulseAnimator;

    // Service
    private RecordingService recordingService;
    private boolean serviceBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            RecordingService.LocalBinder binder = (RecordingService.LocalBinder) ibinder;
            recordingService = binder.getService();
            serviceBound = true;
            Log.d("DataFragment", "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            recordingService = null;
            serviceBound = false;
            Log.d("DataFragment", "Service disconnected");
        }
    };

    // Broadcast receiver for service state updates
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RecordingService.BROADCAST_STATE_UPDATE.equals(intent.getAction())) {
                boolean recording = intent.getBooleanExtra(RecordingService.EXTRA_IS_RECORDING, false);
                if (!recording && Boolean.TRUE.equals(dataViewModel.getIsRecording().getValue())) {
                    // Service stopped but ViewModel still thinks we're recording — sync stop
                    dataViewModel.stopRecording();
                }
            }
        }
    };

    private DataViewModel dataViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);

        dataViewModel = new ViewModelProvider(this).get(DataViewModel.class);
        dataViewModel.setContext(requireContext());

        // --- Find views ---
        statusDot = view.findViewById(R.id.status_dot);
        recordingStatus = view.findViewById(R.id.recording_status);

        timerCapsule = view.findViewById(R.id.timer_capsule);
        capsuleIdleContent = view.findViewById(R.id.capsule_idle_content);
        capsuleRecordingContent = view.findViewById(R.id.capsule_recording_content);
        tvRecordingTime = view.findViewById(R.id.tv_recording_time);
        tvRecordingSize = view.findViewById(R.id.tv_recording_size);

        // 采集信息卡片
        fileOverviewCard = view.findViewById(R.id.file_overview_card);
        ovFileName = view.findViewById(R.id.ov_file_name);
        ovFileDir = view.findViewById(R.id.ov_file_dir);
        ovFileSize = view.findViewById(R.id.ov_file_size);
        ovDuration = view.findViewById(R.id.ov_duration);
        ovCarrierBadge = view.findViewById(R.id.ov_carrier_badge);
        ovCarrierDot = view.findViewById(R.id.ov_carrier_dot);
        ovCarrierStatus = view.findViewById(R.id.ov_carrier_status);

        // 信号强度卡片
        sigCard = view.findViewById(R.id.signal_card);
        sigGroupGps = view.findViewById(R.id.sig_group_gps);
        sigGroupBds = view.findViewById(R.id.sig_group_bds);
        sigGroupGlo = view.findViewById(R.id.sig_group_glo);
        sigGroupGal = view.findViewById(R.id.sig_group_gal);
        sigGpsRows = view.findViewById(R.id.sig_gps_rows);
        sigBdsRows = view.findViewById(R.id.sig_bds_rows);
        sigGloRows = view.findViewById(R.id.sig_glo_rows);
        sigGalRows = view.findViewById(R.id.sig_gal_rows);

        // 四大系统 tile
        tileGps = view.findViewById(R.id.tile_gps);
        tileBds = view.findViewById(R.id.tile_bds);
        tileGlo = view.findViewById(R.id.tile_glo);
        tileGal = view.findViewById(R.id.tile_gal);
        tileGpsCount = view.findViewById(R.id.tile_gps_count);
        tileBdsCount = view.findViewById(R.id.tile_bds_count);
        tileGloCount = view.findViewById(R.id.tile_glo_count);
        tileGalCount = view.findViewById(R.id.tile_gal_count);

        // 初始默认占位值
        resetTiles();
        resetSignalGroups();
        resetOverview();

        // 预建所有可能的频段信号行（各系统最大频段数），运行时只更新值和可见性，避免 addView/removeView 导致跳动
        preBuildAllSignalRows();

        // Timer capsule defaults
        tvRecordingTime.setText("00:00:00");
        tvRecordingSize.setText("—");

        tvRecordButton = view.findViewById(R.id.tv_record_button);
        btnKeepScreenOn = view.findViewById(R.id.btn_keep_screen_on);

        // --- Observe ViewModel ---
        dataViewModel.getIsRecording().observe(getViewLifecycleOwner(), this::updateRecordingUI);

        dataViewModel.getRecordingStats().observe(getViewLifecycleOwner(), this::updateStats);

        dataViewModel.getRecordingElapsedMs().observe(getViewLifecycleOwner(), elapsed -> {
            if (elapsed != null && Boolean.TRUE.equals(dataViewModel.getIsRecording().getValue())) {
                long seconds = elapsed / 1000;
                tvRecordingTime.setText(String.format("%02d:%02d:%02d",
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60));
                // 数据先缓冲在内存，停止录制才落盘 → 显示缓冲历元数而非文件大小
                tvRecordingSize.setText(getString(R.string.eph_epochs_buffered, dataViewModel.getBufferedEpochCount()));
                sendStatsToService(dataViewModel.getCurrentLogFile());
            }
        });

        // --- Initial state ---
        updateRecordingUI(false);

        // --- Header 观测文件入口 → 直接跳转原始观测文件页 ---
        TextView btnObsFiles = view.findViewById(R.id.btn_obs_files);
        btnObsFiles.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new RawFilesFragment())
                        .addToBackStack("raw_files")
                        .commit();
            }
        });

        // --- Bind to RecordingService ---
        Intent serviceIntent = new Intent(requireContext(), RecordingService.class);
        requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // --- Register broadcast receiver ---
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(stateReceiver,
                        new IntentFilter(RecordingService.BROADCAST_STATE_UPDATE));

        // --- Bottom bar click ---
        tvRecordButton.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(dataViewModel.getIsRecording().getValue())) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        // --- Keep screen on toggle ---
        updateKeepScreenOnButton();
        btnKeepScreenOn.setOnClickListener(v -> {
            keepScreenOn = !keepScreenOn;
            applyKeepScreenOn();
            updateKeepScreenOnButton();
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn).apply();
        });

        // --- Register GNSS callback (权限已由 MainActivity 统一管理) ---
        // 不在闲置态注册回调 —— 等到用户点击"开始录制"才激活，避免影响其他模块
        if (permissionGrantedPending) {
            permissionGrantedPending = false;
            // 权限已到位，回调由 startRecording() 触发
        }

        // --- Load logcat preference ---
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        LogcatLogger.setEnabled(prefs.getBoolean(RecordingService.KEY_LOGCAT_ENABLED, false));
        LogcatLogger.setMeasureEnabled(prefs.getBoolean(RecordingService.KEY_LOGCAT_MEASURE, true));

        // --- Load keep screen on preference ---
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false);
        applyKeepScreenOn();

        // 权限在 onCreateView 之前已授予，现在补注册
        if (permissionGrantedPending) {
            permissionGrantedPending = false;
            applyPermissionGranted();
        }

        return view;
    }

    // ==================== FAB Actions ====================

    private void startRecording() {
        try {
            Log.d("DataFragment", "Start recording");

            // 激活 GNSS 数据采集回调（仅录制时注册，闲置态不消耗资源）
            dataViewModel.registerGnssCallback();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).addMeasurementListener(dataViewModel);
            }

            android.location.LocationManager lm = (android.location.LocationManager)
                    requireContext().getSystemService(Context.LOCATION_SERVICE);
            GpsAssistUtil.injectAssistData(lm);

            Intent startIntent = new Intent(requireContext(), RecordingService.class);
            ContextCompat.startForegroundService(requireContext(), startIntent);

            Toast.makeText(requireContext(), R.string.recording, Toast.LENGTH_SHORT).show();
            dataViewModel.startRecording();
        } catch (Exception e) {
            Log.e("DataFragment", "Error starting recording", e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        try {
            Log.d("DataFragment", "Stop recording");
            dataViewModel.stopRecording();

            // 停止录制后立即注销 GNSS 数据回调，释放系统资源
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).removeMeasurementListener(dataViewModel);
            }
            dataViewModel.unregisterGnssCallback();

            if (serviceBound && recordingService != null) {
                recordingService.stopRecording();
            }
        } catch (Exception e) {
            Log.e("DataFragment", "Error stopping recording", e);
        }
    }

    private void sendStatsToService(File logFile) {
        if (!serviceBound || recordingService == null) return;
        Intent intent = new Intent(RecordingService.BROADCAST_STATS_UPDATE);
        intent.putExtra("file_name", logFile != null ? logFile.getName() : "");
        intent.putExtra("file_size", logFile != null ? logFile.length() : 0);
        intent.putExtra("epoch_count", dataViewModel.getRecordingStats().getValue() != null ?
                dataViewModel.getRecordingStats().getValue().epochCount : 0);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
    }

    // ==================== Stats Update ====================

    private void updateStats(RecordingStats stats) {
        if (stats == null) {
            // 闲置态：显示默认占位值
            resetSignalGroups();
            resetTiles();
            resetOverview();
            return;
        }
        if (stats.sysList.isEmpty()) {
            // 停止后仅刷新文件信息，保留已落盘的最终大小
            resetSignalGroups();
            resetTiles();
            if (stats.fileName != null) {
                ovFileName.setText(stats.fileName);
                ovFileDir.setText(stats.fileDir != null ? stats.fileDir : "—");
                ovFileSize.setText(formatSize(stats.fileSize));
                ovDuration.setText(formatDuration(stats.durationSeconds));
                updateCarrierBadge(stats.hasCarrierPhase);
            } else {
                resetOverview();
            }
            return;
        }

        // 填充信号强度卡片（使用预建行，只更新可见性和值）
        for (RecordingStats.SysStats sys : stats.sysList) {
            List<LinearLayout> preBuilt = preBuiltSigRows.get(sys.name);
            if (preBuilt == null) continue;

            for (int i = 0; i < preBuilt.size(); i++) {
                LinearLayout row = preBuilt.get(i);
                String rowCode = ALL_BAND_CODES[getSysIdx(sys.name)][i];

                // 查找该频段是否有当前数据
                RecordingStats.BandStats match = null;
                for (RecordingStats.BandStats b : sys.bands) {
                    if (b.code.equals(rowCode)) {
                        match = b;
                        break;
                    }
                }

                if (match != null) {
                    row.setVisibility(View.VISIBLE);
                    updateBandRow(row, match.avgCn0);
                    // 更新频段标签中的卫星数
                    if (row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView) {
                        ((TextView) row.getChildAt(0)).setText(rowCode);
                    }
                } else {
                    row.setVisibility(View.GONE);
                }
            }
        }

        // 四大系统 tile 卡片（仅显示星数）
        for (RecordingStats.SysStats sys : stats.sysList) {
            setTileCount(sys.name, sys.satCount);
        }

        // 采集信息卡片
        ovFileName.setText(stats.fileName != null ? stats.fileName : "—");
        ovFileDir.setText(stats.fileDir != null ? stats.fileDir : "—");
        ovFileSize.setText(formatSize(stats.fileSize));
        ovDuration.setText(formatDuration(stats.durationSeconds));
        updateCarrierBadge(stats.hasCarrierPhase);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
        return String.format("%.1f MB", bytes / (1024f * 1024f));
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        int m = seconds / 60;
        int s = seconds % 60;
        if (m < 60) return String.format("%d:%02d", m, s);
        int h = m / 60;
        m %= 60;
        return String.format("%d:%02d:%02d", h, m, s);
    }

    private void updateCarrierBadge(boolean hasCarrier) {
        if (hasCarrier) {
            ovCarrierDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getResources().getColor(R.color.panel_gps, null)));
            ovCarrierStatus.setText(getString(R.string.carrier_ok));
        } else {
            ovCarrierDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    0xFF5A6B80));
            ovCarrierStatus.setText(getString(R.string.carrier_na2));
        }
    }

    private void setTileCount(String sysName, int count) {
        String text = String.valueOf(count);
        switch (sysName) {
            case "GPS": tileGpsCount.setText(text); break;
            case "BDS": tileBdsCount.setText(text); break;
            case "GLO": tileGloCount.setText(text); break;
            case "GAL": tileGalCount.setText(text); break;
        }
    }

    private int getSysIdx(String sysName) {
        switch (sysName) {
            case "GPS": return 0;
            case "BDS": return 1;
            case "GLO": return 2;
            case "GAL": return 3;
        }
        return -1;
    }

    /** 更新已有的频段信号行中的 C/N0 值和 bar 宽度 */
    private void updateBandRow(LinearLayout row, int avgCn0) {
        // child[2] = C/N0 值 TextView
        if (row.getChildCount() > 2 && row.getChildAt(2) instanceof TextView) {
            ((TextView) row.getChildAt(2)).setText(avgCn0 + " dB-Hz");
        }
        // child[1] = FrameLayout bar, 更新 fill 宽度
        if (row.getChildCount() > 1 && row.getChildAt(1) instanceof FrameLayout) {
            FrameLayout bar = (FrameLayout) row.getChildAt(1);
            if (bar.getChildCount() > 1) {
                View fill = bar.getChildAt(1);
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                    bar.post(() -> {
                        int pw = bar.getWidth();
                        if (pw > 0) {
                            float ratio = Math.min(1f, avgCn0 / 50f);
                            flp.width = (int)(pw * ratio);
                            fill.setLayoutParams(flp);
                        }
                    });
                }
            }
        }
    }

    /** 动态构建一条频段行：L1 ████████░░ 42 dB-Hz */
    private LinearLayout buildBandRow(String code, int satCount, int avgCn0, int sysColorRes) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(1), 0, dp(1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        // 频段标签 " L1 "
        TextView label = new TextView(requireContext());
        label.setText(code);
        label.setTextSize(9);
        label.setTypeface(android.graphics.Typeface.MONOSPACE);
        label.setTextColor(requireContext().getResources().getColor(sysColorRes, null));
        label.setGravity(android.view.Gravity.CENTER);
        label.setWidth(dp(30));
        row.addView(label);

        // 进度条容器
        FrameLayout bar = new FrameLayout(requireContext());
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0,
                dp(6), 1);
        barLp.setMargins(dp(8), 0, dp(8), 0);
        bar.setLayoutParams(barLp);

        View bg = new View(requireContext());
        bg.setBackgroundResource(R.drawable.bg_cn0_bar);
        FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        bg.setLayoutParams(bgLp);
        bar.addView(bg);

        View fill = new View(requireContext());
        fill.setBackgroundResource(R.drawable.bg_cn0_fill);
        android.graphics.drawable.Drawable fillBg = fill.getBackground();
        if (fillBg != null) {
            fillBg.mutate().setTint(requireContext().getResources().getColor(sysColorRes, null));
        }
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0,
                FrameLayout.LayoutParams.MATCH_PARENT);
        fill.setLayoutParams(fillLp);
        bar.addView(fill);

        // 根据 avgCn0 设置 fill 宽度（max=50 dB-Hz）
        bar.post(() -> {
            int pw = bar.getWidth();
            if (pw > 0) {
                float ratio = Math.min(1f, avgCn0 / 50f);
                fillLp.width = (int)(pw * ratio);
                fill.setLayoutParams(fillLp);
            }
        });

        row.addView(bar);

        // C/N0 值 "42"
        TextView value = new TextView(requireContext());
        value.setText(avgCn0 + " dB-Hz");
        value.setTextSize(9);
        value.setTypeface(android.graphics.Typeface.MONOSPACE);
        value.setTextColor(0xCCF2F5FA); // 半透明白，深色面板可读
        value.setWidth(dp(48));
        value.setGravity(android.view.Gravity.END);
        row.addView(value);

        return row;
    }

    private int dp(float dp) {
        return (int)(dp * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 预建所有系统所有可能频段的信号行，运行时只更新值和 GONE/VISIBLE */
    private void preBuildAllSignalRows() {
        int[] colors = {R.color.panel_gps, R.color.panel_bds, R.color.panel_glo, R.color.panel_gal};
        LinearLayout[] containers = {sigGpsRows, sigBdsRows, sigGloRows, sigGalRows};

        for (int sysIdx = 0; sysIdx < 4; sysIdx++) {
            List<LinearLayout> rows = new ArrayList<>();
            for (String code : ALL_BAND_CODES[sysIdx]) {
                LinearLayout row = buildBandRow(code, 0, 0, colors[sysIdx]);
                row.setVisibility(View.GONE);
                containers[sysIdx].addView(row);
                rows.add(row);
            }
            preBuiltSigRows.put(SYS_NAMES[sysIdx], rows);
        }
    }

    private void resetSignalGroups() {
        sigGroupGps.setVisibility(View.VISIBLE);
        sigGroupBds.setVisibility(View.VISIBLE);
        sigGroupGlo.setVisibility(View.VISIBLE);
        sigGroupGal.setVisibility(View.VISIBLE);
        // 预建行只设 GONE，不删除
        for (List<LinearLayout> rows : preBuiltSigRows.values()) {
            for (LinearLayout row : rows) row.setVisibility(View.GONE);
        }
    }

    private void resetTiles() {
        tileGpsCount.setText("—");
        tileBdsCount.setText("—");
        tileGloCount.setText("—");
        tileGalCount.setText("—");
    }

    private void resetOverview() {
        ovFileName.setText("—");
        ovFileDir.setText("—");
        ovFileSize.setText("—");
        ovDuration.setText("—");
        updateCarrierBadge(false);
    }

    // ==================== Recording UI ====================

    private void updateRecordingUI(boolean isRecording) {
        if (isRecording) {
            recordingStatus.setText(getString(R.string.recording));
            recordingStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            statusDot.setImageResource(R.drawable.status_dot_active);

            tvRecordButton.setBackgroundResource(R.drawable.bg_record_stop);
            tvRecordButton.setText(R.string.btn_stop_recording);
            tvRecordButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));

            // 展开计时胶囊：显示大时间
            capsuleIdleContent.setVisibility(View.GONE);
            capsuleRecordingContent.setVisibility(View.VISIBLE);
            animateCapsuleHeight(dp(132));

            startPulseAnimation();
        } else {
            recordingStatus.setText(getString(R.string.recording_idle));
            recordingStatus.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            statusDot.setImageResource(R.drawable.status_dot);

            tvRecordingTime.setText("00:00:00");
            tvRecordingSize.setText("—");

            tvRecordButton.setBackgroundResource(R.drawable.bg_record_start);
            tvRecordButton.setText(R.string.btn_start_recording);
            tvRecordButton.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));

            // 收拢计时胶囊：显示紧凑提示
            capsuleIdleContent.setVisibility(View.VISIBLE);
            capsuleRecordingContent.setVisibility(View.GONE);
            animateCapsuleHeight(dp(56));

            stopPulseAnimation();
        }
    }

    /** 动画改变计时胶囊高度 */
    private void animateCapsuleHeight(int targetH) {
        if (timerCapsule == null) return;
        int from = timerCapsule.getHeight();
        if (from <= 0) from = targetH;
        ValueAnimator anim = ValueAnimator.ofInt(from, targetH);
        anim.setDuration(260);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = timerCapsule.getLayoutParams();
            if (lp != null) {
                lp.height = (int) a.getAnimatedValue();
                timerCapsule.setLayoutParams(lp);
            }
        });
        anim.start();
    }

    // ==================== Animation ====================

    private void startPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ValueAnimator.ofFloat(0.4f, 1.0f);
        pulseAnimator.setDuration(800);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(a ->
                statusDot.setAlpha((float) a.getAnimatedValue()));
        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        statusDot.setAlpha(1.0f);
    }

    // ==================== Recording Settings ====================

    private void updateKeepScreenOnButton() {
        if (keepScreenOn) {
            btnKeepScreenOn.setBackgroundResource(R.drawable.bg_keep_screen_on);
            btnKeepScreenOn.setTextColor(ContextCompat.getColor(requireContext(), R.color.keep_screen_on_text));
        } else {
            btnKeepScreenOn.setBackgroundResource(R.drawable.bg_keep_screen_off);
            btnKeepScreenOn.setTextColor(ContextCompat.getColor(requireContext(), R.color.keep_screen_off_text));
        }
    }

    private void applyKeepScreenOn() {
        if (getActivity() == null) return;
        if (keepScreenOn) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private int getThemeColor(int attrRes) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }

    // ==================== Lifecycle ====================

    @Override
    public void onResume() {
        super.onResume();
        Boolean recording = dataViewModel.getIsRecording().getValue();
        updateRecordingUI(recording != null && recording);

        // 录制中恢复页面：重新绑定 service 以确保能停止录制
        if (Boolean.TRUE.equals(recording) && !serviceBound) {
            Intent serviceIntent = new Intent(requireContext(), RecordingService.class);
            requireActivity().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            // 回调在 onDestroyView 时未注销（录制中保持），无需重新注册
        }
    }

    private boolean permissionGrantedPending = false;

    /** 由 MainActivity 在位置权限授予后调用 */
    public void onLocationPermissionGranted() {
        if (dataViewModel == null) {
            permissionGrantedPending = true;
            return;
        }
        applyPermissionGranted();
    }

    private void applyPermissionGranted() {
        // GNSS 回调不在此注册 —— 由 startRecording() 在用户点击录制时激活
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPulseAnimation();

        // 录制中保持回调注册以确保数据持续写入；仅闲置态才注销
        boolean isRec = Boolean.TRUE.equals(dataViewModel.getIsRecording().getValue());
        if (!isRec) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).removeMeasurementListener(dataViewModel);
            }
            dataViewModel.unregisterGnssCallback();
        }

        // Unbind service
        if (serviceBound) {
            requireActivity().unbindService(serviceConnection);
            serviceBound = false;
        }
        // 录制中保持 BroadcastReceiver 注册以接收 service 停止通知
        if (!isRec) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stateReceiver);
        }
    }
}
