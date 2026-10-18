package com.ecut.pnav.ui.Sol;

import static android.content.Context.MODE_PRIVATE;
import static com.ecut.pnav.MainActivity.Cn0DbHz;
import static com.ecut.pnav.MainActivity.auto_record_mode;
import static com.ecut.pnav.MainActivity.checked;
import static com.ecut.pnav.MainActivity.if_auto_record;
import static com.ecut.pnav.MainActivity.if_setting;
import static com.ecut.pnav.ui.Sol.SolViewModel.prcopt;
import static com.ecut.pnav.ui.measure_util.calculateSelectedValue;
import static com.ecut.pnav.ui.measure_util.prcopt_default;
import static com.ecut.pnav.ui.measure_util.solopt_default;
import static com.rtklib.rtkpos.rtkPos.D2R;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.TextUtils;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.ecut.pnav.R;
import com.rtklib.bean.prcopt_t;

/**
 * 解算参数设置 Fragment — 配置电离层/对流层模型、截止角、模糊度解算模式等。
 */
public class Solragment extends Fragment {
    private Chip chip_bds, chip_gps, chip_glonass, chip_galileo;
    private ChipGroup gnss_chip_group;
    private SolViewModel solViewModel;
    private Spinner iono_spinner;
    private Spinner tropo_spinner;
    private Spinner azel;
    private Spinner ambres;
    private Spinner ambres_glo;
    private Spinner cn0;
    private Spinner Freq;
    private Spinner stochasticSpinner;
    private RadioGroup record_format;
    EditText editText;
    private static final String PREFS_NAME_SETTING = "solSetting";
    private Button save_btn;

    public Solragment() {
        // Required empty public constructor
    }

