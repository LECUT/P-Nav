package com.ecut.pnav.ui.Ntrip;

import static android.content.Context.MODE_PRIVATE;
import static com.ecut.pnav.positiondatabase.getLatestData;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.ecut.pnav.R;
import com.ecut.pnav.positiondatabase;
import com.ecut.pnav.ui.MyService;
import com.ecut.pnav.ui.ToastBroadcastReceiver;
import com.ecut.pnav.ui.status.HomeViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.rtklib.bean.gtime_t;
import com.rtklib.bean.nav_t;
import com.rtklib.bean.obsd_t;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Objects;

public class NtripFragment extends Fragment implements OnRtcmDataReceivedListener {
    private NtripViewModel ntripViewModel;
    private ToastBroadcastReceiver mReceiver;

    private TextInputEditText etIp, etPort, etMount, etUser, etPassword;
    private TextInputEditText etGgaLat, etGgaLon;
    private RadioGroup radioGgaSource;
    private View layoutGgaManual;
    private androidx.appcompat.widget.SwitchCompat switchNtrip;
    private com.google.android.material.button.MaterialButton btnRefreshMount;

    private SharedPreferences sharedPreferences;

    static {
        System.loadLibrary("pnav");
    }
    public static native int inputrtcm3(rtcm_t rtcm, byte data, obsd_t[] obss, gtime_t[] time, double[] stapos);
    public static native int inputrtcm2(rtcm_t rtcm, byte data, obsd_t[] obss, gtime_t[] time, double[] stapos);
    public static native void initrtcm();
    public static native void initrtcm2();
    /** 从 CORS RTCM 实时解码的星历中提取 nav_t，无需 FTP 下载 */
    public static native nav_t extractRtcmNav();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReceiver = new ToastBroadcastReceiver();
        ntripViewModel = new ViewModelProvider(this, new NtripViewModelFactory(requireContext())).get(NtripViewModel.class);
        ntripViewModel.setDataReceivedListener(this);
        sharedPreferences = requireContext().getSharedPreferences(
                requireContext().getPackageName() + "_preferences", MODE_PRIVATE);
    }

    private final BroadcastReceiver switchstateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean switch_state = intent.getBooleanExtra("isswitchsate", false);
            ntripViewModel.setSwitchState(switch_state);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ntrip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(switchstateReceiver,
                new IntentFilter("com.ecut.pnav.SWITCH_STATE"));

        // 绑定视图
        etIp = view.findViewById(R.id.et_ip);
        etPort = view.findViewById(R.id.et_port);
        etMount = view.findViewById(R.id.et_mount);
        etUser = view.findViewById(R.id.et_user);
        etPassword = view.findViewById(R.id.et_password);
        etGgaLat = view.findViewById(R.id.et_gga_lat);
        etGgaLon = view.findViewById(R.id.et_gga_lon);
        switchNtrip = view.findViewById(R.id.switch_ntrip);
        btnRefreshMount = view.findViewById(R.id.btn_refresh_mount);
        radioGgaSource = view.findViewById(R.id.radio_gga_source);
        layoutGgaManual = view.findViewById(R.id.layout_gga_manual);

        // 加载已保存的配置
        loadSavedConfig();

        // Toast 观察（token → 本地化文案）
        ntripViewModel.getToastMessage().observe(getViewLifecycleOwner(), message -> {
            String msg = message;
            if (NtripViewModel.NT_FETCHING.equals(message)) msg = getString(R.string.ntrip_fetching);
            else if (NtripViewModel.NT_BAD_PWD.equals(message)) msg = getString(R.string.ntrip_bad_pwd);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        // 服务开关状态
        ntripViewModel.getSwitchState().observe(getViewLifecycleOwner(), isChecked -> {
            if (switchNtrip != null) switchNtrip.setChecked(isChecked != null && isChecked);
        });

        // Switch 切换 → 启动/停止服务
        switchNtrip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ntripViewModel.setSwitchState(isChecked);
            if (isChecked) {
                // 保存当前配置并启动服务
                saveCurrentConfig();
                startNtripService();
            } else {
                NtripViewModel.stoprtcmReceiver();
                stopNtripService();
            }
        });

        // 刷新挂载点
        btnRefreshMount.setOnClickListener(v -> {
            String ip = etIp.getText() != null ? etIp.getText().toString().trim() : "";
            String portStr = etPort.getText() != null ? etPort.getText().toString().trim() : "8001";
            ntripViewModel.Mountpoint.clear();
            int nport;
            try { nport = Integer.parseInt(etPort.getText() != null ? etPort.getText().toString().trim() : "8001"); }
            catch (NumberFormatException ex) { nport = 8001; }
            final int fport = nport;
            new Thread(() -> ntripViewModel.getSourceTable(ip, fport, ntripViewModel.Mountpoint)).start();
            // 延迟后显示挂载点选择对话框
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!ntripViewModel.Mountpoint.isEmpty()) {
                            showMountPointDialog();
                        } else {
                            Toast.makeText(getContext(), R.string.ntrip_mount_fail, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();
        });

        // GGA 来源选择
        radioGgaSource.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_gga_manual) {
                layoutGgaManual.setVisibility(View.VISIBLE);
            } else {
                layoutGgaManual.setVisibility(View.GONE);
            }

            if (checkedId == R.id.radio_gga_db) {
                loadGgaFromDatabase();
            } else if (checkedId == R.id.radio_gga_phone) {
                loadGgaFromPhone();
            }
        });

        // 恢复 GGA 来源选择状态
        int savedGgaSource = sharedPreferences.getInt("gga_source", R.id.radio_gga_db);
        radioGgaSource.check(savedGgaSource);
        // 恢复手动输入值
        double savedLat = Double.longBitsToDouble(sharedPreferences.getLong("gga_lat", Double.doubleToLongBits(0)));
        double savedLon = Double.longBitsToDouble(sharedPreferences.getLong("gga_lon", Double.doubleToLongBits(0)));
        if (savedLat != 0) etGgaLat.setText(String.valueOf(savedLat));
        if (savedLon != 0) etGgaLon.setText(String.valueOf(savedLon));

        initrtcm();
    }

    // ==================== 配置加载/保存 ====================

    private void loadSavedConfig() {
        etIp.setText(sharedPreferences.getString("User_ip", "120.253.226.97"));
        etPort.setText(sharedPreferences.getString("User_port", "8001"));
        etMount.setText(sharedPreferences.getString("Mount_point", "RTCM33_GRCE"));
        etUser.setText(sharedPreferences.getString("User_text", "user"));
        etPassword.setText(sharedPreferences.getString("Password_text", "password"));
    }

    private void saveCurrentConfig() {
        sharedPreferences.edit()
                .putString("User_ip", etIp.getText() != null ? etIp.getText().toString().trim() : "")
                .putString("User_port", etPort.getText() != null ? etPort.getText().toString().trim() : "8001")
                .putString("Mount_point", etMount.getText() != null ? etMount.getText().toString().trim() : "")
                .putString("User_text", etUser.getText() != null ? etUser.getText().toString().trim() : "")
                .putString("Password_text", etPassword.getText() != null ? etPassword.getText().toString().trim() : "")
                .apply();
        // 同步到 NtripViewModel
        String ip = etIp.getText() != null ? etIp.getText().toString().trim() : "120.253.226.97";
        int svcPort;
        try { svcPort = Integer.parseInt(etPort.getText() != null ? etPort.getText().toString().trim() : "8001"); }
        catch (NumberFormatException e) { svcPort = 8001; }
        String mount = etMount.getText() != null ? etMount.getText().toString().trim() : "";
        String user = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pwd = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        NtripViewModel.mData.clear();
        NtripViewModel.mData.add(new NtripClient(ip, svcPort, mount, user, pwd));
        ntripViewModel.User = user;
        ntripViewModel.Password = pwd;
    }

    /** 保存 GGA 来源选择 */
    private void saveGgaSource() {
        sharedPreferences.edit().putInt("gga_source", radioGgaSource.getCheckedRadioButtonId()).apply();
    }

    // ==================== 挂载点对话框 ====================

    private void showMountPointDialog() {
        int selectedIndex = ntripViewModel.Mountpoint.indexOf(
                etMount.getText() != null ? etMount.getText().toString().trim() : "");
        String[] items = ntripViewModel.Mountpoint.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_mount_point)
                .setSingleChoiceItems(items, Math.max(selectedIndex, 0), (dialog, which) -> {
                    etMount.setText(items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== GGA 处理 ====================

    private void loadGgaFromDatabase() {
        saveGgaSource();
        positiondatabase dbHelper = new positiondatabase(requireContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String result = getLatestData(db);
        db.close();
        if (Objects.equals(result, "No data found")) {
            Toast.makeText(requireContext(), R.string.no_positioning_data_ntrip, Toast.LENGTH_SHORT).show();
        } else {
            String[] parts = result.split(",");
            if (parts.length < 7) {
                Toast.makeText(requireContext(), R.string.no_positioning_data_ntrip, Toast.LENGTH_SHORT).show();
                return;
            }
            double latDeg = Double.parseDouble(parts[1]);
            double latMin = Double.parseDouble(parts[2]);
            double latSec = Double.parseDouble(parts[3]);
            double lonDeg = Double.parseDouble(parts[4]);
            double lonMin = Double.parseDouble(parts[5]);
            double lonSec = Double.parseDouble(parts[6]);
            NtripViewModel.gga_message[0] = latDeg + latMin / 60.0 + latSec / 3600.0;
            NtripViewModel.gga_message[1] = lonDeg + lonMin / 60.0 + lonSec / 3600.0;
            ntripViewModel.saveGgaMessage();
        }
    }

    private void loadGgaFromPhone() {
        saveGgaSource();
        double[] loc = HomeViewModel.latestGnssLatLon;
        if (loc[0] == 0 && loc[1] == 0) {
            Toast.makeText(requireContext(), R.string.ntrip_no_gnss, Toast.LENGTH_LONG).show();
        } else {
            NtripViewModel.gga_message[0] = loc[0];
            NtripViewModel.gga_message[1] = loc[1];
            ntripViewModel.saveGgaMessage();
            Toast.makeText(requireContext(), String.format(getString(R.string.ntrip_set_pos), loc[0], loc[1]), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveManualGga() {
        saveGgaSource();
        try {
            double lat = Double.parseDouble(etGgaLat.getText() != null ? etGgaLat.getText().toString() : "0");
            double lon = Double.parseDouble(etGgaLon.getText() != null ? etGgaLon.getText().toString() : "0");
            NtripViewModel.gga_message[0] = lat;
            NtripViewModel.gga_message[1] = lon;
            ntripViewModel.saveGgaMessage();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.please_enter_a_number, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 服务启动/停止 ====================

    private void startNtripService() {
        // 保存手动 GGA
        if (radioGgaSource.getCheckedRadioButtonId() == R.id.radio_gga_manual) {
            saveManualGga();
        }
        // 启动 MyService
        Intent serviceIntent = new Intent(requireContext(), MyService.class);
        serviceIntent.putExtra("NtripDataList", (Serializable) new LinkedList<>(NtripViewModel.mData));
        serviceIntent.putExtra("GgaMessage", NtripViewModel.gga_message);
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), serviceIntent);
        Toast.makeText(getContext(), R.string.ntrip_connect_success, Toast.LENGTH_SHORT).show();
    }

    private void stopNtripService() {
        Intent stopIntent = new Intent(requireContext(), MyService.class);
        requireContext().stopService(stopIntent);
    }

    // ==================== 生命周期 ====================

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mReceiver,
                new IntentFilter("com.ecut.pnav.SHOW_TOAST"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 保存手动 GGA
        if (radioGgaSource != null && radioGgaSource.getCheckedRadioButtonId() == R.id.radio_gga_manual) {
            saveManualGga();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRtcmDataReceived(rtcm_t rtcmData) {
        // RTCM 数据由 MyService 直接分发给 ViewModel
    }
}
