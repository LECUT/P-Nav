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
package com.ecut.pnav.ui.RTK;

import static com.ecut.pnav.MainActivity.infile;
import static com.ecut.pnav.MainActivity.outputFilePath;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ecut.pnav.MainActivity;
import com.ecut.pnav.R;
import com.ecut.pnav.ui.GnssMeasurementListener;
import com.ecut.pnav.ui.LogLineAdapter;
import com.ecut.pnav.ui.SolHistoryAdapter;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import com.google.android.material.tabs.TabLayout;

import java.io.IOException;

public class RtkFragment extends Fragment implements GnssMeasurementListener {
    private static final int PERMISSION_REQUEST_CODE = 129;
    private RtkViewModel rtkViewModel;
    private boolean measurementsRegistered = false;

    public static RtkFragment newInstance() {
        return new RtkFragment();
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rtkViewModel = new ViewModelProvider(this, new RTKViewModelFactory(requireContext()))
                .get(RtkViewModel.class);
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

        // ── RTK 需要全部段：RTCM + 解算状态 ──
        root.findViewById(R.id.detail_rtcm_row).setVisibility(View.VISIBLE);
        root.findViewById(R.id.detail_card_solstate).setVisibility(View.VISIBLE);

        // ── 设置行标签 + 获取值视图 ──
        TextView satCount = root.findViewById(R.id.detail_sat_count);
        TextView epochCount = root.findViewById(R.id.detail_epoch_count);
        TextView latValue = root.findViewById(R.id.detail_lat_value);
        TextView lonValue = root.findViewById(R.id.detail_lon_value);
        TextView eleValue = root.findViewById(R.id.detail_ele_value);
        TextView rtcmCount = root.findViewById(R.id.detail_rtcm_count);
        TextView eAcc = setupRow(root, R.id.row_e_acc, R.string.label_east);
        TextView nAcc = setupRow(root, R.id.row_n_acc, R.string.label_north);
        TextView uAcc = setupRow(root, R.id.row_u_acc, R.string.label_up);

        TextView solState = root.findViewById(R.id.detail_sol_state);
        TextView ratio = root.findViewById(R.id.detail_ratio);

        // ── 日志 RecyclerView ──
        RecyclerView logList = root.findViewById(R.id.detail_log_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        logList.setLayoutManager(layoutManager);
        LogLineAdapter logAdapter = new LogLineAdapter();
        logList.setAdapter(logAdapter);
        rtkViewModel.getLogLine().observe(getViewLifecycleOwner(), line -> {
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

        TextView clearBtn = root.findViewById(R.id.detail_log_clear);
        clearBtn.setOnClickListener(v -> {
            logAdapter.clear();
            rtkViewModel.clearLog();
        });

        // ── LiveData 观察 ──
        rtkViewModel.getText().observe(getViewLifecycleOwner(), satCount::setText);
        rtkViewModel.accuracyTracker.epochCount.observe(getViewLifecycleOwner(), c -> {
            epochCount.setText(String.valueOf(c));
        });
        rtkViewModel.getrtcmnum().observe(getViewLifecycleOwner(), rtcmCount::setText);
        rtkViewModel.getlatvalue().observe(getViewLifecycleOwner(), latValue::setText);
        rtkViewModel.getlonvalue().observe(getViewLifecycleOwner(), lonValue::setText);
        rtkViewModel.getelevation().observe(getViewLifecycleOwner(), eleValue::setText);
        rtkViewModel.getsolstate().observe(getViewLifecycleOwner(), solState::setText);
        rtkViewModel.getTextColor().observe(getViewLifecycleOwner(), solState::setBackgroundColor);
        rtkViewModel.getUpdateRatio().observe(getViewLifecycleOwner(), ratio::setText);
        rtkViewModel.getUpdateVAccuracy().observe(getViewLifecycleOwner(), eAcc::setText);
        rtkViewModel.getUpdateHAccuracy().observe(getViewLifecycleOwner(), nAcc::setText);
        rtkViewModel.getUpdateUAccuracy().observe(getViewLifecycleOwner(), uAcc::setText);
        rtkViewModel.getUpdateStatus().observe(getViewLifecycleOwner(), status -> {
            String msg = status;
            if (RtkViewModel.EPH_CORS_UPDATED.equals(status)) msg = getString(R.string.eph_cors_updated);
            else if (RtkViewModel.EPH_PATH_NULL.equals(status)) msg = getString(R.string.eph_path_null);
            else if (RtkViewModel.EPH_UPTODATE.equals(status)) msg = getString(R.string.eph_uptodate);
            else if (RtkViewModel.EPH_UPDATE_OK.equals(status)) msg = getString(R.string.eph_updated_ok);
            else if (RtkViewModel.EPH_UPDATE_FAILED.equals(status)) msg = getString(R.string.eph_update_failed);
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        });

        rtkViewModel.startScheduledTask(requireContext(), infile, outputFilePath);

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
    }

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
        Log.d("SPP", "measurement called");
        rtkViewModel.processGnssMeasurements(eventArgs);
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
    public void onDestroyView() {
        super.onDestroyView();
        unRegisterMeasurements();
        try {
            if (RtkViewModel.tips != null) {
                RtkViewModel.tips.setLength(0);
            }
            if (rtkViewModel != null && rtkViewModel.bufferedWriter != null) {
                try {
                    rtkViewModel.bufferedWriter.flush();
                    rtkViewModel.bufferedWriter.close();
                } catch (IOException e) {
                    Log.e("RtkFragment", "Failed to close writer", e);
                } finally {
                    rtkViewModel.bufferedWriter = null;
                }
            }
        } catch (Exception e) {
            Log.e("RtkFragment", "Error in onDestroyView", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unRegisterMeasurements();
        } catch (Exception e) {
            Log.e("RtkFragment", "Error in onDestroy", e);
        }
        try {
            if (RtkViewModel.tips != null) {
                RtkViewModel.tips.setLength(0);
            }
            if (rtkViewModel != null && rtkViewModel.bufferedWriter != null) {
                try {
                    rtkViewModel.bufferedWriter.flush();
                    rtkViewModel.bufferedWriter.close();
                } catch (IOException e) {
                    Log.e("RtkFragment", "Failed to close writer", e);
                } finally {
                    rtkViewModel.bufferedWriter = null;
                }
            }
        } catch (Exception e) {
            Log.e("RtkFragment", "Error cleaning up", e);
        }
    }
}