    @Override
    public void onPause() {
        super.onPause();
        // 保存用户选择到 SharedPreferences
        SharedPreferences sharedPref = requireActivity().getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("iono_selection", iono_spinner.getSelectedItemPosition()); // 保存iono_spinner的选择
        editor.putInt("tropo_selection", tropo_spinner.getSelectedItemPosition()); // 保存tropo_spinner的选择
        editor.putInt("azel_selection", azel.getSelectedItemPosition());
        editor.putInt("cn0_selection", cn0.getSelectedItemPosition());
        editor.putInt("ambres_selection", ambres.getSelectedItemPosition());
        editor.putInt("ambres_selection_bds", 1);  // BDS AR 默认开启，不再显示设置项
        editor.putInt("ambres_selection_glo", ambres_glo.getSelectedItemPosition());
        editor.putInt("stochastic_selection", stochasticSpinner.getSelectedItemPosition());
        editor.putInt("freq_selection", Freq.getSelectedItemPosition());
        editor.apply();
    }
    @Override
    public void onResume() {
        super.onResume();
        // 恢复用户选择
        SharedPreferences sharedPref = requireActivity().getPreferences(MODE_PRIVATE);
        int ionoSelection = sharedPref.getInt("iono_selection", 0); // OFF
        int tropoSelection = sharedPref.getInt("tropo_selection", 0); // OFF
        int azelSelection = sharedPref.getInt("azel_selection", 3); // 15° (对齐 prcopt_default)
        int cn0Selection = sharedPref.getInt("cn0_selection", 0); // 0 dBHz
        int ambresSelection = sharedPref.getInt("ambres_selection", 1); // Continuous (对齐 prcopt_default)
        int ambresSelection_glo = sharedPref.getInt("ambres_selection_glo", 0);
        int stochasticSelection = sharedPref.getInt("stochastic_selection", 0); // 0=高度角模型
        int freqSelection = Math.min(sharedPref.getInt("freq_selection", 0), 1);
        // 设置Spinner的选择
        iono_spinner.setSelection(ionoSelection);
        tropo_spinner.setSelection(tropoSelection);
        azel.setSelection(azelSelection);
        ambres.setSelection(ambresSelection);
        ambres_glo.setSelection(ambresSelection_glo);
        cn0.setSelection(cn0Selection);
        stochasticSpinner.setSelection(stochasticSelection);
        Freq.setSelection(freqSelection);
    }
    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        solViewModel = new ViewModelProvider(this).get(SolViewModel.class);
        View view = inflater.inflate(R.layout.fragment_solragment, container, false);
        iono_spinner = view.findViewById(R.id.ionosphere_model_spinner);
        tropo_spinner = view.findViewById(R.id.troposphere_model_spinner);
        azel = view.findViewById(R.id.azel_spinner);
        cn0 = view.findViewById(R.id.CN0_Threshold_spinner);
        ambres = view.findViewById(R.id.ambiguity_Res_spinner_GPS);
        ambres_glo = view.findViewById(R.id.ambiguity_Res_spinner_GLO);
        Freq = view.findViewById(R.id.Frequency_spinner);
        stochasticSpinner = view.findViewById(R.id.stochastic_model_spinner);
        editText = view.findViewById(R.id.min_fix_res_value);
        save_btn = view.findViewById(R.id.btn_save_setting);
        record_format = view.findViewById(R.id.radioGroup);
        // Initialize GNSS ChipGroup
        gnss_chip_group = view.findViewById(R.id.gnss_chip_group);
        chip_gps = view.findViewById(R.id.chip_gps);
        chip_bds = view.findViewById(R.id.chip_bds);
        chip_glonass = view.findViewById(R.id.chip_glonass);
        chip_galileo = view.findViewById(R.id.chip_galileo);
        solViewModel.solSettingPrefer = requireContext().getSharedPreferences(PREFS_NAME_SETTING, MODE_PRIVATE);
        String savedValue = solViewModel.solSettingPrefer.getString("inputValue", "");
        // Read saved state and set Chips
        editText.setText(savedValue);
        chip_gps.setChecked(solViewModel.solSettingPrefer.getBoolean("GPS", true));
        chip_bds.setChecked(solViewModel.solSettingPrefer.getBoolean("BDS", false));
        chip_glonass.setChecked(solViewModel.solSettingPrefer.getBoolean("GLONASS", false));
        chip_galileo.setChecked(solViewModel.solSettingPrefer.getBoolean("GALILEO", false));
        // Update checked array
        updateValue();
        // Set ChipGroup change listener
        gnss_chip_group.setOnCheckedChangeListener((group, checkedId) -> updateValue());
        // 读取上一次的选择
        int selectedRadioButtonId = solViewModel.solSettingPrefer.getInt("record_format", -1);
        // 设置上一次的选择
        if (selectedRadioButtonId != -1) {
            record_format.check(selectedRadioButtonId);
            if (selectedRadioButtonId == R.id.radioButtonXYZ) {
                solopt_default.posf = 1;
            } else if (selectedRadioButtonId == R.id.radioButtonBLH) {
                solopt_default.posf = 0;
            }
        }else{
            //如果是第一次使用APP，进行默认设置
            RadioButton radioButtonAutomatic = record_format.findViewById(R.id.radioButtonBLH);
            radioButtonAutomatic.setChecked(true);
            solopt_default.posf = 0;
        }
        // 始终开启自动记录（手动记录已移除）
        if_auto_record = true;
        auto_record_mode = true;
        // 创建适配器并设置数据
        // 使用正确的构造函数来创建 ArrayAdapter 对象
        ArrayAdapter<String> tropo_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.troposphere_options));
        ArrayAdapter<String> iono_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.ionosphere_options));
        ArrayAdapter<String> azel_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, SolViewModel.azel);
        ArrayAdapter<String> ambres_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.ambres_options));
        ArrayAdapter<String> ambres_adapter_glo = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.ambres_glo_options));
        ArrayAdapter<String> cn0_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, SolViewModel.cn0);
        ArrayAdapter<String> Freq_adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.freq_options));
        iono_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tropo_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        azel_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ambres_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ambres_adapter_glo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cn0_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Freq_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 将适配器设置给 Spinner
        iono_spinner.setAdapter(iono_adapter);
        tropo_spinner.setAdapter(tropo_adapter);
        azel.setAdapter(azel_adapter);
        ambres.setAdapter(ambres_adapter);
        ambres_glo.setAdapter(ambres_adapter_glo);
        cn0.setAdapter(cn0_adapter);
        Freq.setAdapter(Freq_adapter);

        /* 随机模型 Spinner */
        ArrayAdapter<String> stochasticAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.stochastic_model_options));
        stochasticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        stochasticSpinner.setAdapter(stochasticAdapter);

        // Inflate the layout for this fragment
        return view;
    }
    private void updateValue() {
        for (int i = 0; i < 4; i++) {
            checked[i] = false;
        }
        if (chip_gps.isChecked()) { // GPS
            checked[0] = true;
        }
        if (chip_bds.isChecked()) { // BDS
            checked[1] = true;
        }
        if (chip_glonass.isChecked()) { // GLONASS
            checked[2] = true;
        }
        if (chip_galileo.isChecked()) { // GALILEO
            checked[3] = true;
        }
        // 同步更新 prcopt.navsys，使各定位模式的星座选择生效
        prcopt.setNavsys(calculateSelectedValue(checked));
    }
    private void saveCheckboxState() {
        // 写入 solSetting
        SharedPreferences.Editor editor = solViewModel.solSettingPrefer.edit();
        editor.putBoolean("BDS", chip_bds.isChecked());
        editor.putBoolean("GPS", chip_gps.isChecked());
        editor.putBoolean("GALILEO", chip_galileo.isChecked());
        editor.putBoolean("GLONASS", chip_glonass.isChecked());
        editor.apply();
        // 同步写入 gnss_prefs，保持与 MainActivity/引导向导一致
        SharedPreferences gnssPrefs = requireContext().getSharedPreferences("gnss_prefs", Context.MODE_PRIVATE);
        gnssPrefs.edit()
                .putBoolean("pref_gps", chip_gps.isChecked())
                .putBoolean("pref_bds", chip_bds.isChecked())
                .putBoolean("pref_glonass", chip_glonass.isChecked())
                .putBoolean("pref_galileo", chip_galileo.isChecked())
                .apply();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        record_format.setOnCheckedChangeListener((group, checkedId) -> {
            SharedPreferences.Editor editor = solViewModel.solSettingPrefer.edit();
            editor.putInt("record_format", checkedId);
            editor.apply();
            if (checkedId == R.id.radioButtonXYZ) {
                solopt_default.posf = 1;
            } else if (checkedId == R.id.radioButtonBLH) {
                solopt_default.posf = 0;
            } else {
                solopt_default.posf = 0;
            }
        });
        iono_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Log.d("SelectedPosition", String.valueOf(position));
                if (position == 0) {
                    prcopt.ionoopt = 0;
                } else if (position == 1) {
                    prcopt.ionoopt = 1;
                } else if (position == 2) {
                    prcopt.ionoopt = 3;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 当没有选择任何选项时触发的事件
                Log.d("SelectedPosition", "Nothing selected");
            }
        });
        tropo_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // 当选择了 Spinner 中的某个选项时触发的事件
                Log.d("SelectedPosition", String.valueOf(position)); // 获取选中的选项在 Spinner 中的位置
                if(position==0){
                    prcopt.tropopt=0;
                }else if(position==1){
                    prcopt.tropopt=1;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 当没有选择任何选项时触发的事件
                Log.d("SelectedPosition", "Nothing selected");
            }
        });
        azel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // 当选择了 Spinner 中的某个选项时触发的事件
                Log.d("SelectedPosition", String.valueOf(position)); // 获取选中的选项在 Spinner 中的位置
                if (position >= 0 && position <= 18) {
                    prcopt.setElmin((position * 5) * D2R);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 当没有选择任何选项时触发的事件
                Log.d("SelectedPosition", "Nothing selected");
            }
        });
        cn0.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // 当选择了 Spinner 中的某个选项时触发的事件
                Log.d("SelectedPosition", String.valueOf(position)); // 获取选中的选项在 Spinner 中的位置
                if (position >= 0 && position <= 9) {
                    Cn0DbHz = position*5;
//                    prcopt.setElmin((position * 5) * D2R);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 当没有选择任何选项时触发的事件
                Log.d("SelectedPosition", "Nothing selected");
            }
        });
        Freq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                prcopt.setNf(position == 0 ? 1 : 2); // 0=L1, 1=L1+L2/L5
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) { }
        });
        stochasticSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                prcopt.setVarmodel(position); // 0=高度角, 1=信噪比
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) { }
        });
        ambres.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // 当选择了 Spinner 中的某个选项时触发的事件
                Log.d("SelectedPosition", String.valueOf(position)); // 获取选中的选项在 Spinner 中的位置
                if(position==0){
                    prcopt.setModear(0);
                }else if(position==1){
                    prcopt.setModear(1);
                }else if(position==2){
                    prcopt.setModear(2);
                }else if(position==3){
                    prcopt.setModear(3);
                }else{
                    prcopt.setModear(0);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 当没有选择任何选项时触发的事件
                Log.d("SelectedPosition", "Nothing selected");
            }
        });
        // 设置点击事件
        save_btn.setOnClickListener(v -> {
            // 在这里处理按钮点击事件
            if_setting = true;
            updateValue();
            saveCheckboxState();
            String inputValue = editText.getText().toString();
            if(!TextUtils.isEmpty(inputValue)){
                prcopt.setThresar(new double[]{Float.parseFloat(inputValue), 0.9999, 0.25, 0.1, 0.05});
            }
            // 保存输入的值
            SharedPreferences.Editor editor = solViewModel.solSettingPrefer.edit();
            editor.putString("inputValue", inputValue);
            editor.apply();
            Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show();
            requireActivity().onBackPressed();
        });
        editText.setOnFocusChangeListener((v1, hasFocus) -> {
            if (!hasFocus) {
                String inputValue = editText.getText().toString();
                prcopt.setThresar(new double[]{Float.parseFloat(inputValue), 0.9999, 0.25, 0.1, 0.05});
                SharedPreferences.Editor editor = solViewModel.solSettingPrefer.edit();
                editor.putString("inputValue", inputValue);
                editor.apply();
            }
        });
    }
}