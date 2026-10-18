package com.ecut.pnav.ui.Spp;


import static com.ecut.pnav.MainActivity.infile;
import static com.ecut.pnav.MainActivity.outputFilePath;
import static com.ecut.pnav.MainActivity.setOutputFilePath;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.MainActivity;
import com.ecut.pnav.R;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.LogLineAdapter;
import com.ecut.pnav.ui.SolHistoryAdapter;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.IOException;

public class SppFragment extends Fragment implements GnssMeasurementListener {

    private static final int PERMISSION_REQUEST_CODE = 127;
    private SppViewModel sppViewModel;
    private boolean measurementsRegistered = false;

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
        Log.d("SPP", "measurement called");
        sppViewModel.processGnssMeasurements(eventArgs);
    }

    /* ── 通过 MainActivity 统一回调获取测量数据 ── */
    public void RegisterMeasurements() {
        if (measurementsRegistered) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).addMeasurementListener(this);
            ((MainActivity) getActivity()).setPriorityListener(this);
        }
        measurementsRegistered = true;
    }

    public void unRegisterMeasurements() {
        measurementsRegistered = false;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).clearPriorityListener(this);
            ((MainActivity) getActivity()).removeMeasurementListener(this);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // ★ 必须在 ViewModel 创建前初始化星历路径，避免 readdata() 找不到文件
        File externalStorageDir = requireActivity().getExternalFilesDir(null);
        String outpath = externalStorageDir != null ? externalStorageDir.getAbsolutePath() : "";
        setOutputFilePath(outpath, outputFilePath);

        sppViewModel = new ViewModelProvider(this, new SppViewModelFactory(requireContext()))
                .get(SppViewModel.class);

        View root = inflater.inflate(R.layout.fragment_solution_detail, container, false);

        // ── Tab 切换 ──
        TabLayout tabLayout = root.findViewById(R.id.detail_tabs);
        ScrollView pageResult = root.findViewById(R.id.page_result);
        ScrollView pageLog = root.findViewById(R.id.page_log);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_solution_result));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_solution_log));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                pageResult.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                pageLog.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // ── SPP 不需要 RTCM 行 / 解算状态卡 ──
        root.findViewById(R.id.detail_rtcm_row).setVisibility(View.GONE);
        root.findViewById(R.id.detail_card_solstate).setVisibility(View.GONE);

        // ── 设置行标签 + 获取值视图 ──
        TextView satCount = root.findViewById(R.id.detail_sat_count);
        TextView epochCount = root.findViewById(R.id.detail_epoch_count);
        TextView latValue = root.findViewById(R.id.detail_lat_value);
        TextView lonValue = root.findViewById(R.id.detail_lon_value);
        TextView eleValue = root.findViewById(R.id.detail_ele_value);
        TextView eAcc = setupRow(root, R.id.row_e_acc, R.string.label_east);
        TextView nAcc = setupRow(root, R.id.row_n_acc, R.string.label_north);
        TextView uAcc = setupRow(root, R.id.row_u_acc, R.string.label_up);

        // ── 日志 RecyclerView ──
        RecyclerView logList = root.findViewById(R.id.detail_log_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        logList.setLayoutManager(layoutManager);
        LogLineAdapter logAdapter = new LogLineAdapter();
        logList.setAdapter(logAdapter);
        sppViewModel.getLogLine().observe(getViewLifecycleOwner(), line -> {
            logAdapter.addLine(line);
            if (logAdapter.isAutoScroll()) {
                logList.scrollToPosition(logAdapter.getLineCount() - 1);
            }
        });

        // ── 日志控制：滚动锁 ──
        View scrollLock = root.findViewById(R.id.detail_log_scroll_lock);
        logList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy < 0) {
                    logAdapter.setAutoScroll(false);
                    scrollLock.setVisibility(View.VISIBLE);
                }
            }
        });
        scrollLock.setOnClickListener(v -> {
            logAdapter.setAutoScroll(true);
            scrollLock.setVisibility(View.GONE);
            logList.scrollToPosition(logAdapter.getLineCount() - 1);
        });

        // ── 清空日志 ──
        TextView clearBtn = root.findViewById(R.id.detail_log_clear);
        clearBtn.setOnClickListener(v -> {
            logAdapter.clear();
            sppViewModel.clearLog();
        });

        // ── LiveData 观察 ──
        sppViewModel.getsatcountText().observe(getViewLifecycleOwner(), satCount::setText);
        sppViewModel.accuracyTracker.epochCount.observe(getViewLifecycleOwner(), c -> {
            epochCount.setText(String.valueOf(c));
        });
        sppViewModel.getlatvalue().observe(getViewLifecycleOwner(), latValue::setText);
        sppViewModel.getlonvalue().observe(getViewLifecycleOwner(), lonValue::setText);
        sppViewModel.getelevation().observe(getViewLifecycleOwner(), eleValue::setText);
        sppViewModel.getUpdateVAccuracy().observe(getViewLifecycleOwner(), eAcc::setText);
        sppViewModel.getUpdateNAccuracy().observe(getViewLifecycleOwner(), nAcc::setText);
        sppViewModel.getUpdateUAccuracy().observe(getViewLifecycleOwner(), uAcc::setText);
        sppViewModel.getUpdateStatus().observe(getViewLifecycleOwner(), status -> {
            if (!isAdded()) return;
            String msg = status;
            if (SppViewModel.EPH_CORS_UPDATED.equals(status)) msg = getString(R.string.eph_cors_updated);
            else if (SppViewModel.EPH_PATH_NULL.equals(status)) msg = getString(R.string.eph_path_null);
            else if (SppViewModel.EPH_BRDC_READY.equals(status)) msg = getString(R.string.eph_brdc_ready);
            else if (SppViewModel.EPH_NRT_TRYING.equals(status)) msg = getString(R.string.eph_nrt_trying);
            else if (SppViewModel.EPH_NRT_READY.equals(status)) msg = getString(R.string.eph_nrt_ready);
            else if (SppViewModel.EPH_UNAVAILABLE.equals(status)) msg = getString(R.string.eph_unavailable);
            else if (SppViewModel.EPH_UPDATE_FAILED.equals(status)) msg = getString(R.string.eph_update_failed);
            else if (SppViewModel.EPH_MODE_SAVED.equals(status)) msg = getString(R.string.eph_mode_saved);
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        // ── 权限检查 + GNSS 注册 ──
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            RegisterMeasurements();
        }
        return root;
    }

    /** 设置 include 行的标签文本，返回该行的值 TextView */
    private TextView setupRow(View root, int rowId, int labelResId) {
        View row = root.findViewById(rowId);
        ((TextView) row.findViewById(R.id.detail_label)).setText(labelResId);
        return (TextView) row.findViewById(R.id.detail_value);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            RegisterMeasurements();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sppViewModel.startScheduledTask(requireContext(), infile, outputFilePath);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unRegisterMeasurements();
        try {
            Log.d("spp", "destroy");
            if (SppViewModel.tips != null) {
                SppViewModel.tips.setLength(0);
            }
            if (sppViewModel != null && sppViewModel.bufferedWriter != null) {
                try {
                    sppViewModel.bufferedWriter.flush();
                    sppViewModel.bufferedWriter.close();
                } catch (IOException e) {
                    Log.e("SppFragment", "Failed to close writer", e);
                } finally {
                    sppViewModel.bufferedWriter = null;
                }
            }
        } catch (Exception e) {
            Log.e("SppFragment", "Error in onDestroyView", e);
        }
    }
}
